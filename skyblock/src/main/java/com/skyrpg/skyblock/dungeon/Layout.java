package com.skyrpg.skyblock.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;

/**
 * A floor's map: Hypixel's square grid of 32-block cells, filled with rooms and joined by doors. Pure logic: it knows
 * nothing of blocks (see {@code Run} for building it).
 *
 * <p>The Entrance and the Blood room go far apart; the Fairy room, the puzzles and the trap are single cells and dead
 * ends (one door each); normal rooms fill the rest in Hypixel's shapes (1×1, 1×2, 1×3, 1×4, 2×2, L). The doors are a
 * random spanning tree from the Entrance (dead ends never lead on) plus a few extra links. Some doors on the way to the
 * Blood room become Wither doors (each with its key in a room before it), and the Blood room's door needs the Blood Key.
 */
public final class Layout {
    public enum Type {
        ENTRANCE, NORMAL, PUZZLE, TRAP, FAIRY, BLOOD
    }

    public enum Shape {
        // Cells as (dx, dz) offsets from the shape's corner, in its own (unrotated) orientation.
        ONE(new int[][] {{0, 0}}), TWO(new int[][] {{0, 0}, {1, 0}}), THREE(new int[][] {{0, 0}, {1, 0}, {2, 0}}),
        FOUR(new int[][] {{0, 0}, {1, 0}, {2, 0}, {3, 0}}), SQUARE(new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}}),
        L(new int[][] {{0, 0}, {1, 0}, {0, 1}});

        final int[][] cells;

        Shape(int[][] cells) {
            this.cells = cells;
        }

        /** Width and depth (cells) in its own orientation. */
        public int width() {
            int w = 0;
            for (int[] c : cells) {
                w = Math.max(w, c[0] + 1);
            }
            return w;
        }

        public int depth() {
            int d = 0;
            for (int[] c : cells) {
                d = Math.max(d, c[1] + 1);
            }
            return d;
        }
    }

    /** A room: its type, shape, quarter turns clockwise (0-3), corner cell (after turning), and the cells it covers. */
    public record Room(int id, Type type, Shape shape, int turns, int x, int z, List<int[]> cells) {
    }

    /** A door between two neighbouring cells (of two rooms), and what opens it. */
    public record Door(int roomA, int roomB, int ax, int az, int bx, int bz, Lock lock) {
    }

    public enum Lock {
        OPEN, WITHER, BLOOD
    }

    /** Where a key is: the room it's found in, for the door it opens. */
    public record Key(int room, int door, Lock lock) {
    }

    public final int width;
    public final int depth;
    public final List<Room> rooms = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();
    public final List<Key> keys = new ArrayList<>();
    private final int[][] grid;

    private Layout(int width, int depth) {
        this.width = width;
        this.depth = depth;
        this.grid = new int[width][depth];
        for (int[] column : grid) {
            java.util.Arrays.fill(column, -1);
        }
    }

    public Room roomAt(int x, int z) {
        return x < 0 || z < 0 || x >= width || z >= depth || grid[x][z] < 0 ? null : rooms.get(grid[x][z]);
    }

    public Room room(Type type) {
        return rooms.stream().filter(r -> r.type() == type).findFirst().orElse(null);
    }

    /**
     * A new map. {@code puzzles}, {@code traps}: how many of each; {@code witherDoors}: how many Wither doors (fewer if the
     * way to the Blood room is short). Tries again until every room is reachable.
     */
    public static Layout generate(int width, int depth, int puzzles, int traps, int witherDoors, RandomSource random) {
        for (int attempt = 0; attempt < 200; attempt++) {
            Layout layout = new Layout(width, depth);
            if (layout.build(puzzles, traps, witherDoors, random)) {
                return layout;
            }
        }
        throw new IllegalStateException("No dungeon layout fits " + width + "x" + depth);
    }

    private boolean build(int puzzles, int traps, int witherDoors, RandomSource random) {
        // The Entrance on an edge, the Blood room as far from it as can be.
        int ex;
        int ez;
        if (random.nextBoolean()) {
            ex = random.nextInt(width);
            ez = random.nextBoolean() ? 0 : depth - 1;
        } else {
            ex = random.nextBoolean() ? 0 : width - 1;
            ez = random.nextInt(depth);
        }
        single(Type.ENTRANCE, ex, ez);
        List<int[]> far = new ArrayList<>();
        int best = -1;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int d = Math.abs(x - ex) + Math.abs(z - ez);
                if (d > best) {
                    best = d;
                    far.clear();
                }
                if (d == best) {
                    far.add(new int[] {x, z});
                }
            }
        }
        int[] blood = far.get(random.nextInt(far.size()));
        single(Type.BLOOD, blood[0], blood[1]);
        // The dead ends: Fairy, puzzles, traps, not next to the Entrance or the Blood room.
        List<Type> extras = new ArrayList<>();
        extras.add(Type.FAIRY);
        for (int i = 0; i < puzzles; i++) {
            extras.add(Type.PUZZLE);
        }
        for (int i = 0; i < traps; i++) {
            extras.add(Type.TRAP);
        }
        for (Type type : extras) {
            List<int[]> free = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (grid[x][z] < 0 && !nextTo(x, z, Type.ENTRANCE) && !nextTo(x, z, Type.BLOOD) && !nextToDeadEnd(x, z)) {
                        free.add(new int[] {x, z});
                    }
                }
            }
            if (free.isEmpty()) {
                return false;
            }
            int[] cell = free.get(random.nextInt(free.size()));
            single(type, cell[0], cell[1]);
        }
        // Normal rooms in Hypixel's shapes, biggest first where they fit.
        List<int[]> order = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                order.add(new int[] {x, z});
            }
        }
        shuffle(order, random);
        Shape[] weighted = {Shape.SQUARE, Shape.FOUR, Shape.THREE, Shape.THREE, Shape.L, Shape.L, Shape.TWO, Shape.TWO, Shape.TWO, Shape.ONE};
        for (int[] cell : order) {
            if (grid[cell[0]][cell[1]] >= 0) {
                continue;
            }
            List<Shape> shapes = new ArrayList<>(List.of(weighted));
            shuffle(shapes, random);
            boolean placed = false;
            for (Shape shape : shapes) {
                for (int turns : shuffled(random, 0, 1, 2, 3)) {
                    List<int[]> cells = cellsOf(shape, turns, cell[0], cell[1]);
                    // The shape must cover the cell being filled (a turned L can miss its own corner).
                    boolean covers = cells.stream().anyMatch(c -> c[0] == cell[0] && c[1] == cell[1]);
                    if (covers && cells.stream().allMatch(c -> free(c[0], c[1]))) {
                        add(Type.NORMAL, shape, turns, cells);
                        placed = true;
                        break;
                    }
                }
                if (placed) {
                    break;
                }
            }
            if (!placed) {
                single(Type.NORMAL, cell[0], cell[1]);
            }
        }
        return connect(random) && lock(witherDoors, random);
    }

    /** The cells of a shape turned {@code turns} quarter turns clockwise, with its corner at (x, z); null if off the grid. */
    static List<int[]> cellsOf(Shape shape, int turns, int x, int z) {
        List<int[]> out = new ArrayList<>();
        int w = shape.width();
        int d = shape.depth();
        for (int[] c : shape.cells) {
            int dx = c[0];
            int dz = c[1];
            int rx;
            int rz;
            switch (turns & 3) {
                case 1 -> {
                    rx = d - 1 - dz;
                    rz = dx;
                }
                case 2 -> {
                    rx = w - 1 - dx;
                    rz = d - 1 - dz;
                }
                case 3 -> {
                    rx = dz;
                    rz = w - 1 - dx;
                }
                default -> {
                    rx = dx;
                    rz = dz;
                }
            }
            out.add(new int[] {x + rx, z + rz});
        }
        return out;
    }

    private boolean free(int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < depth && grid[x][z] < 0;
    }

    private void single(Type type, int x, int z) {
        add(type, Shape.ONE, 0, List.of(new int[] {x, z}));
    }

    private void add(Type type, Shape shape, int turns, List<int[]> cells) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]);
            minZ = Math.min(minZ, c[1]);
            grid[c[0]][c[1]] = rooms.size();
        }
        rooms.add(new Room(rooms.size(), type, shape, turns, minX, minZ, List.copyOf(cells)));
    }

    private boolean nextTo(int x, int z, Type type) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] a : around) {
            Room room = roomAt(x + a[0], z + a[1]);
            if (room != null && room.type() == type) {
                return true;
            }
        }
        return false;
    }

    private boolean nextToDeadEnd(int x, int z) {
        return nextTo(x, z, Type.FAIRY) || nextTo(x, z, Type.PUZZLE) || nextTo(x, z, Type.TRAP);
    }

    private static boolean deadEnd(Type type) {
        return type == Type.FAIRY || type == Type.PUZZLE || type == Type.TRAP || type == Type.BLOOD;
    }

    /** A random spanning tree from the Entrance (dead ends join it but never lead on), then a few extra links. */
    private boolean connect(RandomSource random) {
        Set<Integer> joined = new HashSet<>();
        Room entrance = room(Type.ENTRANCE);
        joined.add(entrance.id());
        List<int[]> frontier = new ArrayList<>(edgesFrom(entrance));
        while (!frontier.isEmpty()) {
            int[] edge = frontier.remove(random.nextInt(frontier.size()));
            Room to = roomAt(edge[2], edge[3]);
            if (to == null || joined.contains(to.id())) {
                continue;
            }
            joined.add(to.id());
            Room from = roomAt(edge[0], edge[1]);
            doors.add(new Door(from.id(), to.id(), edge[0], edge[1], edge[2], edge[3], Lock.OPEN));
            if (!deadEnd(to.type())) {
                frontier.addAll(edgesFrom(to));
            }
        }
        if (joined.size() != rooms.size()) {
            return false;
        }
        // A few loops between normal rooms.
        for (Room room : rooms) {
            if (room.type() != Type.NORMAL) {
                continue;
            }
            for (int[] edge : edgesFrom(room)) {
                Room to = roomAt(edge[2], edge[3]);
                if (to != null && to.type() == Type.NORMAL && to.id() > room.id() && !linked(room.id(), to.id()) && random.nextInt(6) == 0) {
                    doors.add(new Door(room.id(), to.id(), edge[0], edge[1], edge[2], edge[3], Lock.OPEN));
                }
            }
        }
        return true;
    }

    private boolean linked(int a, int b) {
        return doors.stream().anyMatch(d -> d.roomA() == a && d.roomB() == b || d.roomA() == b && d.roomB() == a);
    }

    /** Cell-to-cell steps out of a room into other rooms: {x, z, nx, nz}. */
    private List<int[]> edgesFrom(Room room) {
        List<int[]> out = new ArrayList<>();
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] c : room.cells()) {
            for (int[] a : around) {
                Room other = roomAt(c[0] + a[0], c[1] + a[1]);
                if (other != null && other.id() != room.id()) {
                    out.add(new int[] {c[0], c[1], c[0] + a[0], c[1] + a[1]});
                }
            }
        }
        return out;
    }

    /**
     * Locks doors on the way to the Blood room: the last one needs the Blood Key, and up to {@code witherDoors} before it
     * need Wither Keys. Each key goes in a room you can reach before its door.
     */
    private boolean lock(int witherDoors, RandomSource random) {
        Room blood = room(Type.BLOOD);
        List<Integer> path = pathTo(blood.id());
        if (path == null || path.isEmpty()) {
            return false;
        }
        // path: door indexes from the Entrance to the Blood room, in order.
        int last = path.get(path.size() - 1);
        List<Integer> candidates = new ArrayList<>(path.subList(0, path.size() - 1));
        shuffle(candidates, random);
        List<Integer> wither = new ArrayList<>(candidates.subList(0, Math.min(witherDoors, candidates.size())));
        wither.sort(java.util.Comparator.comparingInt(path::indexOf));
        for (int door : wither) {
            setLock(door, Lock.WITHER);
        }
        setLock(last, Lock.BLOOD);
        // Keys: in a normal room reachable through the doors already unlocked (behind the previous locks).
        List<Integer> locks = new ArrayList<>(wither);
        locks.add(last);
        for (int door : locks) {
            Set<Integer> reachable = reachableBefore(door);
            List<Integer> spots = new ArrayList<>();
            for (int id : reachable) {
                Room room = rooms.get(id);
                if (room.type() == Type.NORMAL && keys.stream().noneMatch(k -> k.room() == id)) {
                    spots.add(id);
                }
            }
            if (spots.isEmpty()) {
                return false;
            }
            keys.add(new Key(spots.get(random.nextInt(spots.size())), door, doors.get(door).lock()));
        }
        return true;
    }

    private void setLock(int index, Lock lock) {
        Door d = doors.get(index);
        doors.set(index, new Door(d.roomA(), d.roomB(), d.ax(), d.az(), d.bx(), d.bz(), lock));
    }

    /** The doors (indexes) on the tree's way from the Entrance to a room, in order. */
    private List<Integer> pathTo(int target) {
        int start = room(Type.ENTRANCE).id();
        Map<Integer, Integer> cameBy = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        cameBy.put(start, -1);
        while (!queue.isEmpty()) {
            int at = queue.poll();
            if (at == target) {
                break;
            }
            for (int i = 0; i < doors.size(); i++) {
                Door d = doors.get(i);
                int next = d.roomA() == at ? d.roomB() : d.roomB() == at ? d.roomA() : -1;
                if (next >= 0 && !cameBy.containsKey(next)) {
                    cameBy.put(next, i);
                    queue.add(next);
                }
            }
        }
        if (!cameBy.containsKey(target)) {
            return null;
        }
        List<Integer> path = new ArrayList<>();
        int at = target;
        while (cameBy.get(at) >= 0) {
            int door = cameBy.get(at);
            path.add(door);
            Door d = doors.get(door);
            at = d.roomA() == at ? d.roomB() : d.roomA();
        }
        Collections.reverse(path);
        return path;
    }

    /** The rooms reachable from the Entrance without going through {@code lockedDoor} or any later locked door. */
    private Set<Integer> reachableBefore(int lockedDoor) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        int start = room(Type.ENTRANCE).id();
        queue.add(start);
        seen.add(start);
        Set<Integer> unlocked = new HashSet<>();
        // Locks before this one count as opened (their keys come earlier).
        for (Key key : keys) {
            unlocked.add(key.door());
        }
        while (!queue.isEmpty()) {
            int at = queue.poll();
            for (int i = 0; i < doors.size(); i++) {
                Door d = doors.get(i);
                if (i == lockedDoor || d.lock() != Lock.OPEN && !unlocked.contains(i)) {
                    continue;
                }
                int next = d.roomA() == at ? d.roomB() : d.roomB() == at ? d.roomA() : -1;
                if (next >= 0 && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, random.nextInt(i + 1));
        }
    }

    private static List<Integer> shuffled(RandomSource random, Integer... values) {
        List<Integer> list = new ArrayList<>(List.of(values));
        shuffle(list, random);
        return list;
    }

    /** The map as text, for logs and tests: one letter per cell (E, B, F, P, T, or the room id's last digit). */
    public String draw() {
        StringBuilder out = new StringBuilder();
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                Room room = roomAt(x, z);
                char c = switch (room.type()) {
                    case ENTRANCE -> 'E';
                    case BLOOD -> 'B';
                    case FAIRY -> 'F';
                    case PUZZLE -> 'P';
                    case TRAP -> 'T';
                    default -> (char) ('a' + room.id() % 26);
                };
                out.append(c);
            }
            out.append('\n');
        }
        return out.toString();
    }
}

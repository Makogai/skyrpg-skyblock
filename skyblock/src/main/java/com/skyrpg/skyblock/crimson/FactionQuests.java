package com.skyrpg.skyblock.crimson;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Daily faction quests: five a day (a new set at midnight UTC), from your faction's pool: two hunts on the Isle, a
 * miniboss, and two errands for your faction's people (bring them what they ask for). Each pays reputation.
 */
public final class FactionQuests {
    public static final int PER_DAY = 5;

    enum Type { KILL, MINIBOSS, FETCH }

    /** A quest: what it asks, how many, who wants it (fetch), the faction ("" = both) and the reputation it pays. */
    record Def(String id, Type type, String target, int amount, String npc, String faction, int rep) {
        String text() {
            return switch (type) {
                case KILL -> "Slay " + amount + " " + target + (amount > 1 && !target.endsWith("s") ? "s" : "");
                case MINIBOSS -> "Defeat " + (target.startsWith("Ma") ? "the " : "") + target;
                case FETCH -> "Bring " + npc + " " + amount + " " + CrimsonIsle.itemName(target) + (amount > 1 && target.endsWith("MUSHROOM") ? "s" : "");
            };
        }
    }

    static final List<Def> POOL = List.of(
            new Def("kill_blaze", Type.KILL, "Blaze", 30, "", "", 80),
            new Def("kill_magma_cube", Type.KILL, "Magma Cube", 30, "", "", 80),
            new Def("kill_flaming_spider", Type.KILL, "Flaming Spider", 25, "", "", 80),
            new Def("kill_wither_spectre", Type.KILL, "Wither Spectre", 20, "", "", 80),
            new Def("kill_wither_skeleton", Type.KILL, "Wither Skeleton", 20, "", "", 80),
            new Def("kill_kada_knight", Type.KILL, "Kada Knight", 10, "", "", 90),
            new Def("kill_smoldering_blaze", Type.KILL, "Smoldering Blaze", 15, "", "", 90),
            new Def("kill_mushroom_bull", Type.KILL, "Mushroom Bull", 15, "", "", 80),
            new Def("boss_bladesoul", Type.MINIBOSS, "Bladesoul", 1, "", "", 120),
            new Def("boss_mage_outlaw", Type.MINIBOSS, "Mage Outlaw", 1, "", "", 120),
            new Def("boss_duke", Type.MINIBOSS, "Barbarian Duke X", 1, "", "", 120),
            new Def("boss_ashfang", Type.MINIBOSS, "Ashfang", 1, "", "", 120),
            new Def("boss_magma_boss", Type.MINIBOSS, "Magma Boss", 1, "", "", 150),
            new Def("fetch_pablo", Type.FETCH, "RED_MUSHROOM", 32, "Pablo", Factions.MAGE, 100),
            new Def("fetch_avorius", Type.FETCH, "CUP_OF_BLOOD", 1, "Avorius", Factions.MAGE, 100),
            new Def("fetch_mollim", Type.FETCH, "MYCEL", 32, "Mollim", Factions.MAGE, 100),
            new Def("fetch_pomtair", Type.FETCH, "ENCHANTED_BONE_MEAL", 1, "Pomtair", Factions.BARBARIAN, 100),
            new Def("fetch_sirih", Type.FETCH, "SULPHUR", 16, "Sirih", Factions.BARBARIAN, 100),
            new Def("fetch_suus", Type.FETCH, "MAGMA_CREAM", 16, "Suus", Factions.BARBARIAN, 100));

    /** One of today's quests and how far along it is. */
    record Quest(String id, int progress, boolean done) {
        static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Quest::id),
                Codec.INT.fieldOf("progress").forGetter(Quest::progress),
                Codec.BOOL.fieldOf("done").forGetter(Quest::done)).apply(i, Quest::new));

        Def def() {
            return POOL.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
        }
    }

    /** Today's quests (for the day number and faction they were drawn for). */
    record Daily(long day, String faction, List<Quest> quests) {
        static final Codec<Daily> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Daily::day),
                Codec.STRING.fieldOf("faction").forGetter(Daily::faction),
                Quest.CODEC.listOf().fieldOf("quests").forGetter(Daily::quests)).apply(i, Daily::new));
    }

    static final AttachmentType<Daily> DAILY = AttachmentRegistry.create(SkyRpgSkyblock.id("crimson_quests"),
            builder -> builder.persistent(Daily.CODEC).copyOnDeath());

    private FactionQuests() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer && entity.level().dimension().equals(CrimsonIsle.LEVEL)) {
                killed(killer, entity);
            }
        });
    }

    static long today() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** Seconds until the next set. */
    static long secondsLeft() {
        long now = System.currentTimeMillis() / 1000;
        return (today() + 1) * 86_400 - now;
    }

    /** Today's quests: drawn fresh on a new day (or after joining or switching factions); none without a faction. */
    static List<Quest> quests(ServerPlayer player) {
        String faction = Factions.faction(player);
        if (faction.isEmpty()) {
            return List.of();
        }
        Daily daily = player.getAttached(DAILY);
        if (daily == null || daily.day() != today() || !daily.faction().equals(faction)) {
            daily = new Daily(today(), faction, draw(player, faction));
            player.setAttached(DAILY, daily);
        }
        return daily.quests();
    }

    /** New quests now (joining, switching). */
    static void reset(ServerPlayer player) {
        player.removeAttached(DAILY);
        quests(player);
    }

    private static List<Quest> draw(ServerPlayer player, String faction) {
        Random random = new Random(today() * 31 + player.getUUID().hashCode());
        List<Def> kills = new ArrayList<>(POOL.stream().filter(d -> d.type() == Type.KILL).toList());
        List<Def> bosses = new ArrayList<>(POOL.stream().filter(d -> d.type() == Type.MINIBOSS).toList());
        List<Def> errands = new ArrayList<>(POOL.stream().filter(d -> d.type() == Type.FETCH && d.faction().equals(faction)).toList());
        java.util.Collections.shuffle(kills, random);
        java.util.Collections.shuffle(bosses, random);
        java.util.Collections.shuffle(errands, random);
        List<Quest> out = new ArrayList<>();
        for (Def def : List.of(kills.get(0), kills.get(1), bosses.get(0), errands.get(0), errands.get(1))) {
            out.add(new Quest(def.id(), 0, false));
        }
        return out;
    }

    private static void update(ServerPlayer player, java.util.function.Predicate<Def> matches, int amount) {
        List<Quest> quests = quests(player);
        List<Quest> next = new ArrayList<>(quests);
        boolean changed = false;
        for (int i = 0; i < next.size(); i++) {
            Quest quest = next.get(i);
            Def def = quest.def();
            if (quest.done() || def == null || !matches.test(def)) {
                continue;
            }
            int progress = Math.min(def.amount(), quest.progress() + amount);
            boolean done = progress >= def.amount();
            next.set(i, new Quest(quest.id(), progress, done));
            changed = true;
            if (done) {
                completed(player, def);
            }
        }
        if (changed) {
            Daily daily = player.getAttached(DAILY);
            player.setAttached(DAILY, new Daily(daily.day(), daily.faction(), List.copyOf(next)));
        }
    }

    private static void completed(ServerPlayer player, Def def) {
        Notice.of(Notice.Kind.COMBAT).tag("QUEST").panel()
                .line(Component.literal(" +" + def.rep() + " " + Factions.title(Factions.faction(player)) + " Reputation")
                        .withColor(Factions.color(Factions.faction(player))))
                .send(player, Component.literal("Faction quest complete: " + def.text()).withStyle(ChatFormatting.GREEN));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
        Factions.give(player, def.rep(), "quest");
    }

    private static void killed(ServerPlayer killer, LivingEntity entity) {
        if (entity.getCustomName() == null) {
            return;
        }
        String name = entity.getCustomName().getString();
        update(killer, def -> def.type() == Type.KILL && def.target().equals(name), 1);
    }

    /** A miniboss fell and this player helped. */
    static void minibossKilled(ServerPlayer player, String boss) {
        update(player, def -> def.type() == Type.MINIBOSS && def.target().equals(boss), 1);
    }

    /**
     * Talking to one of your faction's people: hands over what today's errand for them asks, if you carry it. Returns
     * true when they had an errand for you (done now or not).
     */
    static boolean errand(ServerPlayer player, String npc) {
        for (Quest quest : quests(player)) {
            Def def = quest.def();
            if (def == null || def.type() != Type.FETCH || !def.npc().equals(npc)) {
                continue;
            }
            if (quest.done()) {
                player.sendSystemMessage(Component.literal(npc + ": Thanks again! Come back tomorrow.").withStyle(ChatFormatting.GRAY));
                return true;
            }
            int have = SkyStacks.count(player.getInventory(), def.target());
            if (have < def.amount()) {
                player.sendSystemMessage(Component.literal(npc + ": Bring me " + def.amount() + " " + CrimsonIsle.itemName(def.target())
                        + " (you have " + have + ").").withStyle(ChatFormatting.YELLOW));
                return true;
            }
            SkyStacks.take(player.getInventory(), def.target(), def.amount());
            player.sendSystemMessage(Component.literal(npc + ": Just what I needed!").withStyle(ChatFormatting.GREEN));
            update(player, d -> d.id().equals(def.id()), def.amount());
            return true;
        }
        return false;
    }

    /** Lines for the faction screen: "[✔] text  12/30  +80 Rep". */
    static List<FactionMenu.QuestRow> rows(ServerPlayer player) {
        List<FactionMenu.QuestRow> rows = new ArrayList<>();
        for (Quest quest : quests(player)) {
            Def def = quest.def();
            if (def == null) {
                continue;
            }
            String where = switch (def.type()) {
                case KILL -> "Hunt on the Crimson Isle";
                case MINIBOSS -> "Minibosses return every 2 minutes";
                case FETCH -> "Talk to " + def.npc() + " in " + Factions.town(def.faction());
            };
            rows.add(new FactionMenu.QuestRow(def.text(), where, quest.progress(), def.amount(), def.rep(), quest.done(),
                    new net.minecraft.world.item.ItemStack(switch (def.type()) {
                        case KILL -> net.minecraft.world.item.Items.IRON_SWORD;
                        case MINIBOSS -> net.minecraft.world.item.Items.WITHER_SKELETON_SKULL;
                        case FETCH -> net.minecraft.world.item.Items.PAPER;
                    })));
        }
        return rows;
    }
}

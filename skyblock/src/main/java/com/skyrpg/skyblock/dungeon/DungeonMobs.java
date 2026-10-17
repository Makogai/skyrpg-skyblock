package com.skyrpg.skyblock.dungeon;

import com.skyrpg.skyblock.zone.Zones;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Catacombs' mobs. A room's {@code mob} marker has a role (zombie, skeleton, crypt, spider, elite); the floor picks
 * which of Hypixel's mobs that is and how strong: health and damage go from the F1 to the F7 numbers (NEU's health
 * ranges) by the floor's strength, geometrically. Star mobs ("✯" before the name) must die for their room to be cleared.
 */
public final class DungeonMobs {
    public static final String TAG = "skyrpg_dungeon_mob";
    public static final String STAR = "skyrpg_dungeon_star";
    public static final String STAR_GLYPH = "✯ ";

    /** A role on a floor: the mob's name, its entity, F1 and F7 health, F1 and F7 damage, and its gear. */
    private record Kind(String name, String entity, double hp1, double hp7, double dmg1, double dmg7, ItemStack hand, ItemStack head,
                        ItemStack chest) {
    }

    private DungeonMobs() {
    }

    private static Kind kind(String role, int floor) {
        return switch (role) {
            case "skeleton" -> floor >= 4
                    ? new Kind("Skeleton Master", "minecraft:skeleton", 59_000, 900_000, 180, 18_000, new ItemStack(Items.BOW),
                    new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE))
                    : new Kind("Skeleton Soldier", "minecraft:skeleton", 31_000, 750_000, 160, 15_000, new ItemStack(Items.BOW),
                    ItemStack.EMPTY, ItemStack.EMPTY);
            case "crypt" -> new Kind(floor >= 5 ? "Crypt Souleater" : floor >= 3 ? "Crypt Dreadlord" : "Crypt Lurker", "minecraft:husk",
                    12_000, 840_000, 140, 14_000, new ItemStack(Items.STONE_SWORD), ItemStack.EMPTY, ItemStack.EMPTY);
            case "spider" -> new Kind("Cellar Spider", "minecraft:cave_spider", 22_000, 420_000, 120, 12_000, ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY);
            case "elite" -> floor >= 4
                    ? new Kind("Zombie Commander", "minecraft:zombie", 1_700_000, 3_500_000, 400, 40_000, new ItemStack(Items.DIAMOND_SWORD),
                    new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE))
                    : new Kind("Lost Adventurer", "minecraft:zombie", 60_000, 1_000_000, 300, 25_000, new ItemStack(Items.IRON_SWORD),
                    new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE));
            default -> new Kind(floor >= 5 ? "Zombie Knight" : floor >= 3 ? "Zombie Soldier" : "Zombie Grunt", "minecraft:zombie",
                    22_000, 1_200_000, 150, 15_000, floor >= 3 ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY,
                    new ItemStack(floor >= 3 ? Items.IRON_HELMET : Items.LEATHER_HELMET), ItemStack.EMPTY);
        };
    }

    /** Spawns one: a dungeon mob of {@code role} for this floor, tagged for the run, a star mob if {@code star}. */
    public static Mob spawn(ServerLevel level, Vec3 at, String role, boolean star, Floors.Floor floor, String runTag) {
        Kind kind = kind(role, floor.number());
        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(kind.entity()));
        if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
            return null;
        }
        double t = Math.max(0, floor.strength());
        double health = kind.hp1() * Math.pow(kind.hp7() / kind.hp1(), t);
        double damage = kind.dmg1() * Math.pow(kind.dmg7() / kind.dmg1(), t);
        if (floor.master()) {
            // Master Mode: Floor VII's mobs, half again as strong for each floor.
            health = kind.hp7() * Math.pow(1.5, floor.number());
            damage = kind.dmg7() * Math.pow(1.5, floor.number());
        }
        int mobLevel = 10 + floor.number() * 25 + (role.equals("elite") ? 20 : 0) + (floor.master() ? 200 : 0);
        mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
        Zones.dressAs(mob, (star ? STAR_GLYPH : "") + kind.name(), mobLevel, round(health), round(damage), 10 + 40 * t);
        mob.setItemSlot(EquipmentSlot.MAINHAND, kind.hand().copy());
        mob.setItemSlot(EquipmentSlot.HEAD, kind.head().copy());
        mob.setItemSlot(EquipmentSlot.CHEST, kind.chest().copy());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0);
        }
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        mob.addTag(runTag);
        mob.addTag(Zones.FIGHTABLE_TAG);
        if (star) {
            mob.addTag(STAR);
        }
        level.addFreshEntity(mob);
        return mob;
    }

    /** A Watcher's blood mob: Hypixel names them (Revoker, Psycho, Reaper...). Never a star; always counted by the Blood room. */
    public static Mob blood(ServerLevel level, Vec3 at, Floors.Floor floor, String runTag, String name) {
        Mob mob = spawn(level, at, level.getRandom().nextInt(3) == 0 ? "skeleton" : "zombie", false, floor, runTag);
        if (mob != null) {
            mob.setCustomName(net.minecraft.network.chat.Component.literal(name).withStyle(net.minecraft.ChatFormatting.RED));
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.WITHER_SKELETON_SKULL));
        }
        return mob;
    }

    private static double round(double value) {
        double magnitude = Math.pow(10, Math.max(0, Math.floor(Math.log10(value)) - 1));
        return Math.round(value / magnitude) * magnitude;
    }
}

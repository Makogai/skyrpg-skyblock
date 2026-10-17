package com.skyrpg.items.accessory;

import com.mojang.serialization.Codec;
import com.skyrpg.items.SkyRpgItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The accessory slots, in the order they're shown (a column left of the armour, one per armour row) and
 * stored. Which items go where is decided by item tags ({@code data/skyrpg_items/tags/item/accessory/<id>.json}),
 * so data packs can add accessories and both sides agree on them.
 */
public enum AccessorySlot implements StringRepresentable {
    AMULET("amulet", "Necklace", "Necklaces, worn at the neck", SoundEvents.ARMOR_EQUIP_CHAIN, "NECKLACE"),
    CLOAK("cloak", "Cloak", "Cloaks, worn on the back", SoundEvents.ARMOR_EQUIP_LEATHER, "CLOAK"),
    BELT("belt", "Belt", "Belts, worn at the waist", SoundEvents.ARMOR_EQUIP_LEATHER, "BELT"),
    BRACERS("bracers", "Gloves", "Gloves and bracelets, worn on the hands", SoundEvents.ARMOR_EQUIP_GENERIC, "GLOVES", "BRACELET");

    /** {@code values().length}, as a constant. */
    public static final int COUNT = 4;
    public static final Codec<AccessorySlot> CODEC = StringRepresentable.fromEnum(AccessorySlot::values);

    private final String id;
    private final String displayName;
    private final String hint;
    private final Holder<SoundEvent> equipSound;
    private final TagKey<Item> tag;
    private final Identifier emptyIcon;
    /** Hypixel's equipment categories this slot takes (SkyBlock items). */
    private final java.util.Set<String> categories;

    AccessorySlot(String id, String displayName, String hint, Holder<SoundEvent> equipSound, String... categories) {
        this.id = id;
        this.displayName = displayName;
        this.hint = hint;
        this.equipSound = equipSound;
        this.tag = TagKey.create(Registries.ITEM, SkyRpgItems.id("accessory/" + id));
        this.emptyIcon = SkyRpgItems.id("container/slot/" + id);
        this.categories = java.util.Set.of(categories);
    }

    /** "amulet": the save key, tag and texture name. */
    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** "Amulet". */
    public String displayName() {
        return displayName;
    }

    /** What goes in this slot, for the empty slot's tooltip. */
    public String hint() {
        return hint;
    }

    /** {@code skyrpg_items:accessory/<id>}: the items that fit this slot. */
    public TagKey<Item> tag() {
        return tag;
    }

    /** The silhouette sprite an empty slot shows ({@code skyrpg_items:container/slot/<id>} in the gui atlas). */
    public Identifier emptyIcon() {
        return emptyIcon;
    }

    public Holder<SoundEvent> equipSound() {
        return equipSound;
    }

    /** This slot's index in the player's {@code InventoryMenu}. */
    public int menuIndex() {
        return AccessoryMenuSlot.FIRST_INDEX + ordinal();
    }

    /** Our items by tag; SkyBlock items by their Hypixel equipment category (a necklace in the necklace slot...). */
    public boolean fits(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        com.skyrpg.items.ItemDefinition def = com.skyrpg.items.SkyItems.definition(stack);
        if (def != null && def.skyblock()) {
            String category = com.skyrpg.items.reforge.Reforges.category(def);
            return category != null && categories.contains(category);
        }
        return stack.is(tag);
    }

    /** The slot this item goes in (the first one whose tag has it), or null if it isn't an accessory. */
    public static AccessorySlot of(ItemStack stack) {
        for (AccessorySlot slot : values()) {
            if (slot.fits(stack)) {
                return slot;
            }
        }
        return null;
    }
}

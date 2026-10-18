package com.skyrpg.skyblock.ui;

import com.skyrpg.core.text.Msg;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * A price or a balance shown as a small chip: a currency icon (a sprite under {@code currency/}: coins, copper, pests,
 * tickets, medal_gold, medal_silver, medal_bronze) or an item's icon, the amount, and whether you have enough (red when
 * not). Screens draw them with {@code SkyScreens.chip}.
 */
public record Chip(String currency, ItemStack item, String text, boolean enough) {
    public static final StreamCodec<RegistryFriendlyByteBuf, Chip> CODEC = new StreamCodec<>() {
        @Override
        public Chip decode(RegistryFriendlyByteBuf buf) {
            return new Chip(buf.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, Chip chip) {
            buf.writeUtf(chip.currency());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, chip.item());
            buf.writeUtf(chip.text());
            buf.writeBoolean(chip.enough());
        }
    };

    /** A currency price: {@code amount} against what you {@code have}. */
    public static Chip price(String currency, long amount, long have) {
        return new Chip(currency, ItemStack.EMPTY, Msg.number(amount), have >= amount);
    }

    /** A balance: what you have of a currency. */
    public static Chip balance(String currency, long have) {
        return new Chip(currency, ItemStack.EMPTY, Msg.number(have), true);
    }

    /** An item price: {@code count} of an item against what you {@code have}. */
    public static Chip item(ItemStack icon, long count, long have) {
        return new Chip("", icon.copyWithCount(1), Msg.number(count), have >= count);
    }

    /** A plain note with an item icon (e.g. "needs your Anita's Talisman"). */
    public static Chip note(ItemStack icon, String text, boolean enough) {
        return new Chip("", icon.copyWithCount(1), text, enough);
    }
}

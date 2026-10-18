package com.skyrpg.skyblock.ui;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * A themed card screen's content (see {@link CardMenu}): the theme's name ({@code ScreenTheme}, e.g. "RIFT"), a title and
 * subtitle, the NPC drawn on the left (its entity id, -1 for none), your balances under it, the cards, a footer line, and
 * the grid (columns, card height).
 */
public record CardView(String theme, String title, String subtitle, int npc, List<Chip> wallet, List<Card> cards, String footer,
                       int columns, int cardHeight) {
    /**
     * A card: its title and the line under it, a colour (the title's; 0xFFFFFF with an item shows the item's own name and
     * rarity colour), whether it can be clicked, its button ("" makes the whole card the button), an item icon or a
     * currency sprite, chips (prices, rewards), a progress bar (-1 for none) and a note at its top right.
     */
    public record Card(String title, String detail, int color, boolean enabled, String button, ItemStack icon, String sprite, List<Chip> chips,
                       float progress, String note) {
        public static Card info(String title, String detail, int color, ItemStack icon) {
            return new Card(title, detail, color, false, "", icon, "", List.of(), -1, "");
        }

        /** An item for sale: its own name and tooltip, the price as chips. */
        public static Card offer(ItemStack item, List<Chip> price) {
            return new Card(item.isEmpty() ? "?" : item.getHoverName().getString(), "", 0xFFFFFF, !item.isEmpty(), "", item, "", price, -1, "");
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Card> CODEC = new StreamCodec<>() {
            @Override
            public Card decode(RegistryFriendlyByteBuf buf) {
                return new Card(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readBoolean(), buf.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                        buf.readUtf(), Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readFloat(), buf.readUtf());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Card card) {
                buf.writeUtf(card.title());
                buf.writeUtf(card.detail());
                buf.writeInt(card.color());
                buf.writeBoolean(card.enabled());
                buf.writeUtf(card.button());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, card.icon());
                buf.writeUtf(card.sprite());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, card.chips());
                buf.writeFloat(card.progress());
                buf.writeUtf(card.note());
            }
        };
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CardView> CODEC = new StreamCodec<>() {
        @Override
        public CardView decode(RegistryFriendlyByteBuf buf) {
            return new CardView(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    Card.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CardView view) {
            buf.writeUtf(view.theme());
            buf.writeUtf(view.title());
            buf.writeUtf(view.subtitle());
            buf.writeVarInt(view.npc());
            Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.wallet());
            Card.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.cards());
            buf.writeUtf(view.footer());
            buf.writeVarInt(view.columns());
            buf.writeVarInt(view.cardHeight());
        }
    };
}

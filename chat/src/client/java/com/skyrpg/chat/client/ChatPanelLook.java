package com.skyrpg.chat.client;

import com.skyrpg.chat.ChatLines;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;

/**
 * Panels: a notice with lines under it (a level up and its rewards, a collection tier and its unlocks) that arrives as
 * one message ({@code skyrpg_chat.panel}, see core's Notice.Panel) and shows as one card: a band of the notice's colour
 * behind the header fading out to the right, a lighter body behind the rest, a bright edge on top and a faint one at the
 * bottom. Like SkyHelper's glow, the lines are wrapped in {@link PanelLine} when laid out and painted before their text.
 */
public final class ChatPanelLook {
    private static final int BODY = 0x8A96B4;

    /** One laid-out line of a panel: draws as its text, knows where it sits in the panel and the panel's colour. */
    public record PanelLine(FormattedCharSequence inner, int index, int count, int color) implements FormattedCharSequence {
        @Override
        public boolean accept(FormattedCharSink sink) {
            return inner.accept(sink);
        }
    }

    private ChatPanelLook() {
    }

    /** Wraps a panel message's lines (others pass through). */
    public static List<FormattedCharSequence> lines(GuiMessage message, List<FormattedCharSequence> lines) {
        if (!ChatLook.themed() || !ChatLines.PANEL.equals(ChatHooks.marker(message.content()))) {
            return lines;
        }
        int color = color(message.content());
        List<FormattedCharSequence> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            out.add(new PanelLine(lines.get(i), i, lines.size(), color));
        }
        return out;
    }

    /** The panel's colour, its second argument ("#F2B33D"); SkyRPG gold if it has none. */
    private static int color(Component content) {
        if (content.getContents() instanceof TranslatableContents translatable && translatable.getArgs().length > 1) {
            Object arg = translatable.getArgs()[1];
            String hex = arg instanceof Component component ? component.getString() : String.valueOf(arg);
            try {
                return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return ChatKind.SKYRPG.color();
    }

    /** Paints one line's part of the card; {@code textY} is where vanilla draws its text. */
    public static void draw(GuiGraphicsExtractor g, PanelLine line, int textY, float opacity) {
        int[] box = ChatHelperLook.boxAt(textY);
        if (opacity <= 0.01f || box == null) {
            return;
        }
        int x0 = box[0] + 2;   // the kind stripe keeps the first two pixels
        int x1 = box[2];
        int y0 = box[1];
        int y1 = box[3];
        int width = x1 - x0;
        if (width < 8) {
            return;
        }
        if (line.index() == 0) {
            // the header: the notice's colour, strongest on the left
            int strips = 12;
            for (int i = 0; i < strips; i++) {
                float t = i / (float) (strips - 1);
                g.fill(x0 + width * i / strips, y0, x0 + width * (i + 1) / strips, y1, argb(Mth.lerp(t, 0.30f, 0.04f) * opacity, line.color()));
            }
            g.fill(x0, y0, x1, y0 + 1, argb(0.9f * opacity, line.color()));
        } else {
            g.fill(x0, y0, x1, y1, argb(0.10f * opacity, BODY));
            // a thin rail down the left of the body, in the panel's colour
            g.fill(x0 + 1, y0, x0 + 2, y1, argb(0.45f * opacity, line.color()));
        }
        if (line.index() == line.count() - 1) {
            g.fill(x0, y1 - 1, x1, y1, argb(0.35f * opacity, line.color()));
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Math.round(Mth.clamp(alpha, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}

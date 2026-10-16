package com.skyrpg.combat.slayer;

import com.skyrpg.combat.SkyRpgCombat;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * The Slayer Master over the network: {@link View} opens or refreshes her screen (the slayer lines and the spawner
 * ladder), {@link Status} keeps the HUD up to date; the client asks to {@link Start} or {@link Cancel} a quest and to
 * {@link Buy} spawners, and the server checks everything again.
 */
public final class SlayerPayloads {
    private SlayerPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Cancel.TYPE, Cancel.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Buy.TYPE, Buy.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestPage.TYPE, RequestPage.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PageData.TYPE, PageData.CODEC);
    }

    /** One tier as the player sees it. */
    /**
     * @param title     the tier's name ("Tyrant King")
     * @param abilities what the tier adds, each "A|name|description" (attack) or "D|name|description" (defence)
     */
    public record TierView(long fee, int xp, int bossHealth, int bossLevel, int minutes, int slayerXp, int requires, boolean unlocked,
                           String title, List<String> abilities) {
        static final StreamCodec<ByteBuf, TierView> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, TierView::fee, ByteBufCodecs.VAR_INT, TierView::xp, ByteBufCodecs.VAR_INT, TierView::bossHealth,
                ByteBufCodecs.VAR_INT, TierView::bossLevel, ByteBufCodecs.VAR_INT, TierView::minutes, ByteBufCodecs.VAR_INT, TierView::slayerXp,
                ByteBufCodecs.VAR_INT, TierView::requires, ByteBufCodecs.BOOL, TierView::unlocked, ByteBufCodecs.STRING_UTF8, TierView::title,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TierView::abilities, TierView::new);
    }

    /** One slayer line: your level and XP in it, what it counts, its tiers, and its level reward line. */
    public record LineView(String id, String name, int color, ItemStack icon, int level, int xp, int nextXp, String counts, String perLevel,
                           List<TierView> tiers) {
        static final StreamCodec<RegistryFriendlyByteBuf, LineView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, LineView::id, ByteBufCodecs.STRING_UTF8, LineView::name, ByteBufCodecs.INT, LineView::color,
                ItemStack.STREAM_CODEC, LineView::icon, ByteBufCodecs.VAR_INT, LineView::level, ByteBufCodecs.VAR_INT, LineView::xp,
                ByteBufCodecs.VAR_INT, LineView::nextXp, ByteBufCodecs.STRING_UTF8, LineView::counts, ByteBufCodecs.STRING_UTF8, LineView::perLevel,
                TierView.CODEC.apply(ByteBufCodecs.list()), LineView::tiers, LineView::new);
    }

    /** One spawner on the ladder: unlocked, or what unlocks it and how far along you are. */
    public record SpawnerView(int index, ItemStack spawner, String mob, long price, boolean unlocked, String unlockMob, int kills, int needed) {
        static final StreamCodec<RegistryFriendlyByteBuf, SpawnerView> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SpawnerView::index, ItemStack.STREAM_CODEC, SpawnerView::spawner, ByteBufCodecs.STRING_UTF8, SpawnerView::mob,
                ByteBufCodecs.VAR_LONG, SpawnerView::price, ByteBufCodecs.BOOL, SpawnerView::unlocked, ByteBufCodecs.STRING_UTF8, SpawnerView::unlockMob,
                ByteBufCodecs.VAR_INT, SpawnerView::kills, ByteBufCodecs.VAR_INT, SpawnerView::needed, SpawnerView::new);
    }

    /**
     * {@code refresh}: update an open screen instead of opening one. {@code notice} may be empty. {@code tab}: the tab to
     * open on (0 quests, 1 spawners), or -1 to keep the one that's showing.
     */
    public record View(List<LineView> lines, List<SpawnerView> spawners, String activeLine, int activeTier, Component notice, boolean good,
                       boolean refresh, int tab) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(SkyRpgCombat.id("slayer_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                LineView.CODEC.apply(ByteBufCodecs.list()), View::lines, SpawnerView.CODEC.apply(ByteBufCodecs.list()), View::spawners,
                ByteBufCodecs.STRING_UTF8, View::activeLine, ByteBufCodecs.VAR_INT, View::activeTier,
                ComponentSerialization.STREAM_CODEC, View::notice, ByteBufCodecs.BOOL, View::good, ByteBufCodecs.BOOL, View::refresh,
                ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), View::tab, View::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The quest for the HUD: empty {@code name} when there's none. {@code secondsLeft} counts down while the boss is up. */
    public record Status(String name, int color, int tier, int progress, int needed, boolean bossUp, int secondsLeft) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(SkyRpgCombat.id("slayer_status"));
        public static final StreamCodec<ByteBuf, Status> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Status::name, ByteBufCodecs.INT, Status::color, ByteBufCodecs.VAR_INT, Status::tier,
                ByteBufCodecs.VAR_INT, Status::progress, ByteBufCodecs.VAR_INT, Status::needed, ByteBufCodecs.BOOL, Status::bossUp,
                ByteBufCodecs.VAR_INT, Status::secondsLeft, Status::new);
        public static final Status NONE = new Status("", 0, 0, 0, 0, false, 0);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Start(String line, int tier) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(SkyRpgCombat.id("slayer_start"));
        public static final StreamCodec<ByteBuf, Start> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Start::line, ByteBufCodecs.VAR_INT, Start::tier, Start::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Cancel() implements CustomPacketPayload {
        public static final Type<Cancel> TYPE = new Type<>(SkyRpgCombat.id("slayer_cancel"));
        public static final StreamCodec<ByteBuf, Cancel> CODEC = StreamCodec.unit(new Cancel());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Buy {@code count} of the spawner at {@code index} on the ladder. */
    public record Buy(int index, int count) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(SkyRpgCombat.id("spawner_buy"));
        public static final StreamCodec<ByteBuf, Buy> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Buy::index, ByteBufCodecs.VAR_INT, Buy::count, Buy::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------ the K menu's Slayers page

    /** The client asks for the data of the Slayers page (when it opens). */
    public record RequestPage() implements CustomPacketPayload {
        public static final Type<RequestPage> TYPE = new Type<>(SkyRpgCombat.id("slayer_page_request"));
        public static final StreamCodec<ByteBuf, RequestPage> CODEC = StreamCodec.unit(new RequestPage());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What a slayer level gives per level: a stat, e.g. "Health", 3, not a percent. */
    public record StatPer(String name, double per, boolean percent) {
        static final StreamCodec<ByteBuf, StatPer> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StatPer::name, ByteBufCodecs.DOUBLE, StatPer::per, ByteBufCodecs.BOOL, StatPer::percent, StatPer::new);
    }

    /** A bench recipe unlocked by a slayer level: its result (shown with its real tooltip). */
    public record Unlock(int level, ItemStack item) {
        static final StreamCodec<RegistryFriendlyByteBuf, Unlock> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Unlock::level, ItemStack.STREAM_CODEC, Unlock::item, Unlock::new);
    }

    /** A boss drop on the page: the item, how many, the chance, Hypixel's odds label and the slayer level it needs. */
    public record DropView(ItemStack item, int min, int max, double chance, String odds, int level) {
        static final StreamCodec<RegistryFriendlyByteBuf, DropView> CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, DropView::item, ByteBufCodecs.VAR_INT, DropView::min, ByteBufCodecs.VAR_INT, DropView::max,
                ByteBufCodecs.DOUBLE, DropView::chance, ByteBufCodecs.STRING_UTF8, DropView::odds, ByteBufCodecs.VAR_INT, DropView::level,
                DropView::new);
    }

    /** One tier on the page: the Slayer Master's view of it, the rewards and drops, and what's still missing to start it. */
    public record PageTier(TierView tier, int tokensMin, int tokensMax, double rareChance, double eggChance, List<DropView> drops,
                           int combatXp, String needs) {
        static final StreamCodec<RegistryFriendlyByteBuf, PageTier> CODEC = StreamCodec.composite(
                TierView.CODEC, PageTier::tier, ByteBufCodecs.VAR_INT, PageTier::tokensMin, ByteBufCodecs.VAR_INT, PageTier::tokensMax,
                ByteBufCodecs.DOUBLE, PageTier::rareChance, ByteBufCodecs.DOUBLE, PageTier::eggChance,
                DropView.CODEC.apply(ByteBufCodecs.list()), PageTier::drops, ByteBufCodecs.VAR_INT, PageTier::combatXp,
                ByteBufCodecs.STRING_UTF8, PageTier::needs, PageTier::new);
    }

    /** A stat a slayer level gives: the stat's id and the amount. */
    public record LevelStat(String stat, double value) {
        static final StreamCodec<ByteBuf, LevelStat> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, LevelStat::stat, ByteBufCodecs.DOUBLE, LevelStat::value, LevelStat::new);
    }

    /** What one of Hypixel's slayer levels gives: stats, SkyBlock XP, the items it unlocks (names when we have no such item). */
    public record LevelView(List<LevelStat> stats, int sbxp, List<ItemStack> items, List<String> names) {
        static final StreamCodec<RegistryFriendlyByteBuf, LevelView> CODEC = StreamCodec.composite(
                LevelStat.CODEC.apply(ByteBufCodecs.list()), LevelView::stats, ByteBufCodecs.VAR_INT, LevelView::sbxp,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), LevelView::items, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                LevelView::names, LevelView::new);
    }

    /**
     * One line on the page: static facts (the thresholds, level stats, unlocks, tiers). Your XP and kills come from the
     * synced state. Hypixel's lines have a {@code key} ("zombie") and {@code levelViews}, one per level.
     */
    public record PageLine(String id, String name, int color, ItemStack icon, String counts, List<Integer> levels, List<StatPer> perLevel,
                           List<Unlock> unlocks, ItemStack token, ItemStack rare, List<PageTier> tiers, String key, List<LevelView> levelViews) {
        static final StreamCodec<RegistryFriendlyByteBuf, PageLine> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PageLine::id, ByteBufCodecs.STRING_UTF8, PageLine::name, ByteBufCodecs.INT, PageLine::color,
                ItemStack.OPTIONAL_STREAM_CODEC, PageLine::icon, ByteBufCodecs.STRING_UTF8, PageLine::counts,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), PageLine::levels, StatPer.CODEC.apply(ByteBufCodecs.list()), PageLine::perLevel,
                Unlock.CODEC.apply(ByteBufCodecs.list()), PageLine::unlocks, ItemStack.OPTIONAL_STREAM_CODEC, PageLine::token,
                ItemStack.OPTIONAL_STREAM_CODEC, PageLine::rare, PageTier.CODEC.apply(ByteBufCodecs.list()), PageLine::tiers,
                ByteBufCodecs.STRING_UTF8, PageLine::key, LevelView.CODEC.apply(ByteBufCodecs.list()), PageLine::levelViews, PageLine::new);

        public boolean hypixel() {
            return !levelViews.isEmpty();
        }
    }

    public record PageData(List<PageLine> lines) implements CustomPacketPayload {
        public static final Type<PageData> TYPE = new Type<>(SkyRpgCombat.id("slayer_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PageData> CODEC = StreamCodec.composite(
                PageLine.CODEC.apply(ByteBufCodecs.list()), PageData::lines, PageData::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

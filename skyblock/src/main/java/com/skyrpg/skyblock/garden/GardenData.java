package com.skyrpg.skyblock.garden;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every profile's Garden (shared by its co-op), saved with the overworld. */
final class GardenData extends SavedData {
    /** One Garden: plots unlocked, Garden XP, Copper, crops harvested, the Composter, visitors. */
    static final class State {
        boolean built;
        int plots;
        long xp;
        long copper;
        final Map<String, Long> crops = new HashMap<>();
        final Map<String, Integer> upgrades = new HashMap<>();
        double organic;
        double fuel;
        int compost;
        long composterAt;
        final Map<String, Integer> composter = new HashMap<>();
        final List<String> visitors = new ArrayList<>();
        final Map<String, Integer> served = new HashMap<>();
        long nextVisitor;

        record Saved(boolean built, int plots, long xp, long copper, Map<String, Long> crops, Map<String, Integer> upgrades, double organic,
                     double fuel, int compost, long composterAt, Map<String, Integer> composter, List<String> visitors,
                     Map<String, Integer> served, long nextVisitor) {
        }

        static final Codec<State> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
                Codec.BOOL.optionalFieldOf("built", false).forGetter(Saved::built),
                Codec.INT.optionalFieldOf("plots", 0).forGetter(Saved::plots),
                Codec.LONG.optionalFieldOf("xp", 0L).forGetter(Saved::xp),
                Codec.LONG.optionalFieldOf("copper", 0L).forGetter(Saved::copper),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("crops", Map.of()).forGetter(Saved::crops),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("upgrades", Map.of()).forGetter(Saved::upgrades),
                Codec.DOUBLE.optionalFieldOf("organic", 0.0).forGetter(Saved::organic),
                Codec.DOUBLE.optionalFieldOf("fuel", 0.0).forGetter(Saved::fuel),
                Codec.INT.optionalFieldOf("compost", 0).forGetter(Saved::compost),
                Codec.LONG.optionalFieldOf("composter_at", 0L).forGetter(Saved::composterAt),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("composter", Map.of()).forGetter(Saved::composter),
                Codec.STRING.listOf().optionalFieldOf("visitors", List.of()).forGetter(Saved::visitors),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("served", Map.of()).forGetter(Saved::served),
                Codec.LONG.optionalFieldOf("next_visitor", 0L).forGetter(Saved::nextVisitor)).apply(i, Saved::new))
                .xmap(State::from, State::save);

        private static State from(Saved s) {
            State state = new State();
            state.built = s.built();
            state.plots = s.plots();
            state.xp = s.xp();
            state.copper = s.copper();
            state.crops.putAll(s.crops());
            state.upgrades.putAll(s.upgrades());
            state.organic = s.organic();
            state.fuel = s.fuel();
            state.compost = s.compost();
            state.composterAt = s.composterAt();
            state.composter.putAll(s.composter());
            state.visitors.addAll(s.visitors());
            state.served.putAll(s.served());
            state.nextVisitor = s.nextVisitor();
            return state;
        }

        private Saved save() {
            return new Saved(built, plots, xp, copper, Map.copyOf(crops), Map.copyOf(upgrades), organic, fuel, compost, composterAt,
                    Map.copyOf(composter), List.copyOf(visitors), Map.copyOf(served), nextVisitor);
        }
    }

    static final Codec<GardenData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, State.CODEC)
            .xmap(GardenData::new, d -> Map.copyOf(d.gardens));
    static final SavedDataType<GardenData> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("gardens"), GardenData::new, CODEC, null);

    final Map<UUID, State> gardens = new HashMap<>();

    private GardenData() {
    }

    private GardenData(Map<UUID, State> gardens) {
        this.gardens.putAll(gardens);
    }

    static GardenData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    State of(UUID profile) {
        setDirty();
        return gardens.computeIfAbsent(profile, k -> new State());
    }
}

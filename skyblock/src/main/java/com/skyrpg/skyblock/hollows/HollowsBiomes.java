package com.skyrpg.skyblock.hollows;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The Crystal Hollows' zones, by place like Hypixel's: the Nucleus in the middle, the Magma Fields underneath (below
 * y 64), and the four quadrants above: Jungle north-west, Mithril Deposits north-east, Goblin Holdout south-west,
 * Precursor Remnants south-east. Its biomes come from the dimension's JSON (written by {@code tools/skyblock/gen_hollows.py}).
 */
public final class HollowsBiomes extends BiomeSource implements BiomeResolver {
    public static final MapCodec<HollowsBiomes> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("jungle").forGetter(s -> s.jungle),
            Biome.CODEC.fieldOf("mithril_deposits").forGetter(s -> s.mithril),
            Biome.CODEC.fieldOf("goblin_holdout").forGetter(s -> s.goblins),
            Biome.CODEC.fieldOf("precursor_remnants").forGetter(s -> s.precursor),
            Biome.CODEC.fieldOf("magma_fields").forGetter(s -> s.magma),
            Biome.CODEC.fieldOf("nucleus").forGetter(s -> s.nucleus)).apply(i, HollowsBiomes::new));
    /** The Magma Fields are everything below this y. */
    public static final int MAGMA_TOP = 64;
    /** The Nucleus: within this many blocks of 0, 0 (x and z), above the Magma Fields. */
    public static final int NUCLEUS = 40;

    private final Holder<Biome> jungle;
    private final Holder<Biome> mithril;
    private final Holder<Biome> goblins;
    private final Holder<Biome> precursor;
    private final Holder<Biome> magma;
    private final Holder<Biome> nucleus;

    public HollowsBiomes(Holder<Biome> jungle, Holder<Biome> mithril, Holder<Biome> goblins, Holder<Biome> precursor, Holder<Biome> magma,
            Holder<Biome> nucleus) {
        this.jungle = jungle;
        this.mithril = mithril;
        this.goblins = goblins;
        this.precursor = precursor;
        this.magma = magma;
        this.nucleus = nucleus;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(jungle, mithril, goblins, precursor, magma, nucleus);
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return this;
    }

    /** Biome coordinates are quarter blocks. */
    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        int x = quartX << 2;
        int y = quartY << 2;
        int z = quartZ << 2;
        if (y < MAGMA_TOP) {
            return magma;
        }
        if (Math.abs(x) < NUCLEUS && Math.abs(z) < NUCLEUS) {
            return nucleus;
        }
        if (z < 0) {
            return x < 0 ? jungle : mithril;
        }
        return x < 0 ? goblins : precursor;
    }

    /** The zone's name at a spot (for the scoreboard and Commissions), as {@link #getNoiseBiome} picks it. */
    public static String zone(int x, int y, int z) {
        if (y < MAGMA_TOP) {
            return "Magma Fields";
        }
        if (Math.abs(x) < NUCLEUS && Math.abs(z) < NUCLEUS) {
            return "Crystal Nucleus";
        }
        if (z < 0) {
            return x < 0 ? "Jungle" : "Mithril Deposits";
        }
        return x < 0 ? "Goblin Holdout" : "Precursor Remnants";
    }
}

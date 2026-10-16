package com.skyrpg.skyblock.mixin;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.skyrpg.skyblock.profile.Profiles;
import java.io.File;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Player data lives in SkyBlock profiles: saving writes into the profile the player is playing, loading reads the one
 * they selected (see {@link Profiles}). Built like vanilla's own save, and loaded data gets vanilla's data fixing.
 */
@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
    @Shadow
    @Final
    private File playerDir;

    @Shadow
    @Final
    protected DataFixer fixerUpper;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void skyrpg$saveToProfile(Player player, CallbackInfo ci) {
        if (!Profiles.active()) {
            return;
        }
        ci.cancel();
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(player.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, player.registryAccess());
            player.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            if (!tag.contains("DataVersion")) {
                NbtUtils.addCurrentDataVersion(tag);
            }
            Profiles.save(player, tag);
        } catch (Exception e) {
            LogUtils.getLogger().warn("Failed to save player data for {}", player.getPlainTextName(), e);
        }
    }

    @Inject(method = "load(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void skyrpg$loadFromProfile(NameAndId who, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        if (!Profiles.active()) {
            return;
        }
        cir.setReturnValue(Profiles.load(who, playerDir.toPath().resolve(who.id() + ".dat"))
                .map(tag -> DataFixTypes.PLAYER.updateToCurrentVersion(fixerUpper, tag, NbtUtils.getDataVersion(tag))));
    }
}

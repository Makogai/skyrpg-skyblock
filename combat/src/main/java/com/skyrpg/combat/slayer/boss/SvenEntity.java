package com.skyrpg.combat.slayer.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Sven Packmaster: Hypixel's wolf slayer boss, a wolf that stays angry at whoever it's after and can't be tamed or fed.
 */
public class SvenEntity extends Wolf implements TieredBoss {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(SvenEntity.class, EntityDataSerializers.INT);

    public SvenEntity(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
    }

    @Override
    public int getTier() {
        return getEntityData().get(TIER);
    }

    @Override
    public void setTier(int tier) {
        getEntityData().set(TIER, tier);
    }

    @Override
    public void tick() {
        super.tick();
        // A boss never calms down: keep its anger on its target.
        if (!level().isClientSide() && tickCount % 20 == 0 && getTarget() instanceof LivingEntity target) {
            setPersistentAngerTarget(EntityReference.of(target));
            startPersistentAngerTimer();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SvenTier", getTier());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setTier(input.getIntOr("SvenTier", 1));
    }
}

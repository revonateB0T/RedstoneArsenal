package cofh.redstonearsenal.common.entity;

import cofh.redstonearsenal.common.item.IFluxItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

import static cofh.redstonearsenal.init.registries.ModEntities.FLUX_ARROW;

public class FluxArrow extends AbstractArrow {

    protected static final EntityDataAccessor<Byte> RSA_FLAGS = SynchedEntityData.defineId(FluxArrow.class, EntityDataSerializers.BYTE);

    protected static final int LIFESPAN = 200;
    protected static final float EXPLOSION_RANGE = 4.0F;

    public FluxArrow(EntityType<? extends FluxArrow> entityIn, Level worldIn) {

        super(entityIn, worldIn);
    }

    public FluxArrow(Level worldIn, LivingEntity shooter) {

        super(FLUX_ARROW.get(), shooter, worldIn);
    }

    public FluxArrow(Level worldIn, double x, double y, double z) {

        super(FLUX_ARROW.get(), x, y, z, worldIn);
    }

    @Override
    protected ItemStack getPickupItem() {

        return ItemStack.EMPTY;
    }

    public void setExplodeArrow(boolean explode) {

        setRSAFlag(1, explode);
    }

    public boolean isExplodeArrow() {

        return (this.entityData.get(RSA_FLAGS) & 1) != 0;
    }

    @Override
    protected void defineSynchedData() {

        super.defineSynchedData();
        this.entityData.define(RSA_FLAGS, (byte) 0);
    }

    private void setRSAFlag(int flag, boolean value) {

        byte b0 = this.entityData.get(RSA_FLAGS);
        if (value) {
            this.entityData.set(RSA_FLAGS, (byte) (b0 | flag));
        } else {
            this.entityData.set(RSA_FLAGS, (byte) (b0 & ~flag));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {

        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public DamageSource getDamageSource(AbstractArrow arrow, @Nullable Entity shooter) {

        return IFluxItem.fluxRangedDamage(arrow, shooter == null ? arrow : shooter);
    }

    public void explode(Vec3 pos) {

        if (!level.isClientSide()) {
            ((ServerLevel) level).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.5F, (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F);
            double r2 = EXPLOSION_RANGE * EXPLOSION_RANGE;
            AABB searchArea = this.getBoundingBox().move(pos.subtract(this.position())).inflate(EXPLOSION_RANGE);
            for (Entity target : level.getEntities(this, searchArea, EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
                if (pos.distanceToSqr(target.getBoundingBox().getCenter()) < r2) {
                    target.hurt(getDamageSource(this, getOwner()), (float) getBaseDamage());
                }
            }
            discard();
        }
    }

    @Override
    public void tick() {

        if (!level.isClientSide() && tickCount > LIFESPAN) {
            discard();
        } else {
            super.tick();
        }
    }

    public float getGravity() {

        return this.isNoGravity() || this.noPhysics ? 0.0F : 0.05F;
    }

    @Override
    public byte getPierceLevel() {

        return isExplodeArrow() ? 0 : super.getPierceLevel();
    }

    @Override
    protected float getWaterInertia() {

        return 0.99F;
    }

    @Override
    protected void onHit(HitResult result) {

        if (isExplodeArrow()) {
            explode(result.getLocation());
        } else {
            HitResult.Type type = result.getType();
            if (type == HitResult.Type.ENTITY) {
                this.onHitEntity((EntityHitResult) result);
            } else if (type == HitResult.Type.BLOCK) {
                this.onHitBlock((BlockHitResult) result);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {

        if (isExplodeArrow()) {
            this.explode(result.getLocation());
            return;
        }
        level.broadcastEntityEvent(this, (byte) 3);
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {

        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("explode", this.isExplodeArrow());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {

        super.readAdditionalSaveData(nbt);
        this.setExplodeArrow(nbt.getBoolean("explode"));
    }

}

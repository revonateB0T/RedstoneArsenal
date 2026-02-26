package cofh.redstonearsenal.common.item;

import cofh.core.common.config.CoreClientConfig;
import cofh.core.compat.curios.CuriosProxy;
import cofh.core.util.ProxyUtils;
import cofh.lib.common.energy.EnergyContainerItemWrapper;
import cofh.lib.util.Utils;
import cofh.lib.util.constants.NBTTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;
import java.util.List;

import static cofh.lib.util.helpers.StringHelper.getTextComponent;

public class FluxElytraItem extends ElytraItem implements IMultiModeFluxItem {

    protected String modId = "";

    protected int maxEnergy;
    protected int extract;
    protected int receive;

    public float propelSpeed = 0.85F;
    public float brakeRate = 0.95F;
    public int boostTime = 32;
    public int energyUseInterval = 8;

    public FluxElytraItem(Properties pProperties, int maxEnergy, int maxTransfer) {

        super(pProperties);

        this.maxEnergy = maxEnergy;
        this.extract = maxTransfer;
        this.receive = maxTransfer;

        ProxyUtils.registerItemModelProperty(this, new ResourceLocation("charged"), this::getChargedModelProperty);
        ProxyUtils.registerItemModelProperty(this, new ResourceLocation("empowered"), this::getEmpoweredModelProperty);
    }

    @Override
    public FluxElytraItem setModId(String modId) {

        this.modId = modId;
        return this;
    }

    @Override
    public String getCreatorModId(ItemStack itemStack) {

        return modId == null || modId.isEmpty() ? super.getCreatorModId(itemStack) : modId;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {

        if (Screen.hasShiftDown() || CoreClientConfig.alwaysShowDetails.get()) {
            tooltipDelegate(stack, worldIn, tooltip, flagIn);
        } else if (CoreClientConfig.holdShiftForDetails.get()) {
            tooltip.add(getTextComponent("info.cofh.hold_shift_for_details").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {

        return new EnergyContainerItemWrapper(stack, this, getEnergyCapability());
    }

    @Override
    public boolean isDamageable(ItemStack stack) {

        return true;
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {

    }

    @Override
    public int getDamage(ItemStack stack) {

        return 0;
    }

    // region DURABILITY BAR
    @Override
    public boolean isBarVisible(ItemStack stack) {

        return IMultiModeFluxItem.super.isBarVisible(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {

        return IMultiModeFluxItem.super.getBarColor(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {

        return IMultiModeFluxItem.super.getBarWidth(stack);
    }
    // endregion

    // region IEnergyContainerItem
    @Override
    public int getExtract(ItemStack container) {

        return extract;
    }

    @Override
    public int getReceive(ItemStack container) {

        return receive;
    }

    @Override
    public int getMaxEnergyStored(ItemStack container) {

        return getMaxStored(container, maxEnergy);
    }
    // endregion

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {

        return hasEnergy(stack, false);
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {

        boolean isCreative = Utils.isCreativePlayer(entity);
        boolean simulate = isCreative || flightTicks % energyUseInterval != 0;
        if (!useEnergy(stack, false, simulate)) {
            return false;
        }

        if (entity.isShiftKeyDown() && useEnergy(stack, true, simulate)) {
            stack.getOrCreateTag().remove(NBTTags.TAG_TIME);
            brake(entity);
        } else {
            CompoundTag tag = stack.getOrCreateTag();
            long time = entity.level.getGameTime();
            if (time - tag.getLong(NBTTags.TAG_TIME) <= boostTime) {
                propel(entity);
            } else if (isEmpowered(stack) && useEnergy(stack, true, isCreative)) {
                tag.putLong(NBTTags.TAG_TIME, time);
                propel(entity);
            }
        }

        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {

        if (entity instanceof LivingEntity && !((LivingEntity) entity).isFallFlying()) {
            stack.getOrCreateTag().remove(NBTTags.TAG_TIME);
        }
    }

    public static ItemStack findFluxElytra(LivingEntity entity) {

        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof FluxElytraItem) {
            return chest;
        }
        final ItemStack[] retStack = {ItemStack.EMPTY};
        CuriosProxy.getAllWorn(entity).ifPresent(c -> {
            for (int i = 0; i < c.getSlots(); ++i) {
                ItemStack slot = c.getStackInSlot(i);
                if (slot.getItem() instanceof FluxElytraItem) {
                    retStack[0] = slot;
                    return;
                }
            }
        });
        return retStack[0];
    }

    //Used to boost a single time, similar to a rocket.
    public boolean boost(ItemStack stack, LivingEntity entity, int time) {

        ItemStack elytraStack = findFluxElytra(entity);
        if (elytraStack.isEmpty() || !elytraStack.canElytraFly(entity)) {
            return false;
        }
        boolean isPlayer = entity instanceof Player;
        boolean isCreative = isPlayer && ((Player) entity).abilities.instabuild;
        if (!useEnergy(stack, getEnergyPerUse(true) * time / energyUseInterval, isCreative)) {
            return false;
        }
        if (!entity.isFallFlying() && isPlayer) {
            ((Player) entity).startFallFlying();
        }
        propel(entity, propelSpeed);
        stack.getOrCreateTag().putLong(NBTTags.TAG_TIME, entity.level.getGameTime());

        return true;
    }

    public boolean boost(ItemStack stack, LivingEntity entity) {

        return boost(stack, entity, boostTime);
    }

    public void propel(LivingEntity entity, double speed) {

        if (entity.isFallFlying()) {
            Vec3 look = entity.getLookAngle();
            Vec3 velocity = entity.getDeltaMovement();
            entity.setDeltaMovement(velocity.add(look.x * speed - velocity.x * 0.5, look.y * speed - velocity.y * 0.5, look.z * speed - velocity.z * 0.5));

            if (entity.level.isClientSide()) {
                entity.level.addParticle(DustParticleOptions.REDSTONE, entity.getX(), entity.getY(), entity.getZ(), 0, 0, 0);
            }
        }
    }

    public void propel(LivingEntity entity) {

        propel(entity, propelSpeed);
    }

    public void brake(LivingEntity entity, double rate) {

        if (entity.isFallFlying()) {
            Vec3 velocity = entity.getDeltaMovement();
            double horzBrake = velocity.x() * velocity.x() + velocity.z() * velocity.z() > 0.16 ? rate : 1;
            double vertBrake = velocity.y() * velocity.y() > 0.2 ? rate : 1;
            entity.setDeltaMovement(velocity.multiply(horzBrake, vertBrake, horzBrake));
        }
    }

    public void brake(LivingEntity entity) {

        brake(entity, brakeRate);
    }

}

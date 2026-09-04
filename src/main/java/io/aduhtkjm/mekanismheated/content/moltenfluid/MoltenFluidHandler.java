package io.aduhtkjm.mekanismheated.content.moltenfluid;

import io.aduhtkjm.mekanismheated.registries.ModFluids;
import java.util.HashSet;
import java.util.Set;
import mekanism.common.registration.impl.FluidRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Makes the mod's molten metal fluids behave like lava toward {@link LivingEntity}s.
 *
 * <p>Vanilla lava's player damage is not in {@code LavaFluid} (that class only ignites nearby
 * blocks); it lives in {@link Entity#lavaHurt()}, applied every tick while an entity reports
 * {@link Entity#isInLava()}. We reproduce that entity-side behavior here for our five molten
 * fluids, and deliberately skip the block-ignition part so our fluids do not set nearby blocks
 * on fire.
 *
 * <p>Damage is dealt through the vanilla {@code lava} damage source, so it is negated by the
 * Fire Resistance effect (and by fire-immune entity types such as Striders) exactly like vanilla
 * lava.
 */
public final class MoltenFluidHandler {

    private static Set<Fluid> moltenFluids;

    private MoltenFluidHandler() {
    }

    /**
     * Caches the molten fluids. Must run after fluid registration (wired from
     * {@code FMLCommonSetupEvent#enqueueWork}).
     */
    public static void init() {
        Set<Fluid> set = new HashSet<>();
        add(set, ModFluids.MOLTEN_IRON);
        add(set, ModFluids.MOLTEN_COPPER);
        add(set, ModFluids.MOLTEN_TIN);
        add(set, ModFluids.MOLTEN_BRONZE);
        add(set, ModFluids.MOLTEN_OSMIUM);
        add(set, ModFluids.MOLTEN_THERMOENERGETIC_ALLOY);
        moltenFluids = Set.copyOf(set);
    }

    private static void add(Set<Fluid> set, FluidRegistryObject<?, ?, ?, ?, ?> object) {
        set.add(object.get());
        set.add(object.getFlowingFluid().get());
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide) {
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (living.fireImmune() || !isTouchingMoltenFluid(level, living.getBoundingBox())) {
            return;
        }
        // Mirror Entity#lavaHurt(): ignite for 15s and take lava damage (negated by Fire Resistance).
        living.igniteForSeconds(15.0F);
        if (living.hurt(living.damageSources().lava(), 4.0F)) {
            living.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + living.getRandom().nextFloat() * 0.4F);
        }
    }

    private static boolean isTouchingMoltenFluid(Level level, AABB aabb) {
        if (moltenFluids == null) {
            return false;
        }
        int minX = Mth.floor(aabb.minX);
        int minY = Mth.floor(aabb.minY);
        int minZ = Mth.floor(aabb.minZ);
        int maxX = Mth.floor(aabb.maxX);
        int maxY = Mth.floor(aabb.maxY);
        int maxZ = Mth.floor(aabb.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    FluidState state = level.getFluidState(pos.set(x, y, z));
                    if (!state.isEmpty() && moltenFluids.contains(state.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

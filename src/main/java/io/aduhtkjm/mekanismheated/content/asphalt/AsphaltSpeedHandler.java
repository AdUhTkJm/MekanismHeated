package io.aduhtkjm.mekanismheated.content.asphalt;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Applies the asphalt block's speed bonus.
 *
 * <p>Two details decide how this is done. Player movement is simulated by the client, so the bonus has to
 * be something the client knows about rather than something the server applies to positions: an attribute
 * modifier fits, because attribute changes on a player are synced to that player (and the bonus then feeds
 * straight into {@code LivingEntity#getSpeed()}, which is what walking and sprinting are built from).
 * {@code ADD_MULTIPLIED_TOTAL} keeps it a flat percentage: walking at 0.1 becomes 0.12, sprinting at 0.13
 * becomes 0.156, so the block is always exactly {@code speedBonus} faster.
 *
 * <p>The modifier is transient, so it is never written to player data and can never outlive the player
 * standing on asphalt - leaving the block removes it on the next tick, and a player who logs out (or dies)
 * while standing on it comes back clean. Adding it is conditional because
 * {@code AttributeInstance#addOrUpdateTransientModifier} marks the attribute dirty on every call, which
 * would re-sync the attribute to the client every single tick.
 */
public final class AsphaltSpeedHandler {

    /** Id of the speed modifier, which is also what makes re-adding it update the existing one. */
    private static final ResourceLocation SPEED_MODIFIER_ID = Mod.rl("asphalt_speed");

    private AsphaltSpeedHandler() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        //Attributes are server authoritative; the client only receives the result of this handler.
        if (player.level().isClientSide) {
            return;
        }
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        if (isOnAsphalt(player)) {
            AttributeModifier existing = movementSpeed.getModifier(SPEED_MODIFIER_ID);
            double bonus = Config.Asphalt.SPEED_BONUS.get();
            if (existing == null || existing.amount() != bonus) {
                movementSpeed.addOrUpdateTransientModifier(
                      new AttributeModifier(SPEED_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else {
            movementSpeed.removeModifier(SPEED_MODIFIER_ID);
        }
    }

    /**
     * Whether the block the player is standing on is asphalt. This uses the same "block below that affects
     * movement" lookup vanilla's block speed factor does, so the bonus covers walking and sprinting but not
     * jumping or flying over the road.
     */
    private static boolean isOnAsphalt(Player player) {
        return player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).is(ModBlocks.ASPHALT_BLOCK.get());
    }
}

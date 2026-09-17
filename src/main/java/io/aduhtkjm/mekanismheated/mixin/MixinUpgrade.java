package io.aduhtkjm.mekanismheated.mixin;

import com.mojang.serialization.Codec;
import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrade;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntFunction;
import mekanism.api.Upgrade;
import mekanism.api.text.EnumColor;
import mekanism.api.text.ILangEntry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static mekanism.api.Upgrade.CHEMICAL;

/**
 * Adds this mod's heat upgrades to Mekanism's {@link Upgrade} enum.
 *
 * <p>The enum is closed to ordinary addons, but not to mixins: its {@code $VALUES} array is rebuilt here with the extra
 * constants during the enum's static initializer, and the three static lookups that were built from the original array
 * (the name {@code CODEC}, the ordinal {@code BY_ID} map and the {@code STREAM_CODEC} derived from it) are recreated on
 * top of the extended array.</p>
 *
 * <p>Because the constants are added at the very end of {@code Upgrade.<clinit>}, they are indistinguishable from
 * Mekanism's own afterwards. Everything that iterates {@code Upgrade.values()} or {@code EnumUtils.UPGRADES} — the
 * upgrade slot's validator, the install tick handling, {@code Upgrade.saveMap}/{@code buildMap}, the container sync, the
 * upgrade window and {@code UpgradeAware} — picks them up with no further mixins. Only the two spots that are hard-coded
 * to Mekanism's seven constants need help: {@code UpgradeUtils.getItem} ({@link MixinUpgradeUtils}) and this mod's own
 * configurable install limit (the {@code getMax} injection below).</p>
 *
 * <p>Another addon may add its own constants the same way. To keep ordinals — and therefore saved NBT and network ids —
 * stable, this mod declares a load-order dependency on the one known to do so (see {@code neoforge.mods.toml}); applying
 * this mixin last means the constants are appended after the other mod's.</p>
 */
@Mixin(value = Upgrade.class, remap = false)
public class MixinUpgrade {

    @Shadow
    @Final
    @Mutable
    private static Upgrade[] $VALUES;

    @Shadow
    @Final
    @Mutable
    public static Codec<Upgrade> CODEC;

    @Shadow
    @Final
    @Mutable
    public static StreamCodec<ByteBuf, Upgrade> STREAM_CODEC;

    @Shadow
    @Final
    @Mutable
    public static IntFunction<Upgrade> BY_ID;

    public MixinUpgrade() {
    }

    /**
     * Invoker for the synthetic {@code (String, int, ...)} enum constructor: the first two arguments are the constant
     * name and ordinal that the compiler would normally supply.
     */
    @Invoker("<init>")
    public static Upgrade mekanismheated$init(String internalName, int internalId, String serializedName, ILangEntry langKey, ILangEntry descLangKey,
          int maxStack, EnumColor color) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void mekanismheated$addHeatUpgrades(CallbackInfo ci) {
        HeatedUpgrade.CONDUCTION = mekanismheated$addVariant("CONDUCTION", ModLang.UPGRADE_CONDUCTION, ModLang.UPGRADE_CONDUCTION_DESCRIPTION, EnumColor.ORANGE);
        HeatedUpgrade.INSULATION = mekanismheated$addVariant("INSULATION", ModLang.UPGRADE_INSULATION, ModLang.UPGRADE_INSULATION_DESCRIPTION, EnumColor.INDIGO);
        HeatedUpgrade.CAPACITY = mekanismheated$addVariant("CAPACITY", ModLang.UPGRADE_CAPACITY, ModLang.UPGRADE_CAPACITY_DESCRIPTION, EnumColor.PURPLE);
        HeatedUpgrade.INV_CONDUCTION = mekanismheated$addVariant("INV_CONDUCTION", ModLang.UPGRADE_INV_CONDUCTION, ModLang.UPGRADE_INV_CONDUCTION_DESCRIPTION, EnumColor.ORANGE);
        HeatedUpgrade.INV_INSULATION = mekanismheated$addVariant("INV_INSULATION", ModLang.UPGRADE_INV_INSULATION, ModLang.UPGRADE_INV_INSULATION_DESCRIPTION, EnumColor.INDIGO);
        HeatedUpgrade.INV_CAPACITY = mekanismheated$addVariant("INV_CAPACITY", ModLang.UPGRADE_INV_CAPACITY, ModLang.UPGRADE_INV_CAPACITY_DESCRIPTION, EnumColor.PURPLE);
        HeatedUpgrade.HEAT_UPGRADES = new Upgrade[]{
            HeatedUpgrade.CONDUCTION, HeatedUpgrade.INSULATION, HeatedUpgrade.CAPACITY,
            HeatedUpgrade.INV_CONDUCTION,  HeatedUpgrade.INV_INSULATION,  HeatedUpgrade.INV_CAPACITY
        };
        //The static lookups were built from the original array, so they have to be rebuilt on top of the extended one
        mekanismheated$reinitializeLookups();
    }

    @Unique
    private static Upgrade mekanismheated$addVariant(String constantName, ILangEntry langKey, ILangEntry descLangKey, EnumColor color) {
        // The max passed here is only a fallback: getMax is injected below to return the configured value instead.
        ArrayList<Upgrade> variants = new ArrayList<>(Arrays.asList($VALUES));
        Upgrade upgrade = mekanismheated$init(constantName, variants.getLast().ordinal() + 1, constantName.toLowerCase(Locale.ROOT), langKey, descLangKey, 64, color);
        variants.add(upgrade);
        MixinUpgrade.$VALUES = variants.toArray(new Upgrade[0]);
        return upgrade;
    }

    @Unique
    private static void mekanismheated$reinitializeLookups() {
        Upgrade[] values = $VALUES;
        Function<String, Upgrade> nameLookup = StringRepresentable.createNameLookup(values, Function.identity());
        // Keep Mekanism's backcompat for the "gas" name its chemical upgrade used to have
        Function<String, Upgrade> remapper = it -> "gas".equals(it) ? CHEMICAL : nameLookup.apply(it);
        CODEC = new StringRepresentable.EnumCodec<>(values, remapper);
        BY_ID = ByIdMap.continuous(Upgrade::ordinal, values, ByIdMap.OutOfBoundsStrategy.WRAP);
        STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Upgrade::ordinal);
    }

    /**
     * Makes the heat upgrades' install limit follow the mod's config instead of the placeholder passed to the enum
     * constructor, which has to be a compile time constant.
     */
    @Inject(method = "getMax", at = @At("HEAD"), cancellable = true)
    private void mekanismheated$getMax(CallbackInfoReturnable<Integer> cir) {
        if (HeatedUpgrade.isHeatUpgrade((Upgrade) (Object) this)) {
            cir.setReturnValue(Config.Upgrades.MAX_HEAT_UPGRADES.get());
        }
    }
}

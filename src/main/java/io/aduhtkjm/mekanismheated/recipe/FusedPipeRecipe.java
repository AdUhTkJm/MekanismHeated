package io.aduhtkjm.mekanismheated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * Shapeless dynamic recipe that builds a {@code fused_pipe} from a base of steel ingots plus any
 * combination of "function" ingredients. Each function ingredient present in the grid enables the
 * matching {@link FusedFunction} on the crafted pipe via its BLOCK_ENTITY_DATA component.
 * <p>
 * Because the output depends on which function ingredients are present, this is a single
 * data-driven recipe rather than one static recipe per function combination.
 */
@NonnullDefault
public class FusedPipeRecipe implements CraftingRecipe {

    public static final Codec<FusedFunction> FUNCTION_CODEC = StringRepresentable.fromValues(FusedFunction::values);

    /**
     * A single "function" ingredient: its {@link FusedFunction} and the {@link Ingredient} that
     * enables it when present in the grid.
     */
    public record FunctionEntry(FusedFunction function, Ingredient item) {
        public static final Codec<FunctionEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              FUNCTION_CODEC.fieldOf("function").forGetter(FunctionEntry::function),
              Ingredient.CODEC.fieldOf("item").forGetter(FunctionEntry::item)
        ).apply(instance, FunctionEntry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FunctionEntry> STREAM_CODEC = StreamCodec.composite(
              ByteBufCodecs.fromCodec(FUNCTION_CODEC), FunctionEntry::function,
              Ingredient.CONTENTS_STREAM_CODEC, FunctionEntry::item,
              FunctionEntry::new
        );
    }

    public static final MapCodec<FusedPipeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
          Codec.STRING.optionalFieldOf("group", "").forGetter(FusedPipeRecipe::getGroup),
          CraftingBookCategory.CODEC.fieldOf("category").forGetter(FusedPipeRecipe::category),
          Ingredient.CODEC.fieldOf("base").forGetter(FusedPipeRecipe::getBase),
          Codec.INT.fieldOf("base_count").forGetter(FusedPipeRecipe::getBaseCount),
          FunctionEntry.CODEC.listOf().fieldOf("functions").forGetter(FusedPipeRecipe::getFunctions),
          ItemStack.CODEC.fieldOf("result").forGetter(FusedPipeRecipe::getResultRaw)
    ).apply(instance, FusedPipeRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FusedPipeRecipe> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.STRING_UTF8, FusedPipeRecipe::getGroup,
          CraftingBookCategory.STREAM_CODEC, FusedPipeRecipe::category,
          Ingredient.CONTENTS_STREAM_CODEC, FusedPipeRecipe::getBase,
          ByteBufCodecs.VAR_INT, FusedPipeRecipe::getBaseCount,
          FunctionEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), FusedPipeRecipe::getFunctions,
          ItemStack.STREAM_CODEC, FusedPipeRecipe::getResultRaw,
          FusedPipeRecipe::new
    );

    private final String group;
    private final CraftingBookCategory category;
    private final Ingredient base;
    private final int baseCount;
    private final List<FunctionEntry> functions;
    private final ItemStack result;

    public FusedPipeRecipe(String group, CraftingBookCategory category, Ingredient base, int baseCount,
          List<FunctionEntry> functions, ItemStack result) {
        this.group = group;
        this.category = category;
        this.base = base;
        this.baseCount = baseCount;
        this.functions = functions;
        this.result = result;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public String getGroup() {
        return group;
    }

    public Ingredient getBase() {
        return base;
    }

    public int getBaseCount() {
        return baseCount;
    }

    public List<FunctionEntry> getFunctions() {
        return functions;
    }

    public ItemStack getResultRaw() {
        return result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Set<FusedFunction> enabled = resolve(input);
        return enabled != null && !enabled.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        Set<FusedFunction> enabled = resolve(input);
        if (enabled == null || enabled.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = this.result.copy();
        result.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(FusedPipeConfig.createBlockEntityData(enabled)));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        //Shapeless, so it works in any grid size.
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.FUSED_PIPE.get();
    }

    /**
     * Greedily claims the base slots first, then one slot per function ingredient.
     *
     * @return the set of enabled functions, or null if the grid does not exactly consist of the
     * base plus a subset of the function ingredients (i.e. the base is unmet, or there are any
     * leftover items the recipe does not consume).
     */
    @Nullable
    private Set<FusedFunction> resolve(CraftingInput input) {
        Set<Integer> used = new HashSet<>();
        int baseLeft = baseCount;
        for (int i = 0; i < input.size(); i++) {
            if (baseLeft > 0 && base.test(input.getItem(i))) {
                used.add(i);
                baseLeft--;
            }
        }
        if (baseLeft > 0) {
            return null;
        }
        Set<FusedFunction> enabled = EnumSet.noneOf(FusedFunction.class);
        for (FunctionEntry entry : functions) {
            for (int i = 0; i < input.size(); i++) {
                if (!used.contains(i) && entry.item().test(input.getItem(i))) {
                    used.add(i);
                    enabled.add(entry.function());
                    break;
                }
            }
        }
        //Reject the grid if any non-empty slot is not claimed, so foreign/extra materials block crafting.
        for (int i = 0; i < input.size(); i++) {
            if (!used.contains(i) && !input.getItem(i).isEmpty()) {
                return null;
            }
        }
        return enabled;
    }
}

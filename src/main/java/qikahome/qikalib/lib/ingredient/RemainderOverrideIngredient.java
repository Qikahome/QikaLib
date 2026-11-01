package qikahome.qikalib.lib.ingredient;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class RemainderOverrideIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:remainder_override");
    public static final MapCodec<RemainderOverrideIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    ItemStack.CODEC.fieldOf("remainder_override").forGetter(i -> i.remainderOverride))
                    .apply(v, RemainderOverrideIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<RemainderOverrideIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), RemainderOverrideIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<RemainderOverrideIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), RemainderOverrideIngredient::createType);

    private final ItemStack remainderOverride;

    public RemainderOverrideIngredient(Ingredient ingredient, ItemStack remainderOverride) {
        super(ingredient, false);
        if (ingredient.getCustomIngredient() instanceof SelfHandlingIngredient)
            throw new IllegalArgumentException(
                    "RemainderOverrideIngredient's inner ingredient must not be a SelfHandlingIngredient");
        this.remainderOverride = remainderOverride.copy();
    }

    public static IngredientType<RemainderOverrideIngredient> createType() {
        return new IngredientType<>(CODEC);
    }

    @Override
    public ItemStack consume(ItemStack stack, ServerLevel level, @Nullable LivingEntity entity) {
        SelfHandlingIngredient.consume(ingredient, stack, level, entity);
        return remainderOverride.copy();
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}

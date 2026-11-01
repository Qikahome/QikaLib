package qikahome.qikalib.lib.ingredient;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class RemainderOverrideIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:remainder_override");
    public static final MapCodec<RemainderOverrideIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    ItemStack.CODEC.fieldOf("remainder_override").forGetter(i -> i.remainderOverride))
                    .apply(v, RemainderOverrideIngredient::new));
    public static final CustomIngredientSerializer<RemainderOverrideIngredient> SERIALIZER = new SimpleIngredientSerializer<>(
            ID, CODEC);

    private final ItemStack remainderOverride;

    public RemainderOverrideIngredient(Ingredient ingredient, ItemStack remainderOverride) {
        super(ingredient, false);
        if (Ingredients.unwrap(ingredient) instanceof SelfHandlingIngredient)
            throw new IllegalArgumentException(
                    "RemainderOverrideIngredient's inner ingredient must not be a SelfHandlingIngredient");
        this.remainderOverride = remainderOverride.copy();
    }

    @Override
    public ItemStack consume(ItemStack stack, ServerLevel level, @Nullable LivingEntity entity) {
        SelfHandlingIngredient.consume(ingredient, stack, level, entity);
        return remainderOverride.copy();
    }

    @Override
    public CustomIngredientSerializer<RemainderOverrideIngredient> getSerializer() {
        return SERIALIZER;
    }
}

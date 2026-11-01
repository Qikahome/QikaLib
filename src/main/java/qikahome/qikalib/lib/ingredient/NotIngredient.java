package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public class NotIngredient implements CustomIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:not");
    public static final MapCodec<NotIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient))
                    .apply(v, NotIngredient::new));
    public static final CustomIngredientSerializer<NotIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID,
            CODEC);

    private final Ingredient ingredient;

    private NotIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public static Ingredient of(Ingredient ingredient) {
        if (Ingredients.unwrap(ingredient) instanceof NotIngredient not) {
            return not.ingredient;
        }
        return new NotIngredient(ingredient).toVanilla();
    }

    @Override
    public Stream<Holder<Item>> items() {
        return ingredient.items();
    }

    @Override
    public SlotDisplay display() {
        List<ItemStack> subItems = IngredientDisplays.stacks(ingredient);
        List<ItemStack> decorated = new ArrayList<>(subItems.size());
        if (subItems.isEmpty()) {
            ItemStack copy = new ItemStack(net.minecraft.world.item.Items.STICK);
            copy.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.not", "nothing"));
            decorated.add(copy);
        } else {
            for (ItemStack stack : subItems) {
                ItemStack copy = stack.copy();
                copy.set(DataComponents.CUSTOM_NAME,
                        Component.translatable("ingredient.qikalib.not", copy.getHoverName()));
                decorated.add(copy);
            }
        }
        return IngredientDisplays.of(decorated);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return !ingredient.test(stack);
    }

    @Override
    public boolean requiresTesting() {
        return Ingredients.requiresTesting(ingredient);
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}

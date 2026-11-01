package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class NotIngredient implements CustomIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:not");
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
    public List<ItemStack> getMatchingStacks() {
        ItemStack[] subItems = ingredient.getItems();
        if (subItems.length == 0) {
            // TrueIngredient 的展示栈是单例，需复制后再改名，避免污染该原料自身的展示。
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack stack : TrueIngredient.INSTANCE.getMatchingStacks()) {
                ItemStack copy = stack.copy();
                copy.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.not", "nothing"));
                list.add(copy);
            }
            return list;
        }
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : subItems) {
            ItemStack copy = stack.copy();
            copy.set(DataComponents.CUSTOM_NAME,
                    Component.translatable("ingredient.qikalib.not", stack.getHoverName()));
            list.add(copy);
        }
        return list;
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
        // 取反无法用"匹配集合"表达（Forge 侧对应 isSimple() == false）。
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}

package qikahome.qikalib.lib.ingredient;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TrueIngredient implements CustomIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:true");
    public static final TrueIngredient INSTANCE = new TrueIngredient();
    public static final MapCodec<TrueIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final CustomIngredientSerializer<TrueIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID,
            CODEC);

    private static final ItemStack ANYTHING_STACK;

    static {
        ANYTHING_STACK = new ItemStack(Items.STICK);
        ANYTHING_STACK.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.true"));
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return List.of(ANYTHING_STACK.copy()); // 注意要 copy，避免修改原实例
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        // true = 接受任意物品；null 不是物品栈，不算"任意"
        return stack != null;
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}

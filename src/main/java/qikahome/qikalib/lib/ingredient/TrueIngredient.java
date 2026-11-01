package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public class TrueIngredient implements CustomIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:true");
    public static final TrueIngredient INSTANCE = new TrueIngredient();
    public static final MapCodec<TrueIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final CustomIngredientSerializer<TrueIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID, CODEC);

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(Items.STICK.builtInRegistryHolder());
    }

    @Override
    public SlotDisplay display() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.true"));
        return IngredientDisplays.of(stack);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        // true = 接受任意物品；空栈不是物品，不算"任意"
        return stack != null && !stack.isEmpty();
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

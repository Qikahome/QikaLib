package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class TrueIngredient implements ICustomIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:true");
    public static final TrueIngredient INSTANCE = new TrueIngredient();
    public static final MapCodec<TrueIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final DeferredHolder<IngredientType<?>, IngredientType<TrueIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), TrueIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<TrueIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), TrueIngredient::createType);

    private static final ItemStack ANYTHING_STACK;

    static {
        ANYTHING_STACK = new ItemStack(Items.STICK);
        ANYTHING_STACK.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.true"));
    }

    private TrueIngredient() {
    }

    public static IngredientType<TrueIngredient> createType() {
        return new IngredientType<>(CODEC);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(ANYTHING_STACK.copy());
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        // true = 接受任意物品；null 不是物品栈，不算"任意"
        return stack != null;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}

package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class NotIngredient implements ICustomIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:not");
    public static final MapCodec<NotIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient))
                    .apply(v, NotIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<NotIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), NotIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<NotIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), NotIngredient::createType);

    private final Ingredient ingredient;
    @Nullable
    private List<ItemStack> cachedDisplayStacks = null;

    private NotIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public static Ingredient of(Ingredient ingredient) {
        if (ingredient.getCustomIngredient() instanceof NotIngredient not) {
            return not.ingredient;
        }
        return new NotIngredient(ingredient).toVanilla();
    }

    public static IngredientType<NotIngredient> createType() {
        return new IngredientType<>(CODEC);
    }

    @Override
    public Stream<ItemStack> getItems() {
        if (cachedDisplayStacks == null) {
            ItemStack[] subItems = ingredient.getItems();
            List<ItemStack> list = new ArrayList<>();
            if (subItems.length == 0) {
                for (ItemStack stack : TrueIngredient.INSTANCE.getItems().toList()) {
                    ItemStack copy = stack.copy();
                    copy.set(DataComponents.CUSTOM_NAME,
                            Component.translatable("ingredient.qikalib.not", "nothing"));
                    list.add(copy);
                }
            } else {
                for (ItemStack stack : subItems) {
                    ItemStack copy = stack.copy();
                    copy.set(DataComponents.CUSTOM_NAME,
                            Component.translatable("ingredient.qikalib.not", copy.getHoverName()));
                    list.add(copy);
                }
            }
            cachedDisplayStacks = list;
        }
        return cachedDisplayStacks.stream().map(ItemStack::copy);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return !ingredient.test(stack);
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

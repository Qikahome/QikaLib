package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class ConditionIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:condition");
    private static final String DEFAULT_MESSAGE = "recipe.qikalib.disabled";

    public static final MapCodec<ConditionIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    ICondition.CODEC.fieldOf("condition").forGetter(i -> i.condition),
                    Codec.STRING.optionalFieldOf("message", DEFAULT_MESSAGE).forGetter(i -> i.message))
                    .apply(v, ConditionIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<ConditionIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), ConditionIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<ConditionIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), ConditionIngredient::createType);

    @Nullable
    private final ICondition condition;
    private final String message;

    private ConditionIngredient(Ingredient ingredient, @Nullable ICondition condition, String message) {
        super(ingredient, false);
        this.condition = condition;
        this.message = message;
    }

    public static IngredientType<ConditionIngredient> createType() {
        return new IngredientType<>(CODEC);
    }

    private boolean passes() {
        return condition != null && condition.test(ICondition.IContext.EMPTY);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (!passes())
            return false;
        return super.test(stack);
    }

    @Override
    public Stream<ItemStack> getItems() {
        if (!passes()) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(DataComponents.CUSTOM_NAME, Component.translatable(message));
            return Stream.of(barrier);
        }
        return super.getItems();
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}

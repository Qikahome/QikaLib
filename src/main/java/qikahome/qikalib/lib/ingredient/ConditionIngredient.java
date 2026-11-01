package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class ConditionIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:condition");
    private static final String DEFAULT_MESSAGE = "recipe.qikalib.disabled";

    public static final MapCodec<ConditionIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    ICondition.CODEC.fieldOf("condition").forGetter(i -> i.condition),
                    Codec.STRING.optionalFieldOf("message", DEFAULT_MESSAGE).forGetter(i -> i.message))
                    .apply(v, ConditionIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<ConditionIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

    @Nullable
    private final ICondition condition;
    private final String message;

    private ConditionIngredient(Ingredient ingredient, @Nullable ICondition condition, String message) {
        super(ingredient, false);
        this.condition = condition;
        this.message = message;
    }

    private boolean passes() {
        return condition.test(ICondition.IContext.EMPTY);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (!passes())
            return false;
        return super.test(stack);
    }

    @Override
    public Stream<Holder<Item>> items() {
        if (!passes())
            return Stream.of(Holder.direct(Items.BARRIER));
        return super.items();
    }

    @Override
    public SlotDisplay display() {
        if (!passes()) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable(message));
            return new SlotDisplay.Composite(
                    java.util.List.of(new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(barrier))));
        }
        return super.display();
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}

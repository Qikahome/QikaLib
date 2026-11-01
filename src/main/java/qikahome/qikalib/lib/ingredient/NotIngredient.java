package qikahome.qikalib.lib.ingredient;

import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.Utils;

public class NotIngredient implements ICustomIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:not");
    public static final MapCodec<NotIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient))
                    .apply(v, NotIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<NotIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

    private final Ingredient ingredient;

    private NotIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public static Ingredient of(Ingredient ingredient) {
        if (ingredient.getCustomIngredient() instanceof NotIngredient not) {
            return not.ingredient;
        }
        return new NotIngredient(ingredient).toVanilla();
    }

    @Nullable
    private SlotDisplay cachedDisplay = null;

    @Override
    public SlotDisplay display() {
        if (cachedDisplay == null) {
            List<ItemStack> list = ingredient.display().resolveForStacks(Utils.displayContext());
            if (list.isEmpty()) {
                list = TrueIngredient.INSTANCE.display().resolveForStacks(Utils.displayContext());
                for (var stack : list) {
                    stack.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.not", "nothing"));
                }
            } else {
                for (var stack : list) {
                    stack.set(DataComponents.CUSTOM_NAME,
                            Component.translatable("ingredient.qikalib.not", stack.getHoverName()));
                }
            }
            cachedDisplay = new SlotDisplay.Composite(list.stream()
                    .map(ItemStackTemplate::fromNonEmptyStack)
                    .map(SlotDisplay.ItemStackSlotDisplay::new)
                    .map(t -> (SlotDisplay) t)
                    .toList());
        }
        return cachedDisplay;
    }

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(Holder.direct(Items.BARRIER));
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
        return ingredient.isSimple();
    }

    public static void register() {
        // do nothing
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }
}

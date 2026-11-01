package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class TrueIngredient implements ICustomIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:true");
    public static final TrueIngredient INSTANCE = new TrueIngredient();
    public static final MapCodec<TrueIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final DeferredHolder<IngredientType<?>, IngredientType<TrueIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

    private static java.util.List<ItemStack> anythingStacks;

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(Holder.direct(Items.STICK));
    }

    @Override
    public SlotDisplay display() {
        if (anythingStacks == null) {
            ItemStack stack = new ItemStack(Items.STICK);
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.true"));
            anythingStacks = java.util.List.of(stack);
        }
        return new SlotDisplay.Composite(anythingStacks.stream()
                .map(ItemStackTemplate::fromNonEmptyStack)
                .map(SlotDisplay.ItemStackSlotDisplay::new)
                .map(t -> (SlotDisplay) t)
                .toList());
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return true;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    public static void register() {
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }
}

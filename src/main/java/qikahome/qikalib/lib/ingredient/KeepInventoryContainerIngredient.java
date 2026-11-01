package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import qikahome.qikalib.Utils;

public enum KeepInventoryContainerIngredient implements ICustomIngredient {
    MAY {
        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && !stack.canFitInsideContainerItems();
        }
    },
    CONTAINS {
        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && hasItems(stack);
        }
    };

    public static final Identifier ID = Identifier.parse("qikalib:keep_inventory_container");
    public static final MapCodec<KeepInventoryContainerIngredient> CODEC = Utils
            .enumCodecIgnoreCase(KeepInventoryContainerIngredient.class).fieldOf("mode");
    public static final DeferredHolder<IngredientType<?>, IngredientType<KeepInventoryContainerIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

    @Nullable
    private java.util.List<ItemStack> displayStacks;

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return true;
    }

    private static boolean hasItems(ItemStack stack) {
        var typedData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (typedData == null) {
            return false;
        }
        CompoundTag blockEntityTag = typedData.copyTagWithoutId();
        if (blockEntityTag == null) {
            return false;
        }
        if (blockEntityTag.contains("Items")) {
            ListTag items = blockEntityTag.getListOrEmpty("Items");
            return !items.isEmpty();
        }
        return false;
    }

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(Holder.direct(Items.SHULKER_BOX));
    }

    @Override
    public SlotDisplay display() {
        if (displayStacks == null) {
            ItemStack stack = new ItemStack(Items.SHULKER_BOX);
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable(
                    "ingredient.qikalib.container." + name().toLowerCase(java.util.Locale.ROOT)));
            displayStacks = java.util.List.of(stack);
        }
        return new SlotDisplay.Composite(displayStacks.stream()
                .map(ItemStackTemplate::fromNonEmptyStack)
                .map(SlotDisplay.ItemStackSlotDisplay::new)
                .map(t -> (SlotDisplay) t)
                .toList());
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

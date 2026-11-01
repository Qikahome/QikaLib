package qikahome.qikalib.lib.ingredient;

import java.util.Locale;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public enum KeepInventoryContainerIngredient implements ICustomIngredient {
    MAY {
        {
            displayStack = new ItemStack(Items.SHULKER_BOX);
            displayStack.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.container.may"));
        }

        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && !stack.canFitInsideContainerItems();
        }
    },
    CONTAINS {
        {
            displayStack = new ItemStack(Items.SHULKER_BOX);
            displayStack.set(DataComponents.CUSTOM_NAME,
                    Component.translatable("ingredient.qikalib.container.contains"));
        }

        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && hasItems(stack);
        }
    };

    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:keep_inventory_container");
    public static final MapCodec<KeepInventoryContainerIngredient> CODEC = Codec.STRING
            .xmap(KeepInventoryContainerIngredient::byName, mode -> mode.name().toLowerCase(Locale.ROOT))
            .fieldOf("mode");
    public static final DeferredHolder<IngredientType<?>, IngredientType<KeepInventoryContainerIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), KeepInventoryContainerIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<KeepInventoryContainerIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), KeepInventoryContainerIngredient::createType);

    protected ItemStack displayStack;

    public static IngredientType<KeepInventoryContainerIngredient> createType() {
        return new IngredientType<>(CODEC);
    }

    private static KeepInventoryContainerIngredient byName(String name) {
        for (KeepInventoryContainerIngredient mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid mode: " + name + ". Expected 'may' or 'contains'");
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return true;
    }

    private static boolean hasItems(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag blockEntityTag = data.copyTag();

        if (blockEntityTag.contains("Items", CompoundTag.TAG_LIST)) {
            return !blockEntityTag.getList("Items", CompoundTag.TAG_COMPOUND).isEmpty();
        }

        return false;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(displayStack.copy());
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

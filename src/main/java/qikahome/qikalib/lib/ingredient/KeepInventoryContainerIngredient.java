package qikahome.qikalib.lib.ingredient;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum KeepInventoryContainerIngredient implements CustomIngredient {
    MAY {
        {
            ItemStack stack = new ItemStack(Items.SHULKER_BOX);
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.container.may"));
            items = List.of(stack);
        }

        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && !stack.getItem().canFitInsideContainerItems();
        }
    },
    CONTAINS {
        {
            ItemStack stack = new ItemStack(Items.SHULKER_BOX);
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.container.contains"));
            items = List.of(stack);
        }

        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && hasItems(stack);
        }
    };

    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:keep_inventory_container");
    public static final MapCodec<KeepInventoryContainerIngredient> CODEC = Codec.STRING
            .xmap(name -> KeepInventoryContainerIngredient.valueOf(name.toUpperCase(Locale.ROOT)),
                    mode -> mode.name().toLowerCase(Locale.ROOT))
            .fieldOf("mode");
    public static final CustomIngredientSerializer<KeepInventoryContainerIngredient> SERIALIZER = new SimpleIngredientSerializer<>(
            ID, CODEC);

    protected List<ItemStack> items;

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return true;
    }

    private static boolean hasItems(ItemStack stack) {
        var blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData == null) {
            return false;
        }
        var blockEntityTag = blockEntityData.copyTag();
        if (blockEntityTag.contains("Items", 9)) {
            ListTag items = blockEntityTag.getList("Items", 10);
            return !items.isEmpty();
        }
        return false;
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return items;
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

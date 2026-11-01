package qikahome.qikalib.lib.ingredient;

import java.util.Locale;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public enum KeepInventoryContainerIngredient implements CustomIngredient {
    MAY("ingredient.qikalib.container.may") {
        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && !stack.getItem().canFitInsideContainerItems();
        }
    },
    CONTAINS("ingredient.qikalib.container.contains") {
        @Override
        public boolean test(@Nullable ItemStack stack) {
            return super.test(stack) && hasItems(stack);
        }
    };

    public static final Identifier ID = Identifier.parse("qikalib:keep_inventory_container");
    public static final MapCodec<KeepInventoryContainerIngredient> CODEC = Codec.STRING
            .xmap(s -> KeepInventoryContainerIngredient.valueOf(s.toUpperCase(Locale.ROOT)),
                    m -> m.name().toLowerCase(Locale.ROOT))
            .fieldOf("mode");
    public static final CustomIngredientSerializer<KeepInventoryContainerIngredient> SERIALIZER = new SimpleIngredientSerializer<>(
            ID, CODEC);

    private final String translationKey;
    @Nullable
    private SlotDisplay display;

    KeepInventoryContainerIngredient(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(Items.SHULKER_BOX.builtInRegistryHolder());
    }

    @Override
    public SlotDisplay display() {
        if (display == null) {
            ItemStack stack = new ItemStack(Items.SHULKER_BOX);
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable(translationKey));
            display = IngredientDisplays.of(stack);
        }
        return display;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return true;
    }

    private static boolean hasItems(ItemStack stack) {
        TypedEntityData<?> blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData == null) {
            return false;
        }
        var tag = blockEntityData.copyTagWithoutId();
        if (tag.contains("Items")) {
            return !tag.getList("Items").isEmpty();
        }
        return false;
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

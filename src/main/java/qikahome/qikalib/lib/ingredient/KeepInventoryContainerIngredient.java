package qikahome.qikalib.lib.ingredient;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import qikahome.qikalib.QikaLib;

public class KeepInventoryContainerIngredient implements CustomIngredient {
    public static final ResourceLocation ID = new ResourceLocation(QikaLib.MODID, "keep_inventory_container");
    public static final ResourceLocation LEGACY_ID = new ResourceLocation(QikaLib.LEGACY_MODID,
            "keep_inventory_container");

    public enum Mode {
        MAY,
        CONTAINS
    }

    private final Mode mode;
    private final List<ItemStack> matchingStacks;

    public KeepInventoryContainerIngredient(Mode mode) {
        this.mode = mode;
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.setHoverName(Component.translatable(
                "ingredient.qikalib.container." + mode.name().toLowerCase(Locale.ROOT)));
        this.matchingStacks = List.of(stack);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (mode == Mode.MAY) {
            return !stack.getItem().canFitInsideContainerItems();
        }

        if (mode == Mode.CONTAINS) {
            return hasItems(stack);
        }

        return false;
    }

    private boolean hasItems(ItemStack stack) {
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        if (blockEntityTag == null) {
            return false;
        }

        if (blockEntityTag.contains("Items", 9)) {
            ListTag items = blockEntityTag.getList("Items", 10);
            return !items.isEmpty();
        }

        return false;
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return matchingStacks;
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements CustomIngredientSerializer<KeepInventoryContainerIngredient> {
        public static final Serializer INSTANCE = new Serializer(ID);
        /** 旧 id 的独立实例。 */
        public static final Serializer LEGACY_INSTANCE = new Serializer(LEGACY_ID);

        private final ResourceLocation id;

        public Serializer(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation getIdentifier() {
            return id;
        }

        @Override
        public KeepInventoryContainerIngredient read(FriendlyByteBuf buffer) {
            String modeStr = buffer.readUtf();
            Mode mode = Mode.valueOf(modeStr.toUpperCase());
            return new KeepInventoryContainerIngredient(mode);
        }

        @Override
        public KeepInventoryContainerIngredient read(JsonObject json) {
            String modeStr = json.has("mode") ? json.get("mode").getAsString().toUpperCase() : "MAY";
            try {
                Mode mode = Mode.valueOf(modeStr);
                return new KeepInventoryContainerIngredient(mode);
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("Invalid mode: " + modeStr + ". Expected 'may' or 'contains'");
            }
        }

        @Override
        public void write(FriendlyByteBuf buffer, KeepInventoryContainerIngredient ingredient) {
            buffer.writeUtf(ingredient.mode.name().toLowerCase());
        }

        @Override
        public void write(JsonObject json, KeepInventoryContainerIngredient ingredient) {
            json.addProperty("mode", ingredient.mode.name().toLowerCase());
        }
    }
}

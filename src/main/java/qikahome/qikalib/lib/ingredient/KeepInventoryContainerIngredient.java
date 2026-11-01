package qikahome.qikalib.lib.ingredient;

import java.util.Locale;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

public class KeepInventoryContainerIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:keep_inventory_container");

    public enum Mode {
        MAY,
        CONTAINS
    }

    private final Mode mode;

    public KeepInventoryContainerIngredient(Mode mode) {
        super(Stream.of(new Ingredient.ItemValue(createDisplayStack(mode))));
        this.mode = mode;
    }

    /** JEI/配方书里展示的占位物品：带名称的潜影盒。 */
    private static ItemStack createDisplayStack(Mode mode) {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.setHoverName(Component.translatable(
                "ingredient.qikalib.container." + mode.name().toLowerCase(Locale.ROOT)));
        return stack;
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
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.addProperty("mode", mode.name().toLowerCase());
        return json;
    }

    public static class Serializer implements IIngredientSerializer<KeepInventoryContainerIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public KeepInventoryContainerIngredient parse(FriendlyByteBuf buffer) {
            String modeStr = buffer.readUtf();
            Mode mode = Mode.valueOf(modeStr.toUpperCase());
            return new KeepInventoryContainerIngredient(mode);
        }

        @Override
        public KeepInventoryContainerIngredient parse(JsonObject json) {
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
    }
}

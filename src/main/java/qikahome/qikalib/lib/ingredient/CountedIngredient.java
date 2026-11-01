package qikahome.qikalib.lib.ingredient;

import static qikahome.qikalib.QikaLib.LOGGER;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

public class CountedIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:counted");
    public static final StringTag NO_CONSUME = StringTag.valueOf("{\"translate\":\"ui.qikalib.no_consume\"}");

    private final int count;

    public CountedIngredient(Ingredient ingredient, int count) {
        super(ingredient, false);
        this.count = count;
    }

    @Override
    public ItemStack consume(ItemStack stack) {
        if (stack.isEmpty())
            return stack;
        ItemStack remainder = super.consume(stack).copy();
        if (count == 0) {
            ItemStack stack2 = stack.copy();
            stack2.setCount(1);
            return stack2;
        }
        stack.shrink(count - 1); // Unsafe
        remainder.setCount(remainder.getCount() * count);
        return remainder;
    }

    @Override
    public ItemStack[] getItems() {
        ItemStack[] items = super.getItems();
        for (int i = 0; i < items.length; i++) {
            items[i] = items[i].copy();
            if (count > 0)
                items[i].setCount(count);
            else {
                try {
                    var display = items[i].getOrCreateTagElement(ItemStack.TAG_DISPLAY);
                    var lore = display.getList(ItemStack.TAG_LORE, 8);
                    if (!lore.contains(NO_CONSUME))
                        lore.add(NO_CONSUME);
                    display.put(ItemStack.TAG_LORE, lore);
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse lore: {}", e.getMessage());
                }
            }
        }
        return items;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (super.test(stack))
            if (stack.getCount() >= count)
                return true;
        return false;
    }

    @Override
    public IIngredientSerializer<CountedIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.add("ingredient", ingredient.toJson());
        json.addProperty("count", count);
        return json;
    }

    public static class Serializer implements IIngredientSerializer<CountedIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public CountedIngredient parse(FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int count = buffer.readVarInt();
            return new CountedIngredient(ingredient, count);
        }

        @Override
        public CountedIngredient parse(JsonObject json) {
            if (!json.has("ingredient")) {
                throw new JsonParseException("Counted ingredient must have 'ingredient' field");
            }

            int count = GsonHelper.getAsInt(json, "count", 1);
            Ingredient ingredient;
            if (json.has("remainder_override")) {
                ingredient = RemainderOverrideIngredient.Serializer.INSTANCE.parse(json);
            } else {
                JsonElement ingredientJson = json.get("ingredient");
                ingredient = Ingredient.fromJson(ingredientJson);
            }

            return new CountedIngredient(ingredient, count);
        }

        @Override
        public void write(FriendlyByteBuf buffer, CountedIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeVarInt(ingredient.count);
        }
    }
}

package qikahome.qikalib.lib.ingredient;

import static qikahome.qikalib.QikaLib.LOGGER;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

public class RemainderOverrideIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:remainder_override");

    private final ItemStack remainderOverride;

    public RemainderOverrideIngredient(Ingredient ingredient, ItemStack remainderOverride) {
        super(ingredient, false);
        if (ingredient instanceof SelfHandlingIngredient)
            throw new IllegalArgumentException("RemainderOverrideIngredient's inner ingredient must not be a SelfHandlingIngredient");
        this.remainderOverride = remainderOverride.copy();
    }

    @Override
    public ItemStack consume(ItemStack stack) {
        SelfHandlingIngredient.consume(ingredient, stack);
        return remainderOverride.copy();
    }

    @Override
    public IIngredientSerializer<RemainderOverrideIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.add("ingredient", ingredient.toJson());
        DataResult<JsonElement> result = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, remainderOverride);
        JsonElement element = result.getOrThrow(false, exception -> LOGGER.error(exception));
        json.add("remainder_override", element);
        return json;
    }

    public static class Serializer implements IIngredientSerializer<RemainderOverrideIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public RemainderOverrideIngredient parse(FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            ItemStack remainderOverride = buffer.readItem();
            return new RemainderOverrideIngredient(ingredient, remainderOverride);
        }

        @Override
        public RemainderOverrideIngredient parse(JsonObject json) {
            if (!json.has("ingredient")) {
                throw new JsonParseException("RemainderOverrideIngredient must have 'ingredient' field");
            }
            if (!json.has("remainder_override")) {
                throw new JsonParseException("RemainderOverrideIngredient must have 'remainder_override' field");
            }

            JsonElement ingredientJson = json.get("ingredient");
            Ingredient ingredient = Ingredient.fromJson(ingredientJson);

            try {
                DataResult<ItemStack> result = ItemStack.CODEC.parse(JsonOps.INSTANCE,
                        json.get("remainder_override"));
                ItemStack remainderOverride = result.getOrThrow(false, String::new);
                return new RemainderOverrideIngredient(ingredient, remainderOverride);
            } catch (RuntimeException e) {
                throw new JsonParseException("Invalid remainder_override: " + e.getMessage(), e);
            }
        }

        @Override
        public void write(FriendlyByteBuf buffer, RemainderOverrideIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeItemStack(ingredient.remainderOverride, false);
        }
    }
}

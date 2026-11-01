package qikahome.qikalib.lib.ingredient;

import static qikahome.qikalib.QikaLib.LOGGER;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import qikahome.qikalib.QikaLib;

public class CountedIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation(QikaLib.MODID, "counted");
    public static final ResourceLocation LEGACY_ID = new ResourceLocation(QikaLib.LEGACY_MODID, "counted");
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
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements CustomIngredientSerializer<CountedIngredient> {
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
        public CountedIngredient read(FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int count = buffer.readVarInt();
            return new CountedIngredient(ingredient, count);
        }

        @Override
        public CountedIngredient read(JsonObject json) {
            if (!json.has("ingredient")) {
                throw new JsonParseException("Counted ingredient must have 'ingredient' field");
            }

            int count = GsonHelper.getAsInt(json, "count", 1);
            Ingredient ingredient;
            if (json.has("remainder_override")) {
                ingredient = RemainderOverrideIngredient.Serializer.INSTANCE.read(json).toVanilla();
            } else {
                ingredient = Ingredient.fromJson(json.get("ingredient"));
            }

            return new CountedIngredient(ingredient, count);
        }

        @Override
        public void write(FriendlyByteBuf buffer, CountedIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeVarInt(ingredient.count);
        }

        @Override
        public void write(JsonObject json, CountedIngredient ingredient) {
            json.add("ingredient", ingredient.ingredient.toJson());
            json.addProperty("count", ingredient.count);
        }
    }
}

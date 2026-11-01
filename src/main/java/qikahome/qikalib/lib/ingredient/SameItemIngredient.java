package qikahome.qikalib.lib.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import qikahome.qikalib.lib.handling.HandlingContext;

public class SameItemIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:same_item");
    private final HandlingContext.Key<Item> key;
    protected final int group;
    /** {@link #getItems()} 的缓存，内层原料解析后固定。 */
    private ItemStack[] items;

    public SameItemIngredient(Ingredient ingredient, int group) {
        super(ingredient, true);
        this.group = group;
        this.key = new HandlingContext.Key<>(Item.class, ID.withSuffix(String.valueOf(group)));
    }

    @Override
    public boolean test(ItemStack stack) {
        if (super.test(stack)) {
            var context = getContext();
            return !context.isValid()
                    || stack.is(context.getOrAdd(key, stack::getItem));
        }
        return false;
    }

    @Override
    public IIngredientSerializer<SameItemIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    /**
     * 在 JEI/配方书里给每个候选物品加两行说明（分组语义 + 分组号）。
     * 结果缓存：内层原料解析后不再变化，重复调用不重复造栈。
     */
    @Override
    public ItemStack[] getItems() {
        if (items == null) {
            ItemStack[] inner = ingredient.getItems();
            ItemStack[] decorated = new ItemStack[inner.length];
            for (int i = 0; i < inner.length; i++) {
                ItemStack stack = inner[i];
                if (!stack.isEmpty()) {
                    stack = stack.copy();
                    CompoundTag display = stack.getOrCreateTagElement("display");
                    ListTag lore = display.getList("Lore", Tag.TAG_STRING);
                    lore.add(StringTag.valueOf(Component.Serializer.toJson(
                            Component.translatable("ingredient.qikalib.same_item.requires_same_group")
                                    .withStyle(ChatFormatting.GRAY))));
                    lore.add(StringTag.valueOf(Component.Serializer.toJson(
                            Component.translatable("ingredient.qikalib.same_item.group", group)
                                    .withStyle(ChatFormatting.DARK_GRAY))));
                    display.put("Lore", lore);
                }
                decorated[i] = stack;
            }
            items = decorated;
        }
        return items;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.add("ingredient", ingredient.toJson());
        json.addProperty("group", group);
        return json;
    }

    public static class Serializer implements IIngredientSerializer<SameItemIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public SameItemIngredient parse(FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int group = buffer.readVarInt();
            return new SameItemIngredient(ingredient, group);
        }

        @Override
        public SameItemIngredient parse(JsonObject json) {
            if (!json.has("ingredient")) {
                throw new JsonParseException("Same Item ingredient must have 'ingredient' field");
            }
            if (!json.has("group")) {
                throw new JsonParseException("Same Item ingredient must have 'group' field");
            }

            int group = json.get("group").getAsInt();
            JsonElement ingredientJson = json.get("ingredient");
            Ingredient ingredient = Ingredient.fromJson(ingredientJson);

            return new SameItemIngredient(ingredient, group);
        }

        @Override
        public void write(FriendlyByteBuf buffer, SameItemIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeVarInt(ingredient.group);
        }

    }
}

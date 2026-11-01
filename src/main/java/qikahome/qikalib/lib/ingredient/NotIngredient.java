package qikahome.qikalib.lib.ingredient;

import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import qikahome.qikalib.QikaLib;

public class NotIngredient implements CustomIngredient {
    public static final ResourceLocation ID = new ResourceLocation(QikaLib.MODID, "not");
    public static final ResourceLocation LEGACY_ID = new ResourceLocation(QikaLib.LEGACY_MODID, "not");

    private final Ingredient ingredient;

    private NotIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public static Ingredient of(Ingredient ingredient) {
        if (Ingredients.unwrap(ingredient) instanceof NotIngredient not) {
            return not.ingredient;
        }
        return new NotIngredient(ingredient).toVanilla();
    }

    /**
     * 便捷重载：Fabric 的自定义原料不是 {@link Ingredient}，调用方拿到 {@link CustomIngredient}
     * 时可直接传入，内部再经 {@link CustomIngredient#toVanilla()} 转换。
     */
    public static Ingredient of(CustomIngredient ingredient) {
        return new NotIngredient(ingredient.toVanilla()).toVanilla();
    }

    private ItemStack[] cachedDisplayStacks = null;

    /** 展示物品入口（保持 Forge 原版的数组形式）。 */
    public ItemStack[] getItems() {
        if (cachedDisplayStacks == null) {
            ItemStack[] subItems = ingredient.getItems();
            if (subItems.length == 0) {
                ItemStack[] trueItems = TrueIngredient.INSTANCE.getItems();
                cachedDisplayStacks = new ItemStack[trueItems.length];
                for (int i = 0; i < trueItems.length; i++) {
                    ItemStack copy = trueItems[i].copy();
                    copy.setHoverName(Component.translatable("ingredient.qikalib.not", "nothing"));
                    cachedDisplayStacks[i] = copy;
                }
            } else {
                cachedDisplayStacks = new ItemStack[subItems.length];
                for (int i = 0; i < subItems.length; i++) {
                    ItemStack copy = subItems[i].copy();
                    Component originalName = copy.getHoverName();
                    copy.setHoverName(Component.translatable("ingredient.qikalib.not", originalName));
                    cachedDisplayStacks[i] = copy;
                }
            }
        }
        return cachedDisplayStacks.clone();
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return List.of(getItems());
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return !ingredient.test(stack);
    }

    @Override
    public boolean requiresTesting() {
        // 展示列表是"取反"后的展示栈，并非接受集合，必须走直接测试。
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements CustomIngredientSerializer<NotIngredient> {
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
        public NotIngredient read(FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            return new NotIngredient(ingredient);
        }

        @Override
        public NotIngredient read(JsonObject json) {
            if (!json.has("ingredient")) {
                throw new JsonParseException("Not ingredient must have 'ingredient' field");
            }

            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
            return new NotIngredient(ingredient);
        }

        @Override
        public void write(FriendlyByteBuf buffer, NotIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
        }

        @Override
        public void write(JsonObject json, NotIngredient ingredient) {
            json.add("ingredient", ingredient.ingredient.toJson());
        }
    }
}

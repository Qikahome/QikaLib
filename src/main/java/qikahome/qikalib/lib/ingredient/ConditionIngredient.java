package qikahome.qikalib.lib.ingredient;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import qikahome.qikalib.QikaLib;

public class ConditionIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation(QikaLib.MODID, "condition");
    public static final ResourceLocation LEGACY_ID = new ResourceLocation(QikaLib.LEGACY_MODID, "condition");
    private static final String DEFAULT_MESSAGE = "recipe.qikalib.disabled";

    /**
     * Fabric 1.20.1 的资源条件是 JSON 版，这里保留条件对象原文，运行时改键后交给
     * {@link ResourceConditions#conditionMatches(JsonObject)} 求值。
     */
    @Nullable
    private final JsonObject conditionJson;
    private final boolean networkPasses;
    private final String message;

    private ConditionIngredient(Ingredient ingredient, @Nullable JsonObject conditionJson, boolean networkPasses,
            String message) {
        super(ingredient, false);
        this.conditionJson = conditionJson;
        this.networkPasses = networkPasses;
        this.message = message;
    }

    private boolean passes() {
        if (conditionJson == null)
            return networkPasses; // 从网络来的，使用服务端评估结果
        try {
            // Fabric 的条件 dispatch 键是 "condition"，而 JsonMore 数据沿用 "type"；
            // 求值前把键名换掉，数据格式就和原版保持一致了。
            JsonObject normalized = conditionJson.deepCopy();
            JsonElement type = normalized.remove("type");
            if (type != null && !normalized.has("condition")) {
                normalized.add("condition", type);
            }
            return ResourceConditions.conditionMatches(normalized);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (!passes())
            return false;
        return super.test(stack);
    }

    @Override
    public ItemStack[] getItems() {
        if (!passes()) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.setHoverName(Component.translatable(message));
            return new ItemStack[] { barrier };
        }
        return super.getItems();
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements CustomIngredientSerializer<ConditionIngredient> {
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
        public ConditionIngredient read(FriendlyByteBuf buffer) {
            boolean passes = buffer.readBoolean();
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            String message = buffer.readUtf();
            return new ConditionIngredient(ingredient, null, passes, message);
        }

        @Override
        public ConditionIngredient read(JsonObject json) {
            if (!json.has("condition"))
                throw new JsonParseException("Condition ingredient must have 'condition' field");
            if (!json.has("ingredient"))
                throw new JsonParseException("Condition ingredient must have 'ingredient' field");

            JsonObject conditionJson = json.getAsJsonObject("condition").deepCopy();
            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
            String message = json.has("message") ? json.get("message").getAsString() : DEFAULT_MESSAGE;

            return new ConditionIngredient(ingredient, conditionJson, false, message);
        }

        @Override
        public void write(FriendlyByteBuf buffer, ConditionIngredient ingredient) {
            buffer.writeBoolean(ingredient.passes());
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeUtf(ingredient.message);
        }

        @Override
        public void write(JsonObject json, ConditionIngredient ingredient) {
            if (ingredient.conditionJson != null)
                json.add("condition", ingredient.conditionJson.deepCopy());
            json.add("ingredient", ingredient.ingredient.toJson());
            if (!DEFAULT_MESSAGE.equals(ingredient.message))
                json.addProperty("message", ingredient.message);
        }
    }
}

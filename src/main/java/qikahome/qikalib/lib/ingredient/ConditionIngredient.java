package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.common.crafting.conditions.ICondition;

public class ConditionIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:condition");
    private static final String DEFAULT_MESSAGE = "recipe.qikalib.disabled";

    @Nullable
    private final ICondition condition;
    @Nullable
    private final JsonObject conditionJson;
    private final boolean networkPasses;
    private final String message;

    private ConditionIngredient(Ingredient ingredient, @Nullable ICondition condition, @Nullable JsonObject conditionJson, boolean networkPasses, String message) {
        super(ingredient, false);
        this.condition = condition;
        this.conditionJson = conditionJson;
        this.networkPasses = networkPasses;
        this.message = message;
    }

    private boolean passes() {
        if (conditionJson == null)
            return networkPasses; // 从网络来的，使用服务端评估结果
        return condition.test(ICondition.IContext.EMPTY);
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
            return new ItemStack[]{ barrier };
        }
        return super.getItems();
    }

    @Override
    public IIngredientSerializer<ConditionIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        if (conditionJson != null)
            json.add("condition", conditionJson.deepCopy());
        json.add("ingredient", ingredient.toJson());
        if (!DEFAULT_MESSAGE.equals(message))
            json.addProperty("message", message);
        return json;
    }

    public static class Serializer implements IIngredientSerializer<ConditionIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public ConditionIngredient parse(FriendlyByteBuf buffer) {
            boolean passes = buffer.readBoolean();
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            String message = buffer.readUtf();
            return new ConditionIngredient(ingredient, null, null, passes, message);
        }

        @Override
        public ConditionIngredient parse(JsonObject json) {
            if (!json.has("condition"))
                throw new JsonParseException("Condition ingredient must have 'condition' field");
            if (!json.has("ingredient"))
                throw new JsonParseException("Condition ingredient must have 'ingredient' field");

            JsonObject conditionJson = json.getAsJsonObject("condition").deepCopy();
            ICondition condition = CraftingHelper.getCondition(conditionJson);
            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
            String message = json.has("message") ? json.get("message").getAsString() : DEFAULT_MESSAGE;

            return new ConditionIngredient(ingredient, condition, conditionJson, false, message);
        }

        @Override
        public void write(FriendlyByteBuf buffer, ConditionIngredient ingredient) {
            buffer.writeBoolean(ingredient.passes());
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeUtf(ingredient.message);
        }
    }
}

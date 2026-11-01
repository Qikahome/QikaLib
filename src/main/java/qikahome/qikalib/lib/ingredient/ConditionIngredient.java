package qikahome.qikalib.lib.ingredient;

import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public class ConditionIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:condition");
    private static final String DEFAULT_MESSAGE = "recipe.qikalib.disabled";

    /**
     * Fabric 的资源条件不是 Forge 的 {@code ICondition} 体系，而是可注册的
     * {@link ResourceCondition}；这里保留条件对象原文，运行时用官方 codec 解析后判定。
     */
    public static final Codec<JsonObject> JSON_OBJECT_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json));

    public static final MapCodec<ConditionIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    JSON_OBJECT_CODEC.fieldOf("condition").forGetter(i -> i.condition),
                    Codec.STRING.optionalFieldOf("message", DEFAULT_MESSAGE).forGetter(i -> i.message))
                    .apply(v, ConditionIngredient::new));
    public static final CustomIngredientSerializer<ConditionIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID,
            CODEC);

    private final JsonObject condition;
    private final String message;

    private ConditionIngredient(Ingredient ingredient, JsonObject condition, String message) {
        super(ingredient, false);
        this.condition = condition;
        this.message = message;
    }

    private boolean passes() {
        try {
            // Fabric 条件的 dispatch 键是 "condition"，而 JsonMore/Forge 的数据沿用 "type"；
            // 解析前把键名换掉，数据格式就和 Forge 侧保持一致了。
            JsonObject normalized = condition.deepCopy();
            var type = normalized.remove("type");
            if (type != null && !normalized.has("condition")) {
                normalized.add("condition", type);
            }
            return ResourceCondition.CONDITION_CODEC.parse(JsonOps.INSTANCE, normalized)
                    .result().map(c -> c.test(null)).orElse(false);
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
    public List<ItemStack> getMatchingStacks() {
        if (!passes()) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(DataComponents.CUSTOM_NAME, Component.translatable(message));
            return List.of(barrier);
        }
        return super.getMatchingStacks();
    }

    @Override
    public CustomIngredientSerializer<ConditionIngredient> getSerializer() {
        return SERIALIZER;
    }
}

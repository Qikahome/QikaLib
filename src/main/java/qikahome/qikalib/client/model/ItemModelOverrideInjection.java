package qikahome.qikalib.client.model;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

/**
 * 一条 26.x item model 注入：目标物品 + 优先级 + 待拼接的 ItemModel.Unbaked JSON。
 *
 * <p>JSON 位于 {@code assets/<ns>/item_model_overrides/*.json}，根节点是完整的
 * 26.x item model 定义，要求 {@code type} 为 {@code minecraft:select} /
 * {@code minecraft:range_dispatch} / {@code minecraft:condition}，
 * 并把 {@code fallback}（condition 为 {@code on_false}）写成
 * {@code {"type":"qikalib:nested"}} 占位。
 */
public record ItemModelOverrideInjection(Identifier source, Identifier item, int priority, JsonObject model) {
}

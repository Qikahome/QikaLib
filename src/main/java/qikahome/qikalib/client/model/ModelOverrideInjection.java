package qikahome.qikalib.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条模型 override 注入：目标模型 ID + 要追加的 overrides 列表。
 * <p>
 * JSON 位于资源包 {@code assets/<ns>/model_overrides/}：
 * <pre>{@code
 * {
 *   "model": "ns:path",            // 目标模型 ID（如 tconstruct:item/tools/pickaxe）
 *   "overrides": [                 // 与原版模型 JSON 的 overrides 语法一致
 *     {"predicate": {...}, "model": "..."}
 *   ]
 * }
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public record ModelOverrideInjection(ResourceLocation model, List<ItemOverride> overrides) {
  /**
   * 复用原版 {@link ItemOverride.Deserializer} 解析 override 条目，
   * 保证与原版模型 JSON 的 overrides 语法、数值解析完全一致。
   */
  public static ModelOverrideInjection fromJson(JsonObject json) {
    ResourceLocation model = new ResourceLocation(GsonHelper.getAsString(json, "model"));
    List<ItemOverride> overrides = new ArrayList<>();
    for (JsonElement element : GsonHelper.getAsJsonArray(json, "overrides")) {
      overrides.add(new ItemOverride.Deserializer().deserialize(element, ItemOverride.class, null));
    }
    return new ModelOverrideInjection(model, overrides);
  }
}

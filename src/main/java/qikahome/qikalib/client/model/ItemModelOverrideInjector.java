package qikahome.qikalib.client.model;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ConditionalItemModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import qikahome.qikalib.QikaLib;

/**
 * 26.x item model 注入器（客户端）。
 *
 * <p>26.x 没有 {@code ItemOverride}，物品模型是 {@code assets/<ns>/items/<item>.json}
 * 里的完整 {@link ItemModel.Unbaked} 树。本类通过
 * {@code assets/<ns>/item_model_overrides/*.json} 在 {@link ClientItemInfoLoader} 产出
 * {@code ClientItem} 之后、{@code ModelDiscovery} 之前改写目标物品的模型根节点：
 * 用 {@code qikalib:nested} 占位符指向"下一层"（下一个注入或原始模型）。
 *
 * <p>同一个物品有多条注入时按 {@code priority} 升序拼接，优先级最高的在最外层；
 * 最终 fallback 是物品原本的 {@code ClientItem.model}。
 */
public final class ItemModelOverrideInjector {
  public static final String FOLDER = "item_model_overrides";

  private static final String NESTED_TYPE = "qikalib:nested";

  private ItemModelOverrideInjector() {
  }

  public static ClientItemInfoLoader.LoadedClientInfos apply(ResourceManager manager,
      ClientItemInfoLoader.LoadedClientInfos infos) {
    Map<Identifier, List<ItemModelOverrideInjection>> injections = load(manager);
    if (injections.isEmpty()) {
      return infos;
    }

    Map<Identifier, ClientItem> contents = infos.contents();
    Map<Identifier, ClientItem> result = new HashMap<>(contents);

    for (Map.Entry<Identifier, List<ItemModelOverrideInjection>> entry : injections.entrySet()) {
      Identifier itemId = entry.getKey();
      ClientItem original = contents.get(itemId);
      if (original == null) {
        QikaLib.LOGGER.warn("Item model override targets unknown item {} (mod missing?), skipped", itemId);
        continue;
      }

      List<ItemModelOverrideInjection> chain = new ArrayList<>(entry.getValue());
      chain.sort(Comparator.comparingInt(ItemModelOverrideInjection::priority)
          .thenComparing(injection -> injection.source().toString()));

      ItemModel.Unbaked built = original.model();
      boolean applied = false;
      for (ItemModelOverrideInjection injection : chain) {
        ItemModel.Unbaked next = replaceFallback(injection, built);
        if (next != null) {
          built = next;
          applied = true;
        }
      }

      if (applied) {
        result.put(itemId, new ClientItem(built, original.properties(), original.registrySwapper()));
      }
    }

    return new ClientItemInfoLoader.LoadedClientInfos(result);
  }

  private static Map<Identifier, List<ItemModelOverrideInjection>> load(ResourceManager manager) {
    Map<Identifier, List<ItemModelOverrideInjection>> result = new HashMap<>();
    Map<Identifier, Resource> resources = manager.listResources(FOLDER,
        path -> path.getPath().endsWith(".json"));

    for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
      Identifier source = entry.getKey();
      try (Reader reader = entry.getValue().openAsReader()) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
          QikaLib.LOGGER.error("Item model override {} must be a JSON object", source);
          continue;
        }
        JsonObject json = root.getAsJsonObject();
        Identifier item = Identifier.parse(json.get("item").getAsString());
        int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
        JsonObject model = json.getAsJsonObject("model");
        if (model == null) {
          QikaLib.LOGGER.error("Item model override {} is missing \"model\"", source);
          continue;
        }
        result.computeIfAbsent(item, key -> new ArrayList<>())
            .add(new ItemModelOverrideInjection(source, item, priority, model));
      } catch (Exception ex) {
        QikaLib.LOGGER.error("Couldn't parse item model override {}", source, ex);
      }
    }
    return result;
  }

  private static ItemModel.Unbaked replaceFallback(ItemModelOverrideInjection injection,
      ItemModel.Unbaked fallback) {
    JsonObject model = injection.model();
    Identifier source = injection.source();
    String type = model.has("type") ? model.get("type").getAsString() : "";
    String key;
    if (type.endsWith("condition")) {
      key = "on_false";
    } else if (type.endsWith("select") || type.endsWith("range_dispatch")) {
      key = "fallback";
    } else {
      QikaLib.LOGGER.error("Item model override {} must use select/range_dispatch/condition at root, got {}",
          source, type);
      return null;
    }

    if (!isNestedPlaceholder(model.get(key))) {
      QikaLib.LOGGER.error("Item model override {} must put {{\"type\":\"{}\"}} at \"{}\"",
          source, NESTED_TYPE, key);
      return null;
    }

    JsonObject parseable = model.deepCopy();
    JsonObject empty = new JsonObject();
    empty.addProperty("type", "minecraft:empty");
    parseable.add("fallback", empty);
    parseable.add("on_false", empty);
    if (containsNestedPlaceholder(parseable)) {
      QikaLib.LOGGER.error("Item model override {} has {} outside the root fallback slot",
          source, NESTED_TYPE);
      return null;
    }

    ItemModel.Unbaked parsed = ItemModels.CODEC.parse(ops(), parseable)
        .resultOrPartial(error -> QikaLib.LOGGER.error("Couldn't parse item model override {}: {}", source, error))
        .orElse(null);
    if (parsed == null) {
      return null;
    }

    if (parsed instanceof ConditionalItemModel.Unbaked unbaked) {
      return new ConditionalItemModel.Unbaked(unbaked.transformation(), unbaked.property(),
          unbaked.onTrue(), fallback);
    }
    if (parsed instanceof SelectItemModel.Unbaked unbaked) {
      return new SelectItemModel.Unbaked(unbaked.transformation(), unbaked.unbakedSwitch(),
          Optional.of(fallback));
    }
    if (parsed instanceof RangeSelectItemModel.Unbaked unbaked) {
      return new RangeSelectItemModel.Unbaked(unbaked.transformation(), unbaked.property(),
          unbaked.scale(), unbaked.entries(), Optional.of(fallback));
    }

    QikaLib.LOGGER.error("Item model override {} produced unsupported root {}", source, parsed.getClass());
    return null;
  }

  private static DynamicOps<JsonElement> ops() {
    return ClientRegistryLayer.createRegistryAccess().compositeAccess()
        .createSerializationContext(JsonOps.INSTANCE);
  }

  private static boolean isNestedPlaceholder(JsonElement element) {
    if (element == null || !element.isJsonObject()) {
      return false;
    }
    JsonObject object = element.getAsJsonObject();
    return object.size() == 1
        && object.has("type")
        && NESTED_TYPE.equals(object.get("type").getAsString());
  }

  private static boolean containsNestedPlaceholder(JsonElement element) {
    if (element.isJsonObject()) {
      if (isNestedPlaceholder(element)) {
        return true;
      }
      for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
        if (containsNestedPlaceholder(entry.getValue())) {
          return true;
        }
      }
    } else if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        if (containsNestedPlaceholder(child)) {
          return true;
        }
      }
    }
    return false;
  }
}

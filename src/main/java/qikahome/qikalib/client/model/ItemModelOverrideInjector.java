package qikahome.qikalib.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.fml.ModLoader;
import qikahome.qikalib.QikaLib;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 模型 override 注入器（客户端）。
 * <p>
 * 通过资源包 JSON（{@code assets/<ns>/model_overrides/}）给已有模型追加 override，
 * 与原模型自身的 overrides 合并，目标模型的加载 ID 由 JSON 的 "model" 字段指定
 * （如 {@code tconstruct:item/tools/pickaxe}）。
 * <p>
 * 把加载放在客户端资源重载的<b>异步 prepare 阶段</b>（{@code thenCompose(stage::wait)}），
 * 确保在 {@code ModelBakery} 烘焙模型之前数据就绪 —— 只有 apply 阶段的
 * {@code ResourceManagerReloadListener} 来不及。实际合并由 {@code MixinModelBakery} 完成。
 */
@OnlyIn(Dist.CLIENT)
public enum ItemModelOverrideInjector implements PreparableReloadListener {
  INSTANCE;

  /** 资源包文件夹：assets/&lt;ns&gt;/model_overrides/ */
  public static final String FOLDER = "model_overrides";

  /** 目标模型 ID → 注入的 overrides（加载时追加到模型自身 overrides 之后） */
  private volatile Map<ResourceLocation, List<ItemOverride>> injections = Collections.emptyMap();

  /** 注册客户端重载监听器 */
  public static void init(RegisterClientReloadListenersEvent event) {
    event.registerReloadListener(INSTANCE);
  }

  @Override
  public CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager,
      ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
      Executor backgroundExecutor, Executor gameExecutor) {
    // ModLoader.isLoadingStateValid() 守卫：避免早期失败产生误导性的崩溃报告
    return CompletableFuture.runAsync(() -> {
      if (ModLoader.isLoadingStateValid()) {
        onReloadSafe(resourceManager);
      }
    }, backgroundExecutor).thenCompose(stage::wait);
  }

  private void onReloadSafe(ResourceManager manager) {
    long time = System.nanoTime();
    Map<ResourceLocation, List<ItemOverride>> result = new ConcurrentHashMap<>();
    int loaded = 0;
    for (Entry<ResourceLocation, Resource> entry : manager.listResources(FOLDER, loc -> loc.getPath().endsWith(".json")).entrySet()) {
      try (Reader reader = entry.getValue().openAsReader()) {
        JsonObject json = GsonHelper.parse(reader);
        // 多个文件可注入同一模型，按模型合并
        ModelOverrideInjection injection = ModelOverrideInjection.fromJson(json);
        result.computeIfAbsent(injection.model(), m -> new ArrayList<>()).addAll(injection.overrides());
        loaded++;
      } catch (IllegalArgumentException | IOException | JsonParseException ex) {
        QikaLib.LOGGER.error("Couldn't parse model override injection from {}", entry.getKey(), ex);
      }
    }
    injections = result;
    QikaLib.LOGGER.info("Loaded {} model override injectors targeting {} models in {} ms",
        loaded, injections.size(), (System.nanoTime() - time) / 1000000f);
  }

  /**
   * 取出并移除目标模型的注入 overrides；无注入时返回 null。
   * <p>
   * 移除保证同一模型在每次资源重载内至多应用一次（配合 {@code ModelBakery} 的缓存守卫，
   * 即使某个子类绕过缓存直接调用 {@code loadBlockModel} 也不会重复追加）。
   * 每次资源重载会重建 map，跨重载不受影响。
   */
  public static List<ItemOverride> takeInjections(ResourceLocation model) {
    return INSTANCE.injections.remove(model);
  }
}

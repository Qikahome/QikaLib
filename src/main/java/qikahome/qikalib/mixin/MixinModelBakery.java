package qikahome.qikalib.mixin;

import java.util.List;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import qikahome.qikalib.client.model.ItemModelOverrideInjector;

/**
 * 在模型加载入口合并注入的 overrides。
 * <p>
 * 1.21.1 的物品/方块模型仍是 {@link BlockModel}，统一经
 * {@link ModelBakery#loadBlockModel(ResourceLocation)} 加载，参数即模型 ID
 * （如 {@code minecraft:item/foo}，非文件路径），恰好与注入 JSON 的 "model" 字段对应。
 * 把注入的 overrides 追加到原 overrides 之后（原 overrides 优先匹配）。
 * <p>
 * overrides 字段虽是 final，但反序列化产出的是可变 ArrayList，且
 * {@link BlockModel#getOverrides()} 返回同一引用，烘焙时也读它——原地 addAll 即可。
 */
@Mixin(ModelBakery.class)
public class MixinModelBakery {
  @Inject(method = "loadBlockModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/block/model/BlockModel;",
          at = @At("RETURN"), cancellable = true)
  private void qikalib$injectModelOverrides(ResourceLocation modelId, CallbackInfoReturnable<BlockModel> cir) {
    // 取出即移除：同一模型在每次资源重载内至多应用一次
    List<ItemOverride> injected = ItemModelOverrideInjector.takeInjections(modelId);
    if (injected == null || injected.isEmpty()) {
      return;
    }
    BlockModel model = cir.getReturnValue();
    if (model == null) {
      return;
    }
    // 原地追加：overrides 是可变 ArrayList，getOverrides() 返回同一引用，
    // 追加在原 overrides 之后（原 overrides 优先匹配）。
    model.getOverrides().addAll(injected);
  }
}

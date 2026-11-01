package qikahome.qikalib.mixin;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qikahome.qikalib.client.model.ItemModelOverrideInjector;

import java.util.List;

/**
 * 在模型加载入口合并注入的 overrides。
 * <p>
 * 1.20.1 的物品/方块模型都是 {@link BlockModel}，统一经
 * {@link ModelBakery#loadBlockModel(ResourceLocation)} 加载，参数即模型 ID
 * （如 {@code minecraft:item/foo}，非文件路径），恰好与注入 JSON 的 "model" 字段对应。
 * 把注入的 overrides 追加到原 overrides 之后
 * （{@link net.minecraft.client.renderer.block.model.ItemOverrides} 顺序匹配，原 overrides 优先）。
 * <p>
 * overrides 字段虽是 final，但反序列化产出的是可变 ArrayList
 * （{@code Lists.newArrayList()} + add，见 BlockModel$Deserializer.getOverrides），
 * {@link BlockModel#getOverrides()} 返回同一引用且烘焙时也读它——原地 addAll 即可，
 * 无需重建 BlockModel（避免构造函数传参不全/重建丢字段的风险）。
 * <p>
 * 自定义 loader 模型（如 TCon 工具的 {@code tconstruct:tool}）同样注入：TCon 工具模型
 * 本身就同时使用 loader 与 vanilla overrides（blocking/broken），loader 烘焙路径会处理它们。
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
    // 对所有模型一视同仁（含 builtin 标记、自定义 loader），是否生效由用户自行判断；
    // 注意 builtin 标记是共享单例，注入仅追加到其列表、不会崩，但正常烘焙不会读取。
    model.getOverrides().addAll(injected);
  }
}

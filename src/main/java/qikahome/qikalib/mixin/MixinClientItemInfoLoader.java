package qikahome.qikalib.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.server.packs.resources.ResourceManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import qikahome.qikalib.client.model.ItemModelOverrideInjector;

/**
 * 在 26.x 物品模型解析完成、依赖发现之前改写 {@code ClientItem.model}。
 *
 * <p>{@link ClientItemInfoLoader#scheduleLoad} 的返回值随后会交给
 * {@code ModelDiscovery}/{@code ModelBakery}，因此在这里拼接后的树仍会被正常解析依赖与烘焙。
 */
@Mixin(ClientItemInfoLoader.class)
public class MixinClientItemInfoLoader {
    @Inject(method = "scheduleLoad(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN"), cancellable = true)
    private static void qikalib$applyItemModelOverrides(ResourceManager manager, Executor executor,
            CallbackInfoReturnable<CompletableFuture<ClientItemInfoLoader.LoadedClientInfos>> cir) {
        CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(original.thenApply(infos -> ItemModelOverrideInjector.apply(manager, infos)));
        }
    }
}

package qikahome.qikalib.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * 客户端专用的 {@link SlotDisplayContext} 上下文构造器。
 *
 * <p>本类引用了 {@code net.minecraft.client.Minecraft}，只能由客户端分支调用；
 * 由 {@code Utils#displayContext()} 在确认当前 dist 为 client 后才访问，
 * 专用服务端不会加载本类。
 *
 * <p>这里刻意不加 {@code @OnlyIn(Dist.CLIENT)}：26.x 已移除运行期的成员裁剪，
 * 该注解只剩一条 ERROR 级警告；隔离改由"仅在客户端的 dist 分支里引用"来保证。
 */
public final class ClientDisplayContext {
    private ClientDisplayContext() {
    }

    /**
     * 若客户端 level 已就绪，返回带其 registryAccess 的上下文；否则返回 {@code null}，
     * 由调用方退回 BuiltInRegistries 兜底。
     */
    @Nullable
    public static ContextMap create() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        return new ContextMap.Builder()
                .withParameter(SlotDisplayContext.REGISTRIES, minecraft.level.registryAccess())
                .create(SlotDisplayContext.CONTEXT);
    }
}

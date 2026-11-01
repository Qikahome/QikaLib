package qikahome.qikalib;

import com.mojang.serialization.Codec;

import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import qikahome.qikalib.client.ClientDisplayContext;

public final class Utils {
    private Utils() {
    }

    /**
     * 构造用于解析 SlotDisplay 的 ContextMap（包含 REGISTRIES）。
     * TagSlotDisplay 需要 registry 上下文才能解析 tag，传空 ContextMap 会导致 tag 类 ingredient
     * 在配方预览中显示为空。服务器/客户端 level 都不可用（如启动早期）时，退回静态的
     * BuiltInRegistries 兜底。
     */
    public static ContextMap displayContext() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return new ContextMap.Builder()
                    .withParameter(SlotDisplayContext.REGISTRIES, server.registryAccess())
                    .create(SlotDisplayContext.CONTEXT);
        }
        // 专用服务端在 MinecraftServer 构造期间就会首次加载配方，此时 server 尚未发布，
        // 且没有客户端类；必须先用 dist 守卫，避免加载 net.minecraft.client.Minecraft。
        if (FMLEnvironment.getDist().isClient()) {
            var clientContext = ClientDisplayContext.create();
            if (clientContext != null) {
                return clientContext;
            }
        }
        return new ContextMap.Builder()
                .withParameter(SlotDisplayContext.REGISTRIES,
                        net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                                net.minecraft.core.registries.BuiltInRegistries.REGISTRY))
                .create(SlotDisplayContext.CONTEXT);
    }

    /**
     * 创建枚举的 Codec，区分大小写。
     */
    public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> enumClass) {
        return Codec.STRING.xmap(
                str -> Enum.valueOf(enumClass, str),
                Enum::name);
    }

    /**
     * 创建枚举的 Codec，不区分大小写。
     */
    public static <E extends Enum<E>> Codec<E> enumCodecIgnoreCase(Class<E> enumClass) {
        return Codec.STRING.xmap(
                str -> {
                    for (var c : enumClass.getEnumConstants()) {
                        if (c.name().equalsIgnoreCase(str))
                            return c;
                    }
                    throw new IllegalArgumentException("No enum constant " + enumClass.getName() + "." + str);
                },
                e -> e.name().toLowerCase(java.util.Locale.ROOT));
    }
}

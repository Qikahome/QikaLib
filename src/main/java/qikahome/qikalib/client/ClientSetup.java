package qikahome.qikalib.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import qikahome.qikalib.QikaLib;
import qikahome.qikalib.client.model.ItemModelOverrideInjector;

/** 客户端入口：只在客户端注册模型 override 注入器的重载监听器。 */
@EventBusSubscriber(modid = QikaLib.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        ItemModelOverrideInjector.init(event);
    }
}

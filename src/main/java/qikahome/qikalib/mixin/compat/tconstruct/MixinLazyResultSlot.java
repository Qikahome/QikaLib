package qikahome.qikalib.mixin.compat.tconstruct;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.common.ForgeHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import qikahome.qikalib.QikaLib;
import slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer;
import slimeknights.tconstruct.tables.menu.slot.LazyResultSlot;

/**
 * TConstruct 的工作台结果槽：{@code craftResult} 内部异常会让整个服务端卡死/刷屏，
 * 这里兜底成"清空结果 + 提示玩家"。
 */
@Mixin(LazyResultSlot.class)
public abstract class MixinLazyResultSlot {
    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lslimeknights/tconstruct/tables/block/entity/inventory/LazyResultContainer;craftResult(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void qikalib$craftResult(LazyResultContainer container, Player player, ItemStack resultStack,
            int amountCrafted) {
        try {
            container.craftResult(player, resultStack, amountCrafted);
        } catch (Exception e) {
            QikaLib.LOGGER.error(
                    (player != null ? "Player " + player.getName().getString() + " " : "") + "Failed to craft result",
                    e);
            if (player != null && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.getServer().sendSystemMessage(Component.literal(
                        String.format("Player %s failed to craft result at dimension %s, X:%s, Y:%s, Z:%s",
                                player.getName().getString(),
                                player.level().dimension().location(),
                                player.getBlockX(),
                                player.getBlockY(),
                                player.getBlockZ())));
                serverPlayer
                        .displayClientMessage(Component.translatableWithFallback("error.qikalib.craft_result_failed",
                                "Failed to craft result with an internal exception"), false);
            }
            ForgeHooks.setCraftingPlayer(null);
            resultStack.setCount(0);
            container.clearContent();
        }
    }
}

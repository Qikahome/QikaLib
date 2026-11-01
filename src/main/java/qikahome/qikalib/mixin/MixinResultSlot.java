package qikahome.qikalib.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qikahome.qikalib.util.CraftingPlayer;

/**
 * 原版 {@code RecipeManager#getRemainingItemsFor} 只在 {@code ResultSlot#onTake} 里被调用，而该方法能拿到玩家。
 * 这里在其前后维护 {@link CraftingPlayer#get()}，等价上游 NeoForge 对 {@code CommonHooks#getCraftingPlayer()} 的注入。
 */
@Mixin(ResultSlot.class)
public class MixinResultSlot {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void qikalib$pushCraftingPlayer(Player player, ItemStack stack, CallbackInfo info) {
        CraftingPlayer.set(player);
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void qikalib$popCraftingPlayer(Player player, ItemStack stack, CallbackInfo info) {
        CraftingPlayer.set(null);
    }
}

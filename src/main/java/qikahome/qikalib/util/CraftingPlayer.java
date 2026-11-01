package qikahome.qikalib.util;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;

/**
 * 合成台取结果的那一刻（{@code ResultSlot#onTake}）所处的玩家，由
 * {@code qikahome.qikalib.mixin.MixinResultSlot} 维护。
 *
 * <p>原版 {@code RecipeManager#getRemainingItemsFor} 只在 {@code ResultSlot#onTake} 里被调用，
 * 而配方自身的剩余物结算拿不到玩家；这里用 {@link ThreadLocal} 把玩家带进那次调用。
 */
public final class CraftingPlayer {
    private static final ThreadLocal<Player> CRAFTING_PLAYER = new ThreadLocal<>();

    private CraftingPlayer() {
    }

    public static void set(@Nullable Player player) {
        CRAFTING_PLAYER.set(player);
    }

    @Nullable
    public static Player get() {
        return CRAFTING_PLAYER.get();
    }
}

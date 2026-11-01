package qikahome.qikalib.util;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;

/**
 * 合成台取结果的那一刻（{@code ResultSlot#onTake}）所处的玩家，由 {@code MixinResultSlot} 维护，
 * 等价上游 Neo 的 {@code CommonHooks#getCraftingPlayer()}。
 */
public class CraftingPlayer {
    private static final ThreadLocal<Player> CRAFTING_PLAYER = new ThreadLocal<>();

    public static void set(@Nullable Player player) {
        CRAFTING_PLAYER.set(player);
    }

    @Nullable
    public static Player get() {
        return CRAFTING_PLAYER.get();
    }
}

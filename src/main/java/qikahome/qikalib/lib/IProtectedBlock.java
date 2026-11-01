package qikahome.qikalib.lib;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 受保护的方块：在 {@link Level#setBlock} 真正写入前可以先拒绝这次修改。
 *
 * <p>由 {@code MixinLevel} 在 {@code setBlock} 的 HEAD 处统一拦截，实现方不需要自己 mixin。
 */
public interface IProtectedBlock {
    /**
     * 是否可以设置块状态。
     *
     * @param oldState    旧块状态
     * @param level       世界
     * @param pos         块位置
     * @param newState    新块状态
     * @param updateFlags 更新标志
     * @param updateLimit 更新限制
     * @return 返回 {@code false} 时本次 {@code setBlock} 直接失败
     */
    boolean maySetBlock(BlockState oldState, Level level, BlockPos pos, BlockState newState,
            @Block.UpdateFlags int updateFlags, int updateLimit);
}

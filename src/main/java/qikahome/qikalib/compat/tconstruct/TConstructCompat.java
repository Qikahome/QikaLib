package qikahome.qikalib.compat.tconstruct;

import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * TConstruct 联动：取工具的真实耐久上限。
 * TCon 工具的实际耐久记在 ToolStats 里，{@link ItemStack#getMaxDamage()} 拿不到。
 *
 * <p>调用前必须已用 {@code ModList.get().isLoaded("tconstruct")} 守卫 ——
 * TConstruct 缺席时本类不可解析。
 */
public final class TConstructCompat {
    private TConstructCompat() {}

    public static int getRealMaxDamage(ItemStack stack, int origin) {
        if (stack.getItem() instanceof IModifiable)
            return ToolStack.from(stack).getStats().getInt(ToolStats.DURABILITY);
        return origin;
    }
}

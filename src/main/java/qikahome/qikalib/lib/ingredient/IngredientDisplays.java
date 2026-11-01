package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * 26.x 的原料展示统一走 {@link SlotDisplay}。这里把 {@link ItemStack}（含组件）包成展示项，
 * 供需要改名/加 lore/改数量等"只能体现在栈上"的原料使用。
 */
public final class IngredientDisplays {
    private IngredientDisplays() {
    }

    public static SlotDisplay of(ItemStack stack) {
        if (stack.isEmpty())
            return SlotDisplay.Empty.INSTANCE;
        return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
    }

    public static SlotDisplay of(List<ItemStack> stacks) {
        if (stacks.isEmpty())
            return SlotDisplay.Empty.INSTANCE;
        if (stacks.size() == 1)
            return of(stacks.get(0));
        List<SlotDisplay> displays = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks)
            displays.add(of(stack));
        return new SlotDisplay.Composite(displays);
    }

    /** 内层原料的全部代表栈（不含组件以外的展示修饰）。 */
    public static List<ItemStack> stacks(Ingredient ingredient) {
        return ingredient.items().map(holder -> new ItemStack(holder.value())).toList();
    }
}

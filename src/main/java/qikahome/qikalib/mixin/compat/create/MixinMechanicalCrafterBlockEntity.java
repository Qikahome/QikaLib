package qikahome.qikalib.mixin.compat.create;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInventory;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import qikahome.qikalib.lib.recipe.IHandlingRecipe;

/**
 * Create 的机械手：原版只收集 {@code getRemainingItems} 里的剩余物，
 * 这里让实现 {@link IHandlingRecipe} 的配方按自身钩子重新结算容器。
 */
@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class MixinMechanicalCrafterBlockEntity {

    @Shadow
    private RecipeGridHandler.GroupedItems groupedItems;

    @ModifyVariable(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER), ordinal = 0, remap = false)
    private List<ItemStack> qikalib$replaceContainers(List<ItemStack> originalContainers) {
        // 完全忽略原版 forEach 收集到的物品（即丢弃所有原版容器物品）
        CraftingContainer craftinginventory = new MechanicalCraftingInventory(groupedItems);
        Level world = ((BlockEntity) (Object) this).getLevel();
        var recipe = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftinginventory, world).orElse(null);
        if (recipe == null || !(recipe instanceof IHandlingRecipe))
            return originalContainers;
        List<ItemStack> myContainers = new ArrayList<>();
        recipe.getRemainingItems(craftinginventory).forEach(item -> {
            if (item.isEmpty())
                return;
            myContainers.add(item);
        });
        return myContainers; // 返回的新列表将替代原 containers 变量
    }
}

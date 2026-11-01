package qikahome.qikalib.lib.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import qikahome.qikalib.QikaLib;

/**
 * {@link ItemApplicationRecipe} 的 loader 侧接线：配方同步、客户端缓存与右键事件。
 * 配方语义本身保留在 recipe 类中。
 */
@EventBusSubscriber(modid = QikaLib.MODID)
public final class ItemApplicationEvents {
    /** 客户端经 NeoForge 官方配方同步（sendRecipes）收到的本类型配方缓存，仅逻辑客户端使用。 */
    private static volatile List<ItemApplicationRecipe> clientRecipes = List.of();

    private ItemApplicationEvents() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(ItemApplicationRecipe.TYPE);
    }

    /** 客户端收到平台同步的配方后整体替换缓存（覆盖登录与数据包重载两种时机）。 */
    @SubscribeEvent
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        if (!event.getRecipeTypes().contains(ItemApplicationRecipe.TYPE))
            return;
        List<ItemApplicationRecipe> list = new ArrayList<>();
        for (var holder : event.getRecipeMap().byType(ItemApplicationRecipe.TYPE)) {
            list.add(holder.value());
        }
        clientRecipes = List.copyOf(list);
    }

    /** 离开服务器时清空缓存，防止串服残留旧配方导致误拦。 */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clientRecipes = List.of();
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        ItemStack heldItem = event.getItemStack();
        BlockPos pos = event.getPos();
        BlockState blockState = level.getBlockState(pos);

        if (heldItem.isEmpty())
            return;
        if (blockState.isAir())
            return;
        if (event.isCanceled())
            return;

        if (!heldItem.is(ItemApplicationRecipe.TOOL_TAG))
            return;

        Player player = event.getEntity();

        // 客户端：用平台配方同步收到的全量配方做精确匹配（含 sneaking / force_input），命中才拦截。
        // 缓存为空（尚未同步/串服已清空）时不拦截，由服务端兜底。
        if (level.isClientSide()) {
            for (var recipe : clientRecipes) {
                Boolean sneaking = recipe.getSneaking();
                if (sneaking != null && player.isShiftKeyDown() != sneaking)
                    continue;
                if (!recipe.test(blockState, heldItem))
                    continue;
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            return;
        }

        MinecraftServer server = level.getServer();
        RecipeType<ItemApplicationRecipe> type = ItemApplicationRecipe.TYPE;
        for (var holder : server.getRecipeManager().recipeMap().byType(type)) {
            var recipe = holder.value();
            Boolean sneaking = recipe.getSneaking();
            if (sneaking != null && player.isShiftKeyDown() != sneaking)
                continue;

            if (!recipe.test(blockState, heldItem))
                continue;

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);

            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.COPPER_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1, 1.45f);

            recipe.apply((net.minecraft.server.level.ServerLevel) level, pos, player, event.getHand());
            return;
        }
    }
}

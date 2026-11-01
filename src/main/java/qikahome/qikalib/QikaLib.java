package qikahome.qikalib;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.slf4j.Logger;
import qikahome.qikalib.lib.ingredient.ConditionIngredient;
import qikahome.qikalib.lib.ingredient.CountedIngredient;
import qikahome.qikalib.lib.ingredient.ItemDisplayOverrideIngredient;
import qikahome.qikalib.lib.ingredient.KeepInventoryContainerIngredient;
import qikahome.qikalib.lib.ingredient.NBTCopyIngredient;
import qikahome.qikalib.lib.ingredient.NotIngredient;
import qikahome.qikalib.lib.ingredient.RegexIngredient;
import qikahome.qikalib.lib.ingredient.RemainderOverrideIngredient;
import qikahome.qikalib.lib.ingredient.SameItemIngredient;
import qikahome.qikalib.lib.ingredient.SimpleIngredientSerializer;
import qikahome.qikalib.lib.ingredient.ToolDamagingIngredient;
import qikahome.qikalib.lib.ingredient.TrueIngredient;
import qikahome.qikalib.lib.recipe.ItemApplicationRecipe;
import qikahome.qikalib.lib.recipe.ShapedHandlingRecipe;
import qikahome.qikalib.lib.recipe.ShapelessHandlingRecipe;
import qikahome.qikalib.lib.condition.GameRuleCondition;

/**
 * 库入口。
 *
 * <p>命名空间有两个：
 * <ul>
 *   <li>{@link #MODID} —— 本库自身的 id（{@code qikalib:*}）；</li>
 *   <li>{@link #LEGACY_MODID} —— JsonMore 时代的数据包 id（{@code jsonmore:*}）。
 *       旧 type 由本库再注册一次，每条用独立的 serializer 实例。</li>
 * </ul>
 */
public class QikaLib implements ModInitializer {
    public static final String MODID = "qikalib";
    public static final String LEGACY_MODID = "jsonmore";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile MinecraftServer currentServer;

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Start QikaLib common setup.");

        // ---- 原料 ----
        registerIngredient("true", TrueIngredient.CODEC);
        registerIngredient("not", NotIngredient.CODEC);
        registerIngredient("regex", RegexIngredient.CODEC);
        registerIngredient("keep_inventory_container", KeepInventoryContainerIngredient.CODEC);
        registerIngredient("tool_damaging", ToolDamagingIngredient.CODEC);
        registerIngredient("remainder_override", RemainderOverrideIngredient.CODEC);
        registerIngredient("nbt_copy", NBTCopyIngredient.CODEC);
        registerIngredient("item_display_override", ItemDisplayOverrideIngredient.CODEC);
        registerIngredient("counted", CountedIngredient.CODEC);
        registerIngredient("condition", ConditionIngredient.CODEC);
        registerIngredient("same_item", SameItemIngredient.CODEC);

        // ---- 配方序列化器 ----
        registerRecipeSerializer("shaped_handling", ShapedHandlingRecipe.SERIALIZER,
                "shaped_consuming", ShapedHandlingRecipe.LEGACY_SERIALIZER);
        registerRecipeSerializer("shapeless_handling", ShapelessHandlingRecipe.SERIALIZER,
                "shapeless_consuming", ShapelessHandlingRecipe.LEGACY_SERIALIZER);
        registerRecipeSerializer("item_application", ItemApplicationRecipe.Serializer.INSTANCE,
                "item_application", ItemApplicationRecipe.Serializer.LEGACY_INSTANCE);

        // ---- 配方同步 ----
        // 26.x 原版不再向客户端同步完整配方表；显式声明后由 fabric-recipe-api 同步到客户端。
        RecipeSynchronization.synchronizeRecipeSerializer(ItemApplicationRecipe.Serializer.INSTANCE);
        RecipeSynchronization.synchronizeRecipeSerializer(ItemApplicationRecipe.Serializer.LEGACY_INSTANCE);

        // ---- 条件 ----
        GameRuleCondition.register();

        // ---- 配方类型 + 右键交互 ----
        ItemApplicationRecipe.register();
        UseBlockCallback.EVENT.register(ItemApplicationRecipe::onRightClickBlock);

        // ---- server 缓存（GameRuleCondition 用；无 server 时 false） ----
        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentServer = null);
    }

    /**
     * 同一个原料 type 在 {@code qikalib:} 与 {@code jsonmore:} 两个命名空间各注册一次。
     * 每条使用独立的 serializer 实例（Serializer 无状态，行为一致）。
     */
    private static <T extends CustomIngredient> void registerIngredient(String name, MapCodec<T> codec) {
        CustomIngredientSerializer.register(new SimpleIngredientSerializer<>(
                Identifier.fromNamespaceAndPath(MODID, name), codec));
        CustomIngredientSerializer.register(new SimpleIngredientSerializer<>(
                Identifier.fromNamespaceAndPath(LEGACY_MODID, name), codec));
    }

    private static void registerRecipeSerializer(String name, RecipeSerializer<?> serializer,
            String legacyName, RecipeSerializer<?> legacySerializer) {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MODID, name), serializer);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                Identifier.fromNamespaceAndPath(LEGACY_MODID, legacyName), legacySerializer);
    }

    /** 客户端入口：接收平台同步过来的本类型配方。 */
    @Environment(EnvType.CLIENT)
    public static final class ClientModEvents implements ClientModInitializer {
        @Override
        public void onInitializeClient() {
            ClientRecipeSynchronizedEvent.EVENT.register((client, synchronizedRecipes) ->
                    ItemApplicationRecipe.setClientRecipes(
                            synchronizedRecipes.getAllOfType(ItemApplicationRecipe.TYPE)));
        }
    }
}

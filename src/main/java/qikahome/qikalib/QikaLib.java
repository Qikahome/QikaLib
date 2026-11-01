package qikahome.qikalib;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import qikahome.qikalib.lib.ingredient.ConditionIngredient;
import qikahome.qikalib.lib.ingredient.CountedIngredient;
import qikahome.qikalib.lib.ingredient.ItemDisplayOverrideIngredient;
import qikahome.qikalib.lib.ingredient.KeepInventoryContainerIngredient;
import qikahome.qikalib.lib.ingredient.NBTCopyIngredient;
import qikahome.qikalib.lib.ingredient.NotIngredient;
import qikahome.qikalib.lib.ingredient.RegexIngredient;
import qikahome.qikalib.lib.ingredient.RemainderOverrideIngredient;
import qikahome.qikalib.lib.ingredient.SameItemIngredient;
import qikahome.qikalib.lib.ingredient.ToolDamagingIngredient;
import qikahome.qikalib.lib.ingredient.TrueIngredient;
import qikahome.qikalib.lib.recipe.ItemApplicationRecipe;
import qikahome.qikalib.lib.recipe.ShapedHandlingRecipe;
import qikahome.qikalib.lib.recipe.ShapelessHandlingRecipe;
import qikahome.qikalib.lib.condition.GameRuleCondition;
import qikahome.qikalib.client.model.ItemModelOverrideInjector;

/**
 * 库入口。
 *
 * <p>命名空间有两个：
 * <ul>
 *   <li>{@link #MODID} —— 本库自身的 id（{@code qikalib:*}）；</li>
 *   <li>{@link #LEGACY_MODID} —— JsonMore 时代的数据包 id（{@code jsonmore:*}）。
 *       旧 type 由本库再注册一次；Fabric 的序列化器以 identifier 为键，
 *       所以每条旧 id 都用独立实例，不能复用同一个对象。</li>
 * </ul>
 */
public class QikaLib implements ModInitializer {
    public static final String MODID = "qikalib";
    public static final String LEGACY_MODID = "jsonmore";

    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("Start QikaLib common setup.");

        // ---- 配方序列化器 ----
        registerRecipeSerializer("shapeless_handling", ShapelessHandlingRecipe.Serializer.INSTANCE);
        registerRecipeSerializer("shaped_handling", ShapedHandlingRecipe.Serializer.INSTANCE);
        registerRecipeSerializer("item_application", ItemApplicationRecipe.Serializer.INSTANCE);
        // 旧 id：jsonmore:shapeless_consuming / jsonmore:shaped_consuming / jsonmore:item_application
        registerRecipeSerializer(LEGACY_MODID, "shapeless_consuming", new ShapelessHandlingRecipe.Serializer());
        registerRecipeSerializer(LEGACY_MODID, "shaped_consuming", new ShapedHandlingRecipe.Serializer());
        registerRecipeSerializer(LEGACY_MODID, "item_application", new ItemApplicationRecipe.Serializer());

        // ---- 原料序列化器（qikalib:* + jsonmore:* 旧 id 别名）----
        registerIngredient(TrueIngredient.Serializer.INSTANCE, TrueIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(NotIngredient.Serializer.INSTANCE, NotIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(RegexIngredient.Serializer.INSTANCE, RegexIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(KeepInventoryContainerIngredient.Serializer.INSTANCE,
                KeepInventoryContainerIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(ToolDamagingIngredient.Serializer.INSTANCE,
                ToolDamagingIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(RemainderOverrideIngredient.Serializer.INSTANCE,
                RemainderOverrideIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(NBTCopyIngredient.Serializer.INSTANCE, NBTCopyIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(ItemDisplayOverrideIngredient.Serializer.INSTANCE,
                ItemDisplayOverrideIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(CountedIngredient.Serializer.INSTANCE, CountedIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(ConditionIngredient.Serializer.INSTANCE,
                ConditionIngredient.Serializer.LEGACY_INSTANCE);
        registerIngredient(SameItemIngredient.Serializer.INSTANCE, SameItemIngredient.Serializer.LEGACY_INSTANCE);

        // ---- 配方类型 / 条件 ----
        // 配方类型注册必须早于注册表冻结（静态初始化时机太晚）
        ItemApplicationRecipe.register();
        GameRuleCondition.register();

        // ---- 平台事件 ----
        UseBlockCallback.EVENT.register(ItemApplicationRecipe::onRightClickBlock);
        ServerLifecycleEvents.SERVER_STARTED.register(GameRuleCondition::setCurrentServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GameRuleCondition.setCurrentServer(null));
    }

    private static void registerRecipeSerializer(String path, RecipeSerializer<?> serializer) {
        registerRecipeSerializer(MODID, path, serializer);
    }

    private static void registerRecipeSerializer(String namespace, String path, RecipeSerializer<?> serializer) {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, new ResourceLocation(namespace, path), serializer);
    }

    private static void registerIngredient(CustomIngredientSerializer<?> canonical,
            CustomIngredientSerializer<?> legacy) {
        CustomIngredientSerializer.register(canonical);
        CustomIngredientSerializer.register(legacy);
    }

    /** 客户端入口：只注册模型 override 注入器。 */
    @Environment(EnvType.CLIENT)
    public static final class ClientModEvents implements ClientModInitializer {
        @Override
        public void onInitializeClient() {
            ItemModelOverrideInjector.init();
        }
    }
}

package qikahome.qikalib;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import qikahome.qikalib.client.model.ItemModelOverrideInjector;

/**
 * 库入口。
 *
 * <p>命名空间有两个：
 * <ul>
 *   <li>{@link #MODID} —— 本库自身的 id（{@code qikalib:*}）；</li>
 *   <li>{@link #LEGACY_MODID} —— JsonMore 时代的数据包 id（{@code jsonmore:*}）。
 *       旧 type 由本库再注册一次（独立 serializer 实例），因此 <b>JsonMore 必须删掉它自己那份注册</b>，
 *       否则同一个 id 会被注册两次。</li>
 * </ul>
 */
public class QikaLib implements ModInitializer {
    public static final String MODID = "qikalib";
    public static final String LEGACY_MODID = "jsonmore";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** Fabric 没有 {@code ServerLifecycleHooks}，当前服务器由 {@link ServerLifecycleEvents} 维护。 */
    @Nullable
    private static MinecraftServer currentServer;

    public static void setCurrentServer(@Nullable MinecraftServer server) {
        currentServer = server;
    }

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    static {
        // 注册必须早于内置注册表冻结。
        ItemApplicationRecipe.register();
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Start QikaLib common setup.");

        registerRecipeSerializers();
        registerIngredients();
        GameRuleCondition.register();

        UseBlockCallback.EVENT.register(ItemApplicationRecipe::onRightClickBlock);
        ServerLifecycleEvents.SERVER_STARTED.register(QikaLib::setCurrentServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> setCurrentServer(null));
    }

    private static void registerRecipeSerializers() {
        registerRecipeSerializer("shapeless_handling", ShapelessHandlingRecipe.Serializer.INSTANCE);
        registerRecipeSerializer("shaped_handling", ShapedHandlingRecipe.Serializer.INSTANCE);
        registerRecipeSerializer("item_application", ItemApplicationRecipe.Serializer.INSTANCE);
        // 旧 id：jsonmore:shapeless_consuming / jsonmore:shaped_consuming / jsonmore:item_application
        // 每条用独立的 serializer 实例；serializer 无状态，两个实例行为完全一致。
        registerRecipeSerializer(LEGACY_MODID, "shapeless_consuming", new ShapelessHandlingRecipe.Serializer());
        registerRecipeSerializer(LEGACY_MODID, "shaped_consuming", new ShapedHandlingRecipe.Serializer());
        registerRecipeSerializer(LEGACY_MODID, "item_application", new ItemApplicationRecipe.Serializer());
    }

    private static void registerRecipeSerializer(String name, RecipeSerializer<?> serializer) {
        registerRecipeSerializer(MODID, name, serializer);
    }

    private static void registerRecipeSerializer(String namespace, String name, RecipeSerializer<?> serializer) {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id(namespace, name), serializer);
    }

    private static void registerIngredients() {
        registerIngredient("true", TrueIngredient.CODEC);
        registerIngredient("not", NotIngredient.CODEC);
        registerIngredient("regex", RegexIngredient.CODEC);
        registerIngredient("keep_inventory_container", KeepInventoryContainerIngredient.CODEC);
        registerIngredient("tool_damaging", ToolDamagingIngredient.CODEC);
        registerIngredient("remainder_override", RemainderOverrideIngredient.CODEC);
        registerIngredient("nbt_copy", NBTCopyIngredient.CODEC);
        registerIngredient("counted", CountedIngredient.CODEC);
        registerIngredient("condition", ConditionIngredient.CODEC);
        registerIngredient("same_item", SameItemIngredient.CODEC);

        // item_display_override 有自定义网络 codec（只传展示列表），旧 id 需要独立 serializer 实例。
        registerIngredient("item_display_override", ItemDisplayOverrideIngredient.CODEC,
                ItemDisplayOverrideIngredient.NETWORK_STREAM_CODEC);
    }

    private static <T extends CustomIngredient> void registerIngredient(String name, MapCodec<T> codec) {
        CustomIngredientSerializer.register(new SimpleIngredientSerializer<>(id(MODID, name), codec));
        CustomIngredientSerializer.register(new SimpleIngredientSerializer<>(id(LEGACY_MODID, name), codec));
    }

    private static <T extends CustomIngredient> void registerIngredient(String name, MapCodec<T> codec,
            net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec) {
        CustomIngredientSerializer.register(
                new SimpleIngredientSerializer<>(id(MODID, name), codec, streamCodec));
        CustomIngredientSerializer.register(
                new SimpleIngredientSerializer<>(id(LEGACY_MODID, name), codec, streamCodec));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
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

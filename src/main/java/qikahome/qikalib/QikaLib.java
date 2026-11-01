package qikahome.qikalib;

import com.mojang.logging.LogUtils;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
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
 *       旧 type 由本库再注册一次、指向同一个 serializer 实例，因此 <b>JsonMore 必须删掉它自己那份注册</b>，
 *       否则同一个 id 会被注册两次。</li>
 * </ul>
 */
@Mod(QikaLib.MODID)
public class QikaLib {
    public static final String MODID = "qikalib";
    public static final String LEGACY_MODID = "jsonmore";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    private static final DeferredRegister<RecipeSerializer<?>> LEGACY_RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, LEGACY_MODID);

    public static final RegistryObject<RecipeSerializer<ShapelessHandlingRecipe>> SHAPELESS_HANDLING_RECIPE =
            RECIPE_SERIALIZERS.register("shapeless_handling", () -> ShapelessHandlingRecipe.Serializer.INSTANCE);
    public static final RegistryObject<RecipeSerializer<ShapedHandlingRecipe>> SHAPED_HANDLING_RECIPE =
            RECIPE_SERIALIZERS.register("shaped_handling", () -> ShapedHandlingRecipe.Serializer.INSTANCE);
    public static final RegistryObject<RecipeSerializer<ItemApplicationRecipe>> ITEM_APPLICATION_RECIPE =
            RECIPE_SERIALIZERS.register("item_application", () -> ItemApplicationRecipe.Serializer.INSTANCE);

    static {
        // 旧 id：jsonmore:shapeless_consuming / jsonmore:shaped_consuming / jsonmore:item_application
        // 注意：Forge 的注册表不允许同一个实例挂两个 id，所以旧 id 用独立的 serializer 实例。
        // Serializer 无状态，两个实例的行为完全一致。
        LEGACY_RECIPE_SERIALIZERS.register("shapeless_consuming", ShapelessHandlingRecipe.Serializer::new);
        LEGACY_RECIPE_SERIALIZERS.register("shaped_consuming", ShapedHandlingRecipe.Serializer::new);
        LEGACY_RECIPE_SERIALIZERS.register("item_application", ItemApplicationRecipe.Serializer::new);
    }

    public QikaLib() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        RECIPE_SERIALIZERS.register(modEventBus);
        LEGACY_RECIPE_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerIngredient("true", TrueIngredient.Serializer::new);
            registerIngredient("not", NotIngredient.Serializer::new);
            registerIngredient("regex", RegexIngredient.Serializer::new);
            registerIngredient("keep_inventory_container", KeepInventoryContainerIngredient.Serializer::new);
            registerIngredient("tool_damaging", ToolDamagingIngredient.Serializer::new);
            registerIngredient("remainder_override", RemainderOverrideIngredient.Serializer::new);
            registerIngredient("nbt_copy", NBTCopyIngredient.Serializer::new);
            registerIngredient("item_display_override", ItemDisplayOverrideIngredient.Serializer::new);
            registerIngredient("counted", CountedIngredient.Serializer::new);
            registerIngredient("condition", ConditionIngredient.Serializer::new);
            registerIngredient("same_item", SameItemIngredient.Serializer::new);

            CraftingHelper.register(GameRuleCondition.Serializer.INSTANCE);
            CraftingHelper.register(GameRuleCondition.Serializer.LEGACY_INSTANCE);
        });
    }

    /**
     * 同一个原料 type 在 {@code qikalib:} 与 {@code jsonmore:} 两个命名空间各注册一次。
     *
     * <p>Forge 1.20.1 的 {@code CraftingHelper} 内部是 {@code BiMap} —— key 和 serializer <b>实例</b>
     * 都要求唯一，所以旧 id 必须用独立实例，不能复用同一个 {@code INSTANCE}。
     * Serializer 都是无状态的，两个实例行为完全一致。
     */
    private static void registerIngredient(String name, Supplier<IIngredientSerializer<? extends Ingredient>> factory) {
        CraftingHelper.register(new ResourceLocation(MODID, name), factory.get());
        CraftingHelper.register(new ResourceLocation(LEGACY_MODID, name), factory.get());
    }

    /** 客户端入口：只在客户端注册模型 override 注入器的重载监听器。 */
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientModEvents {
        private ClientModEvents() {}

        @SubscribeEvent
        public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
            ItemModelOverrideInjector.init(event);
        }
    }
}

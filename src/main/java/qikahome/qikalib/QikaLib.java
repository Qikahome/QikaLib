package qikahome.qikalib;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import qikahome.qikalib.lib.condition.GameRuleCondition;
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

/**
 * 库入口。
 *
 * <p>命名空间有两个：
 * <ul>
 *   <li>{@link #MODID} —— 本库自身的 id（{@code qikalib:*}）；</li>
 *   <li>{@link #LEGACY_MODID} —— JsonMore 时代的数据包 id（{@code jsonmore:*}）。
 *       旧 type 由本库再注册一次、使用独立的 serializer / type 实例，因此 <b>JsonMore 必须删掉它自己那份注册</b>，
 *       否则同一个 id 会被注册两次。</li>
 * </ul>
 */
@Mod(QikaLib.MODID)
public class QikaLib {
    public static final String MODID = "qikalib";
    public static final String LEGACY_MODID = "jsonmore";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
            .create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> LEGACY_RECIPE_SERIALIZERS = DeferredRegister
            .create(Registries.RECIPE_SERIALIZER, LEGACY_MODID);

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES = DeferredRegister
            .create(NeoForgeRegistries.INGREDIENT_TYPES, MODID);
    public static final DeferredRegister<IngredientType<?>> LEGACY_INGREDIENT_TYPES = DeferredRegister
            .create(NeoForgeRegistries.INGREDIENT_TYPES, LEGACY_MODID);

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS = DeferredRegister
            .create(NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> LEGACY_CONDITION_CODECS = DeferredRegister
            .create(NeoForgeRegistries.Keys.CONDITION_CODECS, LEGACY_MODID);

    public static final DeferredHolder<RecipeSerializer<?>, ShapelessHandlingRecipe.Serializer> SHAPELESS_HANDLING_RECIPE = RECIPE_SERIALIZERS
            .register("shapeless_handling", ShapelessHandlingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, ShapedHandlingRecipe.Serializer> SHAPED_HANDLING_RECIPE = RECIPE_SERIALIZERS
            .register("shaped_handling", ShapedHandlingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, ItemApplicationRecipe.Serializer> ITEM_APPLICATION_RECIPE = RECIPE_SERIALIZERS
            .register("item_application", ItemApplicationRecipe.Serializer::new);

    static {
        // 旧 id：jsonmore:shapeless_consuming / jsonmore:shaped_consuming / jsonmore:item_application
        // 注册表不允许同一个实例挂两个 id，所以旧 id 用独立的 serializer 实例。
        // Serializer 无状态，两个实例的行为完全一致。
        LEGACY_RECIPE_SERIALIZERS.register("shapeless_consuming", ShapelessHandlingRecipe.Serializer::new);
        LEGACY_RECIPE_SERIALIZERS.register("shaped_consuming", ShapedHandlingRecipe.Serializer::new);
        LEGACY_RECIPE_SERIALIZERS.register("item_application", ItemApplicationRecipe.Serializer::new);

        // 强制加载各类，让它们各自的 qikalib / jsonmore 两个 IngredientType 都进入 DeferredRegister。
        TrueIngredient.register();
        NotIngredient.register();
        RegexIngredient.register();
        KeepInventoryContainerIngredient.register();
        ToolDamagingIngredient.register();
        RemainderOverrideIngredient.register();
        NBTCopyIngredient.register();
        ItemDisplayOverrideIngredient.register();
        CountedIngredient.register();
        ConditionIngredient.register();
        SameItemIngredient.register();
        GameRuleCondition.register();
    }

    public QikaLib(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
        LEGACY_RECIPE_SERIALIZERS.register(modEventBus);
        INGREDIENT_TYPES.register(modEventBus);
        LEGACY_INGREDIENT_TYPES.register(modEventBus);
        CONDITION_CODECS.register(modEventBus);
        LEGACY_CONDITION_CODECS.register(modEventBus);
    }
}

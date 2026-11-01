package qikahome.qikalib;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
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
 *       旧 type 由本库再注册一次，每条用独立实例，因此 <b>JsonMore 必须删掉它自己那份注册</b>，
 *       否则同一个 id 会被注册两次。</li>
 * </ul>
 */
@Mod(QikaLib.MODID)
public class QikaLib {
    public static final String MODID = "qikalib";
    public static final String LEGACY_MODID = "jsonmore";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES = DeferredRegister
            .create(NeoForgeRegistries.INGREDIENT_TYPES, MODID);
    public static final DeferredRegister<IngredientType<?>> LEGACY_INGREDIENT_TYPES = DeferredRegister
            .create(NeoForgeRegistries.INGREDIENT_TYPES, LEGACY_MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
            .create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> LEGACY_RECIPE_SERIALIZERS = DeferredRegister
            .create(Registries.RECIPE_SERIALIZER, LEGACY_MODID);

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister
            .create(Registries.RECIPE_TYPE, MODID);
    public static final DeferredRegister<RecipeType<?>> LEGACY_RECIPE_TYPES = DeferredRegister
            .create(Registries.RECIPE_TYPE, LEGACY_MODID);

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS = DeferredRegister
            .create(NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> LEGACY_CONDITION_CODECS = DeferredRegister
            .create(NeoForgeRegistries.Keys.CONDITION_CODECS, LEGACY_MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ShapelessRecipe>> SHAPELESS_HANDLING_RECIPE = RECIPE_SERIALIZERS
            .register("shapeless_handling", ShapelessHandlingRecipe::newSerializer);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ShapedRecipe>> SHAPED_HANDLING_RECIPE = RECIPE_SERIALIZERS
            .register("shaped_handling", ShapedHandlingRecipe::newSerializer);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ItemApplicationRecipe>> ITEM_APPLICATION_RECIPE = RECIPE_SERIALIZERS
            .register("item_application", ItemApplicationRecipe.Serializer::create);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ItemApplicationRecipe>> ITEM_APPLICATION_TYPE = RECIPE_TYPES
            .register("item_application", () -> ItemApplicationRecipe.TYPE);

    static {
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

        // 旧 id：jsonmore:shapeless_consuming / jsonmore:shaped_consuming / jsonmore:item_application
        LEGACY_RECIPE_SERIALIZERS.register("shapeless_consuming", ShapelessHandlingRecipe::newSerializer);
        LEGACY_RECIPE_SERIALIZERS.register("shaped_consuming", ShapedHandlingRecipe::newSerializer);
        LEGACY_RECIPE_SERIALIZERS.register("item_application", ItemApplicationRecipe.Serializer::create);
        LEGACY_RECIPE_TYPES.register("item_application", () -> ItemApplicationRecipe.LEGACY_TYPE);

        registerLegacyIngredient("true", TrueIngredient.CODEC);
        registerLegacyIngredient("not", NotIngredient.CODEC);
        registerLegacyIngredient("regex", RegexIngredient.CODEC);
        registerLegacyIngredient("keep_inventory_container", KeepInventoryContainerIngredient.CODEC);
        registerLegacyIngredient("tool_damaging", ToolDamagingIngredient.CODEC);
        registerLegacyIngredient("remainder_override", RemainderOverrideIngredient.CODEC);
        registerLegacyIngredient("nbt_copy", NBTCopyIngredient.CODEC);
        LEGACY_INGREDIENT_TYPES.register("item_display_override",
                () -> new IngredientType<>(ItemDisplayOverrideIngredient.CODEC,
                        ItemDisplayOverrideIngredient.NETWORK_STREAM_CODEC));
        registerLegacyIngredient("counted", CountedIngredient.CODEC);
        registerLegacyIngredient("condition", ConditionIngredient.CODEC);
        registerLegacyIngredient("same_item", SameItemIngredient.CODEC);

        LEGACY_CONDITION_CODECS.register("gamerule", () -> GameRuleCondition.LEGACY_CODEC);
    }

    public QikaLib(IEventBus modEventBus) {
        INGREDIENT_TYPES.register(modEventBus);
        LEGACY_INGREDIENT_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        LEGACY_RECIPE_SERIALIZERS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        LEGACY_RECIPE_TYPES.register(modEventBus);
        CONDITION_CODECS.register(modEventBus);
        LEGACY_CONDITION_CODECS.register(modEventBus);
    }

    /**
     * 旧 id 必须用独立的 {@link IngredientType} 实例，不能复用 {@code TYPE}。
     */
    private static <T extends net.neoforged.neoforge.common.crafting.ICustomIngredient> void registerLegacyIngredient(
            String name, MapCodec<T> codec) {
        LEGACY_INGREDIENT_TYPES.register(name, () -> new IngredientType<>(codec));
    }
}

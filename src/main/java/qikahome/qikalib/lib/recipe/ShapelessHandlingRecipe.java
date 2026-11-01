package qikahome.qikalib.lib.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.lib.handling.HandlingContextStack;
import qikahome.qikalib.lib.ingredient.SelfHandlingIngredient;
import qikahome.qikalib.util.CraftingPlayer;

public class ShapelessHandlingRecipe extends ShapelessRecipe implements IHandlingRecipe {

    // 缓存上一次的匹配结果，transient 避免序列化
    private volatile transient Map<Integer, Ingredient> lastMatch;

    /** 与父类私有的 result/ingredients 并行保存一份，供本类使用。 */
    private final ItemStackTemplate result;
    private final List<Ingredient> ingredients;

    /** 是否有原料需要匹配上下文（解析后固定）；不需要时整套 context 机械都跳过。 */
    private final boolean requiresContext;

    public ShapelessHandlingRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ItemStackTemplate result, List<Ingredient> ingredients) {
        super(commonInfo, bookInfo, result, ingredients);
        this.result = result;
        this.ingredients = List.copyOf(ingredients);
        this.requiresContext = this.ingredients.stream().anyMatch(SelfHandlingIngredient::requiresContext);
    }

    /**
     * 有原料需要上下文时才建栈并绑定本配方的全部原料，否则返回 null。
     * 返回值原样交给 {@link #endContext(HandlingContextStack)}。
     */
    @Nullable
    private HandlingContextStack beginContext() {
        if (!requiresContext)
            return null;
        HandlingContextStack context = new HandlingContextStack().push();
        for (Ingredient ingredient : ingredients)
            SelfHandlingIngredient.setContext(ingredient, context);
        return context;
    }

    private void endContext(@Nullable HandlingContextStack context) {
        if (context == null)
            return;
        for (Ingredient ingredient : ingredients)
            SelfHandlingIngredient.invalidContext(ingredient);
    }

    /**
     * 原版 {@code ShapelessRecipe.matches} 会直接调 {@code ingredient.test(...)}，
     * 所以必须在 super 之前绑定上下文。
     */
    @Override
    public boolean matches(CraftingInput container, Level level) {
        HandlingContextStack context = beginContext();
        try {
            return super.matches(container, level);
        } finally {
            endContext(context);
        }
    }

    // ==================== 共用匹配逻辑（带缓存） ====================
    private Map<Integer, Ingredient> matchIngredients(CraftingInput container) {
        // 优先验证缓存
        if (lastMatch != null && isCacheValid(container, lastMatch)) {
            return lastMatch;
        }

        // 缓存无效，执行完整回溯匹配
        List<Ingredient> ingredientsCopy = new ArrayList<>(ingredients);
        List<Integer> nonEmptySlots = new ArrayList<>();
        for (int i = 0; i < container.size(); i++) {
            if (!container.getItem(i).isEmpty()) {
                nonEmptySlots.add(i);
            }
        }

        if (nonEmptySlots.size() != ingredientsCopy.size()) {
            throw new IllegalArgumentException("Item count mismatch: expected " + ingredientsCopy.size() +
                    ", got " + nonEmptySlots.size());
        }

        Map<Integer, Ingredient> slotToIngredient = new HashMap<>();
        boolean[] slotUsed = new boolean[container.size()];
        boolean found = backtrackMatch(0, ingredientsCopy, nonEmptySlots, slotUsed, slotToIngredient, container);

        if (!found) {
            throw new IllegalArgumentException("Cannot match recipe ingredients");
        }

        // 更新缓存
        lastMatch = slotToIngredient;
        return slotToIngredient;
    }

    /**
     * 验证缓存的匹配结果是否仍适用于当前容器。
     */
    private boolean isCacheValid(CraftingInput container, Map<Integer, Ingredient> cached) {
        // 检查缓存中的每个槽位
        for (Map.Entry<Integer, Ingredient> entry : cached.entrySet()) {
            int slot = entry.getKey();
            if (slot >= container.size())
                return false;
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty())
                return false; // 原本有物品，现在空了
            if (!entry.getValue().test(stack))
                return false; // 物品类型不匹配
        }

        // 检查容器中是否有未被缓存覆盖的非空槽位（即多余物品）
        for (int i = 0; i < container.size(); i++) {
            if (!container.getItem(i).isEmpty() && !cached.containsKey(i)) {
                return false;
            }
        }

        // 确保缓存的槽位数与原料数量一致
        return cached.size() == ingredients.size();
    }

    public static boolean backtrackMatch(int idx, List<Ingredient> ingredients, List<Integer> slots,
            boolean[] slotUsed, Map<Integer, Ingredient> slotToIngredient,
            CraftingInput container) {
        if (idx == ingredients.size())
            return true;
        Ingredient ingredient = ingredients.get(idx);
        for (int slot : slots) {
            if (!slotUsed[slot] && ingredient.test(container.getItem(slot))) {
                slotUsed[slot] = true;
                slotToIngredient.put(slot, ingredient);
                if (backtrackMatch(idx + 1, ingredients, slots, slotUsed, slotToIngredient, container)) {
                    return true;
                }
                slotUsed[slot] = false;
                slotToIngredient.remove(slot);
            }
        }
        return false;
    }

    // ==================== 剩余物品处理 ====================
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput container) {
        HandlingContextStack context = beginContext();
        try {
            NonNullList<ItemStack> remainingItems = NonNullList.withSize(container.size(), ItemStack.EMPTY);
            Map<Integer, Ingredient> slotToIngredient = matchIngredients(container);

            MinecraftServer server = QikaLib.getCurrentServer();
            if (server == null)
                return remainingItems; // 客户端预览，跳过消耗逻辑
            ServerLevel level = server.overworld();

            for (var entry : slotToIngredient.entrySet()) {
                int slot = entry.getKey();
                ItemStack stack = container.getItem(slot);
                Ingredient ingredient = entry.getValue();
                ItemStack remaining = SelfHandlingIngredient.consume(ingredient, stack, level, CraftingPlayer.get());
                remainingItems.set(slot, remaining);
            }
            return remainingItems;
        } finally {
            endContext(context);
        }
    }

    // ==================== 合成输出处理 ====================
    @Override
    public ItemStack assemble(CraftingInput container) {
        HandlingContextStack context = beginContext();
        try {
            ItemStack resultStack = super.assemble(container);
            if (resultStack.isEmpty()) {
                return resultStack;
            }

            Map<Integer, Ingredient> slotToIngredient = matchIngredients(container);

            for (var entry : slotToIngredient.entrySet()) {
                int slot = entry.getKey();
                ItemStack inputStack = container.getItem(slot);
                Ingredient ingredient = entry.getValue();
                SelfHandlingIngredient.outputModify(ingredient, inputStack, resultStack);
            }
            return resultStack;
        } finally {
            endContext(context);
        }
    }

    // ==================== 序列化 ====================
    private static final MapCodec<ShapelessHandlingRecipe> RECIPE_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                    Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(recipe -> recipe.ingredients))
                    .apply(instance, ShapelessHandlingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ShapelessHandlingRecipe> RECIPE_STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                Recipe.CommonInfo.STREAM_CODEC.encode(buf, recipe.commonInfo);
                CraftingRecipe.CraftingBookInfo.STREAM_CODEC.encode(buf, recipe.bookInfo);
                ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result);
                writeIngredients(buf, recipe.ingredients);
            },
            buf -> new ShapelessHandlingRecipe(
                    Recipe.CommonInfo.STREAM_CODEC.decode(buf),
                    CraftingRecipe.CraftingBookInfo.STREAM_CODEC.decode(buf),
                    ItemStackTemplate.STREAM_CODEC.decode(buf),
                    readIngredients(buf)));

    private static void writeIngredients(RegistryFriendlyByteBuf buf, List<Ingredient> ingredients) {
        buf.writeVarInt(ingredients.size());
        for (Ingredient ingredient : ingredients)
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
    }

    private static List<Ingredient> readIngredients(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Ingredient> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
            list.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        return list;
    }

    @SuppressWarnings("unchecked")
    public static final RecipeSerializer<ShapelessRecipe> SERIALIZER = new RecipeSerializer<>(
            RECIPE_CODEC.xmap(recipe -> (ShapelessRecipe) recipe, recipe -> (ShapelessHandlingRecipe) recipe),
            StreamCodec.of(
                    (buf, recipe) -> RECIPE_STREAM_CODEC.encode(buf, (ShapelessHandlingRecipe) recipe),
                    buf -> RECIPE_STREAM_CODEC.decode(buf)));
    /** 旧 id {@code jsonmore:shapeless_consuming} 用的独立实例（Serializer 无状态）。 */
    @SuppressWarnings("unchecked")
    public static final RecipeSerializer<ShapelessRecipe> LEGACY_SERIALIZER = new RecipeSerializer<>(
            RECIPE_CODEC.xmap(recipe -> (ShapelessRecipe) recipe, recipe -> (ShapelessHandlingRecipe) recipe),
            StreamCodec.of(
                    (buf, recipe) -> RECIPE_STREAM_CODEC.encode(buf, (ShapelessHandlingRecipe) recipe),
                    buf -> RECIPE_STREAM_CODEC.decode(buf)));

    @Override
    public RecipeSerializer<ShapelessRecipe> getSerializer() {
        return SERIALIZER;
    }
}

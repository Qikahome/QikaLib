package qikahome.qikalib.lib.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import qikahome.qikalib.lib.handling.HandlingContextStack;
import qikahome.qikalib.lib.ingredient.SelfHandlingIngredient;

import static qikahome.qikalib.lib.recipe.ShapelessHandlingRecipe.backtrackMatch;

public class ShapedHandlingRecipe extends ShapedRecipe implements IHandlingRecipe {

    // 缓存上一次的匹配结果
    private volatile transient Map<Integer, Ingredient> lastMatch;

    /** 是否有原料需要匹配上下文（解析后固定）；不需要时整套 context 机械都跳过。 */
    private final boolean requiresContext;

    /** 原版把结果设为 private 且没有访问器，这里自己留一份引用。 */
    private final ItemStackTemplate result;

    public ShapedHandlingRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo, pattern, result);
        this.result = result;
        this.requiresContext = getIngredients().stream()
                .anyMatch(opt -> opt.map(ing -> SelfHandlingIngredient.requiresContext(ing)).orElse(false));
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
        for (Optional<Ingredient> ingredient : getIngredients())
            ingredient.ifPresent(ing -> SelfHandlingIngredient.setContext(ing, context));
        return context;
    }

    private void endContext(@Nullable HandlingContextStack context) {
        if (context == null)
            return;
        for (Optional<Ingredient> ingredient : getIngredients())
            ingredient.ifPresent(SelfHandlingIngredient::invalidContext);
    }

    /**
     * 原版 {@code ShapedRecipe.matches} 会直接调 {@code ingredient.test(...)}，
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
    /**
     * 匹配容器中的物品与配方的原料，返回每个格子对应的原料。
     * 若无法匹配则抛出异常。
     */
    private Map<Integer, Ingredient> matchIngredients(CraftingInput container) {
        // 优先验证缓存
        if (lastMatch != null && isCacheValid(container)) {
            return lastMatch;
        }

        int containerWidth = container.width();
        int containerHeight = container.height();
        List<Ingredient> ingredients = getIngredients().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(ing -> !ing.isEmpty())
                .collect(Collectors.toList());

        // 情况1：容器宽度为0或高度为0（例如模组假工作台），使用无序回溯匹配
        if (containerWidth == 0 || containerHeight == 0) {
            List<Integer> nonEmptySlots = new ArrayList<>();
            for (int i = 0; i < container.size(); i++) {
                if (!container.getItem(i).isEmpty()) {
                    nonEmptySlots.add(i);
                }
            }
            if (nonEmptySlots.size() != ingredients.size()) {
                throw new IllegalArgumentException("Item count mismatch: expected " + ingredients.size() +
                        ", got " + nonEmptySlots.size());
            }
            Map<Integer, Ingredient> slotToIngredient = new HashMap<>();
            boolean[] slotUsed = new boolean[container.size()];
            boolean found = backtrackMatch(0, ingredients, nonEmptySlots, slotUsed, slotToIngredient, container);
            if (!found) {
                throw new IllegalArgumentException("Cannot match recipe ingredients (unordered)");
            }
            lastMatch = slotToIngredient;
            return slotToIngredient;
        }

        // 情况2：正常有序网格，尝试所有起始位置和镜像
        int recipeWidth = pattern.width();
        int recipeHeight = pattern.height();
        for (int startX = 0; startX <= containerWidth - recipeWidth; startX++) {
            for (int startY = 0; startY <= containerHeight - recipeHeight; startY++) {
                for (boolean mirrored : new boolean[] { false, true }) {
                    if (!matchesShape(container, startX, startY, mirrored))
                        continue;

                    Map<Integer, Ingredient> matchedSlots = new HashMap<>();
                    // 临时原料列表，用于消耗标记（不修改原列表）
                    List<Ingredient> remainingIngredients = new ArrayList<>(ingredients);

                    for (int row = 0; row < recipeHeight; row++) {
                        for (int col = 0; col < recipeWidth; col++) {
                            int ingredientIndex = mirrored
                                    ? (recipeWidth - col - 1) + row * recipeWidth
                                    : col + row * recipeWidth;
                            // 原配方 getIngredients() 包含空格，需要映射到过滤后的 ingredients 列表
                            Optional<Ingredient> originalIng = getIngredients().get(ingredientIndex);
                            if (originalIng.isEmpty())
                                continue;

                            // 在剩余的原料列表中找到匹配的原料（支持同种原料重复）
                            int matchedIdx = -1;
                            for (int idx = 0; idx < remainingIngredients.size(); idx++) {
                                if (remainingIngredients.get(idx) == originalIng.get()) {
                                    matchedIdx = idx;
                                    break;
                                }
                            }
                            if (matchedIdx == -1) {
                                matchedSlots = null;
                                break;
                            }

                            int containerSlot = (startY + row) * containerWidth + (startX + col);
                            if (containerSlot >= container.size()) {
                                matchedSlots = null;
                                break;
                            }
                            ItemStack stack = container.getItem(containerSlot);
                            if (stack.isEmpty() || !originalIng.get().test(stack)) {
                                matchedSlots = null;
                                break;
                            }

                            matchedSlots.put(containerSlot, originalIng.get());
                            remainingIngredients.remove(matchedIdx);
                        }
                        if (matchedSlots == null)
                            break;
                    }

                    if (matchedSlots != null && remainingIngredients.isEmpty()) {
                        lastMatch = matchedSlots;
                        return matchedSlots;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Cannot match recipe ingredients (shaped)");
    }

    /**
     * 判断在给定偏移和镜像下形状是否匹配（不检查原料对应关系，仅检查物品是否被原料接受）
     */
    private boolean matchesShape(CraftingInput container, int startX, int startY, boolean mirrored) {
        List<Optional<Ingredient>> ingredients = getIngredients();
        int recipeWidth = pattern.width();
        int recipeHeight = pattern.height();
        int containerWidth = container.width();

        for (int i = 0; i < containerWidth; i++) {
            for (int j = 0; j < container.height(); j++) {
                int k = i - startX;
                int l = j - startY;
                Optional<Ingredient> ingredient = Optional.empty();
                if (k >= 0 && l >= 0 && k < recipeWidth && l < recipeHeight) {
                    ingredient = ingredients.get(mirrored
                            ? recipeWidth - k - 1 + l * recipeWidth
                            : k + l * recipeWidth);
                }
                if (ingredient.isPresent() && !ingredient.get().test(container.getItem(i + j * containerWidth))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 验证缓存的匹配结果是否仍适用于当前容器
     */
    private boolean isCacheValid(CraftingInput container) {
        if (lastMatch == null)
            return false;
        // 检查每个缓存槽位：物品非空且通过原料测试
        for (Map.Entry<Integer, Ingredient> entry : lastMatch.entrySet()) {
            int slot = entry.getKey();
            if (slot >= container.size())
                return false;
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty())
                return false;
            if (!entry.getValue().test(stack))
                return false;
        }
        // 检查容器中是否有未被缓存覆盖的非空槽位
        for (int i = 0; i < container.size(); i++) {
            if (!container.getItem(i).isEmpty() && !lastMatch.containsKey(i)) {
                return false;
            }
        }
        // 确保缓存大小等于非空原料数量
        long nonEmptyIngredientCount = getIngredients().stream()
                .filter(opt -> opt.isPresent() && !opt.get().isEmpty())
                .count();
        return lastMatch.size() == nonEmptyIngredientCount;
    }

    // ==================== 剩余物品处理 ====================
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput container) {
        HandlingContextStack context = beginContext();
        try {
            NonNullList<ItemStack> remainingItems = NonNullList.withSize(container.size(), ItemStack.EMPTY);
            Map<Integer, Ingredient> slotToIngredient = matchIngredients(container);

            Player player = CommonHooks.getCraftingPlayer();
            ServerLevel level = null;
            if (player != null && player.level() instanceof ServerLevel serverLevel)
                level = serverLevel;
            if (level == null) {
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null)
                    level = server.overworld();
                else
                    return remainingItems; // 客户端预览，跳过消耗逻辑
            }

            for (var entry : slotToIngredient.entrySet()) {
                int slot = entry.getKey();
                ItemStack stack = container.getItem(slot);
                Ingredient ingredient = entry.getValue();
                ItemStack remaining = SelfHandlingIngredient.consume(ingredient, stack, level, player);
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
            ItemStack result = super.assemble(container);
            if (result.isEmpty()) {
                return result;
            }

            Map<Integer, Ingredient> slotToIngredient = matchIngredients(container);

            for (var entry : slotToIngredient.entrySet()) {
                int slot = entry.getKey();
                ItemStack inputStack = container.getItem(slot);
                Ingredient ingredient = entry.getValue();
                SelfHandlingIngredient.outputModify(ingredient, inputStack, result);
            }
            return result;
        } finally {
            endContext(context);
        }
    }

    // ==================== 序列化 ====================
    private static final MapCodec<ShapedRecipe> CODEC = RecordCodecBuilder
            .<ShapedHandlingRecipe>mapCodec(
                    inst -> inst.group(
                            Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
                            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
                            ShapedRecipePattern.MAP_CODEC.fieldOf("pattern").forGetter(r -> r.pattern),
                            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result))
                            .apply(inst, ShapedHandlingRecipe::new))
            .xmap(r -> (ShapedRecipe) r, r -> (ShapedHandlingRecipe) r);
    private static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec
            .composite(
                    Recipe.CommonInfo.STREAM_CODEC, (ShapedHandlingRecipe r) -> r.commonInfo,
                    CraftingRecipe.CraftingBookInfo.STREAM_CODEC, (ShapedHandlingRecipe r) -> r.bookInfo,
                    ShapedRecipePattern.STREAM_CODEC, (ShapedHandlingRecipe r) -> r.pattern,
                    ItemStackTemplate.STREAM_CODEC, (ShapedHandlingRecipe r) -> r.result,
                    ShapedHandlingRecipe::new)
            .map(r -> (ShapedRecipe) r, r -> (ShapedHandlingRecipe) r);
    private static final RecipeSerializer<ShapedRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    /** 每个 JSON type 都使用独立的 serializer 实例（注册表不允许同一个实例挂两个 id）。 */
    public static RecipeSerializer<ShapedRecipe> newSerializer() {
        return new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return SERIALIZER;
    }
}

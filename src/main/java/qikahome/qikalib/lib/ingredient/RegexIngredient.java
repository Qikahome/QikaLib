package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nullable;

import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 按物品注册名匹配的原料。
 *
 * <p>三个匹配字段都是正则、都是整串匹配（{@code matches()}，需要"包含"语义时自行写 {@code .*}），
 * 可任选其一或组合使用，同时给出时需全部满足：
 * <ul>
 *   <li>{@code pattern}：匹配完整的 {@code 命名空间:路径}</li>
 *   <li>{@code namespace}：只匹配命名空间（常用于"某个模组的全部物品"）</li>
 *   <li>{@code path}：只匹配路径部分</li>
 * </ul>
 *
 * <p>{@code expand_items}（默认 {@code false}）控制展示：为 {@code true} 时首次
 * {@link #getMatchingStacks()} 会遍历物品注册表、缓存全部匹配物品；否则只给一条代表栈
 * （展示内容写在名称上）。两种模式下 {@code test()} 都是现场取注册名匹配，
 * 只预编译 {@link Pattern}——不做匹配集缓存，避免物品注册表尚未就绪时的缓存失效问题。
 */
public class RegexIngredient implements CustomIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:regex");

    public static final MapCodec<RegexIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("pattern").forGetter(i -> Optional.ofNullable(i.patternRaw)),
            Codec.STRING.optionalFieldOf("namespace").forGetter(i -> Optional.ofNullable(i.namespaceRaw)),
            Codec.STRING.optionalFieldOf("path").forGetter(i -> Optional.ofNullable(i.pathRaw)),
            Codec.BOOL.optionalFieldOf("expand_items", false).forGetter(i -> i.expandItems))
            .apply(instance, (pattern, namespace, path, expandItems) -> new RegexIngredient(
                    pattern.orElse(null), namespace.orElse(null), path.orElse(null), expandItems)));
    public static final CustomIngredientSerializer<RegexIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID,
            CODEC);

    @Nullable
    private final String patternRaw;
    @Nullable
    private final String namespaceRaw;
    @Nullable
    private final String pathRaw;
    private final boolean expandItems;

    @Nullable
    private final Pattern pattern;
    @Nullable
    private final Pattern namespacePattern;
    @Nullable
    private final Pattern pathPattern;

    private final String description;

    private RegexIngredient(@Nullable String pattern, @Nullable String namespace, @Nullable String path,
            boolean expandItems) {
        if (pattern == null && namespace == null && path == null) {
            throw new JsonParseException("Regex ingredient needs at least one of 'pattern', 'namespace', 'path'");
        }
        this.patternRaw = pattern;
        this.namespaceRaw = namespace;
        this.pathRaw = path;
        this.expandItems = expandItems;
        this.pattern = compile(pattern, "pattern");
        this.namespacePattern = compile(namespace, "namespace");
        this.pathPattern = compile(path, "path");
        this.description = describe(pattern, namespace, path);
    }

    @Nullable
    private static Pattern compile(@Nullable String regex, String field) {
        if (regex == null) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new JsonParseException("Invalid '" + field + "' regex: " + e.getDescription(), e);
        }
    }

    private static String describe(@Nullable String pattern, @Nullable String namespace, @Nullable String path) {
        StringBuilder builder = new StringBuilder();
        if (pattern != null) {
            builder.append(pattern);
        }
        if (namespace != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("namespace=").append(namespace);
        }
        if (path != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("path=").append(path);
        }
        return builder.toString();
    }

    private boolean matches(ResourceLocation id) {
        if (pattern != null && !pattern.matcher(id.toString()).matches()) {
            return false;
        }
        if (namespacePattern != null && !namespacePattern.matcher(id.getNamespace()).matches()) {
            return false;
        }
        if (pathPattern != null && !pathPattern.matcher(id.getPath()).matches()) {
            return false;
        }
        return true;
    }

    @Nullable
    private ItemStack representativeStack = null;
    @Nullable
    private List<ItemStack> expandedStacks = null;

    @Override
    public List<ItemStack> getMatchingStacks() {
        if (expandItems) {
            if (expandedStacks == null) {
                List<ItemStack> matched = new ArrayList<>();
                for (Item item : BuiltInRegistries.ITEM) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    if (id != null && matches(id)) {
                        matched.add(new ItemStack(item));
                    }
                }
                // 空结果可能是注册表尚未就绪，留空以便下次调用重试
                if (!matched.isEmpty()) {
                    expandedStacks = matched;
                }
            }
            if (expandedStacks != null) {
                return List.copyOf(expandedStacks);
            }
        }
        if (representativeStack == null) {
            // 匹配集可能极大，只展示一条代表栈，并把规则本身写进名称，让玩家能看出实际接受范围。
            representativeStack = new ItemStack(Items.NAME_TAG);
            representativeStack.set(DataComponents.CUSTOM_NAME,
                    Component.translatable("ingredient.qikalib.regex", description));
        }
        return List.of(representativeStack.copy());
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && matches(id);
    }

    @Override
    public boolean requiresTesting() {
        // 匹配始终走 test()（即使用户开了 expand_items，展开结果也只是展示用），
        // 因此这里返回 true，避免 Fabric 直接拿展示列表当匹配集建索引。
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}

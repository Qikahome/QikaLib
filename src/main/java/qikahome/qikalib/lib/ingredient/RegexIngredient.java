package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

/**
 * 按物品注册名匹配的原料。
 *
 * <p>三个匹配字段都是正则、都是整串匹配（{@code matches()}，需要"包含"语义时自行写 {@code .*}），
 * 可任选其一或组合使用，同时给出时需全部满足：
 * <ul>
 *   <li>{@code pattern}：匹配完整的 {@code 命名空间:路径}</li>
 *   <li>{@code namespace}：只匹配命名空间</li>
 *   <li>{@code path}：只匹配路径部分</li>
 * </ul>
 */
public class RegexIngredient implements ICustomIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:regex");

    public static final MapCodec<RegexIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Codec.STRING.optionalFieldOf("pattern").forGetter(i -> Optional.ofNullable(i.patternRaw)),
                    Codec.STRING.optionalFieldOf("namespace").forGetter(i -> Optional.ofNullable(i.namespaceRaw)),
                    Codec.STRING.optionalFieldOf("path").forGetter(i -> Optional.ofNullable(i.pathRaw)),
                    Codec.BOOL.optionalFieldOf("expand_items", false).forGetter(i -> i.expandItems))
                    .apply(v, RegexIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<RegexIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

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

    @Nullable
    private List<Holder<Item>> expandedItems;
    @Nullable
    private SlotDisplay cachedDisplay;

    private RegexIngredient(Optional<String> pattern, Optional<String> namespace, Optional<String> path,
            boolean expandItems) {
        if (pattern.isEmpty() && namespace.isEmpty() && path.isEmpty()) {
            throw new IllegalArgumentException(
                    "Regex ingredient needs at least one of 'pattern', 'namespace', 'path'");
        }
        this.patternRaw = pattern.orElse(null);
        this.namespaceRaw = namespace.orElse(null);
        this.pathRaw = path.orElse(null);
        this.expandItems = expandItems;
        this.pattern = compile(this.patternRaw, "pattern");
        this.namespacePattern = compile(this.namespaceRaw, "namespace");
        this.pathPattern = compile(this.pathRaw, "path");
        this.description = describe(this.patternRaw, this.namespaceRaw, this.pathRaw);
    }

    @Nullable
    private static Pattern compile(@Nullable String regex, String field) {
        if (regex == null) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid '" + field + "' regex: " + e.getDescription(), e);
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

    private boolean matches(Identifier id) {
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

    private void ensureExpanded() {
        if (expandedItems != null) {
            return;
        }
        List<Holder<Item>> matched = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && matches(id)) {
                matched.add(BuiltInRegistries.ITEM.wrapAsHolder(item));
            }
        }
        // 空结果可能是物品注册表尚未就绪，留空以便下次调用重试
        if (!matched.isEmpty()) {
            expandedItems = List.copyOf(matched);
        }
    }

    @Override
    public Stream<Holder<Item>> items() {
        if (expandItems) {
            ensureExpanded();
            if (expandedItems != null) {
                return expandedItems.stream();
            }
        }
        return Stream.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.NAME_TAG));
    }

    @Override
    public SlotDisplay display() {
        if (cachedDisplay == null) {
            List<ItemStack> list = new ArrayList<>();
            if (expandItems) {
                ensureExpanded();
                if (expandedItems != null) {
                    for (Holder<Item> holder : expandedItems) {
                        list.add(new ItemStack(holder.value()));
                    }
                }
            }
            if (list.isEmpty()) {
                ItemStack stack = new ItemStack(Items.NAME_TAG);
                stack.set(DataComponents.CUSTOM_NAME, Component.translatable("ingredient.qikalib.regex", description));
                list.add(stack);
            }
            cachedDisplay = new SlotDisplay.Composite(list.stream()
                    .map(ItemStackTemplate::fromNonEmptyStack)
                    .map(SlotDisplay.ItemStackSlotDisplay::new)
                    .map(t -> (SlotDisplay) t)
                    .toList());
        }
        return cachedDisplay;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && matches(id);
    }

    @Override
    public boolean isSimple() {
        // 匹配始终走 test()（展开结果也只是展示用），因此必须为 false。
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}

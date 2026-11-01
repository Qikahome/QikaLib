package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.IdentifierException;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.Utils;

public class NBTCopyIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:nbt_copy");

    /**
     * tags 在构造前先校验：非法路径返回 {@link DataResult#error}，让该配方单独加载失败，
     * 而不是抛异常拖垮整个数据包加载。
     */
    private static final Codec<List<String>> TAG_LIST_CODEC = Codec.STRING.listOf().flatXmap(
            NBTCopyIngredient::validateTags,
            DataResult::success);

    private static DataResult<List<String>> validateTags(List<String> tags) {
        for (String tag : tags) {
            if (tag.equals("*")) {
                continue;
            }
            String path = tag.startsWith("!") ? tag.substring(1) : tag;
            try {
                Identifier.parse(path);
            } catch (IdentifierException e) {
                return DataResult.error(() -> "Invalid tag path '" + path + "' (from '" + tag + "'): " + e.getMessage());
            }
        }
        return DataResult.success(tags);
    }

    public static final MapCodec<NBTCopyIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    Utils.enumCodec(Mode.class).fieldOf("mode").forGetter(i -> i.mode),
                    TAG_LIST_CODEC.optionalFieldOf("tags", List.of()).forGetter(i -> i.tags))
                    .apply(v, NBTCopyIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<NBTCopyIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

    public enum Mode {
        REPLACE_ALL,
        REPLACE,
        MERGE_TARGET_FIRST,
        MERGE_SOURCE_FIRST,
    }

    private final Mode mode;
    @Nullable
    private final List<String> tags;
    private boolean hasWildcard = false;
    final List<Identifier> includes = new ArrayList<>();
    final List<Identifier> excludes = new ArrayList<>();

    public NBTCopyIngredient(Ingredient ingredient, Mode mode) {
        this(ingredient, mode, null);
    }

    public NBTCopyIngredient(Ingredient ingredient, Mode mode, @Nullable List<String> tags) {
        super(ingredient, false);
        this.mode = mode;
        this.tags = tags;

        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                if (tag.equals("*")) {
                    hasWildcard = true;
                } else if (tag.startsWith("!")) {
                    excludes.add(Identifier.parse(tag.substring(1)));
                } else {
                    includes.add(Identifier.parse(tag));
                }
            }
        }
    }

    @Override
    public void outputModify(ItemStack matched, ItemStack output) {
        if (matched.isEmpty() || output.isEmpty())
            return;
        copyNBT(output, matched);
    }

    public void copyNBT(ItemStack target, ItemStack source) {
        if (target.isEmpty() || source.isEmpty())
            return;

        var sourceNBT = source.getComponentsPatch();

        switch (mode) {
            case REPLACE -> {
                var targetPatch = target.getComponentsPatch();
                targetPatch.forget(any -> true);
                mergeAllFiltered(target, sourceNBT, true);
            }
            case MERGE_TARGET_FIRST -> {
                mergeAllFiltered(target, sourceNBT, false);
            }
            case MERGE_SOURCE_FIRST -> {
                mergeAllFiltered(target, sourceNBT, true);
            }
            case REPLACE_ALL -> {
                var targetPatch = target.getComponentsPatch();
                targetPatch.forget(any -> true);
                for (var key : source.getComponents().keySet()) {
                    target.set((DataComponentType) key, source.get(key));
                }
            }
        }
    }

    private void mergeAllFiltered(ItemStack target, DataComponentPatch source, boolean sourceWins) {
        var targetPatch = target.getComponentsPatch();
        for (var entry : source.entrySet()) {
            DataComponentType key = entry.getKey();
            Identifier keyName = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(key);
            if (this.hasWildcard || this.includes.isEmpty() || this.includes.contains(keyName)) {
                if (!this.excludes.contains(keyName)) {
                    if (sourceWins || targetPatch.entrySet().stream().noneMatch(e -> e.getKey() == key)) {
                        entry.getValue().ifPresent(val -> target.set(key, val));
                    }
                }
            }
        }
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}

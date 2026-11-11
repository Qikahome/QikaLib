package qikahome.qikalib.lib.ingredient;

import static qikahome.qikalib.QikaLib.LOGGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import qikahome.qikalib.QikaLib;

public class NBTCopyIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation(QikaLib.MODID, "nbt_copy");
    public static final ResourceLocation LEGACY_ID = new ResourceLocation(QikaLib.LEGACY_MODID, "nbt_copy");

    private static final Pattern PATH_PATTERN = Pattern.compile("(\\w+)(?:\\[(\\d+)\\])?");

    public enum Mode {
        REPLACE,
        MERGE_TARGET_FIRST,
        MERGE_SOURCE_FIRST
    }

    private final Mode mode;
    @Nullable
    private final List<String> tags;
    /** 按路径定向复制（类似 copy_nbt），存在时优先于整份 NBT 复制 */
    @Nullable
    private final List<CopyOp> copies;
    @Nullable
    private final transient Set<PathSpec> parsedPaths;
    @Nullable
    private final transient Set<PathSpec> excludedPaths;

    /** 一条定向复制：把源的 source 路径复制到目标的 target 路径；op = replace / merge */
    public record CopyOp(String op, String source, String target) {
        public static final String OP_REPLACE = "replace";
        public static final String OP_MERGE = "merge";
    }

    private static class PathSpec {
        final List<String> keys;
        final List<Integer> indices;

        PathSpec(List<String> keys, List<Integer> indices) {
            this.keys = keys;
            this.indices = indices;
        }
    }

    public NBTCopyIngredient(Ingredient ingredient, Mode mode) {
        this(ingredient, mode, null, null);
    }

    public NBTCopyIngredient(Ingredient ingredient, Mode mode, @Nullable List<String> tags) {
        this(ingredient, mode, tags, null);
    }

    public NBTCopyIngredient(Ingredient ingredient, Mode mode, @Nullable List<String> tags,
            @Nullable List<CopyOp> copies) {
        super(ingredient, false);
        this.mode = mode;
        this.tags = tags;
        this.copies = copies;

        if (tags != null && !tags.isEmpty()) {
            List<String> includes = new ArrayList<>();
            List<String> excludes = new ArrayList<>();
            boolean hasWildcard = false;

            for (String tag : tags) {
                if (tag.equals("*")) {
                    hasWildcard = true;
                } else if (tag.startsWith("!")) {
                    excludes.add(tag.substring(1));
                } else {
                    includes.add(tag);
                }
            }

            this.parsedPaths = (hasWildcard || !includes.isEmpty()) ? parsePaths(includes, false) : null;
            this.excludedPaths = !excludes.isEmpty() ? parsePaths(excludes, true) : null;
        } else {
            this.parsedPaths = null;
            this.excludedPaths = null;
        }
    }

    private Set<PathSpec> parsePaths(List<String> paths, boolean negated) {
        Set<PathSpec> specs = new HashSet<>();
        for (String path : paths) {
            List<String> keys = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();

            Matcher matcher = PATH_PATTERN.matcher(path);
            int pos = 0;
            boolean valid = true;

            while (pos < path.length() && matcher.find(pos)) {
                String key = matcher.group(1);
                String indexStr = matcher.group(2);

                keys.add(key);
                indices.add(indexStr != null ? Integer.parseInt(indexStr) : -1);
                pos = matcher.end();

                if (pos < path.length() && path.charAt(pos) != '.') {
                    valid = false;
                    break;
                }
                pos++;
            }

            if (!valid) {
                throw new com.google.gson.JsonSyntaxException("Invalid tag path: " + path);
            }

            if (!keys.isEmpty()) {
                specs.add(new PathSpec(keys, indices));
            }
        }
        return specs;
    }

    @Override
    public void outputModify(ItemStack matched, ItemStack output) {
        if (matched.isEmpty() || output.isEmpty())
            return;
        if (copies != null && !copies.isEmpty()) {
            copyPaths(matched, output);
            return;
        }
        copyNBT(output, matched);
    }

    /** 按 copies 指定的路径从 matched 复制到 output */
    private void copyPaths(ItemStack matched, ItemStack output) {
        CompoundTag sourceNBT = matched.getTag();
        if (sourceNBT == null) {
            return;
        }
        CompoundTag targetNBT = output.getOrCreateTag();
        for (CopyOp copy : copies) {
            Tag value = getPath(sourceNBT, copy.source());
            if (value == null) {
                continue;
            }
            Tag existing = getPath(targetNBT, copy.target());
            if (CopyOp.OP_MERGE.equals(copy.op()) && existing instanceof CompoundTag targetCompound
                    && value instanceof CompoundTag sourceCompound) {
                targetCompound.merge(sourceCompound.copy());
            } else {
                setPath(targetNBT, copy.target(), value.copy());
            }
        }
    }

    /** 按点分隔的 compound 路径取值；路径为空表示整个根 */
    @Nullable
    private static Tag getPath(CompoundTag root, String path) {
        if (path.isEmpty()) {
            return root;
        }
        Tag current = root;
        for (String key : path.split("\\.")) {
            if (key.isEmpty() || !(current instanceof CompoundTag compound)) {
                return null;
            }
            current = compound.get(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** 按点分隔的 compound 路径写入，缺失的中间 compound 自动创建 */
    private static void setPath(CompoundTag root, String path, Tag value) {
        String[] keys = path.split("\\.");
        CompoundTag current = root;
        for (int i = 0; i < keys.length - 1; i++) {
            if (keys[i].isEmpty()) {
                return;
            }
            Tag child = current.get(keys[i]);
            CompoundTag childCompound;
            if (child instanceof CompoundTag existing) {
                childCompound = existing;
            } else {
                childCompound = new CompoundTag();
                current.put(keys[i], childCompound);
            }
            current = childCompound;
        }
        if (!keys[keys.length - 1].isEmpty()) {
            current.put(keys[keys.length - 1], value);
        }
    }

    public void copyNBT(ItemStack target, ItemStack source) {
        if (target.isEmpty() || source.isEmpty())
            return;

        CompoundTag targetNBT = target.getTag();
        CompoundTag sourceNBT = source.getTag();

        if (sourceNBT == null)
            return;

        switch (mode) {
            case REPLACE:
                target.setTag(sourceNBT.copy());
                break;

            case MERGE_TARGET_FIRST:
                if (targetNBT == null) {
                    target.setTag(sourceNBT.copy());
                } else {
                    CompoundTag merged = targetNBT.copy();
                    mergeNBT(merged, sourceNBT, false);
                    target.setTag(merged);
                }
                break;

            case MERGE_SOURCE_FIRST:
                if (targetNBT == null) {
                    target.setTag(sourceNBT.copy());
                } else {
                    CompoundTag merged = sourceNBT.copy();
                    mergeNBT(merged, targetNBT, false);
                    target.setTag(merged);
                }
                break;
        }
    }

    private void mergeNBT(CompoundTag target, CompoundTag source, boolean sourceWins) {
        mergeNBT(target, source, sourceWins, new ArrayList<>(), 0);
    }

    private void mergeNBT(CompoundTag target, CompoundTag source, boolean sourceWins,
            List<String> currentPath, int depth) {
        for (String key : source.getAllKeys()) {
            List<String> newPath = new ArrayList<>(currentPath);
            newPath.add(key);

            if (parsedPaths != null && !parsedPaths.isEmpty()) {
                if (!matchesAnyPath(newPath, depth)) {
                    continue;
                }
            }

            Tag sourceTag = source.get(key);
            if (sourceTag == null)
                continue;

            if (target.contains(key)) {
                Tag targetTag = target.get(key);
                if (targetTag instanceof CompoundTag && sourceTag instanceof CompoundTag) {
                    mergeNBT((CompoundTag) targetTag, (CompoundTag) sourceTag, sourceWins, newPath, depth + 1);
                } else if (targetTag instanceof ListTag && sourceTag instanceof ListTag) {
                    if (sourceWins) {
                        target.put(key, sourceTag.copy());
                    }
                } else {
                    if (sourceWins) {
                        target.put(key, sourceTag.copy());
                    }
                }
            } else {
                target.put(key, sourceTag.copy());
            }
        }
    }

    private boolean matchesAnyPath(List<String> currentPath, int depth) {
        boolean excluded = isExcluded(currentPath);
        if (excluded) {
            return false;
        }

        if (parsedPaths == null || parsedPaths.isEmpty()) {
            return true;
        }

        for (PathSpec spec : parsedPaths) {
            if (currentPath.size() > spec.keys.size()) {
                continue;
            }

            boolean matches = true;
            for (int i = 0; i < currentPath.size() && i < spec.keys.size(); i++) {
                if (!spec.keys.get(i).equals(currentPath.get(i))) {
                    matches = false;
                    break;
                }
                int index = spec.indices.get(i);
                if (index >= 0) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                if (currentPath.size() == spec.keys.size()) {
                    int lastIndex = spec.indices.get(spec.keys.size() - 1);
                    if (lastIndex >= 0) {
                        return true;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean isExcluded(List<String> currentPath) {
        if (excludedPaths == null || excludedPaths.isEmpty()) {
            return false;
        }

        for (PathSpec spec : excludedPaths) {
            if (currentPath.size() > spec.keys.size()) {
                continue;
            }

            boolean matches = true;
            for (int i = 0; i < currentPath.size() && i < spec.keys.size(); i++) {
                if (!spec.keys.get(i).equals(currentPath.get(i))) {
                    matches = false;
                    break;
                }
                int index = spec.indices.get(i);
                if (index >= 0) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                if (currentPath.size() == spec.keys.size()) {
                    int lastIndex = spec.indices.get(spec.keys.size() - 1);
                    if (lastIndex >= 0) {
                        return true;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements CustomIngredientSerializer<NBTCopyIngredient> {
        public static final Serializer INSTANCE = new Serializer(ID);
        /** 旧 id 的独立实例。 */
        public static final Serializer LEGACY_INSTANCE = new Serializer(LEGACY_ID);

        private final ResourceLocation id;

        public Serializer(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation getIdentifier() {
            return id;
        }

        @Override
        public NBTCopyIngredient read(FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int modeOrdinal = buffer.readVarInt();
            Mode mode = Mode.values()[modeOrdinal];
            boolean hasTags = buffer.readBoolean();
            List<String> tags = null;
            if (hasTags) {
                int tagCount = buffer.readVarInt();
                tags = new ArrayList<>();
                for (int i = 0; i < tagCount; i++) {
                    tags.add(buffer.readUtf());
                }
            }
            List<CopyOp> copies = null;
            if (buffer.readBoolean()) {
                int copyCount = buffer.readVarInt();
                copies = new ArrayList<>();
                for (int i = 0; i < copyCount; i++) {
                    copies.add(new CopyOp(buffer.readUtf(), buffer.readUtf(), buffer.readUtf()));
                }
            }
            return new NBTCopyIngredient(ingredient, mode, tags, copies);
        }

        @Override
        public NBTCopyIngredient read(JsonObject json) {
            if (!json.has("ingredient")) {
                throw new JsonParseException("NBTCopyIngredient must have 'ingredient' field");
            }

            Mode mode = Mode.REPLACE;
            if (json.has("mode")) {
                String modeStr = GsonHelper.getAsString(json, "mode");
                try {
                    mode = Mode.valueOf(modeStr);
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Invalid mode: " + modeStr);
                }
            }

            List<String> tags = null;
            if (json.has("tags")) {
                JsonElement tagsElement = json.get("tags");
                if (tagsElement.isJsonArray()) {
                    tags = new ArrayList<>();
                    for (JsonElement element : tagsElement.getAsJsonArray()) {
                        tags.add(element.getAsString());
                    }
                } else {
                    tags = Arrays.asList(GsonHelper.getAsString(json, "tags"));
                }
            }

            Ingredient ingredient;
            if (json.has("remainder_override")) {
                ingredient = RemainderOverrideIngredient.Serializer.INSTANCE.read(json).toVanilla();
            } else {
                ingredient = Ingredient.fromJson(json.get("ingredient"));
            }

            List<CopyOp> copies = null;
            if (json.has("copies")) {
                JsonElement copiesElement = json.get("copies");
                if (!copiesElement.isJsonArray()) {
                    throw new JsonParseException("nbt_copy 'copies' must be an array");
                }
                copies = new ArrayList<>();
                for (JsonElement element : copiesElement.getAsJsonArray()) {
                    JsonObject entry = GsonHelper.convertToJsonObject(element, "copies");
                    String op = GsonHelper.getAsString(entry, "op", CopyOp.OP_REPLACE);
                    if (!CopyOp.OP_REPLACE.equals(op) && !CopyOp.OP_MERGE.equals(op)) {
                        throw new JsonParseException("Invalid nbt_copy op: " + op);
                    }
                    copies.add(new CopyOp(op, GsonHelper.getAsString(entry, "source"),
                            GsonHelper.getAsString(entry, "target")));
                }
            }

            return new NBTCopyIngredient(ingredient, mode, tags, copies);
        }

        @Override
        public void write(FriendlyByteBuf buffer, NBTCopyIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeVarInt(ingredient.mode.ordinal());
            buffer.writeBoolean(ingredient.tags != null);
            if (ingredient.tags != null) {
                buffer.writeVarInt(ingredient.tags.size());
                for (String tag : ingredient.tags) {
                    buffer.writeUtf(tag);
                }
            }
            buffer.writeBoolean(ingredient.copies != null && !ingredient.copies.isEmpty());
            if (ingredient.copies != null && !ingredient.copies.isEmpty()) {
                buffer.writeVarInt(ingredient.copies.size());
                for (CopyOp copy : ingredient.copies) {
                    buffer.writeUtf(copy.op());
                    buffer.writeUtf(copy.source());
                    buffer.writeUtf(copy.target());
                }
            }
        }

        @Override
        public void write(JsonObject json, NBTCopyIngredient ingredient) {
            json.add("ingredient", ingredient.ingredient.toJson());
            json.addProperty("mode", ingredient.mode.name());
            if (ingredient.tags != null && !ingredient.tags.isEmpty()) {
                if (ingredient.tags.size() == 1) {
                    json.addProperty("tags", ingredient.tags.get(0));
                } else {
                    JsonArray tagsArray = new JsonArray();
                    for (String tag : ingredient.tags) {
                        tagsArray.add(tag);
                    }
                    json.add("tags", tagsArray);
                }
            }
            if (ingredient.copies != null && !ingredient.copies.isEmpty()) {
                JsonArray copiesArray = new JsonArray();
                for (CopyOp copy : ingredient.copies) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("op", copy.op());
                    entry.addProperty("source", copy.source());
                    entry.addProperty("target", copy.target());
                    copiesArray.add(entry);
                }
                json.add("copies", copiesArray);
            }
        }
    }
}

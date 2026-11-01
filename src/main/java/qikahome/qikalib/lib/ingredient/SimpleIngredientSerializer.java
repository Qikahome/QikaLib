package qikahome.qikalib.lib.ingredient;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 把 QikaLib 各原料的 {@link MapCodec} 适配成 Fabric 的 {@link CustomIngredientSerializer}。
 * <p>
 * 对应 Forge 的 {@code IIngredientSerializer}（{@code MapCodec} + 可选 {@code StreamCodec}，
 * 缺省时用 {@code ByteBufCodecs.fromCodecWithRegistries} 走 codec 网络同步）。
 */
public class SimpleIngredientSerializer<T extends CustomIngredient> implements CustomIngredientSerializer<T> {
    private final ResourceLocation id;
    private final MapCodec<T> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> packetCodec;

    public SimpleIngredientSerializer(ResourceLocation id, MapCodec<T> codec) {
        this(id, codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
    }

    public SimpleIngredientSerializer(ResourceLocation id, MapCodec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> packetCodec) {
        this.id = id;
        this.codec = codec;
        this.packetCodec = packetCodec;
    }

    @Override
    public ResourceLocation getIdentifier() {
        return id;
    }

    @Override
    public MapCodec<T> getCodec(boolean allowEmpty) {
        return codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> getPacketCodec() {
        return packetCodec;
    }
}

package qikahome.qikalib.lib.ingredient;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * 把各原料的 {@link MapCodec} 适配成 Fabric 26.x 的 {@link CustomIngredientSerializer}。
 *
 * <p>26.x 的接口只有三个方法：{@code getIdentifier()} / {@code getCodec()} / {@code getStreamCodec()}；
 * 未提供网络 codec 时用 {@code ByteBufCodecs.fromCodecWithRegistries} 走 codec 同步。
 */
public class SimpleIngredientSerializer<T extends CustomIngredient> implements CustomIngredientSerializer<T> {
    private final Identifier id;
    private final MapCodec<T> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> packetCodec;

    public SimpleIngredientSerializer(Identifier id, MapCodec<T> codec) {
        this(id, codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
    }

    public SimpleIngredientSerializer(Identifier id, MapCodec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> packetCodec) {
        this.id = id;
        this.codec = codec;
        this.packetCodec = packetCodec;
    }

    @Override
    public Identifier getIdentifier() {
        return id;
    }

    @Override
    public MapCodec<T> getCodec() {
        return codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> getStreamCodec() {
        return packetCodec;
    }
}

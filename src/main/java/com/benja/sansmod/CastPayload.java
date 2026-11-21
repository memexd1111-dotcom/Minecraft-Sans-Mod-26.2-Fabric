package com.benja.sansmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "lanzar la habilidad número N". */
public record CastPayload(int ability) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CastPayload> TYPE =
            new CustomPacketPayload.Type<>(SansMod.id("cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CastPayload::ability, CastPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

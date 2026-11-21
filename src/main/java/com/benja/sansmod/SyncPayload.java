package com.benja.sansmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: cooldowns, energía de esquive y estados, para el HUD. */
public record SyncPayload(int[] remaining, int[] total, int fatigue, int maxFatigue,
                          boolean dodgeOn, boolean karmaOn, int serious, int badTime)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncPayload> TYPE =
            new CustomPacketPayload.Type<>(SansMod.id("sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
            StreamCodec.ofMember(SyncPayload::write, SyncPayload::read);

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(remaining.length);
        for (int i = 0; i < remaining.length; i++) {
            buf.writeVarInt(remaining[i]);
            buf.writeVarInt(total[i]);
        }
        buf.writeVarInt(fatigue);
        buf.writeVarInt(maxFatigue);
        buf.writeBoolean(dodgeOn);
        buf.writeBoolean(karmaOn);
        buf.writeVarInt(serious);
        buf.writeVarInt(badTime);
    }

    public static SyncPayload read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] remaining = new int[n];
        int[] total = new int[n];
        for (int i = 0; i < n; i++) {
            remaining[i] = buf.readVarInt();
            total[i] = buf.readVarInt();
        }
        int fatigue = buf.readVarInt();
        int maxFatigue = buf.readVarInt();
        boolean dodgeOn = buf.readBoolean();
        boolean karmaOn = buf.readBoolean();
        int serious = buf.readVarInt();
        int badTime = buf.readVarInt();
        return new SyncPayload(remaining, total, fatigue, maxFatigue, dodgeOn, karmaOn, serious, badTime);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

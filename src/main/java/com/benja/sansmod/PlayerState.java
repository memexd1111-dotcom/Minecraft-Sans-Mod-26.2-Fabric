package com.benja.sansmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Estado de cada jugador en el servidor (se pierde al cerrar el mundo). */
public final class PlayerState {
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    public final long[] cooldownEnd = new long[Ability.ALL.length];
    public final int[] cooldownTotal = new int[Ability.ALL.length];
    public int fatigue = 0;
    public long lastRecoverTick = 0;
    public long lastDodgeTick = -1000;
    public boolean dodgeOn = true;
    public boolean karmaOn = false;
    public long seriousUntil = 0;
    public long badTimeUntil = 0;

    public static PlayerState of(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
    }

    public static void clear() {
        STATES.clear();
    }
}

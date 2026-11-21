package com.benja.sansmod.client;

import com.benja.sansmod.Ability;
import com.benja.sansmod.SyncPayload;

/** Lo que el cliente sabe de sus habilidades (llega por SyncPayload). */
public final class ClientState {
    public static int selected = Ability.BONE.ordinal();
    public static final int[] remaining = new int[Ability.ALL.length];
    public static final int[] total = new int[Ability.ALL.length];
    public static int fatigue = 0;
    public static int maxFatigue = 8;
    public static boolean dodgeOn = true;
    public static boolean karmaOn = false;
    public static int serious = 0;
    public static int badTime = 0;

    private ClientState() {}

    public static void apply(SyncPayload p) {
        for (int i = 0; i < remaining.length && i < p.remaining().length; i++) {
            remaining[i] = p.remaining()[i];
            total[i] = p.total()[i];
        }
        fatigue = p.fatigue();
        maxFatigue = Math.max(1, p.maxFatigue());
        dodgeOn = p.dodgeOn();
        karmaOn = p.karmaOn();
        serious = p.serious();
        badTime = p.badTime();
    }

    /** Cada tick del cliente: los contadores bajan solos entre sincronizaciones. */
    public static void tick() {
        for (int i = 0; i < remaining.length; i++) {
            if (remaining[i] > 0) remaining[i]--;
        }
        if (serious > 0) serious--;
        if (badTime > 0) badTime--;
    }

    public static void cycle(int delta) {
        int n = Ability.ALL.length;
        selected = Math.floorMod(selected + delta, n);
    }
}

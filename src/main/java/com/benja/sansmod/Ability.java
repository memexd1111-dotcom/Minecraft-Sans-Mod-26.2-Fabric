package com.benja.sansmod;

/** Lista de habilidades. El orden es el que se ve en el menú y viaja por red como número. */
public enum Ability {
    DODGE("dodge", true),
    KARMA("karma", true),
    BONE("bone", false),
    BONE_WALL("bone_wall", false),
    BLASTER("blaster", false),
    BLASTER_RAIN("blaster_rain", false),
    TELEKINESIS("telekinesis", false),
    TELEPORT("teleport", false),
    EYE("eye", false),
    BAD_TIME("bad_time", false);

    public static final Ability[] ALL = values();

    public final String id;
    /** true = se activa/desactiva (esquive, karma); false = se lanza. */
    public final boolean toggle;

    Ability(String id, boolean toggle) {
        this.id = id;
        this.toggle = toggle;
    }

    /** Cooldown fijo, leído de la configuración (cooldown.<id>, en segundos). */
    public int cooldownTicks() {
        return (int) Math.round(SansConfig.d("cooldown." + id) * 20.0);
    }
}

package com.benja.sansmod;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/** Configuración en config/sansmod.properties (se crea sola con los valores por defecto). */
public final class SansConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Properties PROPS = new Properties();

    private static final String HEADER = """
            # Sans Abilities - configuración
            # Tiempos en segundos salvo que diga "ticks" (20 ticks = 1 segundo).
            # Borra una línea para volver a su valor por defecto.
            """;

    static {
        def("require_item", "true");          // si es false, cualquiera puede usar las habilidades sin el amuleto
        def("exhaustion_per_cast", "1.5");    // hambre que cuesta lanzar una habilidad (balance de supervivencia)

        def("cooldown.dodge", "1");
        def("cooldown.karma", "1");
        def("cooldown.bone", "3");
        def("cooldown.bone_wall", "10");
        def("cooldown.blaster", "15");
        def("cooldown.blaster_rain", "40");
        def("cooldown.telekinesis", "12");
        def("cooldown.teleport", "6");
        def("cooldown.eye", "45");
        def("cooldown.bad_time", "60");

        def("bone.count", "3");
        def("bone.damage", "4.0");
        def("bone.range", "32");
        def("bone.speed", "2.0");
        def("bone.spread_degrees", "8");

        def("bone_wall.damage", "6.0");
        def("bone_wall.width", "7");
        def("bone_wall.distance", "3");
        def("bone_wall.rise_ticks", "8");

        def("blaster.damage", "14.0");
        def("blaster.range", "48");
        def("blaster.charge_ticks", "25");
        def("blaster.beam_radius", "1.0");

        def("blaster_rain.count", "6");
        def("blaster_rain.damage", "9.0");
        def("blaster_rain.radius", "6");
        def("blaster_rain.range", "40");

        def("telekinesis.range", "24");
        def("telekinesis.lift_ticks", "25");
        def("telekinesis.slam_damage", "8.0");

        def("teleport.distance", "12");

        def("dodge.max_fatigue", "8");        // esquives seguidos antes de cansarte
        def("dodge.recovery_seconds", "3");   // segundos para recuperar 1 de energía
        def("dodge.window_ticks", "8");       // ticks de "invulnerabilidad" tras esquivar

        def("karma.wither_seconds", "5");

        def("eye.duration_seconds", "15");
        def("eye.damage_multiplier", "1.5");

        def("bad_time.radius", "8");
        def("bad_time.duration_seconds", "8");
        def("bad_time.damage_taken_multiplier", "1.5");
    }

    private SansConfig() {}

    private static void def(String key, String value) {
        DEFAULTS.put(key, value);
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("sansmod.properties");
        try {
            boolean existed = Files.exists(file);
            if (existed) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    PROPS.load(reader);
                }
            }
            StringBuilder missing = new StringBuilder();
            for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
                if (!PROPS.containsKey(e.getKey())) {
                    missing.append(e.getKey()).append('=').append(e.getValue()).append('\n');
                    PROPS.setProperty(e.getKey(), e.getValue());
                }
            }
            if (!existed) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, HEADER + "\n" + missing);
            } else if (missing.length() > 0) {
                Files.writeString(file, "\n" + missing, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            LOGGER.warn("Sans Abilities: no se pudo leer/escribir la configuración", e);
        }
    }

    public static String s(String key) {
        return PROPS.getProperty(key, DEFAULTS.getOrDefault(key, "0"));
    }

    public static double d(String key) {
        try {
            return Double.parseDouble(s(key).trim());
        } catch (NumberFormatException e) {
            return Double.parseDouble(DEFAULTS.getOrDefault(key, "0"));
        }
    }

    public static int i(String key) {
        return (int) Math.round(d(key));
    }

    public static boolean b(String key) {
        return Boolean.parseBoolean(s(key).trim());
    }
}

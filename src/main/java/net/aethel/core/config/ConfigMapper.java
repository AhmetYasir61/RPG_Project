package net.aethel.core.config;

import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * @ConfigValue ile isaretli alanlari YAML'dan doldurur. Reload'da ayni nesne
 * uzerinde yeniden calisir; modul referansi degismez, yalnizca degerler tazelenir.
 */
public final class ConfigMapper {

    private ConfigMapper() {}

    /** holder icindeki tum @ConfigValue alanlarini section'dan okur. */
    public static void apply(Object holder, ConfigurationSection section) {
        for (Field field : holder.getClass().getDeclaredFields()) {
            ConfigValue ann = field.getAnnotation(ConfigValue.class);
            if (ann == null) continue;

            String path = ann.value().isEmpty() ? kebab(field.getName()) : ann.value();
            if (!section.contains(path)) continue;

            field.setAccessible(true);
            try {
                field.set(holder, read(field.getType(), section, path));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Config alani yazilamadi: " + path, e);
            }
        }
    }

    /** Alanlarin mevcut degerlerini varsayilan olarak dosyaya yazar (ilk olusturma). */
    public static void writeDefaults(Object holder, ConfigurationSection section) {
        for (Field field : holder.getClass().getDeclaredFields()) {
            ConfigValue ann = field.getAnnotation(ConfigValue.class);
            if (ann == null) continue;
            String path = ann.value().isEmpty() ? kebab(field.getName()) : ann.value();
            if (section.contains(path)) continue;
            field.setAccessible(true);
            try {
                section.set(path, field.get(holder));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Config varsayilani okunamadi: " + path, e);
            }
        }
    }

    /** Holder'in bagli oldugu tum YAML yollari; cakisma tespiti icin. */
    public static java.util.List<String> paths(Object holder) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (Field field : holder.getClass().getDeclaredFields()) {
            ConfigValue ann = field.getAnnotation(ConfigValue.class);
            if (ann == null) continue;
            paths.add(ann.value().isEmpty() ? kebab(field.getName()) : ann.value());
        }
        return paths;
    }

    private static Object read(Class<?> type, ConfigurationSection section, String path) {
        if (type == int.class || type == Integer.class) return section.getInt(path);
        if (type == long.class || type == Long.class) return section.getLong(path);
        if (type == double.class || type == Double.class) return section.getDouble(path);
        if (type == float.class || type == Float.class) return (float) section.getDouble(path);
        if (type == boolean.class || type == Boolean.class) return section.getBoolean(path);
        if (type == String.class) return section.getString(path);
        if (List.class.isAssignableFrom(type)) return section.getList(path);
        if (type.isEnum()) return enumValue(type, section.getString(path));
        throw new IllegalArgumentException("Desteklenmeyen config tipi: " + type.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String raw) {
        return Enum.valueOf((Class<? extends Enum>) type, raw.toUpperCase(Locale.ROOT));
    }

    /** startingBalance -> starting-balance */
    private static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}

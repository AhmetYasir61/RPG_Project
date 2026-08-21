package net.aethel.core.util;

import java.util.List;
import java.util.Map;

/**
 * YAML map listelerinden guvenli okuma. Bukkit'in getMapList() joker tipli Map
 * dondurdugu icin getOrDefault dogrudan kullanilamaz; bu yardimcilar onu kapatir.
 */
public final class Yamls {

    private Yamls() {}

    public static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public static double number(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public static int integer(Map<?, ?> map, String key, int fallback) {
        return (int) number(map, key, fallback);
    }

    public static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    /** Liste degilse bos liste doner; tanim hatasi uretimi durdurmasin. */
    public static List<String> stringList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }
}

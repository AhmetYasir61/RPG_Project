package net.aethel.core.modules.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Panelin gunluk penceresi icin halka tampon. Sunucu gunlugunu diskten okumak
 * yerine cekirdek logger'ina bir handler takilir: dosya bicimi, donme (rotation)
 * ve kilitlenme sorunlariyla ugrasilmaz, HTTP thread'i diske hic dokunmaz.
 *
 * Tampon sabit boyutludur; en eski satir dusurulur. Boylece uzun calisan bir
 * sunucuda bellek sizintisi olusmaz.
 */
final class LogBuffer extends Handler {

    private static final int CAPACITY = 200;

    private final Deque<Map<String, Object>> lines = new ArrayDeque<>(CAPACITY);
    private final Logger source;

    LogBuffer(Logger source) {
        this.source = source;
        source.addHandler(this);
    }

    @Override
    public void publish(LogRecord record) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("t", java.time.Instant.ofEpochMilli(record.getMillis()).toString());
        line.put("lvl", record.getLevel().getName());
        line.put("msg", String.valueOf(record.getMessage()));
        synchronized (lines) {
            if (lines.size() >= CAPACITY) lines.removeFirst();
            lines.addLast(line);
        }
    }

    /** En yeni satirlar basta. */
    List<Map<String, Object>> tail(int limit) {
        synchronized (lines) {
            List<Map<String, Object>> copy = new ArrayList<>(lines);
            java.util.Collections.reverse(copy);
            return copy.size() <= limit ? copy : copy.subList(0, limit);
        }
    }

    @Override
    public void flush() {}

    /** Modul kapanirken handler cekirdek logger'indan sokulur. */
    @Override
    public void close() {
        source.removeHandler(this);
        synchronized (lines) {
            lines.clear();
        }
    }
}

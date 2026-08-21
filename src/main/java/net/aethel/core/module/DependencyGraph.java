package net.aethel.core.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modul bagimliliklarini topolojik siralar. Cevrim tespit edilirse boot durur;
 * sessizce yanlis sirada yuklemek, tespiti saatler suren hatalar uretir.
 */
final class DependencyGraph {

    private DependencyGraph() {}

    /** Kayit sirasini koruyarak (LinkedHashMap) deterministik bir yukleme sirasi uretir. */
    static List<ModuleContainer> sort(Map<String, ModuleContainer> modules) {
        Map<String, ModuleContainer> input = new LinkedHashMap<>(modules);
        List<ModuleContainer> sorted = new ArrayList<>(input.size());
        Set<String> done = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String id : input.keySet()) {
            visit(id, input, done, visiting, new ArrayList<>(), sorted);
        }
        return sorted;
    }

    private static void visit(String id,
                              Map<String, ModuleContainer> all,
                              Set<String> done,
                              Set<String> visiting,
                              List<String> path,
                              List<ModuleContainer> out) {
        if (done.contains(id)) return;
        ModuleContainer container = all.get(id);
        if (container == null) return;   // bilinmeyen bagimlilik: enable asamasinda raporlanir

        if (!visiting.add(id)) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(id);
            throw new IllegalStateException("Modul bagimlilik cevrimi: " + String.join(" -> ", cycle));
        }
        path.add(id);

        for (String dep : container.info().depends()) {
            visit(dep, all, done, visiting, path, out);
        }
        for (String soft : container.info().softDepends()) {
            visit(soft, all, done, visiting, path, out);
        }

        path.remove(path.size() - 1);
        visiting.remove(id);
        done.add(id);
        out.add(container);
    }

    /** Bir modulun (dogrudan/dolayli) bagimlilarini bulur; ters sirada kapatmak icin. */
    static List<ModuleContainer> dependentsOf(String id, Map<String, ModuleContainer> all) {
        List<ModuleContainer> result = new ArrayList<>();
        for (ModuleContainer c : all.values()) {
            if (c.id().equals(id)) continue;
            if (dependsOn(c, id, all, new HashSet<>())) result.add(c);
        }
        return result;
    }

    private static boolean dependsOn(ModuleContainer c, String target,
                                     Map<String, ModuleContainer> all, Set<String> seen) {
        if (!seen.add(c.id())) return false;
        for (String dep : c.info().depends()) {
            if (dep.equals(target)) return true;
            ModuleContainer next = all.get(dep);
            if (next != null && dependsOn(next, target, all, seen)) return true;
        }
        return false;
    }
}

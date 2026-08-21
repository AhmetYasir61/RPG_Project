package net.aethel.core.module;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bagimlilik siralamasinin testleri. Sunucuda "dialog -> npc -> dialog" cevrimi
 * boot'u durdurmustu; yumusak cevrimin artik kabul edildigini burada sabitliyoruz.
 */
class DependencyGraphTest {

    @Test
    void yumusakCevrimBootuDurdurmaz() {
        Map<String, ModuleContainer> modules = modules(
                module("dialog", new String[0], new String[] {"npc"}),
                module("npc", new String[0], new String[] {"dialog"}));

        List<ModuleContainer> sorted = DependencyGraph.sort(modules);

        assertEquals(2, sorted.size(), "iki modul de siralanmali");
        assertTrue(sorted.stream().anyMatch(c -> c.id().equals("dialog")));
        assertTrue(sorted.stream().anyMatch(c -> c.id().equals("npc")));
    }

    @Test
    void zorunluCevrimHataFirlatir() {
        Map<String, ModuleContainer> modules = modules(
                module("a", new String[] {"b"}, new String[0]),
                module("b", new String[] {"a"}, new String[0]));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DependencyGraph.sort(modules));
        assertTrue(error.getMessage().contains("cevrim"));
    }

    @Test
    void zorunluBagimlilikOnceGelir() {
        Map<String, ModuleContainer> modules = modules(
                module("economy", new String[] {"profile"}, new String[0]),
                module("profile", new String[0], new String[0]));

        List<ModuleContainer> sorted = DependencyGraph.sort(modules);
        List<String> order = sorted.stream().map(ModuleContainer::id).toList();

        assertTrue(order.indexOf("profile") < order.indexOf("economy"),
                "profile, economy'den once yuklenmeli");
    }

    @Test
    void bilinmeyenBagimlilikSiralamayiBozmaz() {
        Map<String, ModuleContainer> modules = modules(
                module("quest", new String[] {"olmayan-modul"}, new String[0]));

        List<ModuleContainer> sorted = DependencyGraph.sort(modules);
        assertEquals(1, sorted.size(), "bilinmeyen bagimlilik atlanmali, siralama surmeli");
    }

    private Map<String, ModuleContainer> modules(ModuleContainer... containers) {
        Map<String, ModuleContainer> map = new LinkedHashMap<>();
        for (ModuleContainer container : containers) map.put(container.id(), container);
        return map;
    }

    private ModuleContainer module(String id, String[] depends, String[] softDepends) {
        return new ModuleContainer(new Module() {}, info(id, depends, softDepends), true);
    }

    /** Test icin elle kurulmus @ModuleInfo ornegi. */
    private ModuleInfo info(String id, String[] depends, String[] softDepends) {
        return new ModuleInfo() {
            @Override public Class<? extends Annotation> annotationType() { return ModuleInfo.class; }
            @Override public String id() { return id; }
            @Override public String name() { return id; }
            @Override public String[] depends() { return depends; }
            @Override public String[] softDepends() { return softDepends; }
            @Override public boolean hotDisable() { return true; }
        };
    }
}

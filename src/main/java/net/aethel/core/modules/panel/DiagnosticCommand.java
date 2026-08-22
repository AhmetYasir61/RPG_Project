package net.aethel.core.modules.panel;

import net.aethel.core.api.DialogService;
import net.aethel.core.api.HologramService;
import net.aethel.core.api.ItemService;
import net.aethel.core.api.LootService;
import net.aethel.core.api.MenuService;
import net.aethel.core.api.MobService;
import net.aethel.core.api.NpcService;
import net.aethel.core.api.QuestService;
import net.aethel.core.api.RegionService;
import net.aethel.core.api.SkillService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Tek bakista saglik kontrolu: hangi servis ayakta, kac tanim yuklu.
 *
 * Bu komut bir SORUNUN nerede oldugunu daraltmak icin var. "Item gorunmuyor"
 * sikayetinin cevabi cogu zaman "content modulu hic acilmamis" ya da "0 tanim
 * yuklenmis" oluyor; onu gormek icin sunucu gunlugunu bastan okumak gerekiyordu.
 *
 * Sayim yapan her cagri try icinde: bir modulun patlamasi, digerlerinin
 * durumunu gormeni engellememelidir.
 */
@Command(value = "aethel", aliases = {"core-durum"}, permission = "aethel.admin.panel",
        descriptionKey = "diagnostic.command-description")
public final class DiagnosticCommand {

    /** Bir satir: servis adi, ayakta mi, sayim. */
    private record Row(String name, boolean up, String detail) {}

    private final CoreContext ctx;

    public DiagnosticCommand(CoreContext ctx) {
        this.ctx = ctx;
    }

    @Command("")
    public void status(CommandSender sender) {
        List<Row> rows = new ArrayList<>();
        rows.add(count("item", ItemService.class, service -> service.all().size()));
        rows.add(count("skill", SkillService.class, service -> service.all().size()));
        rows.add(count("mob", MobService.class, service -> service.all().size()));
        rows.add(count("npc", NpcService.class, service -> service.all().size()));
        rows.add(count("hologram", HologramService.class, service -> service.all().size()));
        rows.add(count("region", RegionService.class, service -> service.regions().size()));
        rows.add(count("zorluk", RegionService.class, service -> service.difficulties().size()));
        rows.add(count("loot-sandik", LootService.class, service -> service.chests().size()));
        rows.add(count("gorev", QuestService.class, service -> service.quests().size()));
        rows.add(present("diyalog", DialogService.class));
        rows.add(present("menu", MenuService.class));

        long up = rows.stream().filter(Row::up).count();
        ctx.lang().send(sender, "diagnostic.header",
                LangService.of("up", up), LangService.of("total", rows.size()));

        rows.forEach(row -> ctx.lang().send(sender,
                row.up() ? "diagnostic.entry-up" : "diagnostic.entry-down",
                LangService.of("name", row.name()),
                LangService.of("detail", row.detail())));

        ctx.lang().send(sender, "diagnostic.features",
                LangService.of("on", ctx.features().snapshot().values().stream()
                        .filter(Boolean::booleanValue).count()),
                LangService.of("total", ctx.features().snapshot().size()));
    }

    /**
     * Servisi bulur ve sayar. Servis yoksa modul kapali ya da acilamamis demektir;
     * sayim patlarsa modul ayakta ama verisi bozuk demektir. Ikisi ayri satirda
     * gorunmeli, cunku cozumleri farkli.
     */
    private <T> Row count(String name, Class<T> type, java.util.function.ToIntFunction<T> counter) {
        var service = ctx.services().optional(type);
        if (service.isEmpty()) return new Row(name, false, "kapali");
        try {
            return new Row(name, true, counter.applyAsInt(service.get()) + " tanim");
        } catch (RuntimeException e) {
            return new Row(name, false, "hata: " + e.getClass().getSimpleName());
        }
    }

    private Row present(String name, Class<?> type) {
        return ctx.services().optional(type).isPresent()
                ? new Row(name, true, "ayakta") : new Row(name, false, "kapali");
    }
}

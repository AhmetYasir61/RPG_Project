package net.aethel.core.modules.socket;

import net.aethel.core.api.CustomItem;
import net.aethel.core.api.ItemService;
import net.aethel.core.api.SocketService;
import net.aethel.core.api.SocketStone;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soket ve tas mekanigi. Silaha takilan tas stat ekler, dokunun gri bolgesini
 * kendi rengine boyar ve oyuncu oldurdukce asama yukseltir; son asamada silah
 * baska bir item'a evrilir (bakir kilic -> alev kilici).
 */
@ModuleInfo(id = "socket", name = "Soket", depends = {"content"}, softDepends = {"menu"})
public final class SocketModule implements Module, SocketService {

    private final Map<String, SocketStone> stones = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();

    private StoneLoader loader;
    private SocketData data;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.loader = new StoneLoader(ctx.logger());
        this.data = new SocketData(ctx.plugin());
        ctx.services().register(SocketService.class, this, "socket");

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("socket.evolution", true,
                "Oldurdukce asama yukselten soket evrimi");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        reload();
        ctx.listener("socket.evolution", new EvolutionListener(ctx, this, data));
        ctx.listener(new StoneEffectListener(ctx, this, data));
        ctx.commands().register("socket", new SocketCommand(ctx, this));
        ctx.commands().suggest("stone", stones::keySet);
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    @Override
    public int reload() {
        stones.clear();
        stones.putAll(loader.loadAll(new java.io.File(ctx.config().dataFolder(), "contents")));
        ctx.logger().info("Yuklenen soket tasi: " + stones.size());
        return stones.size();
    }

    @Override
    public Optional<SocketStone> stone(String fullId) {
        return Optional.ofNullable(stones.get(fullId));
    }

    @Override
    public Collection<SocketStone> stones() {
        return List.copyOf(stones.values());
    }

    @Override
    public Optional<String> socketedStone(ItemStack stack) {
        return data.stone(stack);
    }

    @Override
    public int kills(ItemStack stack) {
        return data.kills(stack);
    }

    @Override
    public int stage(ItemStack stack) {
        return definition(stack)
                .filter(CustomItem::socketable)
                .map(item -> item.socketing().stageFor(data.kills(stack)))
                .orElse(0);
    }

    /**
     * Tasi takar. Bir yuva ve tek tas destegi var: ikinci bir tas takmak
     * gorunumu belirsiz birakirdi (hangi rengin kazandigi kural gerektirir).
     */
    @Override
    public boolean socket(ItemStack stack, String stoneFullId) {
        Optional<CustomItem> item = definition(stack).filter(CustomItem::socketable);
        if (item.isEmpty() || !stones.containsKey(stoneFullId)) return false;
        if (data.stone(stack).isPresent()) return false;

        data.stone(stack, stoneFullId);
        refresh(stack);
        return true;
    }

    /** Tasi soker. Ilerleme TASA aittir; sokulunce sayac sifirlanir. */
    @Override
    public boolean unsocket(ItemStack stack) {
        if (data.stone(stack).isEmpty()) return false;
        data.stone(stack, null);
        refresh(stack);
        return true;
    }

    /**
     * Item'in gorunumunu ve statlarini mevcut tas/asamaya gore yeniden kurar.
     *
     * item_model her asamada degisir: pakette o asama icin ONCEDEN boyanmis bir
     * doku hazir bekler. Parilti yalnizca SON asamada acilir; her asamada yanan
     * bir parilti, asamalar arasindaki farki gorunmez kilardi.
     */
    void refresh(ItemStack stack) {
        Optional<CustomItem> found = definition(stack).filter(CustomItem::socketable);
        if (found.isEmpty()) return;
        CustomItem item = found.get();

        Optional<String> stoneId = data.stone(stack);
        int stage = item.socketing().stageFor(data.kills(stack));
        Optional<SocketStone> stone = stoneId.flatMap(this::stone);

        stack.editMeta(meta -> {
            String modelStone = stone.map(SocketStone::id).orElse(null);
            meta.setItemModel(NamespacedKey.fromString(item.modelKey(modelStone, stage)));
            meta.setEnchantmentGlintOverride(
                    stone.isPresent() && stage >= item.socketing().lastStage());
            meta.lore(buildLore(item, stone.orElse(null), stage));

            // Nitelikler bastan kurulur: itemin kendi degerleri + tasin
            // asamaya gore olceklenmis katkisi. Temizlemeden eklemek, her
            // yenilemede silahin kalici olarak guclenmesi demek olurdu.
            meta.setAttributeModifiers(null);
            applyAttributes(meta, item.attributes(), null);
            stone.ifPresent(value ->
                    applyAttributes(meta, scaled(value.attributes(), item, stage), "socket"));
        });
    }

    /**
     * Tasin katkisi asamaya gore olceklenir: ilk asamada yarim, son asamada tam.
     * Ilerlemenin GORUNUR degil GERCEK bir karsiligi olmali; yoksa asama
     * yukseltmek yalnizca renk degistirmekten ibaret kalirdi.
     *
     * Sure cinsinden degerler (burn-seconds gibi) nitelik degildir, vurusta
     * uygulanir; burada atlanirlar.
     */
    private java.util.Map<String, Double> scaled(java.util.Map<String, Double> source,
                                                 CustomItem item, int stage) {
        int last = Math.max(1, item.socketing().lastStage());
        double factor = 0.5D + 0.5D * (Math.min(stage, last) / (double) last);

        java.util.Map<String, Double> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key.endsWith("-seconds")) return;
            result.put(key, value * factor);
        });
        return result;
    }

    /** Icerik modulunun nitelik katmanini kullanir; eslesme tek yerde durur. */
    private void applyAttributes(org.bukkit.inventory.meta.ItemMeta meta,
                                 java.util.Map<String, Double> attributes, String suffix) {
        net.aethel.core.modules.content.ItemAttributeBridge.apply(
                ctx.plugin(), meta, attributes, suffix);
    }

    /** Taban lore + soket satiri + ilerleme. Her yenilemede bastan kurulur. */
    private List<Component> buildLore(CustomItem item, SocketStone stone, int stage) {
        List<Component> lore = new ArrayList<>();
        item.lore().forEach(line -> lore.add(
                mini.deserialize(line).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());

        if (stone == null) {
            lore.add(render("socket.lore-empty", item.socketing().slots()));
            return lore;
        }
        lore.add(mini.deserialize(ctx.lang().raw("socket.lore-filled"),
                        Placeholder.parsed("stone", stone.displayName()))
                .decoration(TextDecoration.ITALIC, false));

        stone.attributes().forEach((key, value) -> lore.add(mini.deserialize(
                        ctx.lang().raw("socket.lore-attribute"),
                        Placeholder.unparsed("stat", key),
                        Placeholder.unparsed("value", format(value)))
                .decoration(TextDecoration.ITALIC, false)));

        lore.add(Component.empty());
        lore.add(mini.deserialize(ctx.lang().raw("socket.lore-stage"),
                        Placeholder.unparsed("stage", String.valueOf(stage)),
                        Placeholder.unparsed("last", String.valueOf(item.socketing().lastStage())),
                        Placeholder.unparsed("bar", bar(stage, item.socketing().lastStage())))
                .decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    /** Asama gostergesi; oyuncu ilerlemeyi sayi okumadan gorebilmeli. */
    private String bar(int stage, int lastStage) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= Math.max(1, lastStage); i++) {
            builder.append(i <= stage ? "⬛" : "⬜");
        }
        return builder.toString();
    }

    private Component render(String key, int slots) {
        return mini.deserialize(ctx.lang().raw(key),
                        Placeholder.unparsed("slots", String.valueOf(slots)))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static String format(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    Optional<CustomItem> definition(ItemStack stack) {
        return ctx.services().optional(ItemService.class)
                .flatMap(items -> items.resolve(stack));
    }

    CoreContext context() {
        return ctx;
    }
}

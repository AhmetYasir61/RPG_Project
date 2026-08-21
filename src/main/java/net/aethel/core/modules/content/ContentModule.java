package net.aethel.core.modules.content;

import net.aethel.core.api.CustomItem;
import net.aethel.core.api.ItemService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.modules.content.pack.PackDelivery;
import net.aethel.core.modules.content.pack.PackGenerator;
import net.aethel.core.modules.content.pack.PackIndex;
import net.aethel.core.modules.content.pack.PackPaths;
import net.aethel.core.modules.content.pack.PackServer;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom icerik modulu: item tanimlari, kaynak paketi uretimi ve dagitimi.
 * ItemService'i saglar; pack uretimi tamamen sanal thread'de calisir.
 */
@ModuleInfo(id = "content", name = "Icerik")
public final class ContentModule implements Module, ItemService {

    private final ContentSettings settings = new ContentSettings();
    private final Map<String, CustomItem> items = new ConcurrentHashMap<>();

    private ItemLoader loader;
    private ItemFactory factory;
    private PackPaths paths;
    private PackGenerator generator;
    private PackServer server;
    private PackDelivery delivery;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/content.yml", 1, settings, ConfigMigration.NONE);

        this.loader = new ItemLoader(ctx.logger());
        this.factory = new ItemFactory(ctx.plugin());
        this.paths = new PackPaths(ctx.config().dataFolder());
        this.generator = new PackGenerator(paths, new PackIndex(paths.index()), ctx.logger());
        this.server = new PackServer(ctx.logger(), ctx.scheduler().ioExecutor());
        this.delivery = new PackDelivery(ctx);

        ctx.services().register(ItemService.class, this, "content");

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("content.pack-generation", true, "Kaynak paketi uretimi");
        features.declare("content.force-pack", true,
                "Paketi zorunlu gonderme (kabul etmeyen kicklenir)");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        reload();
        ctx.listener(delivery);

        if (settings.generate && settings.generateOnStart && ctx.feature("content.pack-generation")) {
            regenerate();
        } else {
            publishExisting();
        }
    }

    @Override
    public void onDisable(CoreContext ctx) {
        server.stop();
        items.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    /**
     * Uretimi sanal thread'de calistirir: tarama, JSON yazimi, zip ve SHA-1 birlikte
     * saniyeler surebilir ve ana thread'de yapilirsa sunucu donar.
     */
    public void regenerate() {
        ctx.scheduler().io(() -> {
            PackGenerator.Result result = generator.generate(items.values());
            if (result.sha1() == null) return;
            result.warnings().forEach(warning -> ctx.logger().warning("Pack: " + warning));
            ctx.scheduler().sync("content", () -> publish(result.sha1()));
        });
    }

    /** Onceki uretimden kalan zip ve hash varsa dogrudan sunar. */
    private void publishExisting() {
        try {
            if (!paths.hashFile().exists()) return;
            publish(Files.readString(paths.hashFile().toPath(), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            ctx.logger().warning("Mevcut pack hash'i okunamadi: " + e.getMessage());
        }
    }

    private void publish(String hash) {
        if (!settings.serve) return;
        if (!server.isRunning()
                && !server.start(settings.bind, settings.port, paths.generatedZip())) {
            return;
        }
        delivery.configure(publicUrl(), hash, settings.force && ctx.feature("content.force-pack"));
        ctx.plugin().getServer().getOnlinePlayers().forEach(delivery::send);
    }

    /**
     * Oyuncuya gonderilecek adres. bind adresi (0.0.0.0) BURADA KULLANILAMAZ:
     * 0.0.0.0 "tum arayuzlerde dinle" demektir, istemcinin baglanabilecegi bir
     * adres degildir. Oyuncu boyle bir paketi indiremez ve zorunlu pack yuzunden
     * sunucuya hic giremez.
     */
    private String publicUrl() {
        String host = settings.publicHost;

        if (host == null || host.isBlank()) {
            host = ctx.plugin().getServer().getIp();
        }
        if (host == null || host.isBlank() || host.equals("0.0.0.0")) {
            host = "127.0.0.1";
            ctx.logger().warning("resource-pack.public-host bos. Paket adresi "
                    + "127.0.0.1 olarak gonderiliyor; bu YALNIZCA ayni makinedeki "
                    + "oyuncular icin calisir. Uzaktan baglanan oyuncular icin "
                    + "sunucunun genel IP'sini ya da alan adini yaz.");
        }
        String url = "http://" + host + ":" + settings.port + "/generated.zip";
        ctx.logger().info("Oyunculara gonderilen paket adresi: " + url);
        return url;
    }

    @Override
    public int reload() {
        items.clear();
        items.putAll(loader.loadAll(paths.contents()));
        loader.validate(items, paths.contents())
                .forEach(problem -> ctx.logger().warning("Icerik: " + problem));
        ctx.logger().info("Yuklenen custom item: " + items.size());
        return items.size();
    }

    @Override
    public Optional<ItemStack> create(String fullId) {
        return create(fullId, 1);
    }

    @Override
    public Optional<ItemStack> create(String fullId, int amount) {
        return definition(fullId).map(item -> factory.create(item, amount));
    }

    @Override
    public Optional<CustomItem> resolve(ItemStack stack) {
        return factory.idOf(stack).map(items::get);
    }

    @Override
    public boolean isCustom(ItemStack stack) {
        return factory.idOf(stack).isPresent();
    }

    @Override
    public Optional<CustomItem> definition(String fullId) {
        return Optional.ofNullable(items.get(fullId));
    }

    @Override
    public Collection<CustomItem> all() {
        return java.util.List.copyOf(items.values());
    }

    /** Panelin pack durumunu gostermesi icin. */
    public PackDelivery delivery() {
        return delivery;
    }
}

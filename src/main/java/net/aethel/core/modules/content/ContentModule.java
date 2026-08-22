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

    /**
     * Ayni anda iki uretim calisamaz. Iki is ayni generated.zip dosyasina yazarsa
     * ortaya yarim bir zip cikar ve butun oyuncular paketi indiremeyip atilir.
     */
    private final java.util.concurrent.atomic.AtomicBoolean generating =
            new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        // Ayarlar cekirdek config.yml -> resource-pack.* bolumunden gelir.
        // Ayri bir modules/content.yml tutmak, kullanicinin config.yml'yi
        // duzenleyip hicbir sey degismedigini gormesine yol aciyordu.
        ctx.config().bind("config.yml", settings);

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
        ctx.commands().suggest("item", () -> items.keySet());
        generator.packFormat(settings.format);
        generator.stones(ctx.services().optional(net.aethel.core.api.SocketService.class)
                .map(net.aethel.core.api.SocketService::stones).orElse(java.util.List.of()));
        reload();
        ctx.listener(delivery);
        ctx.commands().register("pack", new PackCommand(ctx, this));

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
        regenerateAndPublish(result -> {});
    }

    /** Calisan bir uretimin sonucu. */
    public record RegenerateResult(boolean ok, int items, int files, String hash,
                                   boolean changed, java.util.List<String> warnings,
                                   long millis, String error) {}

    /**
     * SUNUCU ACIKKEN paketi bastan uretir ve cevrimici herkese yeniden gonderir.
     *
     * Sira onemlidir: once tanimlar DISKTEN yeniden okunur, sonra zip uretilir.
     * Yalnizca zip'i uretmek, YAML'de yapilan bir degisikligi pakete tasimaz --
     * bellekteki eski tanimlar yeniden paketlenir ve "yeniledim ama degismedi"
     * denir. Onceki panel dugmesi tam bunu yapiyordu.
     *
     * Sonuc callback'i ANA THREAD'de calisir; cagiran taraf oyuncuya dogrudan
     * mesaj yazabilir.
     */
    public void regenerateAndPublish(java.util.function.Consumer<RegenerateResult> callback) {
        if (!generating.compareAndSet(false, true)) {
            ctx.scheduler().sync("content", () -> callback.accept(new RegenerateResult(
                    false, 0, 0, null, false, java.util.List.of(), 0, "busy")));
            return;
        }
        String previous = readHash();

        ctx.scheduler().io(() -> {
            try {
                int count = reload();
                // Varyant uretimi icin taslar; soket modulu kapaliysa bos gecer
                // ve yalnizca taban dokular uretilir.
                generator.stones(ctx.services()
                        .optional(net.aethel.core.api.SocketService.class)
                        .map(net.aethel.core.api.SocketService::stones)
                        .orElse(java.util.List.of()));
                PackGenerator.Result result = generator.generate(items.values());
                result.warnings().forEach(warning -> ctx.logger().warning("Pack: " + warning));

                if (result.sha1() == null) {
                    finish(callback, new RegenerateResult(false, count, 0, null, false,
                            result.warnings(), result.millis(), "generate-failed"));
                    return;
                }
                boolean changed = !result.sha1().equals(previous);
                ctx.scheduler().sync("content", () -> {
                    publish(result.sha1());
                    generating.set(false);
                    callback.accept(new RegenerateResult(true, count, result.fileCount(),
                            result.sha1(), changed, result.warnings(), result.millis(), null));
                });
            } catch (RuntimeException e) {
                ctx.logger().log(java.util.logging.Level.SEVERE, "Pack yeniden uretimi patladi", e);
                finish(callback, new RegenerateResult(false, 0, 0, null, false,
                        java.util.List.of(), 0, String.valueOf(e.getMessage())));
            }
        });
    }

    /** Basarisizlikta da kilidi birakir; yoksa bir hata butun yenilemeleri kilitler. */
    private void finish(java.util.function.Consumer<RegenerateResult> callback,
                        RegenerateResult result) {
        ctx.scheduler().sync("content", () -> {
            generating.set(false);
            callback.accept(result);
        });
    }

    /** Diskteki son uretimin hash'i; yoksa bos. */
    private String readHash() {
        try {
            return paths.hashFile().exists()
                    ? Files.readString(paths.hashFile().toPath(), StandardCharsets.UTF_8).trim()
                    : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Paketi tek bir oyuncuya yeniden gonderir (panel/komut icin). */
    public void resend(org.bukkit.entity.Player player) {
        delivery.send(player);
    }

    /** Su an sunulan paketin hash'i. */
    public String currentHash() {
        return readHash();
    }

    /** Ayarli pack_format. */
    public int packFormat() {
        return generator.packFormat();
    }

    /**
     * Paketin gercekten calisip calismayacagini denetler ve sorunlari SATIR SATIR
     * dondurur. Her kontrol, oyunda sessizce mor-siyah kareye yol acan bir duruma
     * karsilik gelir.
     */
    public java.util.List<String> verify() {
        java.util.List<String> problems = new java.util.ArrayList<>();

        if (generator.packFormat() != PackGenerator.DEFAULT_PACK_FORMAT) {
            problems.add("pack_format=" + generator.packFormat() + ", beklenen "
                    + PackGenerator.DEFAULT_PACK_FORMAT + " (resource-pack.format)");
        }
        if (items.isEmpty()) {
            problems.add("hic item tanimi yuklenmemis (contents/<ns>/items/*.yml)");
        }
        // 1) Doku diskte var mi?
        for (CustomItem item : items.values()) {
            String texture = item.texture();
            if (texture == null || texture.isBlank()) {
                problems.add(item.fullId() + ": texture alani bos");
                continue;
            }
            java.io.File file = new java.io.File(
                    new java.io.File(paths.contents(), item.namespace()),
                    "textures/" + texture);
            if (!file.isFile()) {
                problems.add(item.fullId() + ": doku diskte yok -> contents/"
                        + item.namespace() + "/textures/" + texture);
            }
        }
        // 2) Font parcalarinin dokulari. HUD barlari, diyalog kutusu ve portreler
        //    bu dokular uzerinden cizilir; eksik olani istemcide bos bir karakter
        //    olarak gorunur ve sunucu tarafinda hicbir belirti vermez.
        problems.addAll(missingFontTextures());

        // 3) Uretilmis bir zip var mi, dokular icine girmis mi?
        if (!paths.generatedZip().isFile()) {
            problems.add("generated.zip yok -- /pack yenile calistir");
            return problems;
        }
        try (var zip = new java.util.zip.ZipFile(paths.generatedZip())) {
            java.util.Set<String> entries = new java.util.HashSet<>();
            zip.stream().forEach(entry -> entries.add(entry.getName()));

            for (CustomItem item : items.values()) {
                String path = "assets/" + item.namespace() + "/textures/"
                        + item.texture();
                if (!entries.contains(path)) {
                    problems.add(item.fullId() + ": doku pack zip'inde yok -> " + path
                            + " (/pack yenile gerekli olabilir)");
                }
                // item_model bileseninin GOSTERDIGI dosya. Denetimin can alici
                // noktasi budur: "bir tanim dosyasi var mi" degil, "itemin
                // tasidigi anahtar TAM OLARAK o dosyaya mi cozuluyor". Onceki
                // surumde anahtarda fazladan bir "item/" oneki vardi; tanim
                // dosyasi uretiliyordu ama istemci baska bir yola bakiyordu ve
                // denetim "sorun yok" diyordu.
                String key = item.modelKey();
                String resolved = "assets/" + key.replace(":", "/items/") + ".json";
                if (!entries.contains(resolved)) {
                    problems.add(item.fullId() + ": item_model '" + key
                            + "' -> " + resolved + " zip'te yok (item mor-siyah kare gorunur)");
                }
                String model = "assets/" + item.modelPath().replace(":", "/models/") + ".json";
                if (!entries.contains(model)) {
                    problems.add(item.fullId() + ": model dosyasi zip'te yok -> " + model);
                }
            }
        } catch (IOException e) {
            problems.add("generated.zip okunamadi: " + e.getMessage());
        }
        return problems;
    }

    /** contents/<ns>/fonts/*.yml icinde gecen ama diskte olmayan dokular. */
    private java.util.List<String> missingFontTextures() {
        java.util.List<String> problems = new java.util.ArrayList<>();
        java.io.File[] namespaces = paths.contents().listFiles(java.io.File::isDirectory);
        if (namespaces == null) return problems;

        for (java.io.File namespace : namespaces) {
            java.io.File fonts = new java.io.File(namespace, "fonts");
            java.io.File[] files = fonts.listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;

            for (java.io.File file : files) {
                var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                for (String key : yaml.getKeys(false)) {
                    var section = yaml.getConfigurationSection(key);
                    if (section == null) continue;
                    String texture = section.getString("texture", "font/" + key + ".png");
                    java.io.File target = new java.io.File(
                            new java.io.File(namespace, "textures"), texture);
                    if (!target.isFile()) {
                        problems.add("font '" + key + "' (" + file.getName() + "): doku yok -> "
                                + "contents/" + namespace.getName() + "/textures/" + texture);
                    }
                }
            }
        }
        return problems;
    }

    /** Uretilen zip'in boyutu (bayt); yoksa 0. */
    public long packSize() {
        return paths.generatedZip().exists() ? paths.generatedZip().length() : 0L;
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

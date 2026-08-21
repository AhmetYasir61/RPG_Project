package net.aethel.core.modules.panel;

import net.aethel.core.api.AdminPanelService;
import net.aethel.core.api.MenuService;
import net.aethel.core.api.PanelMode;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Yonetim paneli modulu. Tek komut (/adminmenu) buraya duser; GUI modunda oyun ici
 * menu, WEB modunda tek kullanimlik baglanti verir. Iki mod ayni anda etkin olmaz.
 */
@ModuleInfo(id = "panel", name = "Yonetim Paneli", depends = {"menu"})
public final class AdminPanelModule implements Module, AdminPanelService {

    private final PanelSettings settings = new PanelSettings();
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private PanelMode mode = PanelMode.GUI;
    private PanelGui gui;
    private CoreContext ctx;

    /** Panelin bir bolumu; modullerin kaydettigi giris noktasi. */
    record Section(String id, String displayName, String icon, String permission) {}

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/panel.yml", 1, settings, ConfigMigration.NONE);
        this.mode = PanelMode.parse(settings.mode);
        ctx.services().register(AdminPanelService.class, this, "panel");
        ctx.commands().register("panel", new AdminMenuCommand(ctx, this, settings));
    }

    @Override
    public void onEnable(CoreContext ctx) {
        validateMode();
        this.gui = new PanelGui(ctx, this);
        registerDefaultSections();
        ctx.logger().info("Yonetim paneli modu: " + mode);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        sections.clear();
    }

    /**
     * Iki panel ayni anda acik birakilirsa web kapatilir ve uyari verilir: ayni kaydi
     * iki yerden duzenlemek, birinin degisikligini sessizce ezmesine yol acar.
     */
    private void validateMode() {
        boolean webModuleEnabled = ctx.config().get("modules.yml").yaml()
                .getBoolean("modules.web.enabled", false);
        if (mode.isWeb() && !webModuleEnabled) {
            ctx.logger().warning("admin.mode=WEB ama web modulu kapali; GUI'ye dusuluyor.");
            this.mode = PanelMode.GUI;
        }
        if (!mode.isWeb() && webModuleEnabled) {
            ctx.logger().warning("admin.mode=GUI iken web modulu de acik. "
                    + "Ayni kaydi iki yerden duzenlemek veri kaybi uretir; web devre disi birakilmali.");
        }
    }

    /** Cekirdek bolumleri; modul acik degilse bolum de gorunmez. */
    private void registerDefaultSections() {
        registerSection("items", "Item Tanimlari", "DIAMOND_SWORD", "aethel.admin.items");
        registerSection("skills", "Yetenekler", "BLAZE_POWDER", "aethel.admin.skills");
        registerSection("mobs", "Moblar", "ZOMBIE_HEAD", "aethel.admin.mobs");
        registerSection("regions", "Bolgeler", "MAP", "aethel.admin.regions");
        registerSection("difficulties", "Zorluk Seviyeleri", "REDSTONE", "aethel.admin.regions");
        registerSection("loot", "Loot Tablolari", "CHEST", "aethel.admin.loot");
        registerSection("permissions", "Yetkiler", "NAME_TAG", "aethel.admin.permissions");
        registerSection("economy", "Ekonomi", "GOLD_INGOT", "aethel.admin.economy");
        registerSection("players", "Oyuncular", "PLAYER_HEAD", "aethel.profile.inspect");
        registerSection("pack", "Kaynak Paketi", "BOOK", "aethel.admin.pack");
        registerSection("features", "Ozellikler", "LEVER", "aethel.admin.features");
    }

    @Override
    public PanelMode mode() {
        return mode;
    }

    @Override
    public void open(Player player) {
        if (mode.isWeb()) {
            openWeb(player);
            return;
        }
        gui.openRoot(player);
    }

    @Override
    public void openSection(Player player, String section) {
        if (mode.isWeb()) {
            openWeb(player);
            return;
        }
        gui.openSection(player, section);
    }

    /** WEB modunda panel baglantisi auth servisinin jeton mekanizmasini kullanir. */
    private void openWeb(Player player) {
        ctx.services().optional(net.aethel.core.api.AuthService.class).ifPresentOrElse(
                auth -> ctx.lang().send(player, "panel.web-link",
                        net.aethel.core.i18n.LangService.of("url", auth.webLoginUrl(player))),
                () -> ctx.lang().send(player, "panel.web-unavailable"));
    }

    @Override
    public List<String> sections() {
        return new ArrayList<>(sections.keySet());
    }

    @Override
    public void registerSection(String id, String displayName, String icon, String permission) {
        sections.put(id, new Section(id, displayName, icon, permission));
    }

    /** GUI'nin bolum listesini cizmesi icin. */
    List<Section> sectionList() {
        return List.copyOf(sections.values());
    }

    MenuService menus() {
        return ctx.services().get(MenuService.class);
    }
}

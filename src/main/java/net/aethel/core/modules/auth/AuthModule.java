package net.aethel.core.modules.auth;

import net.aethel.core.api.AuthService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giris/kayit modulu. Panel modu GUI ise oyun ici akis (AuthMe benzeri), WEB ise
 * tarayiciya yonlendirme kullanilir; dogrulama kurallari her iki modda da ortaktir.
 */
@ModuleInfo(id = "auth", name = "Kimlik Dogrulama", depends = {"profile"}, hotDisable = false)
public final class AuthModule implements Module, AuthService {

    private final AuthSettings settings = new AuthSettings();
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<String, WebToken> webTokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private AuthRepository repository;
    private AuthListener listener;
    private CoreContext ctx;

    /** WEB modunda uretilen tek kullanimlik giris jetonu. */
    record WebToken(UUID player, long expiresAt) {}

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/auth.yml", 1, settings, ConfigMigration.NONE);
        this.repository = new AuthRepository(ctx.database());
        ctx.services().register(AuthService.class, this, "auth");

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("auth.enabled", true, "Giris / kayit zorunlulugu");
        features.declare("auth.freeze", true, "Dogrulanmamis oyuncunun hareketini engelle");
        features.declare("auth.ip-session", true, "Ayni IP'den donen oyuncuyu otomatik dogrula");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.schema().migrate("auth", AuthSchema.MIGRATIONS);
        this.listener = new AuthListener(ctx, this, settings);
        ctx.listener("auth.enabled", listener);

        // Suresi dolan jetonlar temizlenir; sizan bir baglanti sonsuza kadar gecerli olmaz.
        ctx.scheduler().repeating("auth", 20L * 30, 20L * 30, () -> {
            long now = System.currentTimeMillis();
            webTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        });
        ctx.plugin().getServer().getOnlinePlayers().forEach(this::beginSession);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        states.clear();
        attempts.clear();
        webTokens.clear();
    }

    /** Oyuncu girince durumu belirlenir: kaydi var mi, oturumu hala gecerli mi. */
    void beginSession(Player player) {
        UUID uuid = player.getUniqueId();
        repository.find(uuid).thenAccept(found -> {
            if (found.isEmpty()) {
                states.put(uuid, State.UNREGISTERED);
                return;
            }
            AuthRepository.Record record = found.get();
            String ip = player.getAddress() == null ? "" : player.getAddress().getHostString();
            boolean sessionValid = ctx.feature("auth.ip-session")
                    && record.lastLogin() != null
                    && ip.equals(record.lastIp())
                    && System.currentTimeMillis() - record.lastLogin() < settings.sessionMinutes * 60_000L;
            states.put(uuid, sessionValid ? State.AUTHENTICATED : State.AWAITING_LOGIN);
        });
    }

    void endSession(UUID uuid) {
        states.remove(uuid);
        attempts.remove(uuid);
    }

    @Override
    public State state(UUID uuid) {
        return states.getOrDefault(uuid, State.AWAITING_LOGIN);
    }

    @Override
    public CompletableFuture<Boolean> register(UUID uuid, String secret) {
        if (!valid(secret)) return CompletableFuture.completedFuture(false);
        return repository.find(uuid).thenCompose(existing -> {
            if (existing.isPresent()) return CompletableFuture.completedFuture(false);
            return repository.create(uuid, SecretHasher.hash(secret)).thenApply(rows -> {
                states.put(uuid, State.AUTHENTICATED);
                repository.audit(uuid, "REGISTER", "self");
                return rows > 0;
            });
        });
    }

    @Override
    public CompletableFuture<Boolean> login(UUID uuid, String secret) {
        return repository.find(uuid).thenApply(found -> {
            if (found.isEmpty()) return false;
            boolean ok = SecretHasher.verify(secret, found.get().secretHash());
            if (ok) {
                states.put(uuid, State.AUTHENTICATED);
                attempts.remove(uuid);
                Player player = ctx.plugin().getServer().getPlayer(uuid);
                String ip = player == null || player.getAddress() == null
                        ? "" : player.getAddress().getHostString();
                repository.touchLogin(uuid, ip);
            } else {
                attempts.merge(uuid, 1, Integer::sum);
            }
            return ok;
        });
    }

    @Override
    public CompletableFuture<Boolean> changeSecret(UUID uuid, String oldSecret, String newSecret) {
        if (!valid(newSecret)) return CompletableFuture.completedFuture(false);
        return repository.find(uuid).thenCompose(found -> {
            if (found.isEmpty() || !SecretHasher.verify(oldSecret, found.get().secretHash())) {
                return CompletableFuture.completedFuture(false);
            }
            return repository.updateSecret(uuid, SecretHasher.hash(newSecret)).thenApply(rows -> {
                repository.audit(uuid, "CHANGE_SECRET", "self");
                return rows > 0;
            });
        });
    }

    @Override
    public CompletableFuture<Void> adminReset(UUID uuid, String actor) {
        return repository.delete(uuid)
                .thenCompose(rows -> repository.audit(uuid, "ADMIN_RESET", actor))
                .thenRun(() -> states.put(uuid, State.UNREGISTERED));
    }

    /**
     * Jeton rastgele 32 bayttir ve tek kullanimliktir: tarayicida kullanildigi anda
     * dusuruluru, boylece baglanti kopyalansa bile ikinci kez ise yaramaz.
     */
    @Override
    public String webLoginUrl(Player player) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        webTokens.put(token, new WebToken(player.getUniqueId(),
                System.currentTimeMillis() + settings.webTokenSeconds * 1000L));
        String base = ctx.config().get("config.yml").yaml()
                .getString("admin.web.public-url", "http://127.0.0.1:8080");
        return base + "/auth/" + token;
    }

    /**
     * Web paneli jetonu kullandiginda cagrilir. Jeton tek kullanimliktir ve burada
     * dusurulur; donus degeri KIMIN geldigidir.
     *
     * Jeton tuketmek oyuncuyu DOGRULAMAZ: baglanti oyuna gonderildigi icin "kim"
     * sorusunu cevaplar, "sifreyi biliyor mu" sorusunu cevaplamaz. Dogrulama
     * ancak PIN girildikten sonra markAuthenticated ile yapilir.
     */
    public java.util.Optional<UUID> consumeWebToken(String token) {
        WebToken entry = webTokens.remove(token);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(entry.player());
    }

    /** Web tarafi kimligi dogruladiginda oyun ici durumu da acar. */
    public void markAuthenticated(UUID player) {
        states.put(player, State.AUTHENTICATED);
    }

    int attemptsOf(UUID uuid) {
        return attempts.getOrDefault(uuid, 0);
    }

    private boolean valid(String secret) {
        if (secret == null) return false;
        if (secret.length() < settings.minLength || secret.length() > settings.maxLength) return false;
        return !"PIN".equalsIgnoreCase(settings.mode) || secret.chars().allMatch(Character::isDigit);
    }
}

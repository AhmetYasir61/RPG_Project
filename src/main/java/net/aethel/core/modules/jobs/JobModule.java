package net.aethel.core.modules.jobs;

import net.aethel.core.api.EconomyService;
import net.aethel.core.api.JobService;
import net.aethel.core.api.PlayerProfile;
import net.aethel.core.api.ProfileService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meslek modulu. Meslek verisi profil nitelikleri uzerinde tutulur; her eylem
 * XP ve para verir, seviye basina perk acilir.
 */
@ModuleInfo(id = "jobs", name = "Meslekler", depends = {"profile"}, softDepends = {"economy"})
public final class JobModule implements Module, JobService {

    private static final String JOB_PREFIX = "jobs:";
    private static final String LEAVE_COOLDOWN_KEY = "jobs:leave-cooldown";

    private final JobSettings settings = new JobSettings();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private ConfigFile config;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/jobs.yml", 1, settings, ConfigMigration.NONE);
        this.config = ctx.config().open("jobs.yml", 1, null, ConfigMigration.NONE);
        ctx.services().register(JobService.class, this, "jobs");
        ctx.commands().register("jobs", new JobCommand(ctx, this));

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("jobs.enabled", true, "Meslek sistemi");
        features.declare("jobs.limit", true, "Ayni anda tutulabilecek meslek siniri");
        features.declare("jobs.leave-cooldown", true, "Meslek birakma sogumasi");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.commands().suggest("job", jobs::keySet);
        loadJobs();
        ctx.listener("jobs.enabled", new JobListener(this, ctx.plugin()));
    }

    @Override
    public void onDisable(CoreContext ctx) {
        jobs.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        loadJobs();
    }

    private void loadJobs() {
        jobs.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("jobs");
        if (root == null) {
            writeExample();
            root = config.yaml().getConfigurationSection("jobs");
            if (root == null) return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            jobs.put(id, new Job(id,
                    section.getString("display", id),
                    section.getInt("max-level", 100),
                    readActions(section.getConfigurationSection("actions")),
                    readPerks(section.getConfigurationSection("perks"))));
        }
        ctx.logger().info("Meslek: " + jobs.size());
    }

    private Map<String, Map<String, Reward>> readActions(ConfigurationSection section) {
        Map<String, Map<String, Reward>> actions = new LinkedHashMap<>();
        if (section == null) return actions;
        for (String actionType : section.getKeys(false)) {
            ConfigurationSection targets = section.getConfigurationSection(actionType);
            if (targets == null) continue;
            Map<String, Reward> rewards = new LinkedHashMap<>();
            for (String target : targets.getKeys(false)) {
                ConfigurationSection reward = targets.getConfigurationSection(target);
                if (reward == null) continue;
                rewards.put(target.toUpperCase(java.util.Locale.ROOT), new Reward(
                        reward.getDouble("xp", 1.0), reward.getDouble("money", 0.0)));
            }
            actions.put(actionType.toUpperCase(java.util.Locale.ROOT), rewards);
        }
        return actions;
    }

    private Map<Integer, List<String>> readPerks(ConfigurationSection section) {
        Map<Integer, List<String>> perks = new LinkedHashMap<>();
        if (section == null) return perks;
        section.getKeys(false).forEach(key -> {
            try {
                perks.put(Integer.parseInt(key), section.getStringList(key));
            } catch (NumberFormatException e) {
                ctx.logger().warning("Gecersiz perk seviyesi: " + key);
            }
        });
        return perks;
    }

    private void writeExample() {
        var yaml = config.yaml();
        yaml.set("jobs.madenci.display", "<gray>Madenci</gray>");
        yaml.set("jobs.madenci.max-level", 100);
        yaml.set("jobs.madenci.actions.BREAK.DIAMOND_ORE.xp", 12.0);
        yaml.set("jobs.madenci.actions.BREAK.DIAMOND_ORE.money", 4.5);
        yaml.set("jobs.madenci.actions.BREAK.IRON_ORE.xp", 3.0);
        yaml.set("jobs.madenci.actions.BREAK.IRON_ORE.money", 1.2);
        yaml.set("jobs.madenci.perks.10", List.of("cift-dusme-sansi:5"));
        yaml.set("jobs.avci.display", "<red>Avci</red>");
        yaml.set("jobs.avci.actions.KILL.ZOMBIE.xp", 6.0);
        yaml.set("jobs.avci.actions.KILL.ZOMBIE.money", 2.0);
        config.save();
    }

    @Override
    public List<Job> jobs() {
        return List.copyOf(jobs.values());
    }

    @Override
    public Optional<Job> job(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public Map<String, Integer> jobsOf(UUID player) {
        Map<String, Integer> result = new LinkedHashMap<>();
        profile(player).ifPresent(p -> jobs.keySet().forEach(id -> {
            if (p.attribute(JOB_PREFIX + id).isPresent()) result.put(id, level(player, id));
        }));
        return result;
    }

    /**
     * Meslek sayisi sinirlidir ve birakma sogumaya tabidir: aksi halde oyuncular
     * her isi sirayla yapip ekonomideki uzmanlasmayi tamamen ortadan kaldirirdi.
     */
    @Override
    public JoinResult join(Player player, String jobId) {
        if (!jobs.containsKey(jobId)) return JoinResult.UNKNOWN_JOB;
        Map<String, Integer> current = jobsOf(player.getUniqueId());
        if (current.containsKey(jobId)) return JoinResult.ALREADY_JOINED;
        if (ctx.feature("jobs.limit") && current.size() >= settings.maxActiveJobs) {
            return JoinResult.LIMIT_REACHED;
        }
        if (ctx.feature("jobs.leave-cooldown") && leaveCooldown(player.getUniqueId()) > 0) {
            return JoinResult.ON_COOLDOWN;
        }

        profile(player.getUniqueId()).ifPresent(p -> p.attribute(JOB_PREFIX + jobId, 0.0));
        return JoinResult.OK;
    }

    /** Birakinca ilerlemenin bir kismi korunur; geri donen oyuncu sifirdan baslamaz. */
    @Override
    public boolean leave(Player player, String jobId) {
        return profile(player.getUniqueId()).map(p -> {
            if (p.attribute(JOB_PREFIX + jobId).isEmpty()) return false;
            double kept = p.attributeDouble(JOB_PREFIX + jobId, 0) * settings.progressKeptOnLeave;
            p.removeAttribute(JOB_PREFIX + jobId);
            p.attribute(JOB_PREFIX + "saved:" + jobId, kept);
            p.attribute(LEAVE_COOLDOWN_KEY,
                    System.currentTimeMillis() + settings.leaveCooldownHours * 3_600_000L);
            return true;
        }).orElse(false);
    }

    @Override
    public double experience(UUID player, String jobId) {
        return profile(player).map(p -> p.attributeDouble(JOB_PREFIX + jobId, 0)).orElse(0.0D);
    }

    @Override
    public int level(UUID player, String jobId) {
        Job job = jobs.get(jobId);
        int maxLevel = job == null ? 100 : job.maxLevel();
        return JobCurve.levelOf(experience(player, jobId), maxLevel);
    }

    /** Oyuncunun sahip oldugu her meslek icin eylemi degerlendirir. */
    @Override
    public void handleAction(Player player, String actionType, String target) {
        UUID uuid = player.getUniqueId();
        for (String jobId : jobsOf(uuid).keySet()) {
            Job job = jobs.get(jobId);
            if (job == null) continue;
            Reward reward = job.actions()
                    .getOrDefault(actionType.toUpperCase(java.util.Locale.ROOT), Map.of())
                    .get(target.toUpperCase(java.util.Locale.ROOT));
            if (reward == null) continue;
            award(player, jobId, reward);
        }
    }

    private void award(Player player, String jobId, Reward reward) {
        UUID uuid = player.getUniqueId();
        int before = level(uuid, jobId);
        profile(uuid).ifPresent(p -> p.attribute(JOB_PREFIX + jobId,
                p.attributeDouble(JOB_PREFIX + jobId, 0) + reward.experience()));

        if (reward.money() > 0) {
            ctx.services().optional(EconomyService.class)
                    .ifPresent(economy -> economy.deposit(uuid, reward.money(), "job:" + jobId));
        }
        int after = level(uuid, jobId);
        if (after > before && settings.announceLevelUp) {
            ctx.lang().send(player, "jobs.level-up",
                    LangService.of("job", jobs.get(jobId).displayName()),
                    LangService.of("level", after));
        }
    }

    @Override
    public long leaveCooldown(UUID player) {
        return profile(player)
                .map(p -> Math.max(0, (long) p.attributeDouble(LEAVE_COOLDOWN_KEY, 0)
                        - System.currentTimeMillis()))
                .orElse(0L);
    }

    private Optional<PlayerProfile> profile(UUID uuid) {
        return ctx.services().optional(ProfileService.class)
                .flatMap(profiles -> profiles.cached(uuid));
    }

    JobSettings settings() {
        return settings;
    }
}

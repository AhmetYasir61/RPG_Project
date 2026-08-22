package net.aethel.core.modules.jobs;

import net.aethel.core.api.JobService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Oyuncu meslek komutlari: liste, katilma, birakma ve ilerleme ozeti. */
@Command(value = "meslek", aliases = {"jobs", "is"}, playerOnly = true,
        descriptionKey = "jobs.command-description")
public final class JobCommand {

    private final CoreContext ctx;
    private final JobModule jobs;

    public JobCommand(CoreContext ctx, JobModule jobs) {
        this.ctx = ctx;
        this.jobs = jobs;
    }

    @Command("")
    public void mine(CommandSender sender) {
        Player player = (Player) sender;
        var owned = jobs.jobsOf(player.getUniqueId());
        if (owned.isEmpty()) {
            ctx.lang().send(player, "jobs.none");
            return;
        }
        ctx.lang().send(player, "jobs.header");
        owned.forEach((id, level) -> jobs.job(id).ifPresent(job ->
                ctx.lang().send(player, "jobs.entry",
                        LangService.of("job", job.displayName()),
                        LangService.of("level", level),
                        LangService.of("xp", String.format("%.0f",
                                jobs.experience(player.getUniqueId(), id))))));
    }

    @Command("liste")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        ctx.lang().send(player, "jobs.available");
        jobs.jobs().forEach(job -> ctx.lang().send(player, "jobs.available-entry",
                LangService.of("job", job.displayName()),
                LangService.of("id", job.id())));
    }

    @Command("katil")
    public void join(CommandSender sender, @Arg(value = "meslek", suggests = "job", identifier = true) String jobId) {
        Player player = (Player) sender;
        JobService.JoinResult result = jobs.join(player, jobId);
        ctx.lang().send(player, switch (result) {
            case OK -> "jobs.joined";
            case ALREADY_JOINED -> "jobs.already-joined";
            case LIMIT_REACHED -> "jobs.limit-reached";
            case ON_COOLDOWN -> "jobs.on-cooldown";
            case UNKNOWN_JOB -> "jobs.unknown";
        }, LangService.of("job", jobId),
                LangService.of("max", jobs.settings().maxActiveJobs));
    }

    @Command("birak")
    public void leave(CommandSender sender, @Arg(value = "meslek", suggests = "job", identifier = true) String jobId) {
        Player player = (Player) sender;
        boolean ok = jobs.leave(player, jobId);
        ctx.lang().send(player, ok ? "jobs.left" : "jobs.not-joined",
                LangService.of("job", jobId),
                LangService.of("hours", jobs.settings().leaveCooldownHours));
    }
}

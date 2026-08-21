package net.aethel.core.modules.jobs;

import net.aethel.core.config.ConfigValue;

/** Meslek ayarlari. Sinirli meslek sayisi ekonomide uzmanlasmanin temelidir. */
public final class JobSettings {

    @ConfigValue("jobs.max-active")
    public int maxActiveJobs = 2;

    /** Meslek biraktiktan sonra yenisine girme sogumasi (saat). */
    @ConfigValue("jobs.leave-cooldown-hours")
    public int leaveCooldownHours = 12;

    /** Meslek birakinca ilerlemenin ne kadari korunur (0..1). */
    @ConfigValue("jobs.progress-kept-on-leave")
    public double progressKeptOnLeave = 0.5D;

    @ConfigValue("jobs.announce-level-up")
    public boolean announceLevelUp = true;
}

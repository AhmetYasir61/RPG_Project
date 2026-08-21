package net.aethel.core.modules.scoreboard;

import net.aethel.core.config.ConfigValue;

/** Tab listesi ve yan tablo ayarlari. Icerik scoreboard.yml icinde tanimlanir. */
public final class ScoreboardSettings {

    /** Yenileme sikligi (tick). 20 = saniyede bir; placeholder cozumu buna bagli. */
    @ConfigValue("scoreboard.update-ticks")
    public int updateTicks = 20;

    @ConfigValue("scoreboard.sidebar-enabled")
    public boolean sidebarEnabled = true;

    @ConfigValue("scoreboard.tab-enabled")
    public boolean tabEnabled = true;

    /** Tab listesinde oyuncu adlarinin onune rutbe on eki konsun mu. */
    @ConfigValue("scoreboard.tab-prefix")
    public boolean tabPrefix = true;
}

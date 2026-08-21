package net.aethel.core.module;

/** Modulun yasam dongusundeki anlik durumu; /core modules listesinde gosterilir. */
public enum ModuleState {
    REGISTERED,
    LOADING,
    LOADED,
    ENABLING,
    ENABLED,
    DISABLING,
    DISABLED,
    /** Bagimliligi FAILED oldugu icin hic denenmedi. */
    SKIPPED,
    FAILED;

    public boolean isRunning() {
        return this == ENABLED;
    }
}

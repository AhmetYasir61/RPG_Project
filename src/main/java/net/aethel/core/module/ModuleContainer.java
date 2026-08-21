package net.aethel.core.module;

/**
 * Bir modul ornegini meta verisi ve anlik durumu ile birlikte tutar.
 * ModuleManager disinda kimse durumu degistirmez.
 */
public final class ModuleContainer {

    private final Module instance;
    private final ModuleInfo info;
    private final boolean enabledInConfig;
    private ModuleState state = ModuleState.REGISTERED;
    private Throwable failure;

    public ModuleContainer(Module instance, ModuleInfo info, boolean enabledInConfig) {
        this.instance = instance;
        this.info = info;
        this.enabledInConfig = enabledInConfig;
    }

    public Module instance() { return instance; }
    public ModuleInfo info() { return info; }
    public String id() { return info.id(); }
    public boolean enabledInConfig() { return enabledInConfig; }
    public ModuleState state() { return state; }
    public Throwable failure() { return failure; }

    void state(ModuleState state) { this.state = state; }

    void fail(Throwable t) {
        this.failure = t;
        this.state = ModuleState.FAILED;
    }
}

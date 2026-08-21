package net.aethel.core.modules.content.pack;

import java.io.File;

/**
 * Uretim hattinin klasor duzeni. Kaynak (contents, blueprints) ile turetilmis
 * (generated, cache) icerik kesin olarak ayrilir; generated silinip yeniden uretilebilir.
 */
public final class PackPaths {

    private final File dataFolder;

    public PackPaths(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /** Icerik tanimlari ve ham texture'lar. */
    public File contents() {
        return ensure(new File(dataFolder, "contents"));
    }

    /** Blockbench .bbmodel ve elle yazilmis model JSON'lari. */
    public File blueprints() {
        return ensure(new File(dataFolder, "blueprints"));
    }

    /** Uretilen acik pack agaci (ayiklama icin okunabilir kalir). */
    public File packTree() {
        return ensure(new File(dataFolder, "generated/pack"));
    }

    /** Oyuncuya gonderilen zip. */
    public File generatedZip() {
        return new File(ensure(new File(dataFolder, "generated")), "generated.zip");
    }

    public File hashFile() {
        return new File(ensure(new File(dataFolder, "generated")), "generated.sha1");
    }

    /** Kimlik tahsisleri ve artimli uretim icin dosya hash'leri. */
    public File index() {
        return new File(ensure(new File(dataFolder, "cache")), "pack-index.json");
    }

    public File assets() {
        return ensure(new File(packTree(), "assets"));
    }

    private File ensure(File file) {
        if (!file.exists()) file.mkdirs();
        return file;
    }
}

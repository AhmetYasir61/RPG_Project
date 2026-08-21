package net.aethel.core.modules.dungeon.instance;

import net.aethel.core.modules.dungeon.gen.VoidChunkGenerator;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dungeon boyutlarinin (dunyalarinin) olusturulmasi ve silinmesi. Her ornek kendi
 * dunyasinda yasar; silme islemi tum icerigi (bloklar, entity'ler, yerdeki esyalar)
 * tek hamlede yok eder.
 */
public final class InstanceWorlds {

    private final Plugin plugin;
    private final Logger log;

    public InstanceWorlds(Plugin plugin, Logger log) {
        this.plugin = plugin;
        this.log = log;
    }

    /**
     * Yeni bir ornek dunyasi yaratir. Dunya olusturma ANA THREAD'de yapilmak
     * zorundadir (Bukkit kisiti) ve pahalidir; bu yuzden bos ureticiyle ve spawn
     * chunk'lari yuklenmeden olusturulur.
     */
    public World create(UUID instanceId) {
        String name = "dungeon_" + instanceId.toString().substring(0, 8);
        WorldCreator creator = new WorldCreator(name)
                .generator(new VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .generateStructures(false);

        World world = creator.createWorld();
        if (world == null) {
            log.severe("Dungeon dunyasi olusturulamadi: " + name);
            return null;
        }
        configure(world);
        return world;
    }

    /**
     * Dungeon kurallari: gece/gunduz akmaz (atmosfer sabit kalsin), hava durumu yok,
     * dogal spawn kapali (moblari MobEngine koyar) ve olumde esya dusmez.
     *
     * keepInventory KAPALI, ama esyalar dunya ile birlikte silinecegi icin
     * cokme sirasinda olen oyuncunun esyasi geri alinamaz — mekanigin istedigi bu.
     */
    private void configure(World world) {
        world.setDifficulty(Difficulty.HARD);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setTime(18000L);
        world.setAutoSave(false);   // silinecek bir dunyayi diske yazmak bos is
        world.setKeepSpawnInMemory(false);
    }

    /**
     * Dunyayi bosaltir ve klasorunu siler. Once unload, sonra dosya silme sirasi
     * sart: yuklu bir dunyanin klasorunu silmek bozuk kayit ve dosya kilidi uretir.
     */
    public void destroy(World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();
        String name = world.getName();

        if (!plugin.getServer().unloadWorld(world, false)) {
            log.warning("Dungeon dunyasi bosaltilamadi: " + name);
            return;
        }
        deleteFolder(folder.toPath());
        log.info("Dungeon dunyasi silindi: " + name);
    }

    private void deleteFolder(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            log.log(Level.WARNING, "Dungeon klasoru silinemedi: " + path, e);
        }
    }

    /**
     * Sunucu acilisinda kalan artik dungeon dunyalarini temizler. Cokus sirasinda
     * sunucu kapanirsa klasor diskte kalir; her acilista bunlar suprulur.
     */
    public int cleanupOrphans(File container) {
        File[] folders = container.listFiles(file ->
                file.isDirectory() && file.getName().startsWith("dungeon_"));
        if (folders == null) return 0;

        int removed = 0;
        for (File folder : folders) {
            if (plugin.getServer().getWorld(folder.getName()) != null) continue;
            deleteFolder(folder.toPath());
            removed++;
        }
        if (removed > 0) log.info("Artik dungeon dunyasi temizlendi: " + removed);
        return removed;
    }
}

package net.aethel.core.modules.dungeon.gen;

import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Bos dunya ureticisi. Dungeon dunyalari vanilla arazi uretmez: odalar chunk chunk
 * bizim tarafimizdan yazilir, arazi uretimi sadece bos yere CPU harcar.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    /**
     * Tum uretim asamalari bos birakiliyor. Bunu yapmazsak sunucu her chunk icin
     * gurultu hesabi, magara oymasi ve yapi yerlestirmesi calistirir; dungeon
     * dunyasinda bu isin ciktisi zaten uzerine yazilacagi icin tamamen israftir.
     */
    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ,
                              ChunkData chunkData) {
        // Bilerek bos: arazi yok.
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ,
                                ChunkData chunkData) {
        // Bilerek bos.
    }

    @Override
    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ,
                              ChunkData chunkData) {
        // Bilerek bos.
    }

    @Override
    public boolean shouldGenerateNoise() { return false; }

    @Override
    public boolean shouldGenerateSurface() { return false; }

    @Override
    public boolean shouldGenerateCaves() { return false; }

    @Override
    public boolean shouldGenerateDecorations() { return false; }

    @Override
    public boolean shouldGenerateMobs() { return false; }

    @Override
    public boolean shouldGenerateStructures() { return false; }

    @Override
    public List<BlockPopulator> getDefaultPopulators(org.bukkit.World world) {
        return List.of();
    }
}

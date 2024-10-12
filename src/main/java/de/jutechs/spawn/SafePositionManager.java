package de.jutechs.spawn;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.BiomeKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

public class SafePositionManager {

    private static final ThreadLocalRandom random = ThreadLocalRandom.current();
    private static final Logger logger = LoggerFactory.getLogger("SafePositionManager");

    // List of blocked biomes
    private static final Set<RegistryKey<Biome>> blockedBiomes = new HashSet<>();

    static {
        blockedBiomes.add(BiomeKeys.OCEAN);
        blockedBiomes.add(BiomeKeys.DEEP_OCEAN);
        blockedBiomes.add(BiomeKeys.THE_VOID);
        blockedBiomes.add(BiomeKeys.SMALL_END_ISLANDS);
        blockedBiomes.add(BiomeKeys.COLD_OCEAN);
        blockedBiomes.add(BiomeKeys.WARM_OCEAN);
    }

    // Methode zur Suche nach einer sicheren, zufälligen Position
    public static BlockPos findRandomSafePosition(World world, int radius) {
        int spawnX = world.getSpawnPos().getX();
        int spawnZ = world.getSpawnPos().getZ();

        int minX = spawnX - radius;
        int maxX = spawnX + radius;
        int minZ = spawnZ - radius;
        int maxZ = spawnZ + radius;

        for (int attempt = 0; attempt < 10; attempt++) {
            int x = random.nextInt(minX, maxX + 1);
            int z = random.nextInt(minZ, maxZ + 1);

            BlockPos pos = new BlockPos(x, 0, z);

            // Schritt 1: Überprüfe das Biom
            RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
            if (blockedBiomes.contains(biomeEntry.getKey().orElse(null))) {
                continue; // Blockierte Biome überspringen
            }

            // Schritt 1.5: Chunk laden
            ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4); // x und z sind Blockkoordinaten
            Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);

            // Schritt 2: Dimensionsspezifische Höhenprüfung
            int height;

            if (world.getRegistryKey() == World.NETHER) {
                height = getSafeY(world, pos,ConfigManager.config.NetherMinY,ConfigManager.config.NetherMaxY);
            } else if (world.getRegistryKey() == World.END) {
                height = getSafeY(world, pos,ConfigManager.config.EndMinY,ConfigManager.config.EndMaxY);
            } else {
                height = getSafeY(world, pos,ConfigManager.config.OverworldMinY,ConfigManager.config.OverworldMaxY);
            }

            // Schritt 3: Überprüfe, ob die Position sicher ist
            if (height > 0) {
                BlockPos safePos = new BlockPos(x, height, z);
                if (isSafeFromLiquid(world, safePos)) {
                    return safePos; // Sichere Position gefunden
                }
            }
        }

        // Keine sichere Position nach den Versuchen gefunden
        return null;
    }

    // Methode zur Ermittlung einer sicheren Y-Position
    private static int getSafeY(World world, BlockPos pos, int minY, int maxY) {

        for (int y = maxY; y >= minY; y -= 3) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());

            // Überprüfe die aktuelle Position (y) und zwei weitere Blöcke darüber
            if (world.getBlockState(testPos).isSolidBlock(world, testPos) &&
                    world.getBlockState(testPos.up()).isAir() &&
                    world.getBlockState(testPos.up(2)).isAir()) {
                return y + 1; // Sichere Y-Position gefunden + 1 wegen solid
            }

            // Überprüfe den Fall `solid`-`solid`-`air`
            if (world.getBlockState(testPos).isSolidBlock(world, testPos) &&
                    world.getBlockState(testPos.up()).isSolidBlock(world, testPos.up()) &&
                    world.getBlockState(testPos.up(2)).isAir()) {

                // Zusätzliche Überprüfung, ob sich noch ein weiterer Luftblock darüber befindet
                if (world.getBlockState(testPos.up(3)).isAir()) {
                    return y + 2; // Sichere Position einen Block höher gefunden + 1 wegen solid
                }
            }

            // Überprüfe den Fall `solid`-`solid`-`solid`
            if (world.getBlockState(testPos).isSolidBlock(world, testPos) &&
                    world.getBlockState(testPos.up()).isSolidBlock(world, testPos.up()) &&
                    world.getBlockState(testPos.up(2)).isSolidBlock(world, testPos.up(2))) {

                // Gehe 2 Blöcke nach oben und prüfe, ob dort zwei Luftblöcke vorhanden sind
                if (world.getBlockState(testPos.up(3)).isAir() &&
                        world.getBlockState(testPos.up(4)).isAir()) {
                    return y + 3; // Sichere Position zwei Blöcke höher gefunden + 1 wegen solid
                }
            }
        }

        return -1; // Keine sichere Position gefunden
    }

    // Methode zur Überprüfung, ob die Position sicher vor Flüssigkeiten ist
    public static boolean isSafeFromLiquid(World world, BlockPos pos) {
        // Prüfe die vier horizontal angrenzenden Positionen
        BlockPos[] checkPositions = {
                pos.north(), // Block nördlich
                pos.south(), // Block südlich
                pos.east(),  // Block östlich
                pos.west()   // Block westlich
        };

        for (BlockPos checkPos : checkPositions) {
            if (!world.getFluidState(checkPos).isEmpty()) {
                return false; // Flüssigkeit in der Nähe gefunden, Position ist nicht sicher
            }
        }

        return true; // Keine Flüssigkeit in den horizontal angrenzenden Blöcken, Position ist sicher
    }

    public static boolean isSafePosition(ServerWorld world, BlockPos pos) {
        // Schritt 1.5: Chunk laden
        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4); // x und z sind Blockkoordinaten
        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);

        // Überprüfe, ob der Block unter der Position solide ist
        BlockPos below = pos.down();
        if (!world.getBlockState(below).isSolidBlock(world, below)) {
            return false;
        }

        // Überprüfe, ob die Position und die darüber liegenden Blöcke frei sind
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return false;
        }

        // Überprüfe zuletzt, ob die Position flüssigkeitsfrei ist
        return isSafeFromLiquid(world, pos);
    }
}

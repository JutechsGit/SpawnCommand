package de.jutechs.spawn.Utils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class PositionChecker {
    private static final Random random = new Random();

    public static CompletableFuture<BlockPos> findRandomSafePosition(ServerWorld world, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            int spawnX = world.getSpawnPos().getX();
            int spawnZ = world.getSpawnPos().getZ();

            int minX = spawnX - radius;
            int maxX = spawnX + radius;
            int minZ = spawnZ - radius;
            int maxZ = spawnZ + radius;

            // For the Nether, restrict to Y ≤ 120 (avoid teleporting above the Nether roof)
            int maxY = (world.getRegistryKey() == World.NETHER) ? 120 : world.getTopY(); // Adjust height based on dimension
            int minY = 0;

            for (int attempts = 0; attempts < 20; attempts++) {
                int x = random.nextInt(maxX - minX + 1) + minX;
                int z = random.nextInt(maxZ - minZ + 1) + minZ;

                // Generate a random Y position within the minY and maxY bounds
                BlockPos pos = new BlockPos(x, random.nextInt(maxY - minY + 1) + minY, z);

                // Ensure the position in the Nether is below the roof (Y ≤ 120)
                if (world.getRegistryKey() == World.NETHER && pos.getY() > 120) {
                    continue; // Skip positions above Y=120 in the Nether
                }

                BlockPos safePos = findSafePosition(world, pos, 2);
                if (safePos != null) {
                    return safePos;
                }
            }
            return null; // Return null if no safe position was found after attempts
        });
    }

    public static BlockPos findSafePosition(ServerWorld world, BlockPos pos, int radius) {
        // Get the highest solid block at this position that blocks motion (ignores leaves)
        Chunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);

        // Ensure the position is not too high in the Nether (enforced again here for safety)
        if (world.getRegistryKey() == World.NETHER && pos.getY() > 120) {
            return null; // Discard positions above the Nether roof
        }

        // Check if the position is free of liquid and within the safety radius
        if (pos.getY() > 0 && isSafeFromLiquid(world, pos, radius)) {
            return pos;
        }

        return null;
    }

    private static boolean isSafeFromLiquid(World world, BlockPos pos, int radius) {
        // Check for liquid blocks within the specified radius around the position
        for (int x = pos.getX() - radius; x <= pos.getX() + radius; x++) {
            for (int y = pos.getY() - radius; y <= pos.getY() + radius; y++) {
                for (int z = pos.getZ() - radius; z <= pos.getZ() + radius; z++) {
                    if (!world.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                        return false; // Liquid found, not safe
                    }
                }
            }
        }
        return true; // No liquid nearby, safe
    }
}

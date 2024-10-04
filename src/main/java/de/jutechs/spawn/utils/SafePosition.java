package de.jutechs.spawn.utils;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Random;

public class SafePosition {

    public static BlockPos findSafePosition(ServerWorld world, BlockPos startPos, int radius) {
        Random random = new Random();
        int attempts = 0;
        int maxAttempts = 20; // Maximale Anzahl der Versuche, eine sichere Position zu finden

        while (attempts < maxAttempts) {
            // Generiere zufällige Koordinaten innerhalb des Radius um die Startposition
            int x = startPos.getX() + random.nextInt(radius * 2) - radius;
            int z = startPos.getZ() + random.nextInt(radius * 2) - radius;

            // Finde die Y-Koordinate basierend auf der Dimension
            int y = getValidYForDimension(world, x, z);

            BlockPos pos = new BlockPos(x, y, z);

            // Überprüfe, ob die Position sicher ist
            if (isPositionSafe(world, pos)) {
                return pos; // Sichere Position gefunden
            }

            attempts++;
        }

        // Keine sichere Position nach maxAttempts gefunden
        return null;
    }

    private static int getValidYForDimension(ServerWorld world, int x, int z) {
        if (world.getRegistryKey() == World.NETHER) {
            return world.getTopY(); // In der Nether gibt es spezifische Höhen
        } else if (world.getRegistryKey() == World.END) {
            return world.getTopY(); // Im End ebenfalls
        } else {
            return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
        }
    }

    private static boolean isPositionSafe(ServerWorld world, BlockPos pos) {
        // Überprüfe, ob die Position solide Blöcke unter und Luft über sich hat
        if (world.getBlockState(pos.up()).isAir() &&
                world.getBlockState(pos.down()).isSolid() &&
                !isNearLiquid(world, pos)) {
            return true;
        }
        return false;
    }

    private static boolean isNearLiquid(ServerWorld world, BlockPos pos) {
        int radiusCheck = 2; // Radius, um nach Flüssigkeiten zu suchen
        for (int x = pos.getX() - radiusCheck; x <= pos.getX() + radiusCheck; x++) {
            for (int y = pos.getY() - radiusCheck; y <= pos.getY() + radiusCheck; y++) {
                for (int z = pos.getZ() - radiusCheck; z <= pos.getZ() + radiusCheck; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    if (!world.getFluidState(checkPos).isEmpty()) {
                        return true; // Flüssigkeit gefunden, Position ist unsicher
                    }
                }
            }
        }
        return false; // Keine Flüssigkeiten in der Nähe
    }
}

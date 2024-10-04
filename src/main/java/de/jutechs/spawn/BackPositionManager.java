package de.jutechs.spawn;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair; // Make sure to import this or create your own Pair class

public class BackPositionManager {
    // Stores the previous position (dimension and BlockPos) for each player
    private static final Map<UUID, Pair<RegistryKey<World>, BlockPos>> previousPositions = new HashMap<>();

    // Method to set the previous position
    public static void setPreviousPosition(UUID playerUUID, ServerWorld world, BlockPos position) {
        previousPositions.put(playerUUID, Pair.of(world.getRegistryKey(), position));
    }

    // Method to get the previous position
    public static Pair<RegistryKey<World>, BlockPos> getPreviousPosition(UUID playerUUID) {
        return previousPositions.get(playerUUID);
    }

    // Method to check if a position for the player is stored
    public static boolean hasPreviousPosition(UUID playerUUID) {
        return previousPositions.containsKey(playerUUID);
    }

    // Method to retrieve the ServerWorld based on the stored dimension
    public static ServerWorld getWorldFromKey(ServerWorld currentWorld, RegistryKey<World> worldKey) {
        return currentWorld.getServer().getWorld(worldKey);
    }
}

//credits: eqmind

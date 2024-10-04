package de.jutechs.spawn.utils;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackPositionManager {
    // Speichert die Ausgangsposition (Welt und BlockPos) für jeden Spieler
    private static final Map<UUID, Pair<ServerWorld, BlockPos>> previousPositions = new HashMap<>();

    // Methode zum Setzen der Ausgangsposition
    public static void setPreviousPosition(UUID playerUUID, ServerWorld world, BlockPos position) {
        previousPositions.put(playerUUID, new Pair<>(world, position));
    }

    // Methode zum Abrufen der Ausgangsposition
    public static Pair<ServerWorld, BlockPos> getPreviousPosition(UUID playerUUID) {
        return previousPositions.get(playerUUID);
    }

    // Methode zum Überprüfen, ob eine Position für den Spieler gespeichert ist
    public static boolean hasPreviousPosition(UUID playerUUID) {
        return previousPositions.containsKey(playerUUID);
    }
}

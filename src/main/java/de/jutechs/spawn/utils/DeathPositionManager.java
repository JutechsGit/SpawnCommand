package de.jutechs.spawn.utils;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeathPositionManager {

    // Speichert die Todespositionen (Welt und BlockPos) für jeden Spieler
    private static final Map<UUID, BlockPos> lastDeathPositions = new HashMap<>();
    private static final Map<UUID, ServerWorld> lastDeathWorlds = new HashMap<>();

    // Methode zum Registrieren des Todesereignisses
    public static void registerDeathEvent() {
        // Verwende ServerPlayerEvents, um den Tod des Spielers zu erfassen
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                // Speichere die Position und die Welt des Todes
                lastDeathPositions.put(oldPlayer.getUuid(), oldPlayer.getBlockPos());
                lastDeathWorlds.put(oldPlayer.getUuid(), (ServerWorld) oldPlayer.getWorld());
            }
        });
    }

    // Methode zum Abrufen der letzten Todesposition
    public static BlockPos getLastDeathPosition(UUID playerUUID) {
        return lastDeathPositions.get(playerUUID);
    }

    // Methode zum Abrufen der letzten Todeswelt
    public static ServerWorld getLastDeathWorld(UUID playerUUID) {
        return lastDeathWorlds.get(playerUUID);
    }

    // Überprüfen, ob eine Todesposition gespeichert ist
    public static boolean hasLastDeathPosition(UUID playerUUID) {
        return lastDeathPositions.containsKey(playerUUID);
    }
}

package de.jutechs.spawn.commands;

import de.jutechs.spawn.BackPositionManager;
import de.jutechs.spawn.utils.SafePosition;
import de.jutechs.spawn.utils.TeleportationManager;
import de.jutechs.spawn.utils.TitleUtils;
import de.jutechs.spawn.ConfigManager; // Import des ConfigManager
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld; // Import von ServerWorld
import net.minecraft.world.World; // Import von World
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpawnCommand {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    static final Map<UUID, Long> SpawncooldownMap = new HashMap<>();
    static final Map<UUID, Boolean> teleportInProgressMap = new HashMap<>();

    public static int teleportToSpawn(ServerCommandSource source, int searchAreaRadius, String dimension) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = ConfigManager.config.SpawnCooldown;

        if (teleportInProgressMap.getOrDefault(playerId, false)) {
            player.sendMessage(Text.literal("Spawn teleportation is already in progress. Please wait.")
                    .formatted(Formatting.RED), false);
            return 1;
        }

        if (SpawncooldownMap.containsKey(playerId)) {
            long lastUseTime = SpawncooldownMap.get(playerId);
            if (currentTime - lastUseTime < cooldownTime) {
                long timeLeft = (cooldownTime - (currentTime - lastUseTime)) / 1000;
                player.sendMessage(Text.literal("Please wait " + timeLeft + " more seconds before using /spawn again.")
                        .formatted(Formatting.RED), false);
                return 1;
            }
        }

        teleportInProgressMap.put(playerId, true);

        // Bestimme die Welt basierend auf der Dimension
        ServerWorld world = getWorldForDimension(player, dimension);
        if (world == null) {
            player.sendMessage(Text.literal("Invalid dimension. Using Overworld.").formatted(Formatting.RED), false);
            world = player.getServer().getWorld(World.OVERWORLD);
        }

        // Speichern der aktuellen Position des Spielers vor der Teleportation
        BackPositionManager.setPreviousPosition(player.getUuid(), (ServerWorld) player.getWorld(), player.getBlockPos());

        BlockPos startPos = new BlockPos(0, 0, 0);

        startCountdownAndTeleport(player, world, searchAreaRadius, dimension, startPos);

        return 1;
    }

    private static void startCountdownAndTeleport(ServerPlayerEntity player, ServerWorld world, int radius, String dimension, BlockPos startPos) {
        int countdownTime = ConfigManager.config.SpawnTpDelay;
        boolean notifyPlayerChat = ConfigManager.config.CountdownInChat;
        Vec3d initialPosition = player.getPos();

        TeleportationManager manager = new TeleportationManager(scheduler, player, initialPosition, countdownTime, notifyPlayerChat,
                () -> {
                    CompletableFuture.supplyAsync(() -> SafePosition.findSafePosition(world, startPos, radius))
                            .thenAccept(safePos -> {
                                scheduler.schedule(() -> {
                                    UUID playerId = player.getUuid();

                                    // Titel und Teleportation nach Countdown
                                    TitleUtils.sendTitle(player, "Teleporting to Spawn", "", ConfigManager.config.FadeInTicks, ConfigManager.config.StayTicks, ConfigManager.config.FadeOutTicks, Formatting.GREEN, Formatting.GREEN);
                                    player.sendMessage(Text.literal("Teleported to spawn in " + dimension.toUpperCase()).formatted(Formatting.GOLD), false);

                                    player.getServer().execute(() -> {
                                        if (safePos != null) {
                                            player.teleport(world, safePos.getX(), safePos.getY(), safePos.getZ(), player.getYaw(), player.getPitch());
                                        } else {
                                            player.sendMessage(Text.literal("No safe position found in " + dimension.toUpperCase()).formatted(Formatting.RED), false);
                                        }
                                        teleportInProgressMap.put(playerId, false);
                                        SpawncooldownMap.put(playerId, System.currentTimeMillis());
                                    });
                                }, 1, TimeUnit.SECONDS);
                            });
                },
                () -> {
                    teleportInProgressMap.put(player.getUuid(), false); // Logik für den Abbruch der Teleportation
                }
        );

        manager.startCountdown();
    }

    private static ServerWorld getWorldForDimension(ServerPlayerEntity player, String dimension) {
        switch (dimension.toLowerCase()) {
            case "nether":
                return player.getServer().getWorld(World.NETHER);
            case "end":
                return player.getServer().getWorld(World.END);
            case "overworld":
            default:
                return player.getServer().getWorld(World.OVERWORLD);
        }
    }
}

package de.jutechs.spawn.Utils;

import com.mojang.brigadier.Command;
import de.jutechs.spawn.Main;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;

import static de.jutechs.spawn.Main.scheduler;
import static de.jutechs.spawn.Utils.PositionChecker.findRandomSafePosition;
import static de.jutechs.spawn.Utils.PositionChecker.findSafePosition;

public class TeleportManager {
    private static final Random random = new Random();
    private static final ConcurrentHashMap<UUID, Boolean> teleportInProgressMap = new ConcurrentHashMap<>();

    private static void startCountdownAndTeleport(ServerPlayerEntity player, ServerWorld world, int searchAreaRadius, String dimension) {
        int countdownTime = Main.is_Rtp ? ConfigManager.config.RTPTpDelay : ConfigManager.config.SpawnTpDelay; // Countdown time
        boolean notifyPlayerChat = ConfigManager.config.CountdownInChat;
        Vec3d initialPosition = player.getPos();
        final ScheduledFuture<?>[] countdownTasks = new ScheduledFuture<?>[countdownTime]; // To cancel tasks
        final boolean[] hasMoved = {false}; // Flag to check if the player has moved
        final boolean[] messageSent = {false}; // Flag to ensure the message is sent only once

        for (int i = countdownTime; i > 0; i--) {
            final int countdown = i;
            countdownTasks[countdownTime - i] = scheduler.schedule(() -> {
                if (player.getPos().squaredDistanceTo(initialPosition) > 0.1) { // Check if the player moved
                    if (!messageSent[0]) {
                        player.sendMessage(Text.literal("Teleportation canceled due to movement.").formatted(Formatting.RED), false);
                        messageSent[0] = true; // Ensure the message is sent only once
                    }
                    hasMoved[0] = true; // Indicate that the player has moved
                    cancelRemainingCountdown(countdownTasks);
                    teleportInProgressMap.remove(player.getUuid()); // Unlock for future teleport attempts
                    return;
                }

                // Send countdown title
                int fadeInTicks = ConfigManager.config.FadeInTicks;
                int stayTicks = ConfigManager.config.StayTicks;
                int fadeOutTicks = ConfigManager.config.FadeOutTicks;
                Text subtitle = Text.literal("Please stand still for " + countdown + " more seconds").formatted(Formatting.YELLOW);
                Text subtitleMessageP1 = Text.literal("Please stand still for ").formatted(Formatting.RED, Formatting.ITALIC);
                Text subtitleMessageP2 = Text.literal(String.valueOf(countdown)).formatted(Formatting.GOLD, Formatting.ITALIC);
                Text subtitleMessageP3 = Text.literal(" more seconds").formatted(Formatting.RED, Formatting.ITALIC);
                Text subtitleMessage = Text.empty().append(subtitleMessageP1).append(subtitleMessageP2).append(subtitleMessageP3);

                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Teleporting").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.of(subtitleMessage)));
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));

                if (notifyPlayerChat) {
                    player.sendMessage(subtitle, false);
                }

                if (countdown == 1 && !hasMoved[0] && !messageSent[0]) {
                    // Async part: Find a random safe position off the main thread
                    CompletableFuture.supplyAsync(() -> findRandomSafePosition(world, searchAreaRadius))
                            .thenAccept(safePos -> {
                                scheduler.schedule(() -> {
                                    sendTitle(player, "Teleporting", "", fadeInTicks, stayTicks, fadeOutTicks, Formatting.GREEN, Formatting.GREEN);
                                    player.sendMessage(Text.literal("Teleported to %s".formatted(dimension.toUpperCase())).formatted(Formatting.GOLD), false);

                                    player.getServer().execute(() -> {
                                        if (safePos != null) {
                                            player.getServer().execute(() -> {
                                                // Access getX, getY, and getZ on the Vec3d result, not on the CompletableFuture
                                                player.teleport(world, safePos.getX(), safePos.getY(), safePos.getZ(), player.getYaw(), player.getPitch());
                                                // Only set the cooldown if teleportation was successful
                                                UUID playerId = player.getUuid();
                                                long currentTime = System.currentTimeMillis();
                                                Main.SpawncooldownMap.put(playerId, currentTime); // Update cooldown
                                            });
                                        } else {
                                            player.sendMessage(Text.literal("No safe position found in %s!".formatted(dimension.toUpperCase())).formatted(Formatting.RED), false);
                                        }
                                        teleportInProgressMap.remove(player.getUuid()); // Unlock for future teleport attempts
                                    });
                                }, 1, TimeUnit.SECONDS);
                            });

                }
            }, countdownTime - i, TimeUnit.SECONDS);
        }
    }

    public static int teleportToRandomSafePosition(ServerCommandSource source, int searchAreaRadius, String dimension, boolean is_Rtp) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0; // Not a player
        }

        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = Main.is_Rtp ? ConfigManager.config.RTPCooldown : ConfigManager.config.SpawnCooldown;

        // Prevent multiple simultaneous teleportation attempts
        if (teleportInProgressMap.getOrDefault(playerId, false)) {
            player.sendMessage(Text.literal("Teleportation already in progress. Please wait.").formatted(Formatting.RED), false);
            return Command.SINGLE_SUCCESS;
        }

        // Check if player is on cooldown
        if (Main.SpawncooldownMap.containsKey(playerId)) {
            long lastUseTime = Main.SpawncooldownMap.get(playerId);
            if (currentTime - lastUseTime < cooldownTime) {
                long timeLeft = (cooldownTime - (currentTime - lastUseTime)) / 1000;
                player.sendMessage(Text.literal("Please wait " + timeLeft + " more seconds before using the command again.").formatted(Formatting.RED), false);
                return Command.SINGLE_SUCCESS;
            }
        }

        // Set flag to prevent further attempts during countdown/teleportation
        teleportInProgressMap.put(playerId, true);

        // Determine the world based on the dimension argument
        ServerWorld world;
        if (dimension.equalsIgnoreCase("nether")) {
            world = player.getServer().getWorld(World.NETHER);
        } else if (dimension.equalsIgnoreCase("end")) {
            world = player.getServer().getWorld(World.END);
        } else if (dimension.equalsIgnoreCase("overworld")) {
            world = player.getServer().getWorld(World.OVERWORLD);
        } else {
            player.sendMessage(Text.of("Invalid dimension. Using default Overworld.".formatted(Formatting.RED)), false);
            world = player.getServer().getWorld(World.OVERWORLD); // Default to Overworld
        }

        // Run the countdown and teleportation
        startCountdownAndTeleport(player, world, searchAreaRadius, dimension);

        return Command.SINGLE_SUCCESS;
    }

    public static void sendTitle(ServerPlayerEntity player, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut, Formatting titleColor, Formatting subtitleColor) {
        // Create title and subtitle text with the specified colors
        Text title = Text.literal(titleText).formatted(titleColor);   // Title with custom color
        Text subtitle = Text.literal(subtitleText).formatted(subtitleColor); // Subtitle with custom color

        // Send the title text
        player.networkHandler.sendPacket(new TitleS2CPacket(title));

        // Send the subtitle text
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));

        // Set the fade in, stay, and fade out times
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
    }

    private static void cancelRemainingCountdown(ScheduledFuture<?>[] tasks) {
        for (ScheduledFuture<?> task : tasks) {
            if (task != null && !task.isDone()) {
                task.cancel(false); // Cancel the task if it's not already completed
            }
        }
    }
}

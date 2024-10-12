package de.jutechs.spawn.commands;

import de.jutechs.spawn.BackPositionManager;
import de.jutechs.spawn.ConfigManager;
import de.jutechs.spawn.SafePositionManager;
import de.jutechs.spawn.TpCacheManager;
import de.jutechs.spawn.NotificationManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import static de.jutechs.spawn.Main.MOD_ID;

public class SpawnARTPCommand {

    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);

    // A single cooldown map for all teleportation commands
    public static final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();
    public static final Map<UUID, Boolean> teleportInProgressMap = new ConcurrentHashMap<>();


    // Map for pending teleports
    public static final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private static final ThreadLocalRandom random = ThreadLocalRandom.current();


    public static int teleportToRandomSafePosition(ServerCommandSource source, String dimension, boolean isRtp) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendError(Text.literal("This command can only be executed by a player."));
            return 0;
        }

        assert player != null;
        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();
        long cooldownTime;

        if (teleportInProgressMap.getOrDefault(playerId, false)) {
            player.sendMessage(Text.literal("Teleportation already in progress. Please wait.")
                    .formatted(Formatting.RED), false);
            return 1; // Success
        }

        cooldownTime = isRtp ? ConfigManager.config.RTPCooldown : ConfigManager.config.SpawnCooldown;

        if (cooldownMap.containsKey(playerId)) {
            long lastUseTime = cooldownMap.get(playerId);
            if (currentTime - lastUseTime < cooldownTime) {
                long timeLeft = (cooldownTime - (currentTime - lastUseTime)) / 1000;
                player.sendMessage(Text.literal("Please wait " + timeLeft + " seconds before using the command again.")
                        .formatted(Formatting.RED), false);
                return 1; // Success
            }
        }

        teleportInProgressMap.put(playerId, true);

        ServerWorld world = getWorldFromDimension(player, dimension);
        if (world == null) {
            player.sendMessage(Text.literal("Invalid dimension. Using default Overworld.").formatted(Formatting.RED), false);
            world = Objects.requireNonNull(player.getServer()).getWorld(World.OVERWORLD);
        }

        assert world != null;
        ConcurrentLinkedQueue<BlockPos> cache = TpCacheManager.getCache(world.getRegistryKey(), !isRtp);
        assert cache != null;
        if (cache.isEmpty()) {
            player.sendMessage(Text.literal("Teleportation is currently not available. Please try again shortly.")
                    .formatted(Formatting.RED), false);
            teleportInProgressMap.put(playerId, false);
            return 1; // Success
        }

        BlockPos safePos = null;
        // Try to find a safe position from the cache
        while (!cache.isEmpty()) {
            BlockPos cachedPos = cache.poll();
            if (cachedPos != null && SafePositionManager.isSafePosition(world, cachedPos)) {
                safePos = cachedPos;
                break;
            }
        }

        if (safePos == null) {
            player.sendMessage(Text.literal("No safe positions are currently available. Please try again shortly.")
                    .formatted(Formatting.RED), false);
            teleportInProgressMap.put(playerId, false);
            return 1; // Success
        }

        // Start the teleportation process
        startCountdownAndTeleport(player, world, safePos, dimension, isRtp);

        // After teleportation, check if the cache needs refilling
        TpCacheManager.checkAndRefillCache(player.getServer(), world.getRegistryKey(), !isRtp, cache);

        return 1; // Success
    }

    private static ServerWorld getWorldFromDimension(ServerPlayerEntity player, String dimension) {
        if (dimension.equalsIgnoreCase("nether")) {
            return Objects.requireNonNull(player.getServer()).getWorld(World.NETHER);
        } else if (dimension.equalsIgnoreCase("end")) {
            return Objects.requireNonNull(player.getServer()).getWorld(World.END);
        } else {
            return Objects.requireNonNull(player.getServer()).getWorld(World.OVERWORLD);
        }
    }

    private static void startCountdownAndTeleport(ServerPlayerEntity player, ServerWorld world, BlockPos safePos, String dimension, boolean isRtp) {
        int countdownTime = isRtp ? ConfigManager.config.RTPTpDelay : ConfigManager.config.SpawnTpDelay;
        int ticksDelay = countdownTime * 20; // Convert seconds to ticks

        PendingTeleport pendingTeleport = new PendingTeleport(player, world, safePos, dimension, ticksDelay);
        pendingTeleports.put(player.getUuid(), pendingTeleport);

        // Display countdown as title
        NotificationManager.sendCountdownTitle(player, countdownTime);
    }

    public static void handlePendingTeleports() {
        // Create a copy of the entries to avoid ConcurrentModificationException
        List<Map.Entry<UUID, PendingTeleport>> teleportEntries = new ArrayList<>(pendingTeleports.entrySet());

        for (Map.Entry<UUID, PendingTeleport> entry : teleportEntries) {
            PendingTeleport pendingTeleport = entry.getValue();
            ServerPlayerEntity player = pendingTeleport.player;

            // Check if the player is still online
            if (player == null || player.networkHandler == null) {
                assert player != null;
                teleportInProgressMap.remove(player.getUuid());
                pendingTeleports.remove(entry.getKey());
                continue;
            }

            // Check if the player has moved
            if (!player.getBlockPos().equals(pendingTeleport.initialBlockPos)) {
                if (!pendingTeleport.hasMoved) {
                    player.sendMessage(Text.literal("Teleportation canceled due to movement.")
                            .formatted(Formatting.RED), false);
                    pendingTeleport.hasMoved = true;
                }
                teleportInProgressMap.put(player.getUuid(), false);
                pendingTeleports.remove(entry.getKey());
                continue;
            }

            pendingTeleport.ticksRemaining--;

            if (pendingTeleport.ticksRemaining <= 0) {
                // Perform teleportation
                UUID playerId = player.getUuid();
                BackPositionManager.setPreviousPosition(playerId, (ServerWorld) player.getWorld(), player.getBlockPos());

                // Teleport the player
                Objects.requireNonNull(player.getServer()).execute(() -> {
                    try {
                        if (pendingTeleport.safePos != null && SafePositionManager.isSafeFromLiquid(pendingTeleport.world, pendingTeleport.safePos)) {
                            player.teleport(pendingTeleport.world, pendingTeleport.safePos.getX() + 0.5, pendingTeleport.safePos.getY(), pendingTeleport.safePos.getZ() + 0.5, player.getYaw(), player.getPitch());
                        } else {
                            player.sendMessage(Text.literal("The position is no longer safe. Please try again.")
                                    .formatted(Formatting.RED), false);
                        }
                    } catch (Exception e) {
                        logger.error("Error teleporting player " + player.getName().getString(), e);
                        player.sendMessage(Text.literal("Error during teleportation. Please contact an administrator.")
                                .formatted(Formatting.RED), false);
                    } finally {
                        teleportInProgressMap.put(player.getUuid(), false);
                        cooldownMap.put(player.getUuid(), System.currentTimeMillis());
                        pendingTeleports.remove(entry.getKey());
                    }
                });
            } else {
                // Update countdown display as title
                int secondsRemaining = pendingTeleport.ticksRemaining / 20 + 1;
                NotificationManager.sendCountdownTitle(player, secondsRemaining);
            }
        }
    }

    // Class for pending teleports
    public static class PendingTeleport {
        public ServerPlayerEntity player;
        public ServerWorld world;
        public BlockPos safePos;
        public String dimension;
        public BlockPos initialBlockPos;
        public int ticksRemaining;
        public boolean hasMoved = false;

        public PendingTeleport(ServerPlayerEntity player, ServerWorld world, BlockPos safePos, String dimension, int ticksRemaining) {
            this.player = player;
            this.world = world;
            this.safePos = safePos;
            this.dimension = dimension;
            this.initialBlockPos = player.getBlockPos();
            this.ticksRemaining = ticksRemaining;
        }
    }

}

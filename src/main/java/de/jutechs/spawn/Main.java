package de.jutechs.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import com.mojang.brigadier.Command;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import java.util.Random;

import net.minecraft.world.chunk.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

import static net.fabricmc.loader.impl.FabricLoaderImpl.MOD_ID;

public class Main implements ModInitializer {
    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);
    private static final Map<UUID, Long> cooldownMap = new HashMap<>();
    private static final Random random = new Random();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public void onInitialize() {
        // Load the config
        ConfigManager.loadConfig();

        // Access the range from the config
        int searchAreaRadius = ConfigManager.config.Range;

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawn")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {
                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), searchAreaRadius, dimension);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), searchAreaRadius, "overworld"))
            );
        });
        int rTPRange = ConfigManager.config.RTPRange;
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("rtp")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {

                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), rTPRange, dimension);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), rTPRange, "overworld"))
            );
        });
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
    // Pass the searchAreaRadius as a parameter to the method
    private static int teleportToRandomSafePosition(ServerCommandSource source, int searchAreaRadius, String dimension) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0; // Not a player
        }

        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = ConfigManager.config.Cooldown;

        // Check if player is on cooldown
        if (cooldownMap.containsKey(playerId)) {
            long lastUseTime = cooldownMap.get(playerId);
            if (currentTime - lastUseTime < cooldownTime) {
                long timeLeft = (cooldownTime - (currentTime - lastUseTime)) / 1000;
                player.sendMessage(Text.literal("Please wait " + timeLeft + " more seconds before using /spawn again.").formatted(Formatting.RED), false);
                return Command.SINGLE_SUCCESS;
            }
        }

        // Update the last use time
        cooldownMap.put(playerId, currentTime);

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
    private static void startCountdownAndTeleport(ServerPlayerEntity player, ServerWorld world, int searchAreaRadius, String dimension) {
        int countdownTime = ConfigManager.config.CombatTagTpDelay; // Countdown time in seconds(default = 5 seconds)
        boolean notifyPlayerChat = ConfigManager.config.NotifyPlayerChat;
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
                    return;
                }

                // Send countdown title
                int fadeInTicks = ConfigManager.config.FadeInTicks;
                int stayTicks = ConfigManager.config.StayTicks;
                int fadeOutTicks = ConfigManager.config.FadeOutTicks;
                Text subtitle = Text.literal("Please stand still for " + countdown + " more seconds").formatted(Formatting.YELLOW );
                Text subtitleMessageP1 = Text.literal("Please stand still for ").formatted(Formatting.RED, Formatting.ITALIC);
                Text subtitleMessageP2 = Text.literal(String.valueOf(countdown)).formatted(Formatting.GOLD, Formatting.ITALIC);
                Text subtitleMessageP3 = Text.literal(" more seconds").formatted(Formatting.RED, Formatting.ITALIC);
                Text subtitleMessage = Text.empty().append(subtitleMessageP1).append(subtitleMessageP2).append(subtitleMessageP3);


                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Teleporting").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.of(subtitleMessage)));
                //logger.info(subtitleMessage);
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
                if (notifyPlayerChat == false){
                    player.sendMessage(subtitle, false);
                }

                if (countdown == 1 && !hasMoved[0] && !messageSent[0]) {
                    CompletableFuture.supplyAsync(() -> findRandomSafePosition(world, searchAreaRadius))
                            .thenAccept(safePos -> {
                                // If a safe position is found, teleport the player
                                if (safePos != null) {
                                    player.getServer().execute(() -> {
                                        player.teleport(world, safePos.getX(), safePos.getY(), safePos.getZ(), player.getYaw(), player.getPitch());
                                        sendTitle(player, "Teleporting", "", fadeInTicks,stayTicks,fadeOutTicks, Formatting.GREEN, Formatting.GREEN);
                                        player.sendMessage(Text.literal("Teleported to %s".formatted(dimension.toUpperCase())).formatted(Formatting.GOLD), false);
                                    });
                                } else {
                                    player.sendMessage(Text.literal("No safe position found in %s!".formatted(dimension.toUpperCase())).formatted(Formatting.RED), false);
                                }
                            });
                }
            }, countdownTime - i, TimeUnit.SECONDS);
        }
    }

    private static void cancelRemainingCountdown(ScheduledFuture<?>[] tasks) {
        for (ScheduledFuture<?> task : tasks) {
            if (task != null && !task.isDone()) {
                task.cancel(false); // Cancel the task if it's not already completed
            }
        }
    }



    private static BlockPos findRandomSafePosition(World world, int radius) {
        int spawnX = world.getSpawnPos().getX();
        int spawnZ = world.getSpawnPos().getZ();

        int minX = spawnX - radius;
        int maxX = spawnX + radius;
        int minZ = spawnZ - radius;
        int maxZ = spawnZ + radius;

        // Determine maxY based on the dimension
        int maxY = (world.getRegistryKey() == World.NETHER) ? 120 : world.getTopY(); // 120 for Nether limit, world.getTopY() for others
        int minY = 0; // Adjust if needed

        int attempts = 0;
        int maxAttempts = 20; // Limited to prevent excessive blocking

        while (attempts < maxAttempts) {
            int x, y, z;

            synchronized (random) {
                x = random.nextInt(maxX - minX + 1) + minX;
                z = random.nextInt(maxZ - minZ + 1) + minZ;
                y = random.nextInt(maxY - minY + 1) + minY;
            }

            BlockPos pos = new BlockPos(x, y, z);
            BlockPos safePos = findSafePosition((ServerWorld) world, pos, 2); // Adjust the radius here
            if (safePos != null) {
              //  logger.info("save");
                return safePos;
            }
            attempts++;
        }
        //logger.info("No save");
        return null; // No safe position found after maxAttempts
    }

    private static BlockPos findSafePosition(ServerWorld world, BlockPos pos, int radius) {
        int maxY;

        // Handle Nether and End normally
        if (world.getRegistryKey() == World.NETHER) {
            maxY = 100; // Height limit for the Nether
        } else if (world.getRegistryKey() == World.END) {
            maxY = 100; // Height limit for the End
        } else {
            // For the Overworld, ensure the chunk is loaded
            Chunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);

            // Get the top position using the heightmap
            pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);
           // logger.info("Checking position :)");
           // logger.info(pos.toString());

            // Validate the Y-coordinate from the heightmap
            if (pos.getY() > 0 && isSafeFromLiquid(world, pos, radius)) {
                return pos; // Return the safe position
            } else {
            //    logger.info("Invalid or unsafe position returned by heightmap.");
                return null; // Return null if position is invalid
            }
        }

        // Fallback for Nether and End using regular height limits
        int minY = 0;
        for (int y = maxY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());

            // Check for a safe position:
            if (world.getBlockState(testPos.up()).isAir() &&
                    world.getBlockState(testPos.down()).isSolid() &&
                    isSafeFromLiquid(world, testPos, radius)) {
                return testPos;
            }
        }

        return null;
    }

    private static boolean isSafeFromLiquid(World world, BlockPos pos, int radius) {
        for (int x = pos.getX() - radius; x <= pos.getX() + radius; x++) {
            for (int y = pos.getY() - radius; y <= pos.getY() + radius; y++) { // Check vertical range
                for (int z = pos.getZ() - radius; z <= pos.getZ() + radius; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    FluidState fluidState = world.getFluidState(checkPos);
                    if (!fluidState.isEmpty()) {
                      //  logger.info("Liquid found at " + checkPos + " :(");
                        return false; // Liquid found nearby
                    }
                }
            }
        }
       // logger.info("No liquids found");
        return true; // No liquid found nearby
    }
}
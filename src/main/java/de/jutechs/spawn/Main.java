package de.jutechs.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import com.mojang.brigadier.Command;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.loader.impl.FabricLoaderImpl.MOD_ID;

public class Main implements ModInitializer {
    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);
    private static final Map<UUID, Long> cooldownMap = new HashMap<>();
    private static final Random random = new Random();
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

        // Run the safe position search asynchronously
        CompletableFuture.supplyAsync(() -> findRandomSafePosition(world, searchAreaRadius))
                .thenAccept(safePos -> {
                    // If a safe position is found, teleport the player on the main server thread
                    if (safePos != null) {
                        player.getServer().execute(() -> {
                            player.teleport(world, safePos.getX(), safePos.getY(), safePos.getZ(), player.getYaw(), player.getPitch());
                            player.sendMessage(Text.literal("Teleported to %s".formatted(dimension.toUpperCase())).formatted(Formatting.GOLD), false);
                        });
                    } else {
                        player.sendMessage(Text.literal("No safe position found in %s!".formatted(dimension.toUpperCase())).formatted(Formatting.RED), false);
                    }
                });

        return Command.SINGLE_SUCCESS;
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
            BlockPos safePos = findSafePosition(world, pos, 2); // Adjust the radius here
            if (safePos != null) {
                return safePos;
            }
            attempts++;
        }
        return null; // No safe position found after maxAttempts
    }

    private static BlockPos findSafePosition(World world, BlockPos pos, int radius) {
        int maxY;
        if (world.getRegistryKey() == World.NETHER) {
            maxY = 100; // Height limit for the Nether
        } else if (world.getRegistryKey() == World.END) {
            maxY = 100; // Height limit for the End
        } else {
            maxY = world.getTopY(); // Regular height limit for other dimensions
        }

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
            for (int z = pos.getZ() - radius; z <= pos.getZ() + radius; z++) {
                BlockPos checkPos = new BlockPos(x, pos.getY(), z);
                FluidState fluidState = world.getFluidState(checkPos);
                if (!fluidState.isEmpty()) {
                    return false; // Liquid found nearby
                }
            }
        }
        return true; // No liquid found nearby
    }
}
package de.jutechs.spawn;

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
import net.minecraft.util.math.random.Random;

import java.util.concurrent.CompletableFuture;

public class Main implements ModInitializer {

    private static final int SEARCH_AREA_RADIUS = 1000; // Adjust the search area radius here

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawn")
                    .executes(context -> teleportToRandomSafePosition(context.getSource()))
            );
        });
    }

    private static int teleportToRandomSafePosition(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0; // Not a player
        }

        World world = player.getWorld();
        Random random = (Random) world.random;

        // Run the safe position search asynchronously
        CompletableFuture.supplyAsync(() -> findRandomSafePosition(world, random, SEARCH_AREA_RADIUS))
                .thenAccept(safePos -> {
                    // If a safe position is found, teleport the player on the main server thread
                    if (safePos != null) {
                        ((ServerWorld) world).getServer().submit(() -> {
                            player.teleport((ServerWorld) world, safePos.getX(), safePos.getY(), safePos.getZ(), player.getYaw(), player.getPitch());
                            player.sendMessage(Text.of("Teleported to spawn".formatted(Formatting.GOLD)), false);
                        });
                    } else {
                        player.sendMessage(Text.of("No safe position found at spawn!".formatted(Formatting.RED)), false);
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static BlockPos findRandomSafePosition(World world, Random random, int radius) {
        int spawnX = world.getSpawnPos().getX();
        int spawnZ = world.getSpawnPos().getZ();

        int minX = spawnX - radius;
        int maxX = spawnX + radius;
        int minZ = spawnZ - radius;
        int maxZ = spawnZ + radius;

        int attempts = 0;
        int maxAttempts = 20; // Reduced to prevent timeouts

        while (attempts < maxAttempts) {
            int x = random.nextInt(maxX - minX + 1) + minX;
            int z = random.nextInt(maxZ - minZ + 1) + minZ;
            BlockPos pos = new BlockPos(x, world.getTopY(), z);

            BlockPos safePos = findSafePosition(world, pos, 2); // Adjust the radius here
            if (safePos != null) {
                return safePos;
            }

            attempts++;
        }

        return null; // No safe position found after maxAttempts
    }

    private static BlockPos findSafePosition(World world, BlockPos pos, int radius) {
        int maxY = world.getTopY();
        int minY = 0;

        for (int y = maxY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());

            // Check for a safe position:
            // - Air above
            // - Solid block below
            // - No liquid blocks within a radius
            if (world.getBlockState(testPos.up()).isAir() &&
                    world.getBlockState(testPos.down()).isSolid() &&
                    isSafeFromLiquid(world, testPos, radius)) {
                return testPos;
            }
        }

        return null; // Return null if no safe position is found
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

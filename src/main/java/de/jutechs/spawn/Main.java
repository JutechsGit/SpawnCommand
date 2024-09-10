package de.jutechs.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import com.mojang.brigadier.Command;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
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

import java.util.*;

import net.minecraft.world.chunk.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
    private static boolean is_rtp;

    @Override
    public void onInitialize() {

        ConfigManager.loadConfig();


        int searchAreaRadius = ConfigManager.config.Range;
        int rTPRange = ConfigManager.config.RTPRange;

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawn")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {
                                is_rtp = false;
                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), searchAreaRadius, dimension);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), searchAreaRadius, "overworld"))
            );
        });

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("rtp")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {
                                is_rtp = true;
                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), rTPRange, dimension);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), rTPRange, "overworld"))
            );
        });
    }

    public static void sendTitle(ServerPlayerEntity player, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut, Formatting titleColor, Formatting subtitleColor) {

        Text title = Text.literal(titleText).formatted(titleColor);
        Text subtitle = Text.literal(subtitleText).formatted(subtitleColor);


        player.networkHandler.sendPacket(new TitleS2CPacket(title));


        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));


        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
    }

    private static int teleportToRandomSafePosition(ServerCommandSource source, int searchAreaRadius, String dimension) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = ConfigManager.config.Cooldown;


        if (cooldownMap.containsKey(playerId)) {
            long lastUseTime = cooldownMap.get(playerId);
            if (currentTime - lastUseTime < cooldownTime) {
                long timeLeft = (cooldownTime - (currentTime - lastUseTime)) / 1000;
                player.sendMessage(Text.literal("Please wait " + timeLeft + " more seconds before using /spawn again.").formatted(Formatting.RED), false);
                return Command.SINGLE_SUCCESS;
            }
        }


        cooldownMap.put(playerId, currentTime);


        ServerWorld world;
        if (is_rtp) {
            world = player.getServerWorld();
        } else {
            switch (dimension.toLowerCase()) {
                case "nether":
                    world = Objects.requireNonNull(player.getServer()).getWorld(World.NETHER);
                    break;
                case "end":
                    world = Objects.requireNonNull(player.getServer()).getWorld(World.END);
                    break;
                case "overworld":
                default:
                    world = Objects.requireNonNull(player.getServer()).getWorld(World.OVERWORLD);
                    break;
            }
        }

        // Run the countdown and teleportation
        startCountdownAndTeleport(player, world, searchAreaRadius, dimension);

        return Command.SINGLE_SUCCESS;
    }

    private static void startCountdownAndTeleport(ServerPlayerEntity player, ServerWorld world, int searchAreaRadius, String dimension) {
        int countdownTime = ConfigManager.config.CombatTagTpDelay;
        boolean notifyPlayerChat = ConfigManager.config.NotifyPlayerChat;
        Vec3d initialPosition = player.getPos();
        final ScheduledFuture<?>[] countdownTasks = new ScheduledFuture<?>[countdownTime];
        final boolean[] hasMoved = {false};
        final boolean[] messageSent = {false};

        for (int i = countdownTime; i > 0; i--) {
            final int countdown = i;
            countdownTasks[countdownTime - i] = scheduler.schedule(() -> {
                if (player.getPos().squaredDistanceTo(initialPosition) > 0.1) {
                    if (!messageSent[0]) {
                        player.sendMessage(Text.literal("Teleportation canceled due to movement.").formatted(Formatting.RED), false);
                        messageSent[0] = true;
                    }
                    hasMoved[0] = true;
                    cancelRemainingCountdown(countdownTasks);
                    return;
                }


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
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
                if (!notifyPlayerChat){
                    player.sendMessage(subtitle, false);
                }

                if (countdown == 1 && !hasMoved[0] && !messageSent[0]) {
                    CompletableFuture.supplyAsync(() -> findRandomSafePosition(world, searchAreaRadius))
                            .thenAccept(safePos -> {

                                if (safePos != null) {
                                    Objects.requireNonNull(player.getServer()).execute(() -> {
                                        player.teleport(world, safePos.getX(), safePos.getY(), safePos.getZ(), player.getYaw(), player.getPitch());
                                        sendTitle(player, "Teleporting", "", fadeInTicks, stayTicks, fadeOutTicks, Formatting.GREEN, Formatting.GREEN);
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
                task.cancel(false);
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


        int maxY = (world.getRegistryKey() == World.NETHER) ? 120 : world.getTopY();
        int minY = 0;

        int attempts = 0;
        int maxAttempts = 20;

        while (attempts < maxAttempts) {
            int x, y, z;

            synchronized (random) {
                x = random.nextInt(maxX - minX + 1) + minX;
                z = random.nextInt(maxZ - minZ + 1) + minZ;
                y = random.nextInt(maxY - minY + 1) + minY;
            }

            BlockPos pos = new BlockPos(x, y, z);
            BlockPos safePos = findSafePosition((ServerWorld) world, pos);
            if (safePos != null) {
              //  logger.info("save");
                return safePos;
            }
            attempts++;
        }
        //logger.info("No save");
        return null;
    }

    private static BlockPos findSafePosition(ServerWorld world, BlockPos pos) {
        int maxY;


        if (world.getRegistryKey() == World.NETHER) {
            maxY = 100;
        } else if (world.getRegistryKey() == World.END) {
            maxY = 100;
        } else {

            Chunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);


            pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);



            if (pos.getY() > 0 && isSafeFromLiquid(world, pos)) {
                return pos;
            } else {

                return null;
            }
        }


        int minY = 0;
        for (int y = maxY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());


            if (world.getBlockState(testPos.up()).isAir() &&
                    world.getBlockState(testPos.down()).isSolid() &&
                    isSafeFromLiquid(world, testPos)) {
                return testPos;
            }
        }

        return null;
    }

    private static boolean isSafeFromLiquid(World world, BlockPos pos) {
        for (int x = pos.getX() - 2; x <= pos.getX() + 2; x++) {
            for (int y = pos.getY() - 2; y <= pos.getY() + 2; y++) {
                for (int z = pos.getZ() - 2; z <= pos.getZ() + 2; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    FluidState fluidState = world.getFluidState(checkPos);
                    if (!fluidState.isEmpty()) {
                      //  logger.info("Liquid found at " + checkPos + " :(");
                        return false;
                    }
                }
            }
        }
       // logger.info("No liquids found");
        return true;
    }
}
package de.jutechs.spawn.commands;

import com.mojang.brigadier.Command;
import de.jutechs.spawn.BackPositionManager;
import de.jutechs.spawn.ConfigManager;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static de.jutechs.spawn.Main.previousPositionMap;
import static net.fabricmc.loader.impl.FabricLoaderImpl.MOD_ID;

public class SpawnARTPCommand {

    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);
    static final Map<UUID, Long> SpawncooldownMap = new HashMap<>();
    private static final Map<UUID, Long> RtpCooldownMap = new HashMap<>();
    static final Map<UUID, Boolean> teleportInProgressMap = new HashMap<>();

    private static final ThreadLocalRandom random = ThreadLocalRandom.current();

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static boolean is_Rtp;

    public static void sendTitle(ServerPlayerEntity player, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut, Formatting titleColor, Formatting subtitleColor) {
        Text title = Text.literal(titleText).formatted(titleColor);
        Text subtitle = Text.literal(subtitleText).formatted(subtitleColor);

        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
    }

    public static int teleportToRandomSafePosition(ServerCommandSource source, int searchAreaRadius, String dimension) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();
        long cooldownTime;

        if (teleportInProgressMap.getOrDefault(playerId, false)) {
            player.sendMessage(Text.literal("Teleportation already in progress. Please wait.")
                    .formatted(Formatting.RED), false);
            return Command.SINGLE_SUCCESS;
        }

        if (is_Rtp) {
            cooldownTime = ConfigManager.config.RTPCooldown;
        } else {
            cooldownTime = ConfigManager.config.SpawnCooldown;
        }

        if (SpawncooldownMap.containsKey(playerId)) {
            long lastUseTime = SpawncooldownMap.get(playerId);
            if (currentTime - lastUseTime < cooldownTime) {
                long timeLeft = (cooldownTime - (currentTime - lastUseTime)) / 1000;
                player.sendMessage(Text.literal("Please wait " + timeLeft + " more seconds before using /spawn again.")
                        .formatted(Formatting.RED), false);
                return Command.SINGLE_SUCCESS;
            }
        }

        teleportInProgressMap.put(playerId, true);

        ServerWorld world;
        if (dimension.equalsIgnoreCase("nether")) {
            world = Objects.requireNonNull(player.getServer()).getWorld(World.NETHER);
        } else if (dimension.equalsIgnoreCase("end")) {
            world = Objects.requireNonNull(player.getServer()).getWorld(World.END);
        } else if (dimension.equalsIgnoreCase("overworld")) {
            world = Objects.requireNonNull(player.getServer()).getWorld(World.OVERWORLD);
        } else {
            player.sendMessage(Text.literal("Invalid dimension. Using default Overworld.").formatted(Formatting.RED), false);
            world = Objects.requireNonNull(player.getServer()).getWorld(World.OVERWORLD); // Default to Overworld
        }

        startCountdownAndTeleport(player, world, searchAreaRadius, dimension);

        return Command.SINGLE_SUCCESS;
    }

    private static void startCountdownAndTeleport(ServerPlayerEntity player, ServerWorld world, int searchAreaRadius, String dimension) {
        int countdownTime;
        if (is_Rtp) {
            countdownTime = ConfigManager.config.RTPTpDelay;
        } else {
            countdownTime = ConfigManager.config.SpawnTpDelay;
        }

        boolean notifyPlayerChat = ConfigManager.config.CountdownInChat;
        Vec3d initialPosition = player.getPos();
        final ScheduledFuture<?>[] countdownTasks = new ScheduledFuture<?>[countdownTime];
        final boolean[] hasMoved = {false};
        final boolean[] messageSent = {false};

        for (int i = countdownTime; i > 0; i--) {
            final int countdown = i;
            countdownTasks[countdownTime - i] = scheduler.schedule(() -> {
                if (player.getPos().squaredDistanceTo(initialPosition) > 0.1) {
                    if (!messageSent[0]) {
                        player.sendMessage(Text.literal("Teleportation canceled due to movement.")
                                .formatted(Formatting.RED), false);
                        messageSent[0] = true;
                    }
                    hasMoved[0] = true;
                    cancelRemainingCountdown(countdownTasks);
                    teleportInProgressMap.put(player.getUuid(), false);
                    return;
                }

                int fadeInTicks = ConfigManager.config.FadeInTicks;
                int stayTicks = ConfigManager.config.StayTicks;
                int fadeOutTicks = ConfigManager.config.FadeOutTicks;
                Text subtitleMessage = Text.literal("Please stand still for " + countdown + " more seconds")
                        .formatted(Formatting.YELLOW);

                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Teleporting").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleMessage));
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));

                if (notifyPlayerChat) {
                    player.sendMessage(subtitleMessage, false);
                }

                if (countdown == 1 && !hasMoved[0] && !messageSent[0]) {
                    CompletableFuture.supplyAsync(() -> findRandomSafePosition(world, searchAreaRadius))
                            .thenAccept(safePos -> {
                                scheduler.schedule(() -> {
                                    UUID playerId = player.getUuid();
                                    BackPositionManager.setPreviousPosition(playerId, (ServerWorld) player.getWorld(), player.getBlockPos());

                                    sendTitle(player, "Teleporting", "", fadeInTicks, stayTicks, fadeOutTicks, Formatting.GREEN, Formatting.GREEN);
                                    player.sendMessage(Text.literal("Teleported to %s".formatted(dimension.toUpperCase())).formatted(Formatting.GOLD), false);

                                    Objects.requireNonNull(player.getServer()).execute(() -> {
                                        if (safePos != null) {
                                            player.teleport(world, safePos.getX(), safePos.getY(), safePos.getZ(), player.getYaw(), player.getPitch());
                                        } else {
                                            player.sendMessage(Text.literal("No safe position found in %s!".formatted(dimension.toUpperCase())).formatted(Formatting.RED), false);
                                        }
                                        teleportInProgressMap.put(player.getUuid(), false);
                                        SpawncooldownMap.put(player.getUuid(), System.currentTimeMillis());
                                    });
                                }, 1, TimeUnit.SECONDS);
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

        int attempts = 0;
        int maxAttempts = 20;

        // Liste der blockierten Biome
        Set<RegistryKey<Biome>> blockedBiomes = new HashSet<>();
        blockedBiomes.add(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "ocean")));
        blockedBiomes.add(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "deep_ocean")));
        blockedBiomes.add(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "the_void")));
        blockedBiomes.add(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "small_end_islands")));
        blockedBiomes.add(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "cold_ocean")));
        blockedBiomes.add(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "warm_ocean")));

        while (attempts < maxAttempts) {
            int x, y, z;

            x = random.nextInt(maxX - minX + 1) + minX;
            z = random.nextInt(maxZ - minZ + 1) + minZ;

            BlockPos pos = new BlockPos(x, 0, z);

            // Schritt 1: Prüfe das Biom über RegistryEntry
            Chunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) {
                // Zugriff auf das Biom über RegistryEntry
                RegistryEntry<Biome> biomeEntry = world.getBiome(pos);

                // Prüfe, ob das aktuelle Biom auf der Blacklist steht
                if (blockedBiomes.contains(biomeEntry.getKey().orElse(null))) {
                    // Wenn das Biom blockiert ist, wähle eine neue Position
                    attempts++;
                    continue;
                }
            }

            // Schritt 2: Dimension-spezifische Höhenprüfung
            int height = 0;
            if (world.getRegistryKey() == World.NETHER) {
                // Spezielle Prüfung für den Nether: Verwende feste Y-Werte
                height = getSafeYInNether(world, pos);  // Eine Methode, die die sichere Y-Position im Nether findet
            } else if (world.getRegistryKey() == World.END) {
                // Spezielle Prüfung für das End: Prüfe eine sichere Höhe manuell
                height = getSafeYInEnd(world, pos);  // Eine Methode, die die sichere Y-Position im End findet
            } else {
                // Overworld: Verwende die Heightmap
                height = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            }

            // Schritt 3: Prüfe die sichere Position (nur Flüssigkeitssicherheitscheck)
            if (height > 0) {
                BlockPos safePos = findSafePosition((ServerWorld) world, new BlockPos(x, height, z), 2);
                if (safePos != null) {
                    return safePos;  // Sichere Position gefunden
                }
            }

            attempts++;
        }

        return null;  // Keine sichere Position gefunden
    }

    private static int getSafeYInNether(World world, BlockPos pos) {
        int maxY = 120;  // Typische Obergrenze im Nether, unterhalb des Bedrock-Roofs
        int minY = 10;   // Typische Untergrenze im Nether, oberhalb von Lava-Seen

        for (int y = maxY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());

            // Prüfen, ob der Block unterhalb solid ist und der Block darüber Luft
            if (world.getBlockState(testPos).isSolidBlock(world, testPos) &&
                    world.getBlockState(testPos.up()).isAir()) {
                return y;  // Sichere Y-Position gefunden
            }
        }
        return -1;  // Keine sichere Position gefunden
    }

    private static int getSafeYInEnd(World world, BlockPos pos) {
        int maxY = 120;  // Typische Obergrenze im End
        int minY = -2;   // Typische Untergrenze im End

        for (int y = maxY; y >= minY; y--) {
            BlockPos testPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (world.getBlockState(testPos).isSolidBlock(world, testPos) && world.getBlockState(testPos.up()).isAir()) {
                // Solider Block unten, Luft oben => Sicherer Ort
                return y;
            }
        }
        return -1;  // Keine sichere Position gefunden
    }

    private static BlockPos findSafePosition(ServerWorld world, BlockPos pos, int radius) {
        // Keine Höhenabfrage, nur Flüssigkeitsprüfung
        if (isSafeFromLiquid(world, pos)) {
            return pos;
        } else {
            return null;
        }
    }

    // Optimierte Prüfung auf Flüssigkeit, inklusive der Blöcke neben dem Spieler
    private static boolean isSafeFromLiquid(World world, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        // Prüfe den Block unter dem Spieler
        BlockPos belowPos = new BlockPos(x, y - 1, z);
        if (!world.getFluidState(belowPos).isEmpty()) {
            return false;  // Flüssigkeit im Bodenblock gefunden
        }

        // Prüfe den Block, auf dem der Spieler steht
        BlockPos currentPos = new BlockPos(x, y, z);
        if (!world.getFluidState(currentPos).isEmpty()) {
            return false;  // Flüssigkeit im aktuellen Block gefunden
        }

        // Prüfe den Block über dem Spieler
        BlockPos abovePos = new BlockPos(x, y + 1, z);
        if (!world.getFluidState(abovePos).isEmpty()) {
            return false;  // Flüssigkeit im oberen Block gefunden
        }

        // Prüfe die angrenzenden Blöcke in derselben Höhe (X- und Z-Koordinaten)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                // Seitliche Blöcke in derselben Höhe wie der Spieler
                BlockPos sidePos = new BlockPos(x + dx, y, z + dz);
                if (!world.getFluidState(sidePos).isEmpty()) {
                    return false;  // Flüssigkeit in einem angrenzenden Block gefunden
                }

                // Seitliche Blöcke direkt über dem Spieler
                BlockPos sideAbovePos = new BlockPos(x + dx, y + 1, z + dz);
                if (!world.getFluidState(sideAbovePos).isEmpty()) {
                    return false;  // Flüssigkeit in einem angrenzenden Block direkt über dem Spieler gefunden
                }

                // Seitliche Blöcke direkt unter dem Spieler
                BlockPos sideBelowPos = new BlockPos(x + dx, y - 1, z + dz);
                if (!world.getFluidState(sideBelowPos).isEmpty()) {
                    return false;  // Flüssigkeit in einem angrenzenden Block direkt unter dem Spieler gefunden
                }
            }
        }

        return true;  // Keine Flüssigkeit gefunden, Position ist sicher
    }
}

package de.jutechs.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.jutechs.spawn.commands.LastDeathCommand;
import de.jutechs.spawn.commands.SpawnARTPCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.command.CommandSource.suggestMatching;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static de.jutechs.spawn.commands.BackCommand.startCountdownAndTeleportBack;
import static de.jutechs.spawn.commands.SpawnARTPCommand.teleportToRandomSafePosition;

public class Main implements ModInitializer {

    public static final Map<UUID, Pair<ServerWorld, BlockPos>> previousPositionMap = new ConcurrentHashMap<>();
    public static final String MOD_ID = "Spawn"; // Replace "your_mod_id" with your actual mod ID

    @Override
    public void onInitialize() {
        ConfigManager.loadConfig();

        // Initialize the cache refilling process on server start
        ServerLifecycleEvents.SERVER_STARTED.register(TpCacheManager::initializeCacheRefilling);

        // Shutdown the executors on server stop
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TpCacheManager.shutdownExecutors());

        // Register the server tick event to process pending teleports
        ServerTickEvents.START_SERVER_TICK.register(server -> SpawnARTPCommand.handlePendingTeleports());

        // Remove entries for offline players
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUuid();
            SpawnARTPCommand.teleportInProgressMap.remove(playerId);
            SpawnARTPCommand.cooldownMap.remove(playerId);
            SpawnARTPCommand.pendingTeleports.remove(playerId);
            previousPositionMap.remove(playerId);
        });

        // Tab completion for dimensions
        SuggestionProvider<ServerCommandSource> dimensionSuggestions = (context, builder) ->
                suggestMatching(new String[]{"overworld", "nether", "end"}, builder);

        // Register the /sp command with automatic dimension selection
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("sp")
                        .then(argument("dimension", StringArgumentType.string())
                                .suggests(dimensionSuggestions)
                                .executes(context -> {
                                    String dimension = StringArgumentType.getString(context, "dimension");
                                    return teleportToRandomSafePosition(context.getSource(), dimension, false);
                                })
                        )
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendError(Text.literal("Player not found."));
                                return 0;
                            }
                            String currentDimension = getPlayerDimension(player);
                            return teleportToRandomSafePosition(context.getSource(), currentDimension, false);
                        })
                )
        );

        // Register the /rtp command with automatic dimension selection
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("rtp")
                        .then(argument("dimension", StringArgumentType.string())
                                .suggests(dimensionSuggestions)
                                .executes(context -> {
                                    String dimension = StringArgumentType.getString(context, "dimension");
                                    return teleportToRandomSafePosition(context.getSource(), dimension, true);
                                })
                        )
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendError(Text.literal("Player not found."));
                                return 0;
                            }
                            String currentDimension = getPlayerDimension(player);
                            return teleportToRandomSafePosition(context.getSource(), currentDimension, true);
                        })
                )
        );

        // Register the /back command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("back")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendError(Text.literal("Player not found."));
                                return 0;
                            }
                            startCountdownAndTeleportBack(player);
                            return 1; // Success
                        })
                )
        );

        // Register the /lastdeath command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("lastdeath")
                        .executes(context -> LastDeathCommand.teleportToLastDeath(context.getSource()))
                )
        );

        // Register the /spcache command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("spcache")
                        .requires(source -> source.hasPermissionLevel(2)) // Only operators can execute the command
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            Map<String, String> cacheStatus = TpCacheManager.getCacheStatus();
                            for (Map.Entry<String, String> entry : cacheStatus.entrySet()) {
                                source.sendFeedback(() -> Text.literal(entry.getKey() + ": " + entry.getValue()).formatted(Formatting.GREEN), false);
                            }
                            return 1; // Success
                        })
                )
        );

        // Register the death event
        DeathPositionManager.registerDeathEvent();
    }

    // Method to determine the player's current dimension
    private String getPlayerDimension(ServerPlayerEntity player) {
        World world = player.getWorld();
        if (world.getRegistryKey() == World.NETHER) {
            return "nether";
        } else if (world.getRegistryKey() == World.END) {
            return "end";
        } else {
            return "overworld";
        }
    }
}

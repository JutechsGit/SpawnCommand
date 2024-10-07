package de.jutechs.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.jutechs.spawn.commands.LastDeathCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.command.CommandSource.suggestMatching;
import static de.jutechs.spawn.commands.BackCommand.startCountdownAndTeleportBack;
import static de.jutechs.spawn.commands.SpawnARTPCommand.teleportToRandomSafePosition;

public class Main implements ModInitializer {

    public static boolean is_Rtp;
    public static final Map<UUID, Pair<ServerWorld, BlockPos>> previousPositionMap = new HashMap<>();

    @Override
    public void onInitialize() {
        ConfigManager.loadConfig();

        int searchAreaRadius = ConfigManager.config.SpawnRange;

        // Tab-Vervollständigung für Dimensionen (Expression Lambda)
        SuggestionProvider<ServerCommandSource> dimensionSuggestions = (context, builder) ->
                suggestMatching(new String[]{"overworld", "nether", "end"}, builder);

        // Registrierung des /sp-Befehls mit automatischer Dimensionswahl
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("sp")
                        .then(argument("dimension", StringArgumentType.string())
                                .suggests(dimensionSuggestions)
                                .executes(context -> {
                                    is_Rtp = false;
                                    String dimension = StringArgumentType.getString(context, "dimension");
                                    return teleportToRandomSafePosition(context.getSource(), searchAreaRadius, dimension);
                                })
                        )
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendError(Text.literal("Player not found."));
                                return 0;  // Rückgabe bei fehlendem Spieler
                            }
                            String currentDimension = getPlayerDimension(player);  // Holen der aktuellen Dimension
                            return teleportToRandomSafePosition(context.getSource(), searchAreaRadius, currentDimension);
                        })
                )
        );

        int rTPRange = ConfigManager.config.RTPRange;

        // Registrierung des /rtp-Befehls mit automatischer Dimensionswahl
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("rtp")
                        .then(argument("dimension", StringArgumentType.string())
                                .suggests(dimensionSuggestions)
                                .executes(context -> {
                                    is_Rtp = true;
                                    String dimension = StringArgumentType.getString(context, "dimension");
                                    return teleportToRandomSafePosition(context.getSource(), rTPRange, dimension);
                                })
                        )
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendError(Text.literal("Player not found."));
                                return 0;  // Rückgabe bei fehlendem Spieler
                            }
                            String currentDimension = getPlayerDimension(player);  // Holen der aktuellen Dimension
                            return teleportToRandomSafePosition(context.getSource(), rTPRange, currentDimension);
                        })
                )
        );

        // Registrierung des /back-Befehls
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("back")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendError(Text.literal("Player not found."));
                                return 0;  // Rückgabe bei fehlendem Spieler
                            }
                            startCountdownAndTeleportBack(player);
                            return SINGLE_SUCCESS;
                        })
                )
        );

        // Registrierung des /lastdeath-Befehls
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("lastdeath")
                        .executes(context -> LastDeathCommand.teleportToLastDeath(context.getSource())) // Teleportiert den Spieler zur letzten Todesposition
                )
        );

        // Registrierung des Death Events
        DeathPositionManager.registerDeathEvent();
    }

    // Methode zur Ermittlung der aktuellen Dimension des Spielers
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

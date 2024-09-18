package de.jutechs.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static de.jutechs.spawn.commands.SpawnARTPCommand.teleportToRandomSafePosition;

public class Main implements ModInitializer {

    public static boolean is_Rtp;
    public static final Map<UUID, Pair<ServerWorld, BlockPos>> previousPositionMap = new HashMap<>();



    @Override
    public void onInitialize() {
        ConfigManager.loadConfig();

        int searchAreaRadius = ConfigManager.config.SpawnRange;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawn")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {
                                is_Rtp = false;
                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), searchAreaRadius, dimension);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), searchAreaRadius, "overworld"))
            );
        });
        int rTPRange = ConfigManager.config.RTPRange;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("rtp")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {
                                is_Rtp = true;
                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), rTPRange, dimension);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), rTPRange, "overworld"))
            );
        });

/*        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("back")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        startCountdownAndTeleportBack(player);
                        return 1;
                    })
            );

        });
        */

    }
}
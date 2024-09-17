package de.jutechs.spawn.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.jutechs.spawn.Main;
import de.jutechs.spawn.Utils.ConfigManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;

import static de.jutechs.spawn.Utils.TeleportManager.teleportToRandomSafePosition;

public class SpawnCommand {
    public static void registerSpawnCommand(){
        int searchAreaRadius = ConfigManager.config.SpawnRange;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawn")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {
                                Main.is_Rtp = false;
                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), searchAreaRadius, dimension, Main.is_Rtp);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), searchAreaRadius, "overworld", Main.is_Rtp))
            );
        });
    }
}

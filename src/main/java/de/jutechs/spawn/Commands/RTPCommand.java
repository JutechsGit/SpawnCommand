package de.jutechs.spawn.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.jutechs.spawn.Utils.ConfigManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import de.jutechs.spawn.Main;

import static de.jutechs.spawn.Utils.TeleportManager.teleportToRandomSafePosition;

public class RTPCommand {
    public static void registerRTPCommand(){
        int rTPRange = ConfigManager.config.RTPRange;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("rtp")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .executes(context -> {
                                Main.is_Rtp = true;
                                String dimension = StringArgumentType.getString(context, "dimension");
                                return teleportToRandomSafePosition(context.getSource(), rTPRange, dimension, Main.is_Rtp);
                            })
                    )
                    .executes(context -> teleportToRandomSafePosition(context.getSource(), rTPRange, "overworld", Main.is_Rtp))
            );
        });
    }
}

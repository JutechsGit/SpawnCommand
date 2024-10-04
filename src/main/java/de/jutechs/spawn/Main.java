package de.jutechs.spawn;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.jutechs.spawn.commands.SpawnCommand;
import de.jutechs.spawn.commands.RtpCommand;
import de.jutechs.spawn.commands.BackCommand;
import de.jutechs.spawn.commands.LastDeathCommand;
import de.jutechs.spawn.utils.DeathPositionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;

public class Main implements ModInitializer {

    @Override
    public void onInitialize() {
        // Lade die Konfiguration
        ConfigManager.loadConfig();

        int spawnRadius = ConfigManager.config.SpawnRange;
        int rtpRadius = ConfigManager.config.RTPRange;

        // Registriere den /spawn Befehl
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("spawn")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .suggests((context, builder) -> CommandSource.suggestMatching(new String[]{"overworld", "nether", "end"}, builder)) // Tab-Vervollständigung
                            .executes(context -> SpawnCommand.teleportToSpawn(context.getSource(), spawnRadius, StringArgumentType.getString(context, "dimension"))))
                    .executes(context -> SpawnCommand.teleportToSpawn(context.getSource(), spawnRadius, "overworld")) // Standardmäßig Overworld
            );
        });

        // Registriere den /rtp Befehl
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("rtp")
                    .then(CommandManager.argument("dimension", StringArgumentType.string())
                            .suggests((context, builder) -> CommandSource.suggestMatching(new String[]{"overworld", "nether", "end"}, builder)) // Tab-Vervollständigung
                            .executes(context -> RtpCommand.teleportToRandomPosition(context.getSource(), rtpRadius, StringArgumentType.getString(context, "dimension"))))
                    .executes(context -> RtpCommand.teleportToRandomPosition(context.getSource(), rtpRadius, "overworld")) // Standardmäßig Overworld
            );
        });

        // Registriere den /back Befehl
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("back")
                    .executes(context -> BackCommand.teleportBack(context.getSource())) // Teleportiert den Spieler zurück zur letzten Position
            );
        });

        // Registriere den /lastdeath Befehl
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("lastdeath")
                    .executes(context -> LastDeathCommand.teleportToLastDeath(context.getSource())) // Teleportiert den Spieler zur letzten Todesposition
            );
        });

        // Registriere das Todesereignis (für /lastdeath)
        DeathPositionManager.registerDeathEvent();

        // Debugging-Nachricht zur Überprüfung, ob die Registrierung erfolgreich war
        System.out.println("All commands registered successfully!");
    }
}
package de.jutechs.spawn.commands;

import de.jutechs.spawn.BackPositionManager;
import de.jutechs.spawn.DeathPositionManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;

public class LastDeathCommand {

    public static int teleportToLastDeath(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        // Überprüfen, ob eine letzte Todesposition existiert
        if (DeathPositionManager.hasLastDeathPosition(player.getUuid())) {
            BlockPos lastDeathPos = DeathPositionManager.getLastDeathPosition(player.getUuid());
            ServerWorld world = DeathPositionManager.getLastDeathWorld(player.getUuid());

            // Speichere die aktuelle Position des Spielers vor der Teleportation
            BackPositionManager.setPreviousPosition(player.getUuid(), (ServerWorld) player.getWorld(), player.getBlockPos());

            // Teleportiere den Spieler zur letzten Todesposition
            player.teleport(world, lastDeathPos.getX(), lastDeathPos.getY(), lastDeathPos.getZ(), player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("Teleported to your last death location."), false);
        } else {
            player.sendMessage(Text.literal("No last death location found!"), false);
        }

        return 1;
    }
}

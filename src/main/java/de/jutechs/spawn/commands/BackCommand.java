package de.jutechs.spawn.commands;

import de.jutechs.spawn.utils.BackPositionManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld; // ServerWorld importieren
import net.minecraft.text.Text; // Text importieren

public class BackCommand {

    public static int teleportBack(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        // Überprüfe, ob eine vorherige Position vorhanden ist
        if (BackPositionManager.hasPreviousPosition(player.getUuid())) {
            Pair<ServerWorld, BlockPos> previousPosition = BackPositionManager.getPreviousPosition(player.getUuid());
            ServerWorld world = previousPosition.getLeft();
            BlockPos pos = previousPosition.getRight();

            // Teleportiere den Spieler zurück zur letzten Position
            player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("Teleported back to your previous position."), false);
        } else {
            player.sendMessage(Text.literal("No previous position found!"), false);
        }

        return 1;
    }
}

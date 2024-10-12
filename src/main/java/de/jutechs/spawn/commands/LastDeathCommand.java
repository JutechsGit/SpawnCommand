package de.jutechs.spawn.commands;

import com.mojang.brigadier.Command;
import de.jutechs.spawn.BackPositionManager;
import de.jutechs.spawn.ConfigManager;
import de.jutechs.spawn.DeathPositionManager;
import de.jutechs.spawn.NotificationManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static de.jutechs.spawn.commands.SpawnARTPCommand.cooldownMap;
import static de.jutechs.spawn.commands.SpawnARTPCommand.teleportInProgressMap;

public class LastDeathCommand {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static int teleportToLastDeath(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        int countdownTime = ConfigManager.config.SpawnTpDelay; // no new value
        boolean notifyPlayerChat = ConfigManager.config.CountdownInChat;
        Vec3d initialPosition = player.getPos();
        final ScheduledFuture<?>[] countdownTasks = new ScheduledFuture<?>[countdownTime];
        final boolean[] hasMoved = {false};
        final boolean[] messageSent = {false};
        UUID playerId = player.getUuid();

        if (DeathPositionManager.hasLastDeathPosition(playerId)) {
            BlockPos previousPos = DeathPositionManager.getLastDeathPosition(playerId);
            ServerWorld previousWorld = DeathPositionManager.getLastDeathWorld(playerId);

            //teleportation with a lot of "fancy"
            //countdown technical
            for (int i = countdownTime; i > 0; i--) {
                final int countdown = i;
                countdownTasks[countdownTime - i] = scheduler.schedule(() -> {
                    if (player.getPos().squaredDistanceTo(initialPosition) > 0.1) {
                        if (!messageSent[0]) {
                            NotificationManager.sendChatMessage(player,"Teleportation canceled due to movement.",Formatting.RED);
                            messageSent[0] = true;
                        }
                        hasMoved[0] = true;
                        cancelRemainingCountdown(countdownTasks);
                        teleportInProgressMap.put(player.getUuid(), false);
                        return;
                    }

                    //Countdown
                    NotificationManager.sendCountdownTitle(player, countdown);

                    //check for disturbance
                    if (countdown == 1 && !hasMoved[0] && !messageSent[0]) {
                        scheduler.schedule(() -> {
                            player.getServer().execute(() -> {
                                BackPositionManager.setPreviousPosition(playerId, (ServerWorld) player.getWorld(), player.getBlockPos());
                                //NotificationManager.showEffects(player,true);
                                player.teleport(previousWorld, previousPos.getX(), previousPos.getY(), previousPos.getZ(), player.getYaw(), player.getPitch());
                                NotificationManager.sendChatMessage(player,"Teleported back to your last death.",Formatting.GOLD);
                                //NotificationManager.showEffects(player,false);

                                teleportInProgressMap.put(player.getUuid(), false);
                                cooldownMap.put(player.getUuid(), System.currentTimeMillis());
                            });
                        }, 1, TimeUnit.SECONDS);
                    }
                }, countdownTime - i, TimeUnit.SECONDS);
            }
        }else{
            NotificationManager.sendChatMessage(player,"No last death location found!",Formatting.RED);
    }
        return Command.SINGLE_SUCCESS;
    }
// if player moves cancel the countdown
private static void cancelRemainingCountdown(ScheduledFuture<?>[] tasks) {
    for (ScheduledFuture<?> task : tasks) {
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
    }
}
}

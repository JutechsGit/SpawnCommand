package de.jutechs.spawn.commands;

import de.jutechs.spawn.BackPositionManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import de.jutechs.spawn.ConfigManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import de.jutechs.spawn.NotificationManager;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static de.jutechs.spawn.commands.SpawnARTPCommand.cooldownMap;
import static de.jutechs.spawn.commands.SpawnARTPCommand.teleportInProgressMap;

public class BackCommand {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void startCountdownAndTeleportBack(ServerPlayerEntity player) {
        int countdownTime = ConfigManager.config.SpawnTpDelay; // no new value
        Vec3d initialPosition = player.getPos();
        final ScheduledFuture<?>[] countdownTasks = new ScheduledFuture<?>[countdownTime];
        final boolean[] hasMoved = {false};
        final boolean[] messageSent = {false};

        UUID playerId = player.getUuid();
        ServerWorld currentWorld = (ServerWorld) player.getWorld();

        //ask if player has a position submitted
        if (BackPositionManager.hasPreviousPosition(playerId)) {
            Pair<RegistryKey<World>, BlockPos> backPosition = BackPositionManager.getPreviousPosition(playerId);
            ServerWorld previousWorld = BackPositionManager.getWorldFromKey(currentWorld, backPosition.getLeft());
            if (previousWorld == null) {
                NotificationManager.sendChatMessage(player,"The previous dimension is not loaded or does not exist.",Formatting.RED);

                return; // Or handle it in some way
            }
            BlockPos previousPos = backPosition.getRight();


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
                        scheduler.schedule(() -> Objects.requireNonNull(player.getServer()).execute(() -> {

                            // Zwischenspeichern der Position aus dem /back-Speicher
                            ServerWorld targetWorld = previousWorld;
                            BlockPos targetPos = previousPos;

                            // Aktuelle Position als neue /back-Position speichern
                            BackPositionManager.setPreviousPosition(playerId, currentWorld, player.getBlockPos());

                            // Teleport zum gespeicherten /back-Ziel
                            //NotificationManager.showEffects(player,true);
                            player.teleport(targetWorld, targetPos.getX(), targetPos.getY(), targetPos.getZ(), player.getYaw(), player.getPitch());
                            NotificationManager.sendChatMessage(player,"Teleported back to your previous location.",Formatting.GOLD);
                            //NotificationManager.showEffects(player,false);

                            // Teleportstatus und Cooldown zurücksetzen
                            teleportInProgressMap.put(player.getUuid(), false);
                            cooldownMap.put(player.getUuid(), System.currentTimeMillis());

                        }), 1, TimeUnit.SECONDS);
                    }

                }, countdownTime - i, TimeUnit.SECONDS);
            }
        }else{
            NotificationManager.sendChatMessage(player,"You seem to not have been teleporting lately!",Formatting.RED);
        }
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
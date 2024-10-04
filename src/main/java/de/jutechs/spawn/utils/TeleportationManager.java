package de.jutechs.spawn.utils;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TeleportationManager {
    private final ScheduledExecutorService scheduler;
    private final ServerPlayerEntity player;
    private final Vec3d initialPosition;
    private final boolean notifyPlayerChat;
    private final int countdownTime;
    private final Runnable onTeleportComplete;
    private final Runnable onTeleportCancel;
    private ScheduledFuture<?>[] countdownTasks;

    public TeleportationManager(ScheduledExecutorService scheduler, ServerPlayerEntity player, Vec3d initialPosition, int countdownTime,
                                boolean notifyPlayerChat, Runnable onTeleportComplete, Runnable onTeleportCancel) {
        this.scheduler = scheduler;
        this.player = player;
        this.initialPosition = initialPosition;
        this.countdownTime = countdownTime;
        this.notifyPlayerChat = notifyPlayerChat;
        this.onTeleportComplete = onTeleportComplete;
        this.onTeleportCancel = onTeleportCancel;
        this.countdownTasks = new ScheduledFuture<?>[countdownTime];
    }

    public void startCountdown() {
        final boolean[] hasMoved = {false};
        final boolean[] messageSent = {false};

        for (int i = countdownTime; i > 0; i--) {
            final int countdown = i;
            countdownTasks[countdownTime - i] = scheduler.schedule(() -> {
                if (player.getPos().squaredDistanceTo(initialPosition) > 0.1) {
                    if (!messageSent[0]) {
                        player.sendMessage(Text.literal("Teleportation canceled due to movement.")
                                .formatted(Formatting.RED), false);
                        messageSent[0] = true;
                    }
                    hasMoved[0] = true;
                    cancelCountdown();
                    onTeleportCancel.run(); // Wenn der Spieler sich bewegt, wird die Teleportation abgebrochen
                    return;
                }

                // Nachricht zum Countdown
                if (notifyPlayerChat) {
                    player.sendMessage(Text.literal("Please stand still for " + countdown + " more seconds.")
                            .formatted(Formatting.YELLOW), false);
                }

                if (countdown == 1 && !hasMoved[0]) {
                    onTeleportComplete.run(); // Wenn der Countdown abgeschlossen ist und der Spieler sich nicht bewegt hat
                }
            }, countdownTime - i, TimeUnit.SECONDS);
        }
    }

    public void cancelCountdown() {
        for (ScheduledFuture<?> task : countdownTasks) {
            if (task != null && !task.isDone()) {
                task.cancel(false);
            }
        }
    }
}
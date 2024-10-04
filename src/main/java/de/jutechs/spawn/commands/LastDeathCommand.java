package de.jutechs.spawn.commands;

import com.mojang.brigadier.Command;
import de.jutechs.spawn.BackPositionManager;
import de.jutechs.spawn.ConfigManager;
import de.jutechs.spawn.DeathPositionManager;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static de.jutechs.spawn.commands.SpawnARTPCommand.SpawncooldownMap;
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
                            player.sendMessage(Text.literal("Teleportation canceled due to movement.")
                                    .formatted(Formatting.RED), false);
                            messageSent[0] = true;
                        }
                        hasMoved[0] = true;
                        cancelRemainingCountdown(countdownTasks);
                        teleportInProgressMap.put(player.getUuid(), false);
                        return;
                    }
                    //title properties
                    int fadeInTicks = ConfigManager.config.FadeInTicks;
                    int stayTicks = ConfigManager.config.StayTicks;
                    int fadeOutTicks = ConfigManager.config.FadeOutTicks;
                    Text subtitleMessage = Text.literal("Please stand still for " + countdown + " more seconds")
                            .formatted(Formatting.YELLOW);
                    //send titles
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Teleporting").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)));
                    player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleMessage));
                    player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
                    //ask if to spam the user
                    if (notifyPlayerChat) {
                        player.sendMessage(subtitleMessage, false);
                    }
                    //check for disturbance
                    if (countdown == 1 && !hasMoved[0] && !messageSent[0]) {
                        scheduler.schedule(() -> {
                            player.getServer().execute(() -> {
                                player.teleport(previousWorld, previousPos.getX(), previousPos.getY(), previousPos.getZ(), player.getYaw(), player.getPitch());
                                player.sendMessage(Text.literal("Teleported back to your last death.").formatted(Formatting.GOLD), false);

                                teleportInProgressMap.put(player.getUuid(), false);
                                SpawncooldownMap.put(player.getUuid(), System.currentTimeMillis());
                            });
                        }, 1, TimeUnit.SECONDS);
                    }
                }, countdownTime - i, TimeUnit.SECONDS);
            }
        }else{
            player.sendMessage(Text.literal("No last death location found!"), false);
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

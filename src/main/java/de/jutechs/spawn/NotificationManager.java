package de.jutechs.spawn;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;

import de.jutechs.spawn.ConfigManager;

public class NotificationManager {
    // Titel und Untertitel senden
    public static void sendTitle(ServerPlayerEntity player, String titleText, String subtitleText,
                                 Formatting titleFormat, Formatting subtitleFormat,
                                 int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Text title = Text.literal(titleText).formatted(titleFormat);
        Text subtitle = Text.literal(subtitleText).formatted(subtitleFormat);

        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
    }

    // Chat-Nachricht senden
    public static void sendChatMessage(ServerPlayerEntity player, String messageText, Formatting format) {
        Text message = Text.literal(messageText).formatted(format);
        player.sendMessage(message, false);
    }

    // Countdown-Titel anzeigen
    public static void sendCountdownTitle(ServerPlayerEntity player, int countdown) {
        sendTitle(player, "Teleporting", "Please stand still for " + countdown + " more seconds",
                Formatting.DARK_PURPLE, Formatting.YELLOW, ConfigManager.config.FadeInTicks, ConfigManager.config.StayTicks, ConfigManager.config.FadeOutTicks);

        if (ConfigManager.config.CountdownInChat) {
            sendChatMessage(player, "Please stand still for " + countdown + " more seconds", Formatting.YELLOW);
        }
    }


    public static void showParticles(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;

        Vec3d position = player.getPos();

        // Erzeuge Partikel um den Spieler und zeige sie nur dem Spieler an
        for (int i = 0; i < 20; i++) {
            double offsetX = (serverWorld.random.nextDouble() - 0.5) * 2.0;
            double offsetY = serverWorld.random.nextDouble() * 2.0;
            double offsetZ = (serverWorld.random.nextDouble() - 0.5) * 2.0;

            serverWorld.spawnParticles(player, ParticleTypes.PORTAL, true,
                    position.x + offsetX,
                    position.y + offsetY,
                    position.z + offsetZ,
                    1, 0, 0, 0, 0.1); // Geschwindigkeit und Streuung der Partikel
        }
    }

    // Methode zum Abspielen des Enderman-Teleport-Sounds für alle Spieler
    public static void playStartSound(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;

        // Abspielen des Start-Sounds an der Position des Spielers
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // Methode zum Abspielen des Enderman-Ziel-Sounds für alle Spieler
    public static void playTargetSound(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;

        // Abspielen des Ziel-Sounds an der Position des Spielers
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.8f); // Leicht veränderte Tonhöhe
    }

    // Methode zur Anzeige von Partikel- und Soundeffekten bei Start und Ziel
    public static void showEffects(ServerPlayerEntity player, boolean isStart) {
        showParticles(player);

        if (isStart) {
            playStartSound(player);
        } else {
            playTargetSound(player);
        }
    }
}

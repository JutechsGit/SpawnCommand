package de.jutechs.spawn.utils;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TitleUtils {

    // Methode zum Senden eines Titels und Untertitels an den Spieler
    public static void sendTitle(ServerPlayerEntity player, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut, Formatting titleColor, Formatting subtitleColor) {
        Text title = Text.literal(titleText).formatted(titleColor);
        Text subtitle = Text.literal(subtitleText).formatted(subtitleColor);

        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
    }

    // Methode zum Senden eines einfachen Titels (ohne Untertitel)
    public static void sendTitleOnly(ServerPlayerEntity player, String titleText, int fadeIn, int stay, int fadeOut, Formatting titleColor) {
        Text title = Text.literal(titleText).formatted(titleColor);
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
    }

    // Methode zum Senden eines einfachen Untertitels
    public static void sendSubtitleOnly(ServerPlayerEntity player, String subtitleText, Formatting subtitleColor) {
        Text subtitle = Text.literal(subtitleText).formatted(subtitleColor);
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
    }
}


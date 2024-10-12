package de.jutechs.spawn;

public class ModConfig {
    public int SpawnRange = 1000;
    public int RTPRange = 1000000;
    public long SpawnCooldown = 5000;
    public long RTPCooldown = 5000;
    public int SpawnTpDelay = 5;
    public int RTPTpDelay = 5;
    public boolean CountdownInChat = true;
    public int FadeInTicks = 0;
    public int StayTicks = 40;
    public int FadeOutTicks = 10;

    // New configuration options
    public int CacheSize = 10;
    public int OverworldMaxY = 200;
    public int OverworldMinY = 64;
    public int NetherMaxY = 120;
    public int NetherMinY = 10;
    public int EndMaxY = 100;
    public int EndMinY = 0;
    public int LiquidCheckRadius = 1;
}

package de.jutechs.spawn;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.*;

public class TpCacheManager {

    private static final Logger logger = LoggerFactory.getLogger("TpCacheManager");

    // Cache size from configuration
    public static final int CACHE_SIZE = ConfigManager.config.CacheSize;

    public static final ConcurrentLinkedQueue<BlockPos> overworldSpawnCache = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<BlockPos> overworldRtpCache = new ConcurrentLinkedQueue<>();

    public static final ConcurrentLinkedQueue<BlockPos> netherSpawnCache = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<BlockPos> netherRtpCache = new ConcurrentLinkedQueue<>();

    public static final ConcurrentLinkedQueue<BlockPos> endSpawnCache = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<BlockPos> endRtpCache = new ConcurrentLinkedQueue<>();

    // ExecutorService for asynchronous cache refilling
    private static final ExecutorService cacheRefillExecutor = Executors.newFixedThreadPool(6);

    // Variable for the tick counter
    private static int cacheRefillTickCounter = 0;



    public static void initializeCacheRefilling(MinecraftServer server) {
        // Register the tick event for cache refilling
        ServerTickEvents.START_SERVER_TICK.register(srv -> {
            cacheRefillTickCounter++;
            int delayTicks = 400; // Check every 20 seconds
            if (cacheRefillTickCounter >= delayTicks) {
                scheduleCacheRefill(srv);
                cacheRefillTickCounter = 0;
            }
        });
    }

    private static void scheduleCacheRefill(MinecraftServer server) {
        // Create a priority queue of caches based on their fill level
        PriorityQueue<CacheEntry> cacheQueue = new PriorityQueue<>(Comparator.comparingInt(CacheEntry::getFillLevel));

        cacheQueue.add(new CacheEntry(overworldSpawnCache, World.OVERWORLD, true));
        cacheQueue.add(new CacheEntry(overworldRtpCache, World.OVERWORLD, false));
        cacheQueue.add(new CacheEntry(netherSpawnCache, World.NETHER, true));
        cacheQueue.add(new CacheEntry(netherRtpCache, World.NETHER, false));
        cacheQueue.add(new CacheEntry(endSpawnCache, World.END, true));
        cacheQueue.add(new CacheEntry(endRtpCache, World.END, false));

        // Refill caches starting with the lowest fill level
        while (!cacheQueue.isEmpty()) {
            CacheEntry entry = cacheQueue.poll();
            if (entry.cache.size() < CACHE_SIZE) {
                refillCache(server, entry.dimension, entry.isSpawn, entry.cache);
                // Break after submitting one refill task to prevent overloading
                break;
            }
        }
    }

    private static void refillCache(MinecraftServer server, RegistryKey<World> dimension, boolean isSpawn, ConcurrentLinkedQueue<BlockPos> cache) {
        cacheRefillExecutor.submit(() -> {
            try {
                ServerWorld world = server.getWorld(dimension);
                int searchRadius = isSpawn ? ConfigManager.config.SpawnRange : ConfigManager.config.RTPRange;

                assert world != null;
                BlockPos safePos = SafePositionManager.findRandomSafePosition(world, searchRadius);
                if (safePos != null) {
                    cache.offer(safePos);
                }
                // If no position is found after attempts, it will try again in the next cycle
            } catch (Exception e) {
                logger.error("Error refilling cache for dimension " + dimension.getValue(), e);
            }
        });
    }


    private static class CacheEntry {
        public ConcurrentLinkedQueue<BlockPos> cache;
        public RegistryKey<World> dimension;
        public boolean isSpawn;

        public CacheEntry(ConcurrentLinkedQueue<BlockPos> cache, RegistryKey<World> dimension, boolean isSpawn) {
            this.cache = cache;
            this.dimension = dimension;
            this.isSpawn = isSpawn;
        }

        public int getFillLevel() {
            return cache.size();
        }
    }

    public static void shutdownExecutors() {
        cacheRefillExecutor.shutdown();
        try {
            if (!cacheRefillExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                cacheRefillExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cacheRefillExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void checkAndRefillCache(MinecraftServer server, RegistryKey<World> dimension, boolean isSpawn, ConcurrentLinkedQueue<BlockPos> cache) {
        int cacheSize = cache.size();
        if (cacheSize < CACHE_SIZE) {
            refillCache(server, dimension, isSpawn, cache);
        }
    }

    // Method for the /spcache command
    public static Map<String, String> getCacheStatus() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("Overworld Spawn Cache", overworldSpawnCache.size() + " / " + CACHE_SIZE);
        status.put("Overworld RTP Cache", overworldRtpCache.size() + " / " + CACHE_SIZE);
        status.put("Nether Spawn Cache", netherSpawnCache.size() + " / " + CACHE_SIZE);
        status.put("Nether RTP Cache", netherRtpCache.size() + " / " + CACHE_SIZE);
        status.put("End Spawn Cache", endSpawnCache.size() + " / " + CACHE_SIZE);
        status.put("End RTP Cache", endRtpCache.size() + " / " + CACHE_SIZE);
        return status;
    }

    public static ConcurrentLinkedQueue<BlockPos> getCache(RegistryKey<World> dimension, boolean isSpawn) {
        if (dimension == World.OVERWORLD) {
            return isSpawn ? overworldSpawnCache : overworldRtpCache;
        } else if (dimension == World.NETHER) {
            return isSpawn ? netherSpawnCache : netherRtpCache;
        } else if (dimension == World.END) {
            return isSpawn ? endSpawnCache : endRtpCache;
        }
        return null;
    }
}



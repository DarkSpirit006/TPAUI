package dev.darkspirit69.tpaui.update;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Coordinates cached Modrinth checks and delivers results on the server thread. */
public final class UpdateService {
    private static final long SUCCESS_CACHE_MILLIS = TimeUnit.HOURS.toMillis(6);
    private static final long EMPTY_CACHE_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private final JavaPlugin plugin;
    private final ModrinthClient client;
    private CompletableFuture<UpdateResult> inFlightCheck;
    private UpdateResult cachedResult;
    private long cachedAtMillis;

    public UpdateService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = new ModrinthClient(plugin, new VersionComparator());
    }

    public void check(final Consumer<UpdateResult> callback) {
        CompletableFuture<UpdateResult> check;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long cacheLifetime = cachedResult != null && cachedResult.hasRelease()
                    ? SUCCESS_CACHE_MILLIS
                    : EMPTY_CACHE_MILLIS;
            if (cachedResult != null && now - cachedAtMillis < cacheLifetime) {
                check = CompletableFuture.completedFuture(cachedResult);
            } else if (inFlightCheck != null && !inFlightCheck.isDone()) {
                check = inFlightCheck;
            } else {
                check = CompletableFuture.supplyAsync(client::fetchLatestRelease);
                inFlightCheck = check;
            }
        }

        check.whenComplete((result, error) -> deliverResult(check, result, error, callback));
    }

    private void deliverResult(
            CompletableFuture<UpdateResult> check,
            UpdateResult result,
            Throwable error,
            Consumer<UpdateResult> callback) {
        UpdateResult finalResult = result;
        if (error != null) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            plugin.getLogger().warning("Modrinth update check failed: " + cause.getMessage());
            finalResult = UpdateResult.failed();
        } else if (finalResult == null) {
            finalResult = UpdateResult.failed();
        }

        synchronized (this) {
            if (inFlightCheck == check) {
                inFlightCheck = null;
            }
            cachedResult = finalResult;
            cachedAtMillis = System.currentTimeMillis();
        }

        final UpdateResult deliveredResult = finalResult;
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.isEnabled()) {
                    callback.accept(deliveredResult);
                }
            }
        });
    }
}

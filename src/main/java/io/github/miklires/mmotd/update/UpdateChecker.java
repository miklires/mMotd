package io.github.miklires.mmotd.update;

import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class UpdateChecker {
    private UpdateChecker() {}
    public static void checkAsync(JavaPlugin plugin, String projectId) {
        if (projectId == null || projectId.isBlank()) return;
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().sendAsync(HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + projectId + "/version?loaders=%5B%22paper%22%5D")).timeout(Duration.ofSeconds(8)).header("User-Agent", "miklires/mMotd/" + plugin.getPluginMeta().getVersion()).build(), HttpResponse.BodyHandlers.ofString()).thenAccept(response -> { if (response.statusCode() >= 400) return; String marker = "\"version_number\":\""; int start = response.body().indexOf(marker); if (start < 0) return; start += marker.length(); int end = response.body().indexOf('"', start); if (end > start && compare(response.body().substring(start, end), plugin.getPluginMeta().getVersion()) > 0) plugin.getLogger().info("A newer mMotd version is available: " + response.body().substring(start, end)); }).exceptionally(error -> null);
    }
    static int compare(String left, String right) { int[] a = parts(left), b = parts(right); for (int i=0;i<3;i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]); return 0; }
    private static int[] parts(String value) { String[] raw=value.split("[.-]"); int[] out=new int[3]; for(int i=0;i<Math.min(3,raw.length);i++) try{out[i]=Integer.parseInt(raw[i]);}catch(NumberFormatException ignored){} return out; }
}

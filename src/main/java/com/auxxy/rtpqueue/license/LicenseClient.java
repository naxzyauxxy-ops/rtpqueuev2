package com.auxxy.rtpqueue.license;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Talks to the RTPQueue license server and decides whether this server may run
 * the plugin.
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>Every response is signed with Ed25519. The plugin holds only the public
 *       key, so a leaked jar cannot be used to mint "valid" replies.</li>
 *   <li>A random nonce is sent with each request and must be echoed back,
 *       which stops a captured success response from being replayed forever.</li>
 *   <li>The last good result is cached to disk. If the license server is
 *       unreachable the plugin keeps working for a configurable grace period so
 *       an outage on your side never takes customers offline.</li>
 * </ul>
 *
 * MADE BY AUXXY
 */
public final class LicenseClient {

    private final RTPQueuePlugin plugin;
    private final AtomicBoolean licensed = new AtomicBoolean(false);
    private final AtomicBoolean checking = new AtomicBoolean(false);

    private volatile String status = "Not checked yet";
    private volatile String plan = "";
    private volatile String customer = "";
    private volatile long expiresAt = 0L;
    private volatile long lastGoodCheck = 0L;

    private int taskId = -1;

    public LicenseClient(RTPQueuePlugin plugin) {
        this.plugin = plugin;
        loadCache();
    }

    public boolean isLicensed() {
        return licensed.get();
    }

    public String status() {
        return status;
    }

    public String plan() {
        return plan;
    }

    public String customer() {
        return customer;
    }

    public long expiresAt() {
        return expiresAt;
    }

    /** Kicks off the first check and schedules periodic re-validation. */
    public void start() {
        checkAsync(null);

        long everyMinutes = Math.max(15L, LicenseConstants.RECHECK_MINUTES);
        long ticks = everyMinutes * 60L * 20L;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> checkAsync(null), ticks, ticks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /**
     * Runs a validation off the main thread.
     *
     * @param whenDone optional callback, always delivered on the main thread
     */
    public void checkAsync(Runnable whenDone) {
        if (!checking.compareAndSet(false, true)) {
            return; // a check is already in flight
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                performCheck();
            } catch (Exception e) {
                handleUnreachable(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                checking.set(false);
                if (whenDone != null) {
                    Bukkit.getScheduler().runTask(plugin, whenDone);
                }
            }
        });
    }

    /* ------------------------------------------------------------- network */

    private void performCheck() throws Exception {
        String key = plugin.getConfig().getString("license.key", "").trim();
        if (key.isEmpty() || key.equalsIgnoreCase("PUT-YOUR-LICENSE-KEY-HERE")) {
            fail("No license key set in config.yml");
            return;
        }

        // Baked into the jar. The optional config override exists only so you can
        // point a test server elsewhere; customers never set it.
        String base = LicenseConstants.API_URL.trim();
        if (base.isEmpty() || !LicenseConstants.configured()) {
            fail("This build has no licensing endpoint compiled in (see LicenseConstants.java)");
            return;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        String nonce = UUID.randomUUID().toString();
        String body = buildRequest(key, nonce);

        URL url = URI.create(base + "/validate").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "RTPQueue/" + plugin.getDescription().getVersion());
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setDoOutput(true);

        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        String text = readAll(conn, code);
        conn.disconnect();

        Map<String, Object> response = Json.parseObject(text);

        if (!verifySignature(response)) {
            fail("Response signature invalid - check license.public-key");
            return;
        }
        if (!nonce.equals(Json.string(response, "nonce", ""))) {
            fail("Response did not match the request (possible replay)");
            return;
        }

        boolean valid = Json.bool(response, "valid");
        String message = Json.string(response, "message", Json.string(response, "reason", "Unknown"));

        if (valid) {
            licensed.set(true);
            plan = Json.string(response, "plan", "");
            customer = Json.string(response, "customer", "");
            expiresAt = Json.number(response, "expires_at", 0L);
            lastGoodCheck = System.currentTimeMillis();
            status = "Valid" + (plan.isEmpty() ? "" : " (" + plan + ")");
            saveCache();
            plugin.getLogger().info("License OK. " + (customer.isEmpty() ? "" : "Licensed to " + customer + ". ") + RTPQueuePlugin.MADE_BY);
        } else {
            // A signed rejection is authoritative: clear the cache so a previously
            // good result cannot keep a revoked license alive.
            clearCache();
            fail(message);
        }
    }

    private String buildRequest(String key, String nonce) {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("id", Hwid.serverId(plugin.getDataFolder().toPath()));
        server.put("hwid", Hwid.hardwareId());
        server.put("host", Hwid.hostname());
        server.put("port", Bukkit.getPort());
        server.put("version", Bukkit.getVersion());
        server.put("max_players", Bukkit.getMaxPlayers());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("product", LicenseConstants.PRODUCT);
        payload.put("nonce", nonce);
        payload.put("plugin_version", plugin.getDescription().getVersion());
        payload.put("server", server);
        return Json.canonical(payload);
    }

    private String readAll(HttpURLConnection conn, int code) throws Exception {
        java.io.InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            return "{}";
        }
        try (java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /** Rebuilds the signed bytes and checks them against the embedded public key. */
    private boolean verifySignature(Map<String, Object> response) {
        try {
            String sigB64 = Json.string(response, "signature", "");
            if (sigB64.isEmpty()) {
                return false;
            }
            String pubB64 = LicenseConstants.PUBLIC_KEY.trim();
            if (pubB64.isEmpty()) {
                return false;
            }

            Map<String, Object> payload = new LinkedHashMap<>(response);
            payload.remove("signature");
            byte[] message = Json.canonical(payload).getBytes(StandardCharsets.UTF_8);

            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubB64)));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(message);
            return verifier.verify(Base64.getDecoder().decode(sigB64));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not verify license signature: " + e.getMessage());
            return false;
        }
    }

    /* ------------------------------------------------------------ outcomes */

    private void fail(String reason) {
        licensed.set(false);
        status = reason;
        plugin.getLogger().severe("License check failed: " + reason);
        enforce();
    }

    /**
     * Network failures must not punish the customer, so the last good result
     * stays valid for the configured grace period.
     */
    private void handleUnreachable(String detail) {
        long graceHours = Math.max(0L, LicenseConstants.OFFLINE_GRACE_HOURS);
        long graceMs = graceHours * 60L * 60L * 1000L;
        long age = System.currentTimeMillis() - lastGoodCheck;

        if (lastGoodCheck > 0 && age < graceMs) {
            licensed.set(true);
            long hoursLeft = (graceMs - age) / (60L * 60L * 1000L);
            status = "Offline grace (" + hoursLeft + "h left)";
            plugin.getLogger().warning("License server unreachable (" + detail + "). Running on cached license for another " + hoursLeft + "h.");
        } else {
            licensed.set(false);
            status = "License server unreachable and grace period expired";
            plugin.getLogger().severe("License server unreachable (" + detail + ") and the grace period has run out.");
            enforce();
        }
    }

    /** Applies the configured action when the license is not valid. */
    private void enforce() {
        if (!LicenseConstants.DISABLE_WHEN_INVALID) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().severe("Disabling RTPQueue: " + status);
            Bukkit.getPluginManager().disablePlugin(plugin);
        });
    }

    /* --------------------------------------------------------------- cache */

    private File cacheFile() {
        return new File(plugin.getDataFolder(), ".license-cache.yml");
    }

    private void loadCache() {
        File file = cacheFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        lastGoodCheck = yaml.getLong("last-good", 0L);
        plan = yaml.getString("plan", "");
        customer = yaml.getString("customer", "");
        expiresAt = yaml.getLong("expires-at", 0L);
    }

    private void saveCache() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("last-good", lastGoodCheck);
            yaml.set("plan", plan);
            yaml.set("customer", customer);
            yaml.set("expires-at", expiresAt);
            yaml.set("note", "Cache for offline grace. Deleting this only forces a fresh check. " + RTPQueuePlugin.MADE_BY);
            yaml.save(cacheFile());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not write license cache: " + e.getMessage());
        }
    }

    private void clearCache() {
        lastGoodCheck = 0L;
        File file = cacheFile();
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete the license cache file.");
        }
    }
}

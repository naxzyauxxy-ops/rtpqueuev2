package com.auxxy.rtpqueue.license;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * Builds a stable identifier for this machine and this server installation.
 *
 * <p>Two separate values are produced:</p>
 * <ul>
 *   <li><b>HWID</b> - derived from hardware traits (MAC addresses, CPU count,
 *       OS). Survives reinstalling the plugin but changes when the customer
 *       moves to different hardware.</li>
 *   <li><b>Server ID</b> - a random UUID written next to the config the first
 *       time the plugin runs. Identifies this particular server instance, so a
 *       network running several servers on one box uses one licence slot each.</li>
 * </ul>
 *
 * MADE BY AUXXY
 */
public final class Hwid {

    private Hwid() {
    }

    /** Hashes hardware traits into a hex fingerprint. */
    public static String hardwareId() {
        List<String> parts = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            List<String> macs = new ArrayList<>();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                // Skip loopback/virtual adapters: they are not stable identifiers.
                if (nic.isLoopback() || nic.isVirtual() || !nic.isUp()) {
                    continue;
                }
                byte[] mac = nic.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) {
                    sb.append(String.format("%02x", b));
                }
                macs.add(sb.toString());
            }
            // Sort so adapter enumeration order cannot change the result.
            Collections.sort(macs);
            parts.addAll(macs);
        } catch (Exception ignored) {
            // Fall through: the remaining traits still produce a usable value.
        }

        parts.add(System.getProperty("os.name", ""));
        parts.add(System.getProperty("os.arch", ""));
        parts.add(String.valueOf(Runtime.getRuntime().availableProcessors()));

        return sha256Hex(String.join("|", parts));
    }

    /**
     * Reads (or creates) the per-installation server ID stored beside the config.
     *
     * @param dataFolder the plugin's data folder
     */
    public static String serverId(Path dataFolder) {
        Path file = dataFolder.resolve(".server-id");
        try {
            if (Files.exists(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) {
                    return existing;
                }
            }
            String fresh = UUID.randomUUID().toString();
            Files.createDirectories(dataFolder);
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            return fresh;
        } catch (IOException e) {
            // If the disk is read-only, fall back to a hardware-derived value so
            // validation still works rather than failing the server outright.
            return sha256Hex("fallback|" + hardwareId());
        }
    }

    /** Best-effort local hostname, used only for display in the dashboard. */
    public static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

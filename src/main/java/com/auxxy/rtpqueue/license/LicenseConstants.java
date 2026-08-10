package com.auxxy.rtpqueue.license;

/**
 * Licensing endpoint details, compiled into the jar.
 *
 * <p>Customers never see or set these. Their {@code config.yml} contains only
 * their licence key, which is all they should ever need to paste.</p>
 *
 * <p><b>Set these once, before you build.</b> Both values are printed on your
 * licensing dashboard under "Plugin setup", ready to copy.</p>
 *
 * <p>Embedding the <i>public</i> key here is safe: it can only verify
 * signatures, never create them. Someone who decompiles the jar learns nothing
 * that lets them forge a valid licence response.</p>
 *
 * MADE BY AUXXY
 */
public final class LicenseConstants {

    /** Which product this plugin is, matching the slug on your dashboard. */
    public static final String PRODUCT = "rtpqueue";

    /** Base URL of your licensing API, including /api/v1 and no trailing slash. */
    public static final String API_URL = "https://your-host:25619/api/v1";

    /** Ed25519 public key (base64 SPKI), copied from your dashboard. */
    public static final String PUBLIC_KEY = "";

    /** How often to re-check, in minutes. Minimum enforced is 15. */
    public static final long RECHECK_MINUTES = 180L;

    /**
     * Keep working this long if your licensing server is unreachable, so an
     * outage on your side never takes a paying customer's server offline.
     */
    public static final long OFFLINE_GRACE_HOURS = 72L;

    /** Shut the plugin down when the licence is definitively invalid. */
    public static final boolean DISABLE_WHEN_INVALID = true;

    private LicenseConstants() {
    }

    /** True once the two values above have actually been filled in. */
    public static boolean configured() {
        return !API_URL.isBlank()
                && !PUBLIC_KEY.isBlank()
                && !API_URL.contains("your-host");
    }
}

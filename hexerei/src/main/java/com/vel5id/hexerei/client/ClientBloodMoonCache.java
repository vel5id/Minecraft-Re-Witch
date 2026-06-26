package com.vel5id.hexerei.client;

/**
 * Client-side read-only mirror of the server's blood-moon flag, fed by {@code BloodMoonSyncS2CPacket}.
 *
 * <p>Intentionally NOT {@code @OnlyIn(Dist.CLIENT)}: the common S2C payload handler references it, so the
 * class must be loadable on a dedicated server (where its playToClient handler never runs). It holds only
 * a primitive flag, so loading it server-side is harmless.
 */
public final class ClientBloodMoonCache {
    private ClientBloodMoonCache() {}

    private static boolean active = false;

    public static void set(boolean a) {
        active = a;
    }

    public static boolean isActive() {
        return active;
    }

    public static void clear() {
        active = false;
    }
}

package com.vel5id.hexerei.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-side read-only mirror of the server's blood-moon flag, fed by {@code BloodMoonSyncS2CPacket}. */
@OnlyIn(Dist.CLIENT)
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

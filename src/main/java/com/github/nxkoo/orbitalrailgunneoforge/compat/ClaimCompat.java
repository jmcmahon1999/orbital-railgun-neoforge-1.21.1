package com.github.nxkoo.orbitalrailgunneoforge.compat;

import net.neoforged.fml.ModList;

public final class ClaimCompat {
    private static final boolean HAS_FTB = ModList.get().isLoaded("ftbchunks");
    private static final boolean HAS_OPAC = ModList.get().isLoaded("openpartiesandclaims");

    private ClaimCompat() {
    }

    public static boolean hasFTB() {
        return HAS_FTB;
    }

    public static boolean hasOPAC() {
        return HAS_OPAC;
    }

    public static boolean any() {
        return HAS_FTB || HAS_OPAC;
    }
}

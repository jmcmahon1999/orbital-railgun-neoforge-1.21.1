package com.github.nxkoo.orbitalrailgunneoforge.client;

import org.jetbrains.annotations.Nullable;

public final class ClientUtil {
    private ClientUtil() {}

    /** Parses "#RRGGBB" or "#RRGGBBAA" into normalized RGBA floats (0..1). Falls back to white if invalid. */
    public static float[] parseHexColor(@Nullable String hex, double fallbackAlphaIfNoAA) {
        int r = 255;
        int g = 255;
        int b = 255;
        int a = (int) Math.round(fallbackAlphaIfNoAA * 255.0);
        if (hex != null) {
            String s = hex.trim();
            if (s.startsWith("#")) s = s.substring(1);
            try {
                if (s.length() == 6) {
                    int rgb = Integer.parseInt(s, 16);
                    r = (rgb >> 16) & 0xFF;
                    g = (rgb >> 8) & 0xFF;
                    b = rgb & 0xFF;
                } else if (s.length() == 8) {
                    long rgba = Long.parseLong(s, 16);
                    r = (int) ((rgba >> 24) & 0xFF);
                    g = (int) ((rgba >> 16) & 0xFF);
                    b = (int) ((rgba >> 8) & 0xFF);
                    a = (int) (rgba & 0xFF);
                }
            } catch (NumberFormatException ignored) {}
        }
        return new float[] { r / 255f, g / 255f, b / 255f, a / 255f };
    }
}

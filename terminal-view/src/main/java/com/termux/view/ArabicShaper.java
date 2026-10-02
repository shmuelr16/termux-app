package com.termux.view;

import android.os.Build;

/**
 * Joins Arabic letters into their presentation forms so that they are drawn connected
 * instead of as isolated letters. Uses the ICU4J implementation that is part of Android
 * since Android 7.0 (API level 24) and does nothing on older versions.
 */
final class ArabicShaper {

    private static final boolean AVAILABLE = Build.VERSION.SDK_INT >= 24;

    /**
     * @return a copy of {@code line} with Arabic letters replaced by their contextual
     *         presentation forms, or {@code line} itself if shaping is not available or
     *         would not preserve character positions.
     */
    static char[] shape(char[] line, int charsUsed) {
        if (!AVAILABLE || charsUsed <= 0)
            return line;
        if (!containsArabic(line, charsUsed))
            return line;
        try {
            String shaped = IcuBidi.shape(new String(line, 0, charsUsed));
            if (shaped == null || shaped.length() != charsUsed)
                return line; // positions would not match the row layout any more
            return shaped.toCharArray();
        } catch (Throwable ignored) {
            return line;
        }
    }

    private static boolean containsArabic(char[] line, int charsUsed) {
        for (int i = 0; i < charsUsed; i++) {
            char c = line[i];
            if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F)
                || (c >= 0x08A0 && c <= 0x08FF) || (c >= 0xFB50 && c <= 0xFDFF)
                || (c >= 0xFE70 && c <= 0xFEFF))
                return true;
        }
        return false;
    }
}

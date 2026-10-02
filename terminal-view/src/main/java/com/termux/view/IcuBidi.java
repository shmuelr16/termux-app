package com.termux.view;

import android.icu.text.ArabicShaping;
import android.icu.text.Bidi;

/**
 * ICU4J backed implementation of {@link BidiLayout.VisualRuns} and Arabic shaping.
 * <p/>
 * This class references {@code android.icu} which only exists since Android 7.0 (API 24),
 * so it must only be loaded after checking {@code Build.VERSION.SDK_INT}.
 */
final class IcuBidi {

    static BidiLayout.VisualRuns visualRuns(char[] line, int charsUsed) {
        Bidi bidi = new Bidi(new String(line, 0, charsUsed), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        return new IcuVisualRuns(bidi);
    }

    static String shape(String text) {
        return ArabicShaping.shape(text, 0, text.length(), ArabicShaping.LETTER_SHAPE_BY_UNICODE);
    }

    private static final class IcuVisualRuns implements BidiLayout.VisualRuns {
        private final Bidi mBidi;

        IcuVisualRuns(Bidi bidi) {
            mBidi = bidi;
        }

        @Override public int count() { return mBidi.countRuns(); }

        @Override public int logicalStart(int run) { return mBidi.getLogicalStart(run); }

        @Override public int logicalLimit(int run) { return mBidi.getLogicalLimit(run); }

        @Override public boolean isRtl(int run) {
            return mBidi.getRunDirection(run) == Bidi.DIRECTION_RIGHT_TO_LEFT;
        }
    }
}

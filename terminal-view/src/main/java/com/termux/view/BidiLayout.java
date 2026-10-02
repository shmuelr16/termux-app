package com.termux.view;

import com.termux.terminal.WcWidth;

/**
 * Maps logical terminal columns to the visual (on screen) columns, implementing the
 * Unicode Bidirectional Algorithm (UAX#9) with mirroring (rule L4), so that
 * right-to-left text (Hebrew, Arabic, ...) is displayed the way it is in any
 * normal text rendering context.
 * <p/>
 * A terminal row is a flat array of cells where a logical column may hold a base
 * character plus any number of zero width characters (combining marks). This class
 * groups those into clusters, orders the clusters visually and remembers which
 * characters must be drawn mirrored.
 * <p/>
 * Rows without right-to-left characters never need this and are not touched, so the
 * visual layout is only computed for rows that actually contain RTL text.
 */
final class BidiLayout {

    private final int[] mVisualColumn;
    private final char[] mDisplay;

    private BidiLayout(int[] visualColumn, char[] display) {
        mVisualColumn = visualColumn;
        mDisplay = display;
    }

    /** The characters to draw: same layout as the terminal row, but shaped and mirrored where needed. */
    char[] displayChars() {
        return mDisplay;
    }

    /** Visual column on screen of the given logical column. */
    int visualColumn(int logicalColumn) {
        if (logicalColumn < 0 || logicalColumn >= mVisualColumn.length)
            return logicalColumn;
        int visual = mVisualColumn[logicalColumn];
        return visual < 0 ? logicalColumn : visual;
    }

    /** True if the given code point is a strong right-to-left character. */
    static boolean isStrongRtl(int codePoint) {
        return (codePoint >= 0x0590 && codePoint <= 0x05FF)   // Hebrew
            || (codePoint >= 0x0600 && codePoint <= 0x07BF)   // Arabic, Syriac, Thaana, NKo
            || (codePoint >= 0x0860 && codePoint <= 0x08FF)   // Arabic extended-A/B
            || (codePoint >= 0xFB1D && codePoint <= 0xFDFF)   // Hebrew/Arabic presentation forms A
            || (codePoint >= 0xFE70 && codePoint <= 0xFEFF)   // Arabic presentation forms B
            || (codePoint >= 0x10800 && codePoint <= 0x10FFF) // ancient RTL scripts
            || (codePoint >= 0x1E800 && codePoint <= 0x1EFFF);
    }

    /** True if the given code point is strong left-to-right or an explicit left-to-right mark. */
    static boolean isStrongLtr(int codePoint) {
        if (codePoint == 0x200E /* LRM */ || codePoint == 0x202A /* LRE */ || codePoint == 0x202D /* LRO */)
            return true;
        return Character.isLetterOrDigit(codePoint) && !isStrongRtl(codePoint);
    }

    /** Quick check whether a terminal row contains any strong right-to-left character. */
    static boolean containsRtl(char[] line, int charsUsed) {
        for (int i = 0; i < charsUsed; i++) {
            int codePoint = line[i];
            if (Character.isHighSurrogate(codePoint) && i + 1 < charsUsed)
                codePoint = Character.toCodePoint(line[i], line[++i]);
            if (isStrongRtl(codePoint))
                return true;
        }
        return false;
    }

    /** Code point at the given array index, or -1 if it is a lone low surrogate. */
    private static int codePointAt(char[] line, int index, int limit) {
        char c = line[index];
        if (Character.isHighSurrogate(c) && index + 1 < limit)
            return Character.toCodePoint(c, line[index + 1]);
        return c;
    }

    /** A maximal run of text in the row: the array index of its first character and its length. */
    private static final class Cluster {
        int index;
        int charCount;
        int column;
        int width;
    }

    /**
     * Compute the visual layout of a terminal row.
     *
     * @param line the row characters as stored by the terminal buffer.
     * @param charsUsed number of valid characters in {@code line}.
     * @param columns number of columns in the terminal.
     */
    static BidiLayout create(char[] line, int charsUsed, int columns) {
        int[] visualColumn = new int[Math.max(1, columns)];
        for (int i = 0; i < visualColumn.length; i++)
            visualColumn[i] = i;

        char[] display = ArabicShaper.shape(line, charsUsed);

        // Group the row into clusters: a base character plus following zero width characters.
        int capacity = charsUsed + 1;
        Cluster[] clusters = new Cluster[capacity];
        int clusterCount = 0;
        int column = 0;
        for (int index = 0; index < charsUsed && column < columns; ) {
            int codePoint = codePointAt(line, index, charsUsed);
            int charCount = Character.isHighSurrogate(line[index]) ? 2 : 1;
            Cluster cluster = new Cluster();
            cluster.index = index;
            cluster.column = column;
            cluster.width = WcWidth.width(codePoint);
            if (cluster.width < 1)
                cluster.width = 1;
            while (index + charCount < charsUsed && WcWidth.width(line, index + charCount) <= 0)
                charCount += Character.isHighSurrogate(line[index + charCount]) ? 2 : 1;
            cluster.charCount = charCount;
            clusters[clusterCount++] = cluster;
            index += charCount;
            column += cluster.width;
        }

        // Assign every cluster its visual column, walking the runs left to right on screen.
        VisualRuns runs = VisualRuns.create(line, charsUsed);
        int runCount = runs.count();
        int visual = 0;
        int cluster = 0;
        for (int run = 0; run < runCount && visual < columns; run++) {
            int start = runs.logicalStart(run);
            int limit = runs.logicalLimit(run);
            boolean rtl = runs.isRtl(run);
            if (rtl)
                BidiMirror.mirrorRun(display, start, limit);
            int first = cluster;
            while (first < clusterCount && clusters[first].index < start)
                first++;
            int last = first;
            while (last < clusterCount && clusters[last].index < limit)
                last++;
            if (rtl) {
                for (int i = last - 1; i >= first; i--) {
                    visualColumn[clusters[i].column] = visual;
                    visual += clusters[i].width;
                }
            } else {
                for (int i = first; i < last; i++) {
                    visualColumn[clusters[i].column] = visual;
                    visual += clusters[i].width;
                }
            }
            cluster = last;
        }

        return new BidiLayout(visualColumn, display);
    }

    /**
     * A set of text runs in visual order (left to right on screen), as returned by the
     * bidirectional algorithm. Terminal lines are left-to-right paragraphs, so runs are
     * listed in logical order and right-to-left runs are reversed internally.
     */
    interface VisualRuns {
        int count();
        /** Array index of the first character of the given visual run. */
        int logicalStart(int run);
        /** Array index after the last character of the given visual run. */
        int logicalLimit(int run);
        boolean isRtl(int run);

        static VisualRuns create(char[] line, int charsUsed) {
            if (android.os.Build.VERSION.SDK_INT >= 24)
                return IcuBidi.visualRuns(line, charsUsed);
            return SimpleVisualRuns.create(line, charsUsed);
        }
    }

    /**
     * Fallback for Android versions without ICU4J in the platform (below Android 7.0):
     * treats every stretch between strong left-to-right characters as one right-to-left
     * run. Mixed neutrals are simplified, but Hebrew and Arabic words, digits and the
     * punctuation around them end up in the right order.
     */
    static final class SimpleVisualRuns implements VisualRuns {
        private final int mCount;
        private final int[] mStarts;
        private final int[] mLimits;
        private final boolean[] mRtl;

        private SimpleVisualRuns(int count, int[] starts, int[] limits, boolean[] rtl) {
            mCount = count;
            mStarts = starts;
            mLimits = limits;
            mRtl = rtl;
        }

        static VisualRuns create(char[] line, int charsUsed) {
            int[] starts = new int[charsUsed + 1];
            int[] limits = new int[charsUsed + 1];
            boolean[] rtl = new boolean[charsUsed + 1];
            int count = 0;
            int previous = 0;
            for (int i = 0; i < charsUsed; ) {
                int codePoint = codePointAt(line, i, charsUsed);
                int step = Character.isHighSurrogate(line[i]) ? 2 : 1;
                if (!BidiLayout.isStrongRtl(codePoint)) {
                    i += step;
                    continue;
                }
                int start = i;
                while (i < charsUsed && !BidiLayout.isStrongLtr(codePointAt(line, i, charsUsed))) {
                    i += Character.isHighSurrogate(line[i]) ? 2 : 1;
                }
                if (start > previous) {
                    starts[count] = previous;
                    limits[count] = start;
                    rtl[count] = false;
                    count++;
                }
                starts[count] = start;
                limits[count] = i;
                rtl[count] = true;
                count++;
                previous = i;
            }
            if (charsUsed > previous) {
                starts[count] = previous;
                limits[count] = charsUsed;
                rtl[count] = false;
                count++;
            }
            if (count == 0 && charsUsed > 0) {
                starts[0] = 0;
                limits[0] = charsUsed;
                rtl[0] = false;
                count = 1;
            }
            return new SimpleVisualRuns(count, starts, limits, rtl);
        }

        @Override public int count() { return mCount; }

        @Override public int logicalStart(int run) { return mStarts[run]; }

        @Override public int logicalLimit(int run) { return mLimits[run]; }

        @Override public boolean isRtl(int run) { return mRtl[run]; }
    }
}

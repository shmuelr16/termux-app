package com.termux.view;

/**
 * Character mirroring for rule L4 of the Unicode Bidirectional Algorithm: characters with
 * the Bidi_Mirrored property must be drawn with their mirrored glyph when they appear in
 * a right-to-left run, so that brackets and parentheses keep pointing the right way.
 */
final class BidiMirror {

    private static final char[] FROM = {'(', ')', '[', ']', '{', '}', '<', '>',
        '\u2039', '\u203A', '\u00AB', '\u00BB', '\u3008', '\u3009', '\u3010', '\u3011'};
    private static final char[] TO = {')', '(', ']', '[', '}', '{', '>', '<',
        '\u203A', '\u2039', '\u00BB', '\u00AB', '\u3009', '\u3008', '\u3011', '\u3010'};

    private BidiMirror() {}

    /** @return the mirrored code point, or -1 if the character has no known mirrored form. */
    static int mirror(int codePoint) {
        for (int i = 0; i < FROM.length; i++) {
            if (FROM[i] == codePoint)
                return TO[i];
        }
        switch (codePoint) {
            case 0x226E: return 0x226F; // ≮ ≯
            case 0x226F: return 0x226E;
            case 0x2282: return 0x2283; // ⊂ ⊃
            case 0x2283: return 0x2282;
            case 0x2286: return 0x2287; // ⊆ ⊇
            case 0x2287: return 0x2286;
            default: return -1;
        }
    }

    /** Replace mirrored characters of the given range by their mirrored form (rule L4). */
    static void mirrorRun(char[] display, int start, int limit) {
        int end = Math.min(limit, display.length);
        for (int i = start; i < end; i++) {
            char c = display[i];
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c))
                continue;
            int mirrored = mirror(c);
            if (mirrored > 0)
                display[i] = (char) mirrored;
        }
    }
}

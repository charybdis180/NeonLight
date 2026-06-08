package com.charybdis.Neon_Lights;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class NeonSignGlyphs {
    public static final char ARROW_UP = '\uE000';
    public static final char ARROW_DOWN = '\uE001';
    public static final char ARROW_LEFT = '\uE002';
    public static final char ARROW_RIGHT = '\uE003';

    private static final Map<Character, String> GLYPH_MODELS = Map.ofEntries(
            Map.entry('0', "block/number_0"),
            Map.entry('1', "block/number_1"),
            Map.entry('2', "block/number_2"),
            Map.entry('3', "block/number_3"),
            Map.entry('4', "block/number_4"),
            Map.entry('5', "block/number_5"),
            Map.entry('6', "block/number_6"),
            Map.entry('7', "block/number_7"),
            Map.entry('8', "block/number_8"),
            Map.entry('9', "block/number_9"),
            Map.entry('!', "block/symbol_exclamation"),
            Map.entry('@', "block/symbol_at"),
            Map.entry('#', "block/symbol_hash"),
            Map.entry('$', "block/symbol_dollar"),
            Map.entry('%', "block/symbol_percent"),
            Map.entry('^', "block/symbol_caret"),
            Map.entry('&', "block/symbol_ampersand"),
            Map.entry('*', "block/symbol_asterisk"),
            Map.entry('(', "block/symbol_paren_open"),
            Map.entry(')', "block/symbol_paren_close"),
            Map.entry('-', "block/symbol_minus"),
            Map.entry('_', "block/symbol_underscore"),
            Map.entry('.', "block/symbol_period"),
            Map.entry(',', "block/symbol_comma"),
            Map.entry('?', "block/symbol_question"),
            Map.entry(ARROW_UP, "block/arrow_up"),
            Map.entry(ARROW_DOWN, "block/arrow_down"),
            Map.entry(ARROW_LEFT, "block/arrow_left"),
            Map.entry(ARROW_RIGHT, "block/arrow_right")
    );

    private NeonSignGlyphs() {
    }

    public static boolean isAllowed(String character) {
        if (character == null || character.length() != 1) {
            return character != null && character.isEmpty();
        }
        char c = character.charAt(0);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
            return true;
        }
        return GLYPH_MODELS.containsKey(c);
    }

    public static String sanitize(String character) {
        if (character == null || character.isEmpty()) {
            return "";
        }
        if (character.length() != 1 || !isAllowed(character)) {
            return "";
        }
        return character;
    }

    public static String glyphModelFor(String character) {
        if (character == null || character.length() != 1) {
            return null;
        }
        char c = character.charAt(0);
        if (c >= 'a' && c <= 'z') {
            return "block/letter_" + c;
        }
        if (c >= 'A' && c <= 'Z') {
            return "block/letter_upper_" + Character.toLowerCase(c);
        }
        return GLYPH_MODELS.get(c);
    }

    public static Stream<String> additionalModelPaths() {
        Set<String> paths = new LinkedHashSet<>();
        paths.add("block/letter_glyph");
        for (char glyph = 'a'; glyph <= 'z'; glyph++) {
            paths.add("block/letter_" + glyph);
            paths.add("block/letter_upper_" + glyph);
        }
        GLYPH_MODELS.values().forEach(paths::add);
        return paths.stream();
    }
}

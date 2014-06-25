package com.yrek.incant.glk;

import java.util.List;

public class SpeechMunger {
    public static String chooseInput(List<String> options) {
        String input = options.get(0);
        if ("south east".equals(input)) {
            return "southeast";
        } else if ("go south east".equals(input)) {
            return "go southeast";
        } else if ("no I".equals(input)) {
            return "north";
        } else if ("wat".equals(input)) {
            return "wait";
        } else if (input.startsWith("where the ") || input.startsWith("where a ") || input.startsWith("where an ")) {
            return "wear" + input.substring(5);
        }
        return input;
    }

    public static char chooseCharacterInput(List<String> options) {
        String input = options.get(0);
        if ("space".equals(input)) {
            return ' ';
        } else if ("enter".equals(input)) {
            return '\n';
        }
        return input.charAt(0);
    }
}

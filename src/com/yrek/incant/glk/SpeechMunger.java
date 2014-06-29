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
        } else if (input.startsWith("but ")) {
            return "put" + input.substring(3);
        } else if (input.startsWith("we the ") || input.startsWith("we a ") || input.startsWith("we an ")) {
            return "read" + input.substring(2);
        } else if (input.startsWith("going to ")) {
            return "go in" + input.substring(6);
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

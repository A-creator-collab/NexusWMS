package com.nexuswms.voice;

import java.util.*;

/**
 * Turns what a speech recogniser produces ("create order for five of sku one zero two three from acme") into the
 * typed grammar the CommandParser understands ("create order for 5 of sku-1023 from acme").
 * Pure, locale-fixed (English) and fully unit-tested.
 */
public final class SpokenNormalizer {
    private SpokenNormalizer() {}

    private static final Map<String, Integer> UNITS = new HashMap<>();
    private static final Map<String, Integer> TENS = new HashMap<>();
    static {
        String[] u = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
                "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
        for (int i = 0; i < u.length; i++) UNITS.put(u[i], i);
        UNITS.put("oh", 0);
        String[] t = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        for (int i = 2; i < t.length; i++) TENS.put(t[i], i * 10);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[,;:!?\"']", " ").replaceAll("\\bs\\.? ?k\\.? ?u\\b", "sku")
                .replaceAll("\\b(?:dash|hyphen|minus)\\b", "-").trim();
        s = s.replaceAll("^(?:hey nexus|ok nexus|nexus|please|can you|could you|i want to|i would like to|i'd like to)\\s+", "")
             .replaceAll("\\s+please$", "");
        List<String> tokens = new ArrayList<>(Arrays.asList(s.split("\\s+")));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.size(); ) {
            int j = i;
            while (j < tokens.size() && isNumberWord(tokens.get(j))) j++;
            if (j > i) {
                out.append(convert(tokens.subList(i, j))).append(' ');
                i = j;
            } else { out.append(tokens.get(i)).append(' '); i++; }
        }
        // "sku 1023", "sku - 1023", "sku1023" -> "sku-1023"
        return out.toString().trim().replaceAll("\\bsku\\s*-?\\s*(\\d+)", "sku-$1").replaceAll("\\s+", " ");
    }

    private static boolean isNumberWord(String w) {
        return UNITS.containsKey(w) || TENS.containsKey(w) || w.equals("hundred") || w.equals("thousand") || w.matches("\\d+");
    }

    /** A run of number words: single digits in a row are read digit-by-digit ("one zero two" -> 102), otherwise as a cardinal. */
    static String convert(List<String> run) {
        boolean allDigits = run.size() >= 2 && run.stream().allMatch(w -> w.matches("\\d") || (UNITS.containsKey(w) && UNITS.get(w) < 10));
        if (allDigits) {
            StringBuilder sb = new StringBuilder();
            for (String w : run) sb.append(w.matches("\\d") ? w : String.valueOf(UNITS.get(w)));
            return sb.toString();
        }
        if (run.size() == 1 && run.get(0).matches("\\d+")) return run.get(0);
        long total = 0, current = 0;
        for (String w : run) {
            if (w.matches("\\d+")) { current += Long.parseLong(w); }
            else if (UNITS.containsKey(w)) current += UNITS.get(w);
            else if (TENS.containsKey(w)) current += TENS.get(w);
            else if (w.equals("hundred")) current = (current == 0 ? 1 : current) * 100;
            else if (w.equals("thousand")) { total += (current == 0 ? 1 : current) * 1000; current = 0; }
        }
        return String.valueOf(total + current);
    }
}

package com.nexuswms.voice;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/** Deterministic grammar for typed and spoken commands (speech is first passed through {@link SpokenNormalizer}). */
@Component
public class CommandParser {
    private static final Pattern LOW = Pattern.compile("^(?:show |list |display |what is |what's )?(?:the )?low[- ]stock(?: items)?(?: (?:for|in) (?:the )?(?:category )?(.+))?$");
    private static final Pattern ORDER = Pattern.compile("^(?:create|place|make|new)(?: an| a)?(?: purchase)? order for (\\d+)(?: units| pieces)?(?: of)? (\\S+)(?: from (.+))?$");
    private static final Pattern VALUE = Pattern.compile("^(?:what is |what's |show |get )?(?:the )?(?:total )?inventory value$");
    private static final Pattern FORECAST = Pattern.compile("^(?:run |show |get )?(?:the )?(?:forecast|reorder recommendations?)$");
    private static final Pattern NAV = Pattern.compile("^(?:go to|open|show me|show|take me to)(?: the)? (dashboard|inventory|orders|suppliers|warehouse map|map|analytics|agents|approvals|audit|settings)(?: page| screen| view)?$");

    public ParsedCommand parse(String text) {
        if (text == null) return new ParsedCommand(ParsedCommand.Type.UNKNOWN, Map.of());
        String t = SpokenNormalizer.normalize(text).replaceAll("[.]+$", "").trim();
        Matcher m;
        if ((m = LOW.matcher(t)).matches()) {
            Map<String, String> a = new HashMap<>();
            if (m.group(1) != null) a.put("category", m.group(1).trim());
            return new ParsedCommand(ParsedCommand.Type.LOW_STOCK, a);
        }
        if ((m = ORDER.matcher(t)).matches()) {
            Map<String, String> a = new HashMap<>();
            a.put("quantity", m.group(1));
            a.put("sku", normalizeSku(m.group(2)));
            if (m.group(3) != null) a.put("supplier", m.group(3).trim());
            return new ParsedCommand(ParsedCommand.Type.CREATE_ORDER, a);
        }
        if (VALUE.matcher(t).matches()) return new ParsedCommand(ParsedCommand.Type.INVENTORY_VALUE, Map.of());
        if (FORECAST.matcher(t).matches()) return new ParsedCommand(ParsedCommand.Type.FORECAST, Map.of());
        if ((m = NAV.matcher(t)).matches()) {
            String v = m.group(1);
            return new ParsedCommand(ParsedCommand.Type.NAVIGATE, Map.of("view", v.equals("map") ? "Warehouse Map" : capitalize(v)));
        }
        if (t.equals("help") || t.equals("what can you do")) return new ParsedCommand(ParsedCommand.Type.HELP, Map.of());
        return new ParsedCommand(ParsedCommand.Type.UNKNOWN, Map.of("text", t));
    }

    static String normalizeSku(String s) {
        String u = s.toUpperCase();
        return u.matches("\\d+") ? "SKU-" + u : u;
    }

    private static String capitalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }
}

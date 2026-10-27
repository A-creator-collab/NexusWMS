package com.nexuswms.voice;

import java.util.Map;

public record ParsedCommand(Type type, Map<String, String> args) {
    public enum Type { LOW_STOCK, CREATE_ORDER, INVENTORY_VALUE, FORECAST, NAVIGATE, HELP, UNKNOWN }
    public String arg(String k) { return args.get(k); }
}

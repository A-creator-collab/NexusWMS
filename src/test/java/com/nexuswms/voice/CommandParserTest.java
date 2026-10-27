package com.nexuswms.voice;

import org.junit.jupiter.api.Test;
import static com.nexuswms.voice.ParsedCommand.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {
    private final CommandParser p = new CommandParser();

    @Test void lowStockWithCategory() {
        ParsedCommand c = p.parse("Show low stock for category Dairy");
        assertEquals(LOW_STOCK, c.type()); assertEquals("dairy", c.arg("category"));
    }
    @Test void lowStockWithoutCategory() { assertEquals(LOW_STOCK, p.parse("low stock").type()); assertNull(p.parse("low stock").arg("category")); }
    @Test void createOrder() {
        ParsedCommand c = p.parse("create order for 5 of SKU-1023 from Acme");
        assertEquals(CREATE_ORDER, c.type()); assertEquals("5", c.arg("quantity")); assertEquals("SKU-1023", c.arg("sku")); assertEquals("acme", c.arg("supplier"));
    }
    @Test void createOrderBareNumberSkuAndNoSupplier() {
        ParsedCommand c = p.parse("Create an order for 12 units of 1005");
        assertEquals(CREATE_ORDER, c.type()); assertEquals("SKU-1005", c.arg("sku")); assertNull(c.arg("supplier"));
    }
    @Test void inventoryValue() { assertEquals(INVENTORY_VALUE, p.parse("What is inventory value?").type()); }
    @Test void forecastAndHelp() { assertEquals(FORECAST, p.parse("forecast").type()); assertEquals(HELP, p.parse("help").type()); }
    @Test void unknownAndNull() { assertEquals(UNKNOWN, p.parse("make me a sandwich").type()); assertEquals(UNKNOWN, p.parse(null).type()); }

    @Test void spokenNumbersAndSkuAreNormalised() {
        ParsedCommand c = p.parse("create order for five of sku one zero two three from acme");
        assertEquals(CREATE_ORDER, c.type()); assertEquals("5", c.arg("quantity")); assertEquals("SKU-1023", c.arg("sku")); assertEquals("acme", c.arg("supplier"));
        ParsedCommand d = p.parse("Create an order for twenty five units of S K U one zero zero five");
        assertEquals("25", d.arg("quantity")); assertEquals("SKU-1005", d.arg("sku"));
    }
    @Test void politeFillerIsIgnored() { assertEquals(LOW_STOCK, p.parse("hey nexus show low stock please").type()); }
    @Test void navigationCommands() {
        assertEquals(NAVIGATE, p.parse("open warehouse map").type()); assertEquals("Warehouse Map", p.parse("go to the map").arg("view"));
        assertEquals("Inventory", p.parse("show inventory").arg("view")); assertEquals(INVENTORY_VALUE, p.parse("show inventory value").type());
    }
}

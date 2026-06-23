package io.quarkus.tools.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JekyllFiltersExtensionTest {

    @Test
    void testCapitalizeNormalString() {
        assertEquals("Hello", JekyllFiltersExtension.capitalize("hello"));
    }

    @Test
    void testCapitalizeAlreadyCapitalized() {
        assertEquals("Hello", JekyllFiltersExtension.capitalize("Hello"));
    }

    @Test
    void testCapitalizeSingleChar() {
        assertEquals("A", JekyllFiltersExtension.capitalize("a"));
    }

    @Test
    void testCapitalizeEmpty() {
        assertEquals("", JekyllFiltersExtension.capitalize(""));
    }

    @Test
    void testCapitalizeNull() {
        assertNull(JekyllFiltersExtension.capitalize(null));
    }
}

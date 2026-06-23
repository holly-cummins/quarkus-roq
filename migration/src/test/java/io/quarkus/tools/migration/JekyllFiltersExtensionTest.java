package io.quarkus.tools.migration;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JekyllFiltersExtensionTest {

    @Test
    void testRfc822FromLocalDateTime() {
        LocalDateTime dt = LocalDateTime.of(2024, 3, 15, 10, 30, 0);
        String result = JekyllFiltersExtension.rfc822(dt);
        assertEquals("Fri, 15 Mar 2024 10:30:00 +0000", result);
    }

    @Test
    void testRfc822AlwaysEnglish() {
        LocalDateTime dt = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        String result = JekyllFiltersExtension.rfc822(dt);
        assertTrue(result.startsWith("Mon"), "Day name should be in English");
        assertTrue(result.contains("Jan"), "Month name should be in English");
    }

    @Test
    void testEscapeHtml() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;",
                JekyllFiltersExtension.escapeHtml("&<>\"'"));
    }

    @Test
    void testEscapeHtmlPlainText() {
        assertEquals("hello world", JekyllFiltersExtension.escapeHtml("hello world"));
    }

    @Test
    void testEscapeHtmlNull() {
        assertEquals("", JekyllFiltersExtension.escapeHtml(null));
    }

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

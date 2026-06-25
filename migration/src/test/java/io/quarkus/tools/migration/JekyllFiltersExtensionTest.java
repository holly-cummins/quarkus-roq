package io.quarkus.tools.migration;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import java.util.List;

import io.quarkus.qute.RawString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @Test
    void testSplitByDot() {
        var parts = JekyllFiltersExtension.split("3.17", ".");
        assertEquals(2, parts.size());
        assertEquals("3", parts.get(0).toString());
        assertEquals("17", parts.get(1).toString());
    }

    @Test
    void testSplitByComma() {
        var parts = JekyllFiltersExtension.split("a,b,c", ",");
        assertEquals(3, parts.size());
        assertEquals("a", parts.get(0).toString());
        assertEquals("b", parts.get(1).toString());
        assertEquals("c", parts.get(2).toString());
    }

    @Test
    void testSplitNull() {
        assertEquals(List.of(), JekyllFiltersExtension.split(null, ","));
    }

    @Test
    void testSplitEmpty() {
        assertEquals(List.of(), JekyllFiltersExtension.split("", ","));
    }

    @Test
    void testSplitReturnsRawStrings() {
        var parts = JekyllFiltersExtension.split("<p>before</p>|<p>after</p>", "|");
        assertEquals(2, parts.size());
        assertInstanceOf(RawString.class, parts.get(0));
        assertInstanceOf(RawString.class, parts.get(1));
        assertEquals("<p>before</p>", parts.get(0).toString());
        assertEquals("<p>after</p>", parts.get(1).toString());
    }

    @Test
    void testSplitTrimmedReturnsRawStrings() {
        var parts = JekyllFiltersExtension.splitTrimmed(" <p>a</p> , <p>b</p> ", ",");
        assertEquals(2, parts.size());
        assertInstanceOf(RawString.class, parts.get(0));
        assertEquals("<p>a</p>", parts.get(0).toString());
        assertEquals("<p>b</p>", parts.get(1).toString());
    }
}

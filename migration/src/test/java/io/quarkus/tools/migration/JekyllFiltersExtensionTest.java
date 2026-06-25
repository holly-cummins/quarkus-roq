package io.quarkus.tools.migration;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import java.util.List;

import io.quarkus.qute.RawString;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
        assertEquals(List.of("3", "17"), JekyllFiltersExtension.split("3.17", "."));
    }

    @Test
    void testSplitByComma() {
        assertEquals(List.of("a", "b", "c"), JekyllFiltersExtension.split("a,b,c", ","));
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
    void testWhereExpSingleConditionGreaterThan() {
        var items = List.of(
                Map.of("urldate", "2021-09-14", "title", "A"),
                Map.of("urldate", "2021-10-01", "title", "B"),
                Map.of("urldate", "2021-08-01", "title", "C"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate > '2021-09-01'");
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpSingleConditionLessThanOrEqual() {
        var items = List.of(
                Map.of("urldate", "2021-09-14", "title", "A"),
                Map.of("urldate", "2021-10-01", "title", "B"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate <= '2021-09-14'");
        assertEquals(1, result.size());
    }

    @Test
    void testWhereExpMultipleConditions() {
        var items = List.of(
                Map.of("urldate", "2021-08-01"),
                Map.of("urldate", "2021-09-14"),
                Map.of("urldate", "2021-10-01"),
                Map.of("urldate", "2021-11-01"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub",
                List.of("pub.urldate > '2021-09-01'", "pub.urldate <= '2021-10-01'"));
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpNullItems() {
        var result = (List<?>) JekyllFiltersExtension.whereExp(null, "pub", "pub.date > '2021-01-01'");
        assertEquals(List.of(), result);
    }

    @Test
    void testWhereExpEquals() {
        var items = List.of(
                Map.of("type", "article"),
                Map.of("type", "video"),
                Map.of("type", "article"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "item", "item.type == 'article'");
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpGreaterThanOrEqual() {
        var items = List.of(
                Map.of("urldate", "2021-09-01"),
                Map.of("urldate", "2021-09-14"),
                Map.of("urldate", "2021-08-01"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate >= '2021-09-01'");
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpJsonArrayReturnsJsonArray() {
        var items = new JsonArray()
                .add(new JsonObject().put("urldate", "2021-09-14"))
                .add(new JsonObject().put("urldate", "2021-08-01"));
        var result = JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate > '2021-09-01'");
        assertInstanceOf(JsonArray.class, result);
        assertEquals(1, ((JsonArray) result).size());
    }

    @Test
    void testSplitRawReturnsRawStrings() {
        var parts = JekyllFiltersExtension.splitRaw("<p>before</p>|<p>after</p>", "|");
        assertEquals(2, parts.size());
        assertInstanceOf(RawString.class, parts.get(0));
        assertInstanceOf(RawString.class, parts.get(1));
        assertEquals("<p>before</p>", parts.get(0).toString());
        assertEquals("<p>after</p>", parts.get(1).toString());
    }
}

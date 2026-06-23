package io.quarkus.tools.migration.jekyll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JekyllFrontMatterConverterTest {

    private JekyllFrontMatterConverter converter;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    @BeforeEach
    void setUp() {
        converter = new JekyllFrontMatterConverter();
    }

    // --- Pagination tests ---

    @Test
    void testPaginationBasic(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                layout: blog
                title: "Blog"
                pagination:
                  enabled: true
                ---
                """);

        converter.convertPagination(contentDir, null);

        String result = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(result.contains("paginate:"));
        assertTrue(result.contains("collection: posts"));
        assertTrue(result.contains("size: 10"));
        assertTrue(result.contains("link: blog/page/:page"));
        assertFalse(result.contains("pagination:"));
        assertFalse(result.contains("enabled: true"));
    }

    @Test
    void testPaginationWithCustomCollection(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("articles.md"), """
                ---
                layout: list
                pagination:
                  enabled: true
                ---
                """);

        String configYaml = """
                pagination:
                  collection: articles
                  per_page: 5
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.convertPagination(contentDir, config);

        String result = Files.readString(contentDir.resolve("articles.md"));
        assertTrue(result.contains("collection: articles"));
        assertTrue(result.contains("size: 5"));
        assertTrue(result.contains("link: articles/page/:page"));
    }

    @Test
    void testPaginationWithCustomPerPage(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                pagination:
                  enabled: true
                ---
                """);

        String configYaml = """
                pagination:
                  per_page: 25
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.convertPagination(contentDir, config);

        String result = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(result.contains("size: 25"));
    }

    @Test
    void testPaginationMultipleFiles(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                layout: blog
                pagination:
                  enabled: true
                ---
                """);
        Files.writeString(contentDir.resolve("author.md"), """
                ---
                layout: authors
                pagination:
                  enabled: true
                ---
                """);

        converter.convertPagination(contentDir, null);

        String blog = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(blog.contains("link: blog/page/:page"));

        String author = Files.readString(contentDir.resolve("author.md"));
        assertTrue(author.contains("link: author/page/:page"));
    }

    @Test
    void testPaginationNoConfig(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                pagination:
                  enabled: true
                ---
                """);

        converter.convertPagination(contentDir, null);

        String result = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(result.contains("collection: posts"));
        assertTrue(result.contains("size: 10"));
    }

    @Test
    void testPaginationLeavesNonPaginatedFilesAlone(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        String original = """
                ---
                layout: page
                title: "About"
                ---
                Some content.
                """;
        Files.writeString(contentDir.resolve("about.md"), original);

        converter.convertPagination(contentDir, null);

        assertEquals(original, Files.readString(contentDir.resolve("about.md")));
    }

    // --- Permalink tests ---

    @Test
    void testPermalinkMatchingFilename(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("about.md"), """
                ---
                layout: page
                permalink: /about/
                title: "About"
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("about.md"));
        assertFalse(result.contains("permalink:"));
        assertFalse(result.contains("aliases:"));
        assertTrue(result.contains("layout: page"));
        assertTrue(result.contains("title: \"About\""));
    }

    @Test
    void testPermalinkRemovalLeavesNoBlankLine(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("about.md"), """
                ---
                layout: page
                permalink: /about/
                title: "About"
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("about.md"));
        assertFalse(result.contains("\n\n"), "Should not have blank lines in frontmatter");
    }

    @Test
    void testPermalinkDifferentFromFilename(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("events.md"), """
                ---
                layout: page
                permalink: /community/events/
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("events.md"));
        assertTrue(result.contains("aliases: /community/events/"));
        assertFalse(result.contains("permalink:"));
    }

    @Test
    void testPermalinkAbsent(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        String original = """
                ---
                layout: page
                title: "About"
                ---
                """;
        Files.writeString(contentDir.resolve("about.md"), original);

        converter.convertPermalinks(contentDir);

        assertEquals(original, Files.readString(contentDir.resolve("about.md")));
    }

    @Test
    void testPermalinkWithoutSlashes(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("about.md"), """
                ---
                permalink: about
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("about.md"));
        assertFalse(result.contains("permalink:"));
        assertFalse(result.contains("aliases:"));
    }

    // --- Redirect deduplication tests ---

    @Test
    void testMergeRedirectDuplicates(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path redirectsDir = contentDir.resolve("redirects/guides");
        Files.createDirectories(redirectsDir);

        Files.writeString(redirectsDir.resolve("foo-guide.html"), """
                ---
                permalink: /guides/foo-guide.html
                newUrl: /guides/foo
                ---
                """);
        Files.writeString(redirectsDir.resolve("foo-guide.md"), """
                ---
                permalink: /guides/foo-guide/index.html
                newUrl: /guides/foo
                ---
                """);

        converter.mergeRedirectDuplicates(contentDir);

        assertTrue(Files.exists(redirectsDir.resolve("foo-guide.html")));
        assertFalse(Files.exists(redirectsDir.resolve("foo-guide.md")));

        String result = Files.readString(redirectsDir.resolve("foo-guide.html"));
        assertTrue(result.contains("/guides/foo-guide.html"));
        assertTrue(result.contains("/guides/foo-guide/index.html"));
        assertTrue(result.contains("newUrl: /guides/foo"));
    }

    @Test
    void testMergeRedirectDuplicatesNoMatch(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path redirectsDir = contentDir.resolve("redirects/guides");
        Files.createDirectories(redirectsDir);

        Files.writeString(redirectsDir.resolve("only-md.md"), """
                ---
                permalink: /guides/only-md/index.html
                newUrl: /guides/something
                ---
                """);
        Files.writeString(redirectsDir.resolve("only-html.html"), """
                ---
                permalink: /guides/only-html.html
                newUrl: /guides/other
                ---
                """);

        converter.mergeRedirectDuplicates(contentDir);

        assertTrue(Files.exists(redirectsDir.resolve("only-md.md")));
        assertTrue(Files.exists(redirectsDir.resolve("only-html.html")));
    }

    @Test
    void testConvertProjectMergesRedirectsInCollectionDir(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), "title: Test\n");

        Path redirectsDir = tempDir.resolve("_redirects/guides");
        Files.createDirectories(redirectsDir);
        Files.writeString(redirectsDir.resolve("bar-guide.html"), """
                ---
                permalink: /guides/bar-guide.html
                newUrl: /guides/bar
                ---
                """);
        Files.writeString(redirectsDir.resolve("bar-guide.md"), """
                ---
                permalink: /guides/bar-guide/index.html
                newUrl: /guides/bar
                ---
                """);

        converter.convertProject(tempDir);

        assertTrue(Files.exists(redirectsDir.resolve("bar-guide.html")));
        assertFalse(Files.exists(redirectsDir.resolve("bar-guide.md")));

        String result = Files.readString(redirectsDir.resolve("bar-guide.html"));
        assertTrue(result.contains("/guides/bar-guide.html"));
        assertTrue(result.contains("/guides/bar-guide/index.html"));
    }

    // --- Integration test ---

    @Test
    void testConvertProjectRunsBothTransforms(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), """
                pagination:
                  collection: posts
                  per_page: 10
                """);
        Files.writeString(tempDir.resolve("content/blog.md"), """
                ---
                layout: blog
                permalink: /blog/
                pagination:
                  enabled: true
                ---
                """);

        converter.convertProject(tempDir);

        String result = Files.readString(tempDir.resolve("content/blog.md"));
        assertFalse(result.contains("permalink:"));
        assertTrue(result.contains("paginate:"));
        assertTrue(result.contains("link: blog/page/:page"));
    }
}

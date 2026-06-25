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
        assertFalse(result.contains("link:"));
        assertTrue(result.contains("layout: page"));
        assertTrue(result.contains("title: \"About\""));
    }

    @Test
    void testPermalinkInSubdirNotStrippedWhenDifferentFromRelativePath(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path guidesDir = contentDir.resolve("guides");
        Files.createDirectories(guidesDir);
        Files.writeString(guidesDir.resolve("guides.md"), """
                ---
                layout: documentation
                permalink: /guides/
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(guidesDir.resolve("guides.md"));
        assertTrue(result.contains("link: /guides/"),
                "permalink /guides/ should become link because relative path is guides/guides, not guides");
        assertFalse(result.contains("permalink:"));
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
        assertTrue(result.contains("link: /community/events/"));
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
        assertFalse(result.contains("link:"));
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

    @Test
    void testConvertProjectConvertsPermalinksInCollectionDirs(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), "title: Test\n");

        Path guidesDir = tempDir.resolve("_guides");
        Files.createDirectories(guidesDir);
        Files.writeString(guidesDir.resolve("guides.md"), """
                ---
                layout: documentation
                permalink: /guides/
                ---
                """);

        converter.convertProject(tempDir);

        String result = Files.readString(guidesDir.resolve("guides.md"));
        assertTrue(result.contains("link: /guides/"),
                "permalink /guides/ in _guides/guides.md should become link because " +
                "post-move path guides/guides != guides");
        assertFalse(result.contains("permalink:"));
    }

    @Test
    void testConvertProjectStripsRedundantPermalinkInCollectionDir(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), "title: Test\n");

        Path guidesDir = tempDir.resolve("_guides");
        Files.createDirectories(guidesDir);
        Files.writeString(guidesDir.resolve("foo.md"), """
                ---
                permalink: /guides/foo/
                ---
                """);

        converter.convertProject(tempDir);

        String result = Files.readString(guidesDir.resolve("foo.md"));
        assertFalse(result.contains("permalink:"), "redundant permalink should be stripped");
        assertFalse(result.contains("link:"), "redundant permalink should not become link");
    }

    // --- Collection link template tests ---

    @Test
    void testAddCollectionLinkTemplatesWorksWithJekyllLayoutsDir(@TempDir Path tempDir) throws IOException {
        Path layoutsDir = tempDir.resolve("_layouts");
        Files.createDirectories(layoutsDir);
        Files.writeString(layoutsDir.resolve("post.html"), """
                ---
                layout: main
                ---
                {page.title}
                """);

        String configYaml = """
                defaults:
                  - scope:
                      type: posts
                    values:
                      layout: post
                      permalink: /blog/:title/
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.addCollectionLinkTemplates(tempDir, config);

        String result = Files.readString(layoutsDir.resolve("post.html"));
        assertTrue(result.contains("link: /blog/:name/"),
                "Should work with _layouts/ dir (pre-move). Got: " + result);
    }

    @Test
    void testAddCollectionLinkTemplatesTranslatesPermalink(@TempDir Path tempDir) throws IOException {
        Path layoutsDir = tempDir.resolve("templates/layouts");
        Files.createDirectories(layoutsDir);
        Files.writeString(layoutsDir.resolve("post.html"), """
                ---
                layout: main
                ---
                {page.title}
                """);

        String configYaml = """
                defaults:
                  - scope:
                      type: posts
                    values:
                      layout: post
                      permalink: /blog/:title/
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.addCollectionLinkTemplates(tempDir, config);

        String result = Files.readString(layoutsDir.resolve("post.html"));
        assertTrue(result.contains("link: /blog/:name/"),
                "Should translate Jekyll :title to Roq :name. Got: " + result);
        assertTrue(result.contains("layout: main"), "Should preserve existing frontmatter");
    }

    @Test
    void testAddCollectionLinkTemplatesForCustomCollection(@TempDir Path tempDir) throws IOException {
        Path layoutsDir = tempDir.resolve("templates/layouts");
        Files.createDirectories(layoutsDir);
        Files.writeString(layoutsDir.resolve("guide.html"), """
                ---
                layout: main
                ---
                {page.title}
                """);

        String configYaml = """
                collections:
                  guides:
                    output: true
                    layout: guide
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.addCollectionLinkTemplates(tempDir, config);

        String result = Files.readString(layoutsDir.resolve("guide.html"));
        assertTrue(result.contains("link: /:collection/:name/"),
                "Should use default link template when no permalink specified. Got: " + result);
    }

    @Test
    void testAddCollectionLinkTemplatesSkipsWhenLinkAlreadyPresent(@TempDir Path tempDir) throws IOException {
        Path layoutsDir = tempDir.resolve("templates/layouts");
        Files.createDirectories(layoutsDir);
        Files.writeString(layoutsDir.resolve("post.html"), """
                ---
                layout: main
                link: /custom/:slug/
                ---
                {page.title}
                """);

        String configYaml = """
                defaults:
                  - scope:
                      type: posts
                    values:
                      layout: post
                      permalink: /blog/:title/
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.addCollectionLinkTemplates(tempDir, config);

        String result = Files.readString(layoutsDir.resolve("post.html"));
        assertTrue(result.contains("link: /custom/:slug/"), "Should not overwrite existing link");
        assertFalse(result.contains("link: /blog/:name/"));
    }

    @Test
    void testAddCollectionLinkTemplatesSkipsMissingLayout(@TempDir Path tempDir) throws IOException {
        Path layoutsDir = tempDir.resolve("templates/layouts");
        Files.createDirectories(layoutsDir);

        String configYaml = """
                defaults:
                  - scope:
                      type: posts
                    values:
                      layout: post
                      permalink: /blog/:title/
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        // Should not throw even when layout file doesn't exist
        converter.addCollectionLinkTemplates(tempDir, config);
    }

    @Test
    void testAddCollectionLinkTemplatesDefaultPostsLayout(@TempDir Path tempDir) throws IOException {
        Path layoutsDir = tempDir.resolve("templates/layouts");
        Files.createDirectories(layoutsDir);
        Files.writeString(layoutsDir.resolve("post.html"), """
                ---
                layout: main
                ---
                {page.title}
                """);

        // No explicit layout or permalink config — posts default to "post" layout
        String configYaml = "title: My Blog\n";
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.addCollectionLinkTemplates(tempDir, config);

        String result = Files.readString(layoutsDir.resolve("post.html"));
        assertTrue(result.contains("link: /:collection/:name/"),
                "Should add default link to post layout even without explicit config. Got: " + result);
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

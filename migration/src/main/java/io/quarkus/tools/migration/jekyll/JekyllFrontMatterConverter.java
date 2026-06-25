package io.quarkus.tools.migration.jekyll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Converts Jekyll frontmatter in content files to Roq equivalents.
 * Handles pagination and permalink transformations.
 */
public class JekyllFrontMatterConverter {

    private static final Pattern PAGINATION_BLOCK = Pattern.compile(
            "^pagination:[ \\t]*\\n([ \\t]+.*\\n)*", Pattern.MULTILINE);

    private static final Pattern PERMALINK_LINE = Pattern.compile("^permalink:[ \\t]*(.*)\\n", Pattern.MULTILINE);

    private static final Pattern LINK_LINE = Pattern.compile("^link:[ \\t]", Pattern.MULTILINE);

    private final YAMLMapper yamlMapper = new YAMLMapper();

    /**
     * Run all frontmatter conversions on content files in a project directory.
     */
    public void convertProject(Path projectDir) throws IOException {
        Path contentDir = projectDir.resolve("content");
        if (!Files.isDirectory(contentDir)) {
            return;
        }

        Path configFile = projectDir.resolve("_config.yml");
        JsonNode config = Files.exists(configFile)
                ? yamlMapper.readTree(Files.readString(configFile))
                : null;

        // Merge redirect duplicates in content/ and in pre-move _<collection> dirs
        mergeRedirectDuplicates(contentDir);
        try (Stream<Path> dirs = Files.list(projectDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("_"))
                    .forEach(p -> {
                        try {
                            mergeRedirectDuplicates(p);
                        } catch (IOException e) {
                            System.err.println("Warning: could not deduplicate redirects in " + p + ": " + e.getMessage());
                        }
                    });
        }
        convertPermalinks(contentDir);
        // Also convert permalinks in pre-move _<collection> dirs.
        // Pass the collection name as a path prefix so that e.g. _guides/guides.md
        // compares against "guides/guides" (its post-move path), not just "guides".
        try (Stream<Path> collDirs = Files.list(projectDir)) {
            collDirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("_"))
                    .filter(p -> !p.getFileName().toString().equals("_posts"))
                    .forEach(p -> {
                        try {
                            String collectionName = p.getFileName().toString().substring(1);
                            convertPermalinks(p, collectionName);
                        } catch (IOException e) {
                            System.err.println("Warning: could not convert permalinks in " + p + ": " + e.getMessage());
                        }
                    });
        }
        addCollectionLinkTemplates(projectDir, config);
        convertPagination(contentDir, config);
    }

    /**
     * Convert Jekyll permalink frontmatter to Roq aliases.
     * If the permalink matches the file's effective path, it's redundant and removed.
     * Otherwise it becomes an alias.
     */
    public void convertPermalinks(Path contentDir) throws IOException {
        convertPermalinks(contentDir, "");
    }

    /**
     * Convert Jekyll permalink frontmatter to Roq aliases.
     *
     * @param pathPrefix prepended to the relative path for comparison — used for
     *                   pre-move collection dirs where _foo/bar.md will become content/foo/bar.md
     */
    public void convertPermalinks(Path contentDir, String pathPrefix) throws IOException {
        for (Path file : findContentFiles(contentDir)) {
            String content = Files.readString(file);
            Matcher matcher = PERMALINK_LINE.matcher(content);
            if (!matcher.find()) {
                continue;
            }

            String permalinkValue = matcher.group(1).trim()
                    .replaceAll("^['\"]|['\"]$", "");
            String normalized = permalinkValue.replaceAll("^/|/$", "");

            String relativePathNoExt = stripExtension(
                    contentDir.relativize(file).toString());
            String effectivePath = pathPrefix.isEmpty()
                    ? relativePathNoExt
                    : pathPrefix + "/" + relativePathNoExt;

            if (normalized.equals(effectivePath)) {
                content = PERMALINK_LINE.matcher(content).replaceFirst("");
            } else {
                content = PERMALINK_LINE.matcher(content).replaceFirst("link: " + Matcher.quoteReplacement(matcher.group(1)) + "\n");
            }

            Files.writeString(file, content);
        }
    }

    /**
     * Add {@code link:} to collection layout templates, translating Jekyll permalink
     * placeholders to their Roq equivalents.
     * <p>
     * Jekyll's {@code :title} placeholder is filename-derived, while Roq's {@code :slug}
     * is title-derived. This method reads the Jekyll permalink pattern for each collection
     * and translates it (e.g. {@code /blog/:title/} → {@code /blog/:name/}).
     * Collections without an explicit permalink get {@code /:collection/:name/}.
     */
    public void addCollectionLinkTemplates(Path projectDir, JsonNode config) throws IOException {
        // Check both pre-move (_layouts/) and post-move (templates/layouts/) locations
        Path[] layoutsDirs = {
                projectDir.resolve("_layouts"),
                projectDir.resolve("templates/layouts")
        };

        Map<String, CollectionInfo> collections = getCollectionInfo(config);
        for (Path layoutsDir : layoutsDirs) {
            if (!Files.isDirectory(layoutsDir)) {
                continue;
            }
            for (CollectionInfo info : collections.values()) {
                addLinkToLayout(layoutsDir, info.layout, info.linkTemplate());
            }
        }
    }

    record CollectionInfo(String layout, String permalink) {
        String linkTemplate() {
            if (permalink == null) {
                return "/:collection/:name/";
            }
            return translatePermalinkPlaceholders(permalink);
        }
    }

    static String translatePermalinkPlaceholders(String permalink) {
        return permalink
                .replace(":title", ":name")
                .replace(":categories", ":collection");
    }

    private Map<String, CollectionInfo> getCollectionInfo(JsonNode config) {
        Map<String, CollectionInfo> result = new HashMap<>();

        // Default: posts collection uses "post" layout
        result.put("posts", new CollectionInfo("post", null));

        if (config == null) {
            return result;
        }

        // Read explicit collection layouts
        if (config.has("collections")) {
            JsonNode collections = config.get("collections");
            if (collections.isObject()) {
                collections.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    JsonNode collectionConfig = entry.getValue();
                    if (collectionConfig.isObject() && collectionConfig.has("layout")) {
                        result.merge(name,
                                new CollectionInfo(collectionConfig.get("layout").asText(), null),
                                (old, neu) -> new CollectionInfo(neu.layout, old.permalink));
                    }
                });
            }
        }

        // Read layouts and permalinks from defaults section
        if (config.has("defaults")) {
            JsonNode defaults = config.get("defaults");
            if (defaults.isArray()) {
                for (JsonNode entry : defaults) {
                    if (!entry.has("scope") || !entry.has("values")) {
                        continue;
                    }
                    JsonNode scope = entry.get("scope");
                    if (!scope.has("type")) {
                        continue;
                    }
                    String type = scope.get("type").asText();
                    JsonNode values = entry.get("values");
                    String layout = values.has("layout") ? values.get("layout").asText() : null;
                    String permalink = values.has("permalink") ? values.get("permalink").asText() : null;

                    result.merge(type,
                            new CollectionInfo(
                                    layout != null ? layout : "post",
                                    permalink),
                            (old, neu) -> new CollectionInfo(
                                    neu.layout != null ? neu.layout : old.layout,
                                    neu.permalink != null ? neu.permalink : old.permalink));
                }
            }
        }

        return result;
    }

    private void addLinkToLayout(Path layoutsDir, String layoutName, String linkTemplate) throws IOException {
        Path layoutFile = layoutsDir.resolve(layoutName + ".html");
        if (!Files.exists(layoutFile)) {
            return;
        }

        String content = Files.readString(layoutFile);
        if (LINK_LINE.matcher(content).find()) {
            return;
        }

        // Insert link: after the opening --- line
        int fmStart = content.indexOf("---");
        if (fmStart < 0) {
            return;
        }
        int afterFirstLine = content.indexOf('\n', fmStart) + 1;
        content = content.substring(0, afterFirstLine)
                + "link: " + linkTemplate + "\n"
                + content.substring(afterFirstLine);
        Files.writeString(layoutFile, content);
    }

    /**
     * Convert Jekyll pagination frontmatter to Roq paginate syntax.
     * Reads collection and per_page from the Jekyll _config.yml pagination block.
     * Derives the link pattern from each page's filename.
     */
    public void convertPagination(Path contentDir, JsonNode config) throws IOException {
        String collection = getPaginationConfig(config, "collection", "posts");
        String size = getPaginationConfig(config, "per_page", "10");

        for (Path file : findContentFiles(contentDir)) {
            String content = Files.readString(file);
            if (!PAGINATION_BLOCK.matcher(content).find()) {
                continue;
            }

            String pageSlug = stripExtension(file.getFileName().toString());
            String replacement = "paginate:\n"
                    + "  collection: " + collection + "\n"
                    + "  size: " + size + "\n"
                    + "  link: " + pageSlug + "/page/:page\n";

            content = PAGINATION_BLOCK.matcher(content).replaceFirst(replacement);
            Files.writeString(file, content);
        }
    }

    /**
     * Merge duplicate redirect files where both foo.html and foo.md exist.
     * Jekyll redirect collections often have pairs covering both /foo.html and /foo/index.html URLs.
     * In Roq, .md compiles to .html, causing duplicate template errors.
     * Merges the .md permalink into the .html file as a YAML list of aliases, then deletes the .md.
     */
    public void mergeRedirectDuplicates(Path contentDir) throws IOException {
        try (Stream<Path> paths = Files.walk(contentDir)) {
            Map<String, Path> htmlFiles = new HashMap<>();
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".html"))
                    .forEach(p -> htmlFiles.put(stripExtension(p.getFileName().toString()), p));

            for (Map.Entry<String, Path> entry : htmlFiles.entrySet()) {
                Path mdFile = entry.getValue().getParent().resolve(entry.getKey() + ".md");
                if (!Files.exists(mdFile)) {
                    continue;
                }

                String htmlContent = Files.readString(entry.getValue());
                String mdContent = Files.readString(mdFile);

                Matcher htmlPermalink = PERMALINK_LINE.matcher(htmlContent);
                Matcher mdPermalink = PERMALINK_LINE.matcher(mdContent);

                if (!htmlPermalink.find() || !mdPermalink.find()) {
                    continue;
                }

                String htmlPl = htmlPermalink.group(1).trim().replaceAll("^['\"]|['\"]$", "");
                String mdPl = mdPermalink.group(1).trim().replaceAll("^['\"]|['\"]$", "");

                String merged = PERMALINK_LINE.matcher(htmlContent)
                        .replaceFirst("aliases:\n  - " + htmlPl + "\n  - " + mdPl + "\n");

                Files.writeString(entry.getValue(), merged);
                Files.delete(mdFile);
            }
        }
    }

    private String getPaginationConfig(JsonNode config, String key, String defaultValue) {
        if (config == null || !config.has("pagination")) {
            return defaultValue;
        }
        JsonNode pagination = config.get("pagination");
        if (!pagination.has(key)) {
            return defaultValue;
        }
        return pagination.get(key).asText();
    }

    private Path[] findContentFiles(Path contentDir) throws IOException {
        try (Stream<Path> paths = Files.walk(contentDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".md") || name.endsWith(".adoc") || name.endsWith(".asciidoc");
                    })
                    .toArray(Path[]::new);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}

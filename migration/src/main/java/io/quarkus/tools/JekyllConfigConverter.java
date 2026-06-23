package io.quarkus.tools;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Converts Jekyll _config.yml to Roq application.properties and data/siteConfig.yml.
 * Replaces the bash script logic from roq-it-jekyll lines 61-62, 184-192, and 238-277.
 */
public class JekyllConfigConverter {

    private final YAMLMapper yamlMapper;
    private final ObjectMapper objectMapper;

    public JekyllConfigConverter() {
        this.yamlMapper = new YAMLMapper();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create application.properties values with standard Roq properties for Jekyll compatibility.
     *
     * @return Application properties (without plugin-dependent properties)
     */
    public Properties createApplicationProperties() {
        return createApplicationProperties(null);
    }

    /**
     * Create application.properties values with standard Roq properties for Jekyll compatibility,
     * including properties derived from detected Jekyll plugins.
     *
     * @param config Parsed _config.yml (can be null)
     * @return Application properties
     */
    public Properties createApplicationProperties(JsonNode config) {
        Properties properties = new Properties();

        // Always enable the alternative expression syntax to reduce overhead of escaping braces
        properties.setProperty("quarkus.qute.alt-expr-syntax", "true");
        // Set a date format with a sensible default for Jekyll.
        properties.setProperty("site.date-format", "yyyy-MM-dd['T'HH:mm:ss][X]");
        properties.setProperty("quarkus.qute.strict-rendering", "false");
        // Exclude type checking for:
        // - Object.* (JsonArray iteration yields Object at build time)
        // - Page.paginator (only on NormalPage subclass, not visible at compile time)
        // - DocumentPage.* (post loop variables access custom frontmatter via data)
        properties.setProperty("quarkus.qute.type-check-excludes",
                "java.lang.Object.*,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.paginator,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.tags,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.tagsCount,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage.*");

        // Jekyll SCSS often uses absolute url('/assets/...') references. EsBuild can't resolve
        // these at build time since it doesn't know public/ is the site root. Mark them as external.
        properties.setProperty("quarkus.web-bundler.bundling.external", "/assets/*");

        if (hasPlugin(config, "jekyll-auto-authors")) {
            addAutoAuthorProperties(config, properties);
        }

        addCollectionProperties(config, properties);

        return properties;
    }

    /**
     * Create a siteConfig.yml file for template access to site properties.
     * This makes Jekyll's site.* properties available as cdi:siteConfig.* in Roq.
     * Replaces roq-it-jekyll lines 238-277.
     * 
     * @param configYaml The content of _config.yml
     * @param cnameContent Optional CNAME file content (can be null)
     * @return YAML content for data/siteConfig.yml
     * @throws IOException if parsing fails
     */
    public String createSiteConfigYaml(String configYaml, String cnameContent) throws IOException {
        return createSiteConfigYaml(yamlMapper.readTree(configYaml), cnameContent);
    }

    /**
     * Create a siteConfig.yml from a pre-parsed config.
     */
    public String createSiteConfigYaml(JsonNode config, String cnameContent) throws IOException {
        // Build a new config with selected properties
        Map<String, Object> siteConfig = new LinkedHashMap<>();
        
        // Add CNAME
        siteConfig.put("cname", cnameContent != null ? cnameContent.trim() : "");
        
        // Copy common properties (description is NOT here — it goes in index page frontmatter
        // so Roq's site.description picks it up)
        copyIfPresent(config, siteConfig, "baseurl");
        copyIfPresent(config, siteConfig, "language");
        copyIfPresent(config, siteConfig, "twitter_username");
        copyIfPresent(config, siteConfig, "github_username");
        
        // Handle nested search config
        if (config.has("search")) {
            JsonNode search = config.get("search");
            Map<String, Object> searchConfig = new LinkedHashMap<>();
            copyIfPresent(search, searchConfig, "script-mode");
            copyIfPresent(search, searchConfig, "host");
            copyIfPresent(search, searchConfig, "script-path");
            copyIfPresent(search, searchConfig, "cached-script-file");
            if (!searchConfig.isEmpty()) {
                siteConfig.put("search", searchConfig);
            }
        }
        
        // Copy feed config (e.g. posts_limit) — used by converted feed.xml template
        if (config.has("feed")) {
            JsonNode feed = config.get("feed");
            Map<String, Object> feedConfig = new LinkedHashMap<>();
            copyIfPresent(feed, feedConfig, "posts_limit");
            if (!feedConfig.isEmpty()) {
                siteConfig.put("feed", feedConfig);
            }
        }

        // Copy author (site-level default author for feed/posts)
        copyIfPresent(config, siteConfig, "author");

        // Add empty tags array (for Jekyll compatibility)
        siteConfig.put("tags", new Object[0]);
        
        // Convert to YAML
        return yamlMapper.writeValueAsString(siteConfig);
    }

    /**
     * Convert Jekyll config files from a project directory.
     * Reads _config.yml and CNAME, creates config/application.properties and data/siteConfig.yml.
     * Replaces roq-it-jekyll lines 61-62, 184-192, and 238-277.
     * 
     * @param projectDir The Jekyll project directory
     * @throws IOException if file operations fail
     */
    public void convertProject(Path projectDir) throws IOException {
        Path configFile = projectDir.resolve("_config.yml");
        Path cnameFile = projectDir.resolve("CNAME");
        
        if (!Files.exists(configFile)) {
            throw new IOException("_config.yml not found in " + projectDir + ". Is this a Jekyll project?");
        }
        
        // Read input files
        String configYaml = Files.readString(configFile);
        String cnameContent = Files.exists(cnameFile) ? Files.readString(cnameFile) : null;
        
        // Create config/application.properties
        Path configDir = projectDir.resolve("config");
        Files.createDirectories(configDir);
        Path propsFile = configDir.resolve("application.properties");

        JsonNode config = yamlMapper.readTree(configYaml);

        // Write properties manually — Properties.store() escapes colons in values,
        // which corrupts date format patterns like yyyy-MM-dd['T'HH:mm:ss][X]
        try (Writer writer = Files.newBufferedWriter(propsFile)) {
            Properties props = createApplicationProperties(config);
            for (String key : props.stringPropertyNames().stream().sorted().toList()) {
                writer.write(key + "=" + props.getProperty(key) + "\n");
            }
        }
        
        // Create data/siteConfig.yml
        String siteConfigYaml = createSiteConfigYaml(config, cnameContent);
        Path dataDir = projectDir.resolve("data");
        Files.createDirectories(dataDir);
        Path siteConfigFile = dataDir.resolve("siteConfig.yml");
        Files.writeString(siteConfigFile, siteConfigYaml);

        // Move Jekyll collection directories (_<name>) to Roq content/<name>
        moveCollectionDirectories(projectDir, config);

        // Add site description to index page frontmatter (Roq reads site.description from there)
        if (config.has("description")) {
            addDescriptionToIndexPage(projectDir, config.get("description").asText());
        }
    }

    void moveCollectionDirectories(Path projectDir, JsonNode config) throws IOException {
        if (!config.has("collections")) {
            return;
        }
        JsonNode collections = config.get("collections");
        if (!collections.isObject()) {
            return;
        }
        Path contentDir = projectDir.resolve("content");
        collections.fieldNames().forEachRemaining(name -> {
            if ("posts".equals(name)) {
                return;
            }
            Path source = projectDir.resolve("_" + name);
            if (Files.isDirectory(source)) {
                Path target = contentDir.resolve(name);
                try {
                    Files.createDirectories(target.getParent());
                    Files.move(source, target);
                } catch (IOException e) {
                    System.err.println("Warning: could not move _" + name + " to content/" + name + ": " + e.getMessage());
                }
            }
        });
    }

    void addDescriptionToIndexPage(Path projectDir, String description) throws IOException {
        Path indexFile = findIndexFile(projectDir);
        if (indexFile == null) {
            return;
        }
        String content = Files.readString(indexFile);
        if (content.contains("description:")) {
            return;
        }
        // Insert description after the opening ---
        content = content.replaceFirst("(---\\s*\\n)", "$1description: \"" +
                description.replace("\"", "\\\"") + "\"\n");
        Files.writeString(indexFile, content);
    }

    private Path findIndexFile(Path projectDir) {
        for (String dir : new String[] { "", "content" }) {
            for (String name : new String[] { "index.md", "index.html", "index.adoc" }) {
                Path p = projectDir.resolve(dir).resolve(name);
                if (Files.exists(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private boolean hasPlugin(JsonNode config, String pluginName) {
        if (config == null || !config.has("plugins")) {
            return false;
        }
        JsonNode plugins = config.get("plugins");
        if (!plugins.isArray()) {
            return false;
        }
        for (JsonNode plugin : plugins) {
            if (pluginName.equals(plugin.asText())) {
                return true;
            }
        }
        return false;
    }

    private void addCollectionProperties(JsonNode config, Properties properties) {
        if (config == null || !config.has("collections")) {
            return;
        }
        JsonNode collections = config.get("collections");
        if (!collections.isObject()) {
            return;
        }
        collections.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            if ("posts".equals(name)) {
                return;
            }
            JsonNode collectionConfig = entry.getValue();
            boolean output = collectionConfig.isObject()
                    && collectionConfig.has("output")
                    && collectionConfig.get("output").asBoolean(false);
            if (!output) {
                return;
            }
            properties.setProperty("site.collections." + name, "true");
            if (collectionConfig.has("layout")) {
                properties.setProperty("site.collections." + name + ".layout",
                        collectionConfig.get("layout").asText());
            }
        });
    }

    private void addAutoAuthorProperties(JsonNode config, Properties properties) {
        String layout = "author";
        String dataName = "authors";

        if (config != null && config.has("autopages")) {
            JsonNode autopages = config.get("autopages");
            if (autopages.has("authors")) {
                JsonNode authors = autopages.get("authors");
                if (authors.has("layouts") && authors.get("layouts").isArray()
                        && authors.get("layouts").size() > 0) {
                    layout = stripExtension(authors.get("layouts").get(0).asText());
                }
                if (authors.has("data")) {
                    dataName = stripExtension(stripPath(authors.get("data").asText()));
                }
            }
        }

        properties.setProperty("site.collections.author.layout", layout);
        properties.setProperty("site.collections.author.from-data.id-key", "_key");
        properties.setProperty("site.collections.author.from-data.name", dataName);
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String stripPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void copyIfPresent(JsonNode source, Map<String, Object> target, String key) {
        if (source.has(key)) {
            JsonNode value = source.get(key);
            if (value.isTextual()) {
                target.put(key, value.asText());
            } else if (value.isNumber()) {
                target.put(key, value.numberValue());
            } else if (value.isBoolean()) {
                target.put(key, value.asBoolean());
            } else if (value.isObject() || value.isArray()) {
                try {
                    target.put(key, objectMapper.convertValue(value, Object.class));
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: could not convert config value for key '" + key + "': " + e.getMessage());
                }
            }
        }
    }
}

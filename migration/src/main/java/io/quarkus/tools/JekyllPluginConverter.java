package io.quarkus.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class JekyllPluginConverter {

    enum Classification { HANDLED, TRANSLATABLE, MANUAL }

    record PluginInfo(Classification classification, String reason, String equivalentFile) {
        PluginInfo(Classification classification, String reason) {
            this(classification, reason, null);
        }
    }

    record Result(
            List<String> handled,
            List<String> translated,
            List<String> skipped,
            List<String> failed,
            Map<String, String> failureMessages) {}

    private static final Map<String, PluginInfo> KNOWN_PLUGINS = Map.of(
            "cname.rb", new PluginInfo(Classification.HANDLED,
                    "CNAME value is already configured in siteConfig.yml"),
            "regex_filter.rb", new PluginInfo(Classification.HANDLED,
                    "replace_regex filter is converted to .replaceAll() by the template converter"),
            "strings.rb", new PluginInfo(Classification.HANDLED,
                    "equals/startswith/endswith filters are converted to native String methods"),
            "copy-search-wc.rb", new PluginInfo(Classification.TRANSLATABLE,
                    "Downloads search script at build time",
                    "SearchScriptDownloader.java"),
            "asciidoctor-extension.rb", new PluginInfo(Classification.MANUAL,
                    "AsciiDoc extensions (inline macros, tree/post processors)",
                    "AsciidoctorExtension.java")
    );

    private static final String JAVA_PACKAGE = "io.quarkus.tools.migration";
    private static final String JAVA_PACKAGE_PATH = "src/main/java/io/quarkus/tools/migration";

    private final Path projectDir;

    public JekyllPluginConverter(Path projectDir) {
        this.projectDir = projectDir;
    }

    public Result convert() throws IOException {
        Path pluginsDir = projectDir.resolve("_plugins");
        List<String> handled = new ArrayList<>();
        List<String> translated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, String> failureMessages = new LinkedHashMap<>();

        if (!Files.isDirectory(pluginsDir)) {
            return new Result(handled, translated, skipped, failed, failureMessages);
        }

        List<Path> rubyFiles;
        try (Stream<Path> paths = Files.list(pluginsDir)) {
            rubyFiles = paths
                    .filter(p -> p.getFileName().toString().endsWith(".rb"))
                    .sorted()
                    .toList();
        }

        for (Path rbFile : rubyFiles) {
            String fileName = rbFile.getFileName().toString();
            String rbContent = Files.readString(rbFile);

            PluginInfo info = KNOWN_PLUGINS.get(fileName);
            if (info == null) {
                info = classifyUnknownPlugin(fileName, rbContent);
            }

            switch (info.classification()) {
                case HANDLED -> {
                    System.out.println("  [HANDLED] " + fileName + " — " + info.reason());
                    handled.add(fileName);
                }
                case TRANSLATABLE -> {
                    Path equivalentPath = projectDir.resolve(JAVA_PACKAGE_PATH)
                            .resolve(info.equivalentFile());
                    if (Files.exists(equivalentPath)) {
                        System.out.println("  [SKIP] " + fileName
                                + " — equivalent already exists: " + info.equivalentFile());
                        skipped.add(fileName);
                    } else {
                        translatePlugin(fileName, rbContent, info);
                        System.out.println("  [TRANSLATED] " + fileName
                                + " → " + info.equivalentFile());
                        translated.add(fileName);
                    }
                }
                case MANUAL -> {
                    Path equivalentPath = projectDir.resolve(JAVA_PACKAGE_PATH)
                            .resolve(info.equivalentFile());
                    if (Files.exists(equivalentPath)) {
                        System.out.println("  [SKIP] " + fileName
                                + " — equivalent already exists: " + info.equivalentFile());
                        skipped.add(fileName);
                    } else {
                        String msg = buildManualFailureMessage(fileName, rbContent, info);
                        failureMessages.put(fileName, msg);
                        failed.add(fileName);
                        System.err.println("  [MANUAL] " + fileName + " — needs hand-coded equivalent");
                    }
                }
            }
        }

        return new Result(handled, translated, skipped, failed, failureMessages);
    }

    private PluginInfo classifyUnknownPlugin(String fileName, String content) {
        String javaName = rubyFileToJavaClass(fileName) + ".java";
        return new PluginInfo(Classification.MANUAL,
                "Unknown Jekyll plugin — needs manual migration", javaName);
    }

    private String rubyFileToJavaClass(String rbFileName) {
        String base = rbFileName.replaceAll("\\.rb$", "");
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : base.toCharArray()) {
            if (c == '-' || c == '_') {
                capitalize = true;
            } else {
                sb.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            }
        }
        return sb.toString();
    }

    private void translatePlugin(String fileName, String rbContent, PluginInfo info) throws IOException {
        if ("copy-search-wc.rb".equals(fileName)) {
            generateSearchScriptDownloader();
        }
    }

    private void generateSearchScriptDownloader() throws IOException {
        String searchHost = "https://search.quarkus.io";
        String scriptPath = "/static/bundle/app.js";
        String cachedFile = "assets/javascript/search-wc.js";
        String scriptMode = "cached";

        Path siteConfig = projectDir.resolve("data/siteConfig.yml");
        if (Files.exists(siteConfig)) {
            YAMLMapper mapper = new YAMLMapper();
            JsonNode root = mapper.readTree(Files.readString(siteConfig));
            JsonNode search = root.get("search");
            if (search != null) {
                if (search.has("host")) searchHost = search.get("host").asText();
                if (search.has("scriptPath")) scriptPath = search.get("scriptPath").asText();
                if (search.has("cachedScriptFile")) cachedFile = search.get("cachedScriptFile").asText();
                if (search.has("scriptMode")) scriptMode = search.get("scriptMode").asText();
            }
        }

        String java = generateSearchScriptJava(searchHost, scriptPath, cachedFile, scriptMode);

        Path outputDir = projectDir.resolve(JAVA_PACKAGE_PATH);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("SearchScriptDownloader.java"), java);
    }

    private String generateSearchScriptJava(String host, String path, String cachedFile, String mode) {
        return "package " + JAVA_PACKAGE + ";\n" +
                "\n" +
                "import java.io.IOException;\n" +
                "import java.io.InputStream;\n" +
                "import java.net.URI;\n" +
                "import java.nio.file.Files;\n" +
                "import java.nio.file.Path;\n" +
                "\n" +
                "import io.quarkus.runtime.Startup;\n" +
                "import jakarta.enterprise.context.ApplicationScoped;\n" +
                "import org.jboss.logging.Logger;\n" +
                "\n" +
                "@ApplicationScoped\n" +
                "@Startup\n" +
                "public class SearchScriptDownloader {\n" +
                "\n" +
                "    private static final Logger LOG = Logger.getLogger(SearchScriptDownloader.class);\n" +
                "    private static final String SCRIPT_URL = \"" + host + path + "\";\n" +
                "    private static final String CACHED_FILE = \"" + cachedFile + "\";\n" +
                "\n" +
                "    SearchScriptDownloader() {\n" +
                "        if (!\"cached\".equals(\"" + mode + "\")) {\n" +
                "            return;\n" +
                "        }\n" +
                "        Path dest = Path.of(\"public\", CACHED_FILE.split(\"/\"));\n" +
                "        if (Files.exists(dest)) {\n" +
                "            LOG.debugf(\"Search script already exists at %s, skipping download\", dest);\n" +
                "            return;\n" +
                "        }\n" +
                "        try {\n" +
                "            Files.createDirectories(dest.getParent());\n" +
                "            try (InputStream in = URI.create(SCRIPT_URL).toURL().openStream()) {\n" +
                "                String content = new String(in.readAllBytes());\n" +
                "                content = content.replaceAll(\"//# sourceMappingURL=.*\\\\.map\", \"\");\n" +
                "                Files.writeString(dest, content);\n" +
                "                LOG.infof(\"Downloaded search script from %s to %s\", SCRIPT_URL, dest);\n" +
                "            }\n" +
                "        } catch (IOException e) {\n" +
                "            LOG.errorf(e, \"Failed to download search script from %s\", SCRIPT_URL);\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
    }

    private String buildManualFailureMessage(String fileName, String rbContent, PluginInfo info) {
        StringBuilder msg = new StringBuilder();
        msg.append("Jekyll plugin '").append(fileName).append("' requires manual migration.\n");
        msg.append("Create: ").append(JAVA_PACKAGE_PATH).append("/").append(info.equivalentFile()).append("\n\n");

        if (fileName.equals("asciidoctor-extension.rb")) {
            msg.append("This plugin registers the following AsciiDoc extensions:\n");
            Pattern inlineMacro = Pattern.compile("named\\s+:(\\w+)");
            Matcher m = inlineMacro.matcher(rbContent);
            while (m.find()) {
                msg.append("  - inline macro: ").append(m.group(1)).append("\n");
            }
            if (rbContent.contains("tree_processor")) {
                msg.append("  - tree_processor (configuration reference table handling)\n");
            }
            if (rbContent.contains("postprocessor")) {
                msg.append("  - postprocessor (CSS class injection for config tables)\n");
            }
        } else {
            msg.append("Plugin contents:\n");
            String[] lines = rbContent.split("\n");
            for (int i = 0; i < Math.min(lines.length, 20); i++) {
                msg.append("  ").append(lines[i]).append("\n");
            }
            if (lines.length > 20) {
                msg.append("  ... (").append(lines.length - 20).append(" more lines)\n");
            }
        }

        return msg.toString();
    }
}

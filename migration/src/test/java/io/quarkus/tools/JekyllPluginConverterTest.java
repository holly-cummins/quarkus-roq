package io.quarkus.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JekyllPluginConverterTest {

    @TempDir
    Path projectDir;

    Path pluginsDir;
    Path srcDir;

    @BeforeEach
    void setUp() throws IOException {
        pluginsDir = projectDir.resolve("_plugins");
        Files.createDirectories(pluginsDir);
        srcDir = projectDir.resolve("src/main/java/io/quarkus/tools/migration");
        Files.createDirectories(srcDir);
    }

    // --- HANDLED plugins: skip silently ---

    @Test
    void testCnamePluginIsHandled() throws IOException {
        Files.writeString(pluginsDir.resolve("cname.rb"),
                "module Jekyll\n  class LimitedEnvironmentVariables < Generator\n  end\nend");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).containsExactly("cname.rb");
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    @Test
    void testRegexFilterPluginIsHandled() throws IOException {
        Files.writeString(pluginsDir.resolve("regex_filter.rb"),
                "module Jekyll\n  module RegexFilter\n  end\nend");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).containsExactly("regex_filter.rb");
        assertThat(result.failed()).isEmpty();
    }

    @Test
    void testStringsPluginIsHandled() throws IOException {
        Files.writeString(pluginsDir.resolve("strings.rb"),
                "module Jekyll\n  module StringsFilter\n  end\nend");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).containsExactly("strings.rb");
        assertThat(result.failed()).isEmpty();
    }

    @Test
    void testMultipleHandledPlugins() throws IOException {
        Files.writeString(pluginsDir.resolve("cname.rb"), "# cname");
        Files.writeString(pluginsDir.resolve("regex_filter.rb"), "# regex");
        Files.writeString(pluginsDir.resolve("strings.rb"), "# strings");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).containsExactlyInAnyOrder(
                "cname.rb", "regex_filter.rb", "strings.rb");
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- TRANSLATABLE plugins: generate Java ---

    @Test
    void testCopySearchWcGeneratesStartupBean() throws IOException {
        Files.writeString(pluginsDir.resolve("copy-search-wc.rb"),
                "require 'open-uri'\n" +
                "module Jekyll\n" +
                "  class CopySearchScript < Jekyll::Plugin\n" +
                "    Jekyll::Hooks.register :site, :post_write do |site|\n" +
                "      script_mode = site.config['search']['script-mode']\n" +
                "    end\n" +
                "  end\n" +
                "end");

        // Provide siteConfig.yml so the converter can read search config
        Path dataDir = projectDir.resolve("data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("siteConfig.yml"),
                "search:\n" +
                "  scriptMode: \"cached\"\n" +
                "  host: \"https://search.quarkus.io\"\n" +
                "  scriptPath: \"/static/bundle/app.js\"\n" +
                "  cachedScriptFile: \"assets/javascript/search-wc.js\"\n");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.translated()).containsExactly("copy-search-wc.rb");
        assertThat(result.failed()).isEmpty();

        Path generated = srcDir.resolve("SearchScriptDownloader.java");
        assertThat(generated).exists();

        String content = Files.readString(generated);
        assertThat(content).contains("@Startup");
        assertThat(content).contains("search.quarkus.io");
        assertThat(content).contains("search-wc.js");
        assertThat(content).contains("sourceMappingURL");
        assertThat(content).contains("Files.exists(dest)");
    }

    // --- MANUAL plugins: fail unless equivalent exists ---

    @Test
    void testUnknownPluginFailsWithoutEquivalent() throws IOException {
        Files.writeString(pluginsDir.resolve("custom-thing.rb"),
                "module Jekyll\n  # something custom\nend");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).containsExactly("custom-thing.rb");
        assertThat(result.failureMessages().get("custom-thing.rb"))
                .contains("custom-thing.rb");
    }

    @Test
    void testUnknownPluginSkipsWhenEquivalentExists() throws IOException {
        Files.writeString(pluginsDir.resolve("custom-thing.rb"),
                "module Jekyll\n  # something custom\nend");

        // Hand-code the equivalent
        Files.writeString(srcDir.resolve("CustomThing.java"),
                "package io.quarkus.tools.migration;\npublic class CustomThing {}");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).isEmpty();
        assertThat(result.skipped()).containsExactly("custom-thing.rb");
    }

    @Test
    void testAsciidoctorExtensionFailsWithDescription() throws IOException {
        Files.writeString(pluginsDir.resolve("asciidoctor-extension.rb"),
                "require 'asciidoctor/extensions'\n" +
                "Extensions.register do\n" +
                "  inline_macro do\n" +
                "    named :tooltip\n" +
                "  end\n" +
                "end\n" +
                "Extensions.register do\n" +
                "  tree_processor do\n" +
                "  end\n" +
                "end");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).containsExactly("asciidoctor-extension.rb");
        String msg = result.failureMessages().get("asciidoctor-extension.rb");
        assertThat(msg).contains("tooltip");
        assertThat(msg).contains("AsciidoctorExtension.java");
    }

    @Test
    void testAsciidoctorExtensionSkipsWhenEquivalentExists() throws IOException {
        Files.writeString(pluginsDir.resolve("asciidoctor-extension.rb"),
                "require 'asciidoctor/extensions'\nExtensions.register do\nend");

        // Hand-code the equivalent
        Files.writeString(srcDir.resolve("AsciidoctorExtension.java"),
                "package io.quarkus.tools.migration;\npublic class AsciidoctorExtension {}");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).isEmpty();
        assertThat(result.skipped()).containsExactly("asciidoctor-extension.rb");
    }

    // --- No plugins directory ---

    @Test
    void testNoPluginsDirSucceeds() throws IOException {
        Files.delete(pluginsDir);

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).isEmpty();
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- Empty plugins directory ---

    @Test
    void testEmptyPluginsDirSucceeds() throws IOException {
        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).isEmpty();
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- Non-ruby files are ignored ---

    @Test
    void testNonRubyFilesIgnored() throws IOException {
        Files.writeString(pluginsDir.resolve("readme.md"), "# Plugins");
        Files.writeString(pluginsDir.resolve("helper.txt"), "notes");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).isEmpty();
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- Full quarkusio-style scenario ---

    @Test
    void testFullQuarkusioScenario() throws IOException {
        Files.writeString(pluginsDir.resolve("cname.rb"), "# cname");
        Files.writeString(pluginsDir.resolve("regex_filter.rb"), "# regex");
        Files.writeString(pluginsDir.resolve("strings.rb"), "# strings");
        Files.writeString(pluginsDir.resolve("copy-search-wc.rb"),
                "require 'open-uri'\nmodule Jekyll\nend");
        Files.writeString(pluginsDir.resolve("asciidoctor-extension.rb"),
                "require 'asciidoctor/extensions'\nExtensions.register do\nend");

        // Provide search config for copy-search-wc translation
        Path dataDir = projectDir.resolve("data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("siteConfig.yml"),
                "search:\n" +
                "  scriptMode: \"cached\"\n" +
                "  host: \"https://search.quarkus.io\"\n" +
                "  scriptPath: \"/static/bundle/app.js\"\n" +
                "  cachedScriptFile: \"assets/javascript/search-wc.js\"\n");

        // Provide hand-coded asciidoctor equivalent
        Files.writeString(srcDir.resolve("AsciidoctorExtension.java"),
                "package io.quarkus.tools.migration;\npublic class AsciidoctorExtension {}");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).containsExactlyInAnyOrder(
                "cname.rb", "regex_filter.rb", "strings.rb");
        assertThat(result.translated()).containsExactly("copy-search-wc.rb");
        assertThat(result.skipped()).containsExactly("asciidoctor-extension.rb");
        assertThat(result.failed()).isEmpty();
    }
}

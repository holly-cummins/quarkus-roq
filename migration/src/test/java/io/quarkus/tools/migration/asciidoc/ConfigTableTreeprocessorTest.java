package io.quarkus.tools.migration.asciidoc;

import static org.assertj.core.api.Assertions.assertThat;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConfigTableTreeprocessorTest {

    static Asciidoctor asciidoctor;

    @BeforeAll
    static void setup() {
        asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.javaExtensionRegistry()
                .treeprocessor(new ConfigTableTreeprocessor())
                .postprocessor(new ConfigTablePostprocessor())
                .inlineMacro(new ConfigPropertyCopyButtonInlineMacroProcessor())
                .inlineMacro(new EnvVarCopyButtonInlineMacroProcessor());
    }

    @AfterAll
    static void cleanup() {
        asciidoctor.close();
    }

    private String convert(String adoc) {
        return asciidoctor.convert(adoc, Options.builder()
                .attributes(Attributes.builder()
                        .attribute("add-copy-button-to-config-props", "true")
                        .attribute("add-copy-button-to-env-var", "true")
                        .build())
                .build());
    }

    static final String CONFIG_TABLE = """
            [.configuration-legend]
            Configuration property fixed at build time
            [.configuration-reference.searchable, cols="80,.^10,.^10"]
            |===

            h|Configuration property
            h|Type
            h|Default

            a|`quarkus.redis.hosts`
            config_property_copy_button:quarkus.redis.hosts[]

            [.description]
            --
            The redis hosts to use while connecting to the redis server.

            Environment variable: env_var_with_copy_button:QUARKUS_REDIS_HOSTS[]
            --
            |string
            |`localhost:6379`

            a|`quarkus.redis.timeout`

            [.description]
            --
            Environment variable: env_var_with_copy_button:QUARKUS_REDIS_TIMEOUT[]
            --
            |int
            |`10`

            |===
            """;

    @Test
    void addsAllRowsClassToTable() {
        String html = convert(CONFIG_TABLE);
        assertThat(html).contains("configuration-reference-all-rows");
    }

    @Test
    void collapsibleDescriptionGetsId() {
        String html = convert(CONFIG_TABLE);
        assertThat(html).contains("conf-collapsible-desc-");
    }

    @Test
    void collapsibleDescriptionGetsDecoration() {
        String html = convert(CONFIG_TABLE);
        assertThat(html).contains("description-decoration");
        assertThat(html).contains("fa fa-chevron-down");
        assertThat(html).contains("Show more");
    }

    @Test
    void nonCollapsibleDescriptionGetsNonCollapsibleId() {
        String html = convert(CONFIG_TABLE);
        assertThat(html).contains("conf-non-collapsible-desc-");
    }

    @Test
    void collapsibleRowGetsRowClasses() {
        String html = convert(CONFIG_TABLE);
        assertThat(html).contains("row-collapsible");
        assertThat(html).contains("row-collapsed");
    }

    @Test
    void searchInputInjected() {
        String html = convert(CONFIG_TABLE);
        assertThat(html).contains("FILTER CONFIGURATION");
        assertThat(html).contains("type=\"search\"");
    }

    @Test
    void nonSearchableTableHasNoSearchInput() {
        String adoc = """
                [.configuration-reference, cols="80,.^10,.^10"]
                |===

                h|Property
                h|Type
                h|Default

                a|`quarkus.test`

                [.description]
                --
                A simple property.

                Environment variable: env_var_with_copy_button:QUARKUS_TEST[]
                --
                |string
                |

                |===
                """;
        String html = convert(adoc);
        assertThat(html).doesNotContain("FILTER CONFIGURATION");
        assertThat(html).contains("configuration-reference-all-rows");
    }

    @Test
    void noConfigTableLeavesDocumentUnchanged() {
        String html = convert("== Just a heading\n\nSome text.");
        assertThat(html).doesNotContain("configuration-reference");
        assertThat(html).doesNotContain("row-collapsible");
    }
}

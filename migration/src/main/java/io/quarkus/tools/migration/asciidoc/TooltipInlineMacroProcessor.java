package io.quarkus.tools.migration.asciidoc;

import java.util.HashMap;
import java.util.Map;

import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.InlineMacroProcessor;
import org.asciidoctor.extension.Name;

@Name("tooltip")
public class TooltipInlineMacroProcessor extends InlineMacroProcessor {

    @Override
    public PhraseNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String text = attributes.get("1") != null ? attributes.get("1").toString() : "";
        String html;
        if (text.isEmpty()) {
            html = "<code>" + target + "</code>";
        } else {
            html = "<span class=\"asciidoc-tooltip-wrapper\">"
                    + "<code>" + target + "</code>"
                    + "<span class=\"asciidoc-tooltip\">" + text + "</span>"
                    + "</span>";
        }
        return createPhraseNode(parent, "quoted", html, attributes, new HashMap<>(Map.of("subs", ":none")));
    }
}

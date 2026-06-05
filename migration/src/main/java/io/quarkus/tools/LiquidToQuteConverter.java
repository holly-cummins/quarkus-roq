package io.quarkus.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Liquid to Qute Template Converter
 * Converts Jekyll/Liquid templates to Roq/Qute templates
 * <p>
 * Usage:
 * mvn exec:java -Dexec.args="<input_file> [output_file]"
 * mvn exec:java -Dexec.args="<input_directory> <output_directory> -r"
 * <p>
 * Author: Roq Team
 * License: Apache 2.0
 */
///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5

/**
 * Liquid to Qute Template Converter
 * Converts Jekyll/Liquid templates to Roq/Qute templates
 *
 * Usage:
 *   jbang LiquidToQuteConverter.java <input_file> [output_file]
 *   jbang LiquidToQuteConverter.java <input_directory> <output_directory> -r
 *
 * Author: Roq Team
 * License: Apache 2.0
 */
@Command(name = "liquid-to-qute", mixinStandardHelpOptions = true, version = "1.0",
        description = "Converts Liquid templates to Qute templates for Roq")
public class LiquidToQuteConverter implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file or directory")
    private Path input;

    @Parameters(index = "1", description = "Output file or directory (optional for single files)", arity = "0..1")
    private Path output;

    @Option(names = {"-r", "--recursive"}, description = "Process directories recursively")
    private boolean recursive;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose;

    private final List<String> conversionsApplied = new ArrayList<>();

    public static void main(String... args) {
        int exitCode = new CommandLine(new LiquidToQuteConverter()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(input)) {
            System.err.println("Error: Input path '" + input + "' does not exist");
            return 1;
        }

        if (Files.isRegularFile(input)) {
            return convertFile(input, getOutputPath(input)) ? 0 : 1;
        }

        if (Files.isDirectory(input)) {
            if (output == null) {
                System.err.println("Error: Output directory required for directory conversion");
                return 1;
            }
            convertDirectory(input, output);
            return 0;
        }

        System.err.println("Error: '" + input + "' is neither a file nor a directory");
        return 1;
    }

    private Path getOutputPath(Path inputPath) {
        if (output != null) {
            return output;
        }
        // Default: add .qute before extension
        String fileName = inputPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String name = fileName.substring(0, dotIndex);
            String ext = fileName.substring(dotIndex);
            return inputPath.getParent().resolve(name + ".qute" + ext);
        }
        return inputPath.getParent().resolve(fileName + ".qute");
    }

    boolean convertFile(Path inputPath, Path outputPath) {
        try {
            String content = Files.readString(inputPath);
            conversionsApplied.clear();

            String converted = convert(content);

            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, converted);

            if (verbose) {
                System.out.println("✓ Converted: " + inputPath + " -> " + outputPath);
                System.out.println(getConversionReport());
                System.out.println();
            }

            return true;
        } catch (IOException e) {
            System.err.println("✗ Error converting " + inputPath + ": " + e.getMessage());
            return false;
        }
    }

    private void convertDirectory(Path inputDir, Path outputDir) throws IOException {
        List<String> templateExtensions = List.of(".html", ".htm", ".liquid", ".md", ".markdown");

        int convertedCount = 0;
        int errorCount = 0;

        try (Stream<Path> paths = recursive ? Files.walk(inputDir) : Files.list(inputDir)) {
            for (Path inputPath : paths.filter(Files::isRegularFile).toList()) {
                String fileName = inputPath.getFileName().toString();
                boolean isTemplate = templateExtensions.stream()
                        .anyMatch(fileName::endsWith);

                if (isTemplate) {
                    Path relativePath = inputDir.relativize(inputPath);
                    Path outputPath = outputDir.resolve(relativePath);

                    if (convertFile(inputPath, outputPath)) {
                        convertedCount++;
                    } else {
                        errorCount++;
                    }
                }
            }
        }

        System.out.println("\nConversion complete:");
        System.out.println("  ✓ " + convertedCount + " files converted");
        if (errorCount > 0) {
            System.out.println("  ✗ " + errorCount + " files failed");
        }
    }

    String convert(String content) {
        String original = content;

        // Strip Liquid whitespace-trimming markers before any conversion
        content = content.replaceAll("\\{%-", "{%");
        content = content.replaceAll("-%\\}", "%}");

        // Convert in order of complexity
        content = convertComments(content);
        content = convertVariables(content);
        content = convertFilters(content);
        content = convertConditionals(content);
        content = convertLoops(content);
        content = convertIncludes(content);
        content = convertAssignments(content);
        content = convertCaseStatements(content);
        content = convertLayoutTags(content);
        content = convertSpecialTags(content);

        content = convertBracketNotation(content);

        // Final cleanup steps - ORDER MATTERS!
        // Remove spaces first so ternary wrapping can match properly
        content = removeSpacesBeforeMethods(content);
        content = wrapTernaryBeforeMethods(content);

        if (!content.equals(original)) {
            conversionsApplied.add("Template converted successfully");
        }

        return content;
    }

    private String convertComments(String content) {
        // Liquid: {% comment %}...{% endcomment %}
        // Qute: {! ... !}
        Pattern pattern = Pattern.compile("\\{%\\s*comment\\s*%\\}(.*?)\\{%\\s*endcomment\\s*%\\}", Pattern.DOTALL);
        String result = pattern.matcher(content).replaceAll("{! $1 !}");

        if (!result.equals(content)) {
            conversionsApplied.add("Converted comments");
        }

        return result;
    }

    private String convertVariables(String content) {
        // Liquid: {{ variable }}
        // Qute (Roq alternative syntax): {=variable}
        Pattern pattern = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String var = matcher.group(1).trim();

            // Convert post.* to page.* (Roq uses page for all content)
            var = var.replaceAll("\\bpost\\.", "page.");

            matcher.appendReplacement(sb, "{=" + Matcher.quoteReplacement(var) + "}");
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted variable outputs to alternative expression syntax");
        }

        return result;
    }

    private String convertFilters(String content) {
        // Handle append filter first (string concatenation)
        // Liquid: "text" | append: variable | append: "more"
        // Qute: "text" + variable + "more"
        Pattern appendPattern = Pattern.compile("([^|{]+?)((?:\\s*\\|\\s*append:\\s*[^|]+)+)");
        Matcher appendMatcher = appendPattern.matcher(content);
        StringBuffer appendSb = new StringBuffer();

        while (appendMatcher.find()) {
            String base = appendMatcher.group(1).trim();
            String appends = appendMatcher.group(2);

            // Extract all append values
            Pattern appendValuePattern = Pattern.compile("\\|\\s*append:\\s*([^|]+?)(?=\\s*\\||$)");
            Matcher appendValueMatcher = appendValuePattern.matcher(appends);
            StringBuilder concatenation = new StringBuilder(base);

            while (appendValueMatcher.find()) {
                String appendValue = appendValueMatcher.group(1).trim();
                concatenation.append(" + ").append(appendValue);
            }

            // Check if there are other filters after the appends
            String remaining = appends.replaceAll("\\|\\s*append:\\s*[^|]+", "").trim();
            String replacement;
            if (!remaining.isEmpty() && remaining.startsWith("|")) {
                // Wrap concatenation in parentheses if followed by other filters
                replacement = "(" + concatenation + ")" + remaining;
            } else {
                replacement = concatenation.toString();
            }

            appendMatcher.appendReplacement(appendSb, Matcher.quoteReplacement(replacement));
        }
        appendMatcher.appendTail(appendSb);

        String result = appendSb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted append filter to string concatenation");
            content = result;
        }

        // Handle prepend filter (string concatenation, reversed order)
        // Liquid: path | prepend: base
        // Qute: base + path
        Pattern prependPattern = Pattern.compile("([a-zA-Z0-9_.\"']+)\\s*\\|\\s*prepend:\\s*([^|}%]+)");
        Matcher prependMatcher = prependPattern.matcher(content);
        StringBuffer prependSb = new StringBuffer();

        while (prependMatcher.find()) {
            String base = prependMatcher.group(1).trim();
            String prefix = prependMatcher.group(2).trim();
            prependMatcher.appendReplacement(prependSb, Matcher.quoteReplacement(prefix + " + " + base));
        }
        prependMatcher.appendTail(prependSb);

        result = prependSb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted prepend filter to string concatenation");
            content = result;
        }

        // Filters with a single argument: sort, startswith, endswith, contains
        String[][] filterWithArgMap = {
                {"sort", "sort"},
                {"startswith", "startsWith"},
                {"endswith", "endsWith"},
                {"contains", "contains"},
                {"equals", "equals"},
                {"map", "map"},
                {"group_by", "groupBy"},
                {"slice", "slice"},
                {"add", "add"},
                {"minus", "minus"},
                {"times", "times"},
                {"truncate", "truncate"},
                {"remove_first", "removeFirst"},
                {"where_exp", "whereExp"},
        };

        for (String[] mapping : filterWithArgMap) {
            String liquidFilter = mapping[0];
            String quteMethod = mapping[1];
            Pattern fwaPattern = Pattern.compile("\\|\\s*" + liquidFilter + ":\\s*([^|}%]+)");
            Matcher fwaMatcher = fwaPattern.matcher(content);
            StringBuilder fwaSb = new StringBuilder();

            while (fwaMatcher.find()) {
                String arg = fwaMatcher.group(1).trim();
                fwaMatcher.appendReplacement(fwaSb, "." + quteMethod + "(" + Matcher.quoteReplacement(arg) + ")");
            }
            fwaMatcher.appendTail(fwaSb);

            result = fwaSb.toString();
            if (!result.equals(content)) {
                conversionsApplied.add("Converted filter: " + liquidFilter + " -> " + quteMethod + "()");
                content = result;
            }
        }

        // Common filter conversions
        String[][] filterMap = {
                {"upcase", "toUpperCase"},
                {"downcase", "toLowerCase"},
                {"capitalize", "capitalize"},
                {"strip_html", "stripHtml"},
                {"number_of_words", "numberOfWords"},
                {"size", "size"},
                {"first", "first"},
                {"last", "last"},
                {"join", "join"},
                {"sort", "sort"},
                {"reverse", "reverse"},
                {"uniq", "distinct"},
                {"compact", "filterNotNull"},
                {"strip", "trim()"},
                {"lstrip", "trimStart"},
                {"rstrip", "trimEnd"},
                {"escape", "escapeHtml"},
                {"url_encode", "urlEncode"},
                {"slugify", "slugify"}
        };

        for (String[] mapping : filterMap) {
            String liquidFilter = mapping[0];
            String quteFilter = mapping[1];
            Pattern pattern = Pattern.compile("\\|\\s*" + liquidFilter + "\\b");
            String replacement = "." + quteFilter;
            String newContent = pattern.matcher(content).replaceAll(replacement);
            if (!newContent.equals(content)) {
                conversionsApplied.add("Converted filter: " + liquidFilter + " -> " + quteFilter);
                content = newContent;
            }
        }

        // Date filter conversion
        // Liquid: {{ page.date | date: "%Y-%m-%d" }}
        // Qute: {page.date.format('yyyy-MM-dd')}
        Pattern datePattern = Pattern.compile("\\|\\s*date:\\s*[\"']([^\"']+)[\"']");
        Matcher dateMatcher = datePattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (dateMatcher.find()) {
            String liquidFormat = dateMatcher.group(1);
            String javaFormat = liquidFormat
                    .replace("%Y", "yyyy")
                    .replace("%m", "MM")
                    .replace("%d", "dd")
                    .replace("%H", "HH")
                    .replace("%M", "mm")
                    .replace("%S", "ss")
                    .replace("%B", "MMMM")
                    .replace("%b", "MMM")
                    .replace("%A", "EEEE")
                    .replace("%a", "EEE");

            dateMatcher.appendReplacement(sb, ".format('" + javaFormat + "')");
        }
        dateMatcher.appendTail(sb);

        result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted date filters");
            content = result;
        }

        // Default filter
        // Liquid: {{ var | default: "value" }}
        // Qute: {var ?: "value"}
        Pattern defaultPattern = Pattern.compile("\\|\\s*default:\\s*([\"'][^\"']*[\"']|\\S+)");
        Matcher defaultMatcher = defaultPattern.matcher(content);
        sb = new StringBuilder();

        while (defaultMatcher.find()) {
            String defaultVal = defaultMatcher.group(1);
            defaultMatcher.appendReplacement(sb, " ?: " + Matcher.quoteReplacement(defaultVal));
        }
        defaultMatcher.appendTail(sb);

        result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted default filter");
            content = result;
        }

        content = content.replaceAll("\\s{2,}\\?:", " ?:");

        // Truncate filter
        // Liquid: {{ text | truncatewords: 50 }}
        // Qute: {text.wordLimit(50)}
        Pattern truncatePattern = Pattern.compile("\\|\\s*truncatewords:\\s*(\\d+)");
        result = truncatePattern.matcher(content).replaceAll(".wordLimit($1)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted truncate filter");
            content = result;
        }

        // Replace_regex filter with two arguments (must be before replace to avoid partial match)
        // Liquid: {{ text | replace_regex: 'pattern', 'replacement' }}
        // Qute: {text.replaceAll('pattern', 'replacement')}
        Pattern replaceRegexPattern = Pattern.compile("\\|\\s*replace_regex:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        result = replaceRegexPattern.matcher(content).replaceAll(".replaceAll($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted replace_regex filter");
            content = result;
        }

        // Replace filter with two arguments
        // Liquid: {{ text | replace: 'old', 'new' }}
        // Qute: {text.replace('old', 'new')}
        Pattern replacePattern = Pattern.compile("\\|\\s*replace:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        result = replacePattern.matcher(content).replaceAll(".replace($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted replace filter");
            content = result;
        }

        // Push filter (array append)
        // Liquid: {{ array | push: item }}
        // Qute: {array.push(item)}
        Pattern pushPattern = Pattern.compile("\\|\\s*push:\\s*([^}|%]+)");
        Matcher pushMatcher = pushPattern.matcher(content);
        StringBuilder pushSb = new StringBuilder();

        while (pushMatcher.find()) {
            String param = pushMatcher.group(1).trim();
            pushMatcher.appendReplacement(pushSb, ".push(" + param + ")");
        }
        pushMatcher.appendTail(pushSb);

        result = pushSb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted push filter");
            content = result;
        }

        // Where filter
        // Liquid: {{ array | where: "key", "value" }}
        // Qute: {array.where("key", "value")}
        Pattern wherePattern = Pattern.compile("\\|\\s*where:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        result = wherePattern.matcher(content).replaceAll(".where($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted where filter");
            content = result;
        }

        // Split filter (must happen before empty string split detection)
        // Liquid: {{ text | split: "," }}
        // Qute: {text.split(",")}
        Pattern splitPattern = Pattern.compile("\\|\\s*split:\\s*(['\"][^'\"]*['\"])");
        result = splitPattern.matcher(content).replaceAll(".split($1)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted split filter");
            content = result;
        }

        // Strip filter (whitespace trimming)
        // Liquid: {{ text | strip }}
        // Qute: {text.trim()}
        Pattern stripPattern = Pattern.compile("\\|\\s*strip\\b");
        result = stripPattern.matcher(content).replaceAll(".trim()");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted strip filter");
            content = result;
        }

        return content;
    }

    private String convertConditionals(String content) {
        // if statements
        content = content.replaceAll("\\{%\\s*if\\s+([^%]+?)\\s*%\\}", "{#if $1}");
        content = content.replaceAll("\\{%\\s*elsif\\s+([^%]+?)\\s*%\\}", "{#else if $1}");
        content = content.replaceAll("\\{%\\s*else\\s*%\\}", "{#else}");
        content = content.replaceAll("\\{%\\s*endif\\s*%\\}", "{/if}");

        // unless (negative if)
        content = content.replaceAll("\\{%\\s*unless\\s+([^%]+?)\\s*%\\}", "{#if !($1)}");
        content = content.replaceAll("\\{%\\s*endunless\\s*%\\}", "{/if}");

        // Convert operators ONLY inside conditional blocks to avoid corrupting prose text
        Pattern ifPattern = Pattern.compile("(\\{#(?:if|else if)\\s+)([^}]+?)(\\})");
        Matcher ifMatcher = ifPattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (ifMatcher.find()) {
            String prefix = ifMatcher.group(1);
            String condition = ifMatcher.group(2);
            String suffix = ifMatcher.group(3);

            condition = condition.replaceAll("\\band\\b", "&&");
            condition = condition.replaceAll("\\bor\\b", "||");

            ifMatcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + condition + suffix));
        }
        ifMatcher.appendTail(sb);
        content = sb.toString();

        conversionsApplied.add("Converted conditionals");
        return content;
    }

    private String convertLoops(String content) {
        // Basic for loop
        content = content.replaceAll("\\{%\\s*for\\s+(\\w+)\\s+in\\s+([^%]+?)\\s*%\\}", "{#for $1 in $2}");
        content = content.replaceAll("\\{%\\s*endfor\\s*%\\}", "{/for}");

        // Loop variables
        content = content.replaceAll("forloop\\.index0", "item_index");
        content = content.replaceAll("forloop\\.index", "item_count");
        content = content.replaceAll("forloop\\.first", "item_odd");
        content = content.replaceAll("forloop\\.last", "!item_hasNext");

        // Handle limit and offset
        Pattern limitPattern = Pattern.compile("\\{#for\\s+(\\w+)\\s+in\\s+([^}]+?)\\s+limit:(\\d+)(?:\\s+offset:(\\d+))?\\s*\\}");
        Matcher limitMatcher = limitPattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (limitMatcher.find()) {
            String replacement = getString(limitMatcher);

            limitMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        limitMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            content = result;
        }

        conversionsApplied.add("Converted loops");
        return content;
    }

    private static String getString(Matcher limitMatcher) {
        String var = limitMatcher.group(1);
        String collection = limitMatcher.group(2);
        String limit = limitMatcher.group(3);
        String offset = limitMatcher.group(4);

        String replacement;
        if (offset != null && !offset.equals("0")) {
            replacement = "{#for " + var + " in " + collection + ".skip(" + offset + ").limit(" + limit + ")}";
        } else {
            replacement = "{#for " + var + " in " + collection + ".limit(" + limit + ")}";
        }
        return replacement;
    }

    private String convertIncludes(String content) {
        // Liquid: {% include "file.html" %}
        // Qute: {#include file.html /}
        Pattern includePattern = Pattern.compile("\\{%\\s*include\\s+[\"']?([^\"'%\\s]+)[\"']?\\s*([^%]*?)\\s*%\\}");
        Matcher includeMatcher = includePattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (includeMatcher.find()) {
            String file = includeMatcher.group(1);
            String params = includeMatcher.group(2).trim();

            String replacement;
            if (!params.isEmpty()) {
                replacement = "{#include " + file + " " + params + " /}";
            } else {
                replacement = "{#include " + file + " /}";
            }

            includeMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        includeMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted includes");
        }

        return result;
    }

    private String convertAssignments(String content) {
        // Convert post.* to page.* inside assign tags before converting the tags themselves
        content = content.replaceAll("(\\{%\\s*assign\\s+\\w+\\s*=\\s*[^%]*?)\\bpost\\.", "$1page.");

        // Simple assignments
        // Liquid: {% assign var = value %}
        // Qute: {#let var=value}{/let}
        content = content.replaceAll("\\{%\\s*assign\\s+(\\w+)\\s*=\\s*([^%]+?)\\s*%\\}", "{#let $1=$2}{/let}");

        // Capture (multi-line assignment)
        content = content.replaceAll("\\{%\\s*capture\\s+(\\w+)\\s*%\\}", "{#let $1}");
        content = content.replaceAll("\\{%\\s*endcapture\\s*%\\}", "{/let}");

        conversionsApplied.add("Converted assignments");
        return content;
    }

    private String convertCaseStatements(String content) {
        // Liquid: {% case var %}{% when val1 %}...{% endcase %}
        // Qute: {#switch var}{#case val1}...{/switch}
        content = content.replaceAll("\\{%\\s*case\\s+([^%]+?)\\s*%\\}", "{#switch $1}");
        content = content.replaceAll("\\{%\\s*when\\s+([^%]+?)\\s*%\\}", "{#case $1}");
        content = content.replaceAll("\\{%\\s*endcase\\s*%\\}", "{/switch}");

        conversionsApplied.add("Converted case statements");
        return content;
    }

    private String convertLayoutTags(String content) {
        // Jekyll layout inheritance tags
        // {% layout "name" %} -> {#insert name /}
        content = content.replaceAll("\\{%\\s*layout\\s+[\"']([^\"']+)[\"']\\s*%\\}", "{#insert $1 /}");

        // {% block name %} -> {#block name}
        content = content.replaceAll("\\{%\\s*block\\s+(\\w+)\\s*%\\}", "{#block $1}");
        content = content.replaceAll("\\{%\\s*endblock\\s*%\\}", "{/block}");

        // {% append name %} -> {#append name}
        content = content.replaceAll("\\{%\\s*append\\s+(\\w+)\\s*%\\}", "{#append $1}");
        content = content.replaceAll("\\{%\\s*endappend\\s*%\\}", "{/append}");

        // {% prepend name %} -> {#prepend name}
        content = content.replaceAll("\\{%\\s*prepend\\s+(\\w+)\\s*%\\}", "{#prepend $1}");
        content = content.replaceAll("\\{%\\s*endprepend\\s*%\\}", "{/prepend}");

        conversionsApplied.add("Converted layout tags");
        return content;
    }

    private String convertSpecialTags(String content) {
        // Raw blocks (escape Qute processing)
        // Liquid: {% raw %}...{% endraw %}
        // Qute: {| ... |}
        content = content.replaceAll("\\{%\\s*raw\\s*%\\}", "{|");
        content = content.replaceAll("\\{%\\s*endraw\\s*%\\}", "|}");

        // Highlight blocks (for code syntax highlighting)
        // Liquid: {% highlight lang %}...{% endhighlight %}
        // Qute: <pre><code class="language-lang">...</code></pre>
        Pattern highlightPattern = Pattern.compile("\\{%\\s*highlight\\s+(\\w+)\\s*%\\}(.*?)\\{%\\s*endhighlight\\s*%\\}", Pattern.DOTALL);
        Matcher highlightMatcher = highlightPattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (highlightMatcher.find()) {
            String lang = highlightMatcher.group(1);
            String code = highlightMatcher.group(2);
            String replacement = "<pre><code class=\"language-" + lang + "\">" + code + "</code></pre>";
            highlightMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        highlightMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted special tags");
        }

        return result;
    }

    private String getConversionReport() {
        if (conversionsApplied.isEmpty()) {
            return "No conversions needed";
        }
        return conversionsApplied.stream()
                .map(s -> "✓ " + s)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String convertBracketNotation(String content) {
        // Liquid: object[variable] (dynamic property access)
        // Qute: object.get(variable)
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_.]+)\\[([a-zA-Z0-9_.]+)\\]");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String object = matcher.group(1);
            String key = matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(object + ".get(" + key + ")"));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted bracket notation to .get()");
        }

        return result;
    }

    /**
     * Wrap ternary operators in parentheses when followed by method calls.
     * This prevents empty expression errors like {?:}
     * Examples:
     *   post.author ?: "".split(",") -> (post.author ?: "").split(",")
     *   var ?: "".trim() -> (var ?: "").trim()
     */
    private String wrapTernaryBeforeMethods(String content) {
        // Pattern: expression ?: value.method(
        // The value can be a quoted string or variable, followed immediately by .method(
        // Match patterns like: post.author ?: "".split(",")
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_\\.\\[\\]]+)\\s*\\?:\\s*([\"'][^\"']*[\"']|[a-zA-Z0-9_\\.\\[\\]]+)\\.([a-zA-Z0-9_]+)\\s*\\(");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String expr = matcher.group(1);
            String defaultVal = matcher.group(2);
            String method = matcher.group(3);
            // Wrap the entire ternary expression in parentheses
            String replacement = "(" + expr + " ?: " + defaultVal + ")." + method + "(";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Wrapped ternary operators before method calls");
        }

        return result;
    }

    /**
     * Remove spaces before method calls that can cause parsing issues in Qute.
     * Examples:
     *   "" .split(",") -> "".split(",")
     *   a .trim -> a.trim
     */
    private String removeSpacesBeforeMethods(String content) {
        // Pattern matches: any character/closing bracket/quote followed by space(s) then a dot and method name
        // This handles cases like:
        //   "" .split(
        //   ) .method(
        //   variable .method(
        //   "string" .method(
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_\\)\\]\"'])\\s+\\.([a-zA-Z0-9_]+)");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String before = matcher.group(1);
            String method = matcher.group(2);
            matcher.appendReplacement(sb, before + "." + method);
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Removed spaces before method calls");
        }

        return result;
    }
}

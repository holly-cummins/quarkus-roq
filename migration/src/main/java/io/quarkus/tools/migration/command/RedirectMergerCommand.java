///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2

package io.quarkus.tools.migration.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Merges standalone redirect files into their target files as aliases.
 * 
 * Jekyll/Roq redirect files (with newUrl/aliases frontmatter) often exist
 * as standalone pages. Before deleting the redirects directory, this command
 * extracts each redirect's source path and target URL, then adds the source
 * path as an alias to the target file.
 * 
 * Example:
 *   _redirects/guides/old-name/index.md with aliases: /guides/new-name
 *   → adds "aliases: /guides/old-name" to content/guides/new-name.adoc
 */
@Command(name = "merge-redirects", mixinStandardHelpOptions = true, version = "1.0",
        description = "Merge redirect files into target files as aliases")
public class RedirectMergerCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project directory (must contain content/)")
    private Path projectDir;

    private static final Pattern ALIASES_LINE = Pattern.compile("^aliases:\\s*\\n((?:[ \\t]+-[ \\t]+[^\\n]+\\n)*)", Pattern.MULTILINE);
    private static final Pattern FRONTMATTER = Pattern.compile("^---\\s*\\n(.*?)^---\\s*\\n", Pattern.MULTILINE | Pattern.DOTALL);

    public static void main(String... args) {
        int exitCode = new CommandLine(new RedirectMergerCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Path contentDir = projectDir.resolve("content");
        Path redirectsDir = contentDir.resolve("redirects");

        if (!Files.isDirectory(redirectsDir)) {
            System.out.println("No redirects directory found - nothing to merge");
            return 0;
        }

        int mergedCount = 0;

        try (Stream<Path> paths = Files.walk(redirectsDir)) {
            for (Path redirectFile : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md") || 
                                 p.getFileName().toString().endsWith(".html"))
                    .toList()) {
                
                if (mergeRedirect(contentDir, redirectsDir, redirectFile)) {
                    mergedCount++;
                }
            }
        }

        System.out.println("Merged " + mergedCount + " redirect(s) into target files");
        return 0;
    }

    private boolean mergeRedirect(Path contentDir, Path redirectsDir, Path redirectFile) throws IOException {
        String content = Files.readString(redirectFile);
        
        // Extract frontmatter
        Matcher fmMatcher = FRONTMATTER.matcher(content);
        if (!fmMatcher.find()) {
            return false;
        }
        
        String frontmatter = fmMatcher.group(1);
        
        // Extract aliases (target URL)
        Matcher aliasesMatcher = ALIASES_LINE.matcher(frontmatter);
        if (!aliasesMatcher.find()) {
            return false;
        }
        
        String aliasesBlock = aliasesMatcher.group(1);
        // Get first alias (target)
        String target = aliasesBlock.lines()
                .map(String::trim)
                .filter(l -> l.startsWith("- "))
                .findFirst()
                .map(l -> l.substring(2).trim())
                .orElse(null);
        
        if (target == null || target.isEmpty()) {
            return false;
        }
        
        // Calculate source path (where this redirect lives)
        Path relPath = redirectsDir.relativize(redirectFile);
        String sourcePath = "/" + relPath.toString()
                .replace('\\', '/')
                .replaceAll("/index\\.(md|html)$", "")
                .replaceAll("\\.(md|html)$", "");
        
        // Find target file
        // Remove leading slash and .html/.adoc extensions from target
        String targetPath = target.replaceFirst("^/", "")
                .replaceAll("/$", "")
                .replaceAll("\\.html$", "");
        
        Path targetFile = findTargetFile(contentDir, targetPath);
        if (targetFile == null) {
            System.out.println("  Skip: " + sourcePath + " → " + target + " (target not found)");
            return false;
        }
        
        // Add source path as alias to target file
        addAliasToFile(targetFile, sourcePath);
        System.out.println("  Merged: " + sourcePath + " → " + target);
        return true;
    }

    private Path findTargetFile(Path contentDir, String targetPath) throws IOException {
        // Try with .adoc extension first (most common for guides)
        Path adocFile = contentDir.resolve(targetPath + ".adoc");
        if (Files.exists(adocFile)) {
            return adocFile;
        }
        
        // Try .md
        Path mdFile = contentDir.resolve(targetPath + ".md");
        if (Files.exists(mdFile)) {
            return mdFile;
        }
        
        // Try .html
        Path htmlFile = contentDir.resolve(targetPath + ".html");
        if (Files.exists(htmlFile)) {
            return htmlFile;
        }
        
        return null;
    }

    private void addAliasToFile(Path file, String alias) throws IOException {
        String content = Files.readString(file);
        
        Matcher fmMatcher = FRONTMATTER.matcher(content);
        if (!fmMatcher.find()) {
            // No frontmatter, add it
            content = "---\naliases:\n  - " + alias + "\n---\n" + content;
            Files.writeString(file, content);
            return;
        }
        
        String frontmatter = fmMatcher.group(1);
        
        // Check if aliases already exist
        Matcher aliasesMatcher = ALIASES_LINE.matcher(frontmatter);
        if (aliasesMatcher.find()) {
            String existingAliases = aliasesMatcher.group(1);
            // Check if this alias is already present
            if (existingAliases.contains("- " + alias + "\n") || existingAliases.contains("- " + alias + " \n")) {
                return; // Already has this alias
            }
            // Add to existing aliases
            String updatedAliases = existingAliases + "  - " + alias + "\n";
            String updatedFrontmatter = aliasesMatcher.replaceFirst("aliases:\n" + Matcher.quoteReplacement(updatedAliases));
            content = fmMatcher.replaceFirst("---\n" + Matcher.quoteReplacement(updatedFrontmatter) + "---\n");
        } else {
            // Add new aliases section
            String updatedFrontmatter = "aliases:\n  - " + alias + "\n" + frontmatter;
            content = fmMatcher.replaceFirst("---\n" + Matcher.quoteReplacement(updatedFrontmatter) + "---\n");
        }
        
        Files.writeString(file, content);
    }
}

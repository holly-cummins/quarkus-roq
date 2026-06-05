package io.quarkus.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LiquidToQuteCommandTest {

    @Test
    void testFileConversion(@TempDir Path tempDir) throws IOException {
        // Create input file
        Path inputFile = tempDir.resolve("input.html");
        String inputContent = "{{page.title | strip}}";
        Files.writeString(inputFile, inputContent);

        // Create output file path
        Path outputFile = tempDir.resolve("output.html");

        // Convert
        LiquidToQuteCommand command = new LiquidToQuteCommand();
        boolean success = command.convertFile(inputFile, outputFile);

        // Verify
        assertTrue(success, "File conversion should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");

        String outputContent = Files.readString(outputFile);
        String expected = "{=page.title.trim()}";
        assertEquals(expected, outputContent, "File content should be converted");
    }
}

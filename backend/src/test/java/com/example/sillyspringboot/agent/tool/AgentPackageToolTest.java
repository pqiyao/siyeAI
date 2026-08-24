package com.example.sillyspringboot.agent.tool;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPackageToolTest {
    @Test
    void verifiesRequiredZipEntries() throws Exception {
        byte[] zip = zip("manifest.json", "index.html");
        assertDoesNotThrow(() -> AgentPackageTool.verifyZip(zip, List.of("manifest.json", "index.html")));
        assertThrows(IllegalStateException.class, () -> AgentPackageTool.verifyZip(zip, List.of("missing.json")));
    }

    @Test
    void rejectsCorruptZipBytes() {
        assertThrows(IllegalStateException.class,
                () -> AgentPackageTool.verifyZip("not-a-zip".getBytes(StandardCharsets.UTF_8), List.of()));
    }

    private static byte[] zip(String... names) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}

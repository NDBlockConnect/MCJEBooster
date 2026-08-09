/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.test;

import com.mcjebooster.adapter.JsonVersionAdapter;
import com.mcjebooster.adapter.VersionAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.0-Alpha.7: full-catalog adapter integrity validation.
 *
 * Every shipped {@code adapters/*.mcjeb} file must parse, carry a
 * coherent identity, and expose the mappings the transformer and
 * scheduler depend on. This locks the 34-adapter catalog down so a
 * future edit cannot silently ship a broken adapter.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.7
 */
class AdapterIntegrityTest {

    private static final Path ADAPTERS_DIR = Paths.get("adapters");

    private static List<Path> adapterFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(ADAPTERS_DIR)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ADAPTERS_DIR, "*.mcjeb")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        files.sort(Path::compareTo);
        return files;
    }

    @TestFactory
    @DisplayName("Every shipped adapter passes full integrity validation")
    Stream<DynamicTest> allAdaptersValid() throws IOException {
        List<Path> files = adapterFiles();
        return files.stream().map(path -> DynamicTest.dynamicTest(
            "adapter: " + path.getFileName(), () -> validateAdapter(path)));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Catalog contains the canonical adapter set")
    void testCanonicalSet() throws IOException {
        List<Path> files = adapterFiles();
        assertTrue(files.size() >= 30,
            "expected the full adapter catalog (>= 30), found " + files.size());

        Set<String> ids = new HashSet<>();
        for (Path file : files) {
            ids.add(stripExtension(file.getFileName().toString()));
        }

        String[] canonical = {
            "1.8.9-Vanilla", "1.12.2-Vanilla", "1.12.2-Forge",
            "1.16.5-Vanilla", "1.16.5-Fabric",
            "1.18.1-Vanilla", "1.20.6-Vanilla", "1.21.8-Vanilla",
            "26.1-Fabric", "26.1.1-Vanilla"
        };
        for (String id : canonical) {
            assertTrue(ids.contains(id), "missing canonical adapter: " + id);
        }

        // IDs must be unique (guaranteed by set size matching file count)
        assertEquals(files.size(), ids.size(), "duplicate adapter ids detected");
    }

    private static void validateAdapter(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        String expectedId = stripExtension(fileName);

        VersionAdapter adapter = new JsonVersionAdapter(path);

        // Identity coherence
        assertEquals(expectedId, adapter.getAdapterId(),
            "adapterId must match file name: " + fileName);
        assertNotNull(adapter.getMinecraftVersion());
        assertFalse(adapter.getMinecraftVersion().isEmpty(),
            "minecraftVersion must not be empty: " + fileName);
        assertNotEquals(VersionAdapter.LoaderType.UNKNOWN, adapter.getLoaderType(),
            "loaderType must be resolvable: " + fileName);

        // Mappings the transformer depends on
        assertFalse(adapter.getClassMappings().isEmpty(),
            "classMappings must not be empty: " + fileName);
        assertTrue(adapter.getClassMappings().containsKey("MinecraftServer")
                || adapter.getClassMappings().containsKey("Minecraft"),
            "classMappings must define a server entry point: " + fileName);

        // Scheduling targets
        assertNotNull(adapter.getTickMethodTarget(),
            "tickMethodTarget required: " + fileName);
        assertFalse(adapter.getTickMethodTarget().isEmpty(),
            "tickMethodTarget must not be empty: " + fileName);

        // Sane scheduling parameters
        assertTrue(adapter.getRegionSize() >= 4,
            "regionSize must be >= 4: " + fileName);
        assertTrue(adapter.getRecommendedWorkerCount() >= 1,
            "recommendedWorkerCount must be >= 1: " + fileName);
        assertTrue(adapter.getTickTimeoutMs() > 0,
            "tickTimeoutMs must be positive: " + fileName);
        assertTrue(adapter.getRequiredJavaVersion() >= 8,
            "requiredJavaVersion implausible: " + fileName);

        // Full validation pass (Java compatibility + required fields)
        assertTrue(adapter.validate(), "adapter failed validate(): " + fileName);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

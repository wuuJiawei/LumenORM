package io.lighting.lumen.template;

import io.lighting.lumen.meta.Table;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class ClassPathTableScanner {
    record ScanResult(Map<String, Class<?>> entities, Map<String, List<String>> conflicts) {
    }

    static ScanResult scan(ClassLoader classLoader) {
        String classPath = System.getProperty("java.class.path");
        if (classPath == null || classPath.isBlank()) {
            return new ScanResult(Map.of(), Map.of());
        }
        Map<String, Class<?>> entities = new LinkedHashMap<>();
        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        String[] entries = classPath.split(File.pathSeparator);
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path path = Paths.get(entry);
            if (Files.isDirectory(path)) {
                scanDirectory(path, classLoader, entities, conflicts);
            } else if (entry.endsWith(".jar")) {
                scanJar(path, classLoader, entities, conflicts);
            }
        }
        return new ScanResult(Map.copyOf(entities), copyConflicts(conflicts));
    }

    private static void scanDirectory(
        Path root,
        ClassLoader classLoader,
        Map<String, Class<?>> entities,
        Map<String, List<String>> conflicts
    ) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".class"))
                .forEach(path -> registerCandidate(toClassName(root, path), classLoader, entities, conflicts));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan classpath directory: " + root, ex);
        }
    }

    private static void scanJar(
        Path jarPath,
        ClassLoader classLoader,
        Map<String, Class<?>> entities,
        Map<String, List<String>> conflicts
    ) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                registerCandidate(className, classLoader, entities, conflicts);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan classpath jar: " + jarPath, ex);
        }
    }

    private static String toClassName(Path root, Path classFile) {
        String relative = root.relativize(classFile).toString();
        String className = relative.replace(File.separatorChar, '.');
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - ".class".length());
        }
        return className;
    }

    private static void registerCandidate(
        String className,
        ClassLoader classLoader,
        Map<String, Class<?>> entities,
        Map<String, List<String>> conflicts
    ) {
        if (className == null || className.isBlank()) {
            return;
        }
        if (className.endsWith("module-info") || className.endsWith("package-info")) {
            return;
        }
        Class<?> type;
        try {
            type = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException | LinkageError ex) {
            return;
        }
        if (type.getAnnotation(Table.class) == null) {
            return;
        }
        String simpleName = type.getSimpleName();
        if (simpleName == null || simpleName.isBlank()) {
            return;
        }
        Class<?> existing = entities.get(simpleName);
        if (existing == null) {
            if (conflicts.containsKey(simpleName)) {
                addConflict(conflicts, simpleName, type.getName());
            } else {
                entities.put(simpleName, type);
            }
            return;
        }
        if (existing.getName().equals(type.getName())) {
            return;
        }
        entities.remove(simpleName);
        addConflict(conflicts, simpleName, existing.getName());
        addConflict(conflicts, simpleName, type.getName());
    }

    private static void addConflict(Map<String, List<String>> conflicts, String name, String className) {
        List<String> names = conflicts.computeIfAbsent(name, key -> new ArrayList<>());
        if (!names.contains(className)) {
            names.add(className);
        }
    }

    private static Map<String, List<String>> copyConflicts(Map<String, List<String>> conflicts) {
        if (conflicts.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}

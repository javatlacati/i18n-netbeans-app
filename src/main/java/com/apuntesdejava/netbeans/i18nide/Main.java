/*
 * Copyright 2019 Diego Silva Limaco <diego.silva at apuntesdejava.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.apuntesdejava.netbeans.i18nide;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Diego Silva Limaco <diego.silva at apuntesdejava.com>
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String netbeansDir = "c:\\opt\\netbeans-11.2", outputDir = "output", lang = "es";
        String cmd = "init";
        for (String arg : args) {
            if (StringUtils.startsWith(arg, "--cmd=")) {
                cmd = StringUtils.substringAfter(arg, "--cmd=");
            } else if (StringUtils.startsWith(arg, "--netbeans=")) {
                netbeansDir = StringUtils.substringAfter(arg, "--netbeans=");
            } else if (StringUtils.startsWith(arg, "--lang=")) {
                lang = StringUtils.substringAfter(arg, "--lang=");
            } else if (StringUtils.startsWith(arg, "--help")) {
                showHelp();
                return;
            }
        }
        Main main = new Main(netbeansDir, lang, outputDir);
        if (StringUtils.equalsIgnoreCase(cmd, "store")) {
            main.store();
        } else {
            main.start();
        }
    }

    private static void showHelp() {
        System.out.println("== NetBeans L10N Tool ==");
        System.out.println("--cmd=[init|store]  init: Inicializa el entorno, extrae los bundle");
        System.out.println("                   store: Guarda los bundle en el netbeans");
        System.out.println("--netbeans={netbeans_dir}  Ruta de NetBeans");
    }
    private final Path netbeansDir;
    private final Path outputDir;
    private List<EntryLocalization> outputDirs = Collections.emptyList();
    private final Jsonb jsonb;
    private final String lang;
    private final String bundleL10n;

    private Main(String netbeansDir, String lang, String outputDir) {
        this.netbeansDir = Paths.get(netbeansDir);
        this.outputDir = Paths.get(outputDir);
        this.bundleL10n = StringUtils.isNotBlank(lang) ? ("Bundle_" + lang + ".properties") : null;

        JsonbConfig config = new JsonbConfig()
                .withSerializers(new PathSerializer())
                .withDeserializers(new PathDeserializer());
        this.jsonb = JsonbBuilder.create(config);
        this.lang = lang;

        loadOutputDirs();
    }

    private void start() {
        if (outputDirs.isEmpty()) {
            create();
        }
    }

    private void create() {
        Collection<Path> files = searchFiles(netbeansDir);
        createStructure(files, outputDir);
        outputDirs.forEach((f) -> LOGGER.info(f.toString()));
        if (!outputDirs.isEmpty()) {
            extractBundles();
        }

    }

    private void search(Set<Path> files, Path dir) {

        try ( Stream<Path> walk = Files.walk(dir)) {
            files.addAll(walk.filter(Files::isRegularFile).filter((f) -> {
                if (f.toString().endsWith(JAR)) {
                    try ( JarFile jarFile = new JarFile(f.toString())) {

                        long cuenta = jarFile.stream().filter((e) -> !e.isDirectory() && e.getRealName().endsWith("Bundle.properties")).count();
                        return cuenta > 0;
                    } catch (IOException ex) {
                        LOGGER.error(ex.getMessage(), ex);
                    }
                }
                return false;
            }).collect(Collectors.toList()));
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    private Set<Path> searchFiles(Path netbeansDir) {
        Set<Path> dirs = null;
        Set<Path> files = new LinkedHashSet<>();
        try ( Stream<Path> walk = Files.walk(netbeansDir)) {
            dirs = new LinkedHashSet<>(walk.filter(Files::isDirectory).collect(Collectors.toList()));
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
        if (dirs != null) {
            dirs.forEach((d) -> {
                LOGGER.info("==>{}", d);
                search(files, d);
            });
        }
        return files;
    }

    private void createStructure(Collection<Path> files, Path outputDir) {
        outputDirs = new ArrayList<>();
        try {
            Files.createDirectories(outputDir);
            files.forEach((f) -> {

                try {
                    Path g = Paths.get(outputDir.toString(), netbeansDir.relativize(f).toString());
                    outputDirs.add(new EntryLocalization(f, g));
                    Files.createDirectories(g);
                } catch (IOException ex) {
                    LOGGER.error(ex.getMessage(), ex);
                }
            });
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }

    }

    private void extractBundles() {

        outputDirs.forEach((dir) -> {
            try ( JarFile jarFile = new JarFile(dir.sourcePath.toString())) {
                List<JarEntry> bundles = jarFile.stream().filter((e)
                        -> !e.isDirectory() && (StringUtils.endsWith(e.getRealName(), BUNDLE_NAME)
                        || (StringUtils.isNotBlank(bundleL10n) && StringUtils.endsWith(e.getRealName(), bundleL10n)))
                ).collect(Collectors.toList());
                bundles.forEach((b) -> {
                    try {
                        String bundleName = b.getName();
                        Path bundleOutputPath = Paths.get(dir.outputPath.toString(), bundleName);
                        if (!StringUtils.endsWith(bundleOutputPath.toString(), bundleL10n)) {
                            dir.addBundleOutputPath(bundleOutputPath);
                        }
                        Path bundleParent = bundleOutputPath.getParent();
                        Files.createDirectories(bundleParent);

                        try ( InputStream is = jarFile.getInputStream(b)) {
                            byte[] buffer = is.readAllBytes();
                            String text = new String(buffer);
                            Files.writeString(bundleOutputPath, text);
                        }
                    } catch (IOException ex) {
                        LOGGER.error(ex.getMessage(), ex);
                    }
                });
            } catch (IOException ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        });
        saveOutputDirs();
    }

    private void loadOutputDirs() {
        try ( InputStream is = new FileInputStream(STRUCTURE_FILENAME)) {
            outputDirs = jsonb.fromJson(is, new ArrayList<EntryLocalization>() {
            }.getClass().getGenericSuperclass());
            LOGGER.info("Directorios detectados:{}", outputDirs.size());
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    private void saveOutputDirs() {
        try ( OutputStream os = new FileOutputStream(STRUCTURE_FILENAME)) {
            jsonb.toJson(outputDirs, os);
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    private void store() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        outputDirs.forEach((entry) -> {
            entry.getBundleOutputPath().forEach((out) -> {
                Path propsDir = out.getParent();
                Path langProp = Paths.get(propsDir.toString(), bundleL10n).toAbsolutePath();
                if (Files.exists(langProp)) {
                    try {

                        LOGGER.info("Agregando {}", langProp);
                        Path original = entry.getSourcePath();
                        Path target = entry.getSourcePath();
                        Path backup = Paths.get(original.getParent().toString(), original.getFileName().toString() + '-' + date);
                        Files.move(original, backup, REPLACE_EXISTING);
                        Set<String> names = new LinkedHashSet<>();
                        try ( OutputStream os = new FileOutputStream(target.toFile());  JarOutputStream jos = new JarOutputStream(os);  JarFile jarFile = new JarFile(backup.toString())) {
                            String entryName = StringUtils.substring(StringUtils.substringAfter(langProp.toString(), entry.getOutputPath().toString()), 1);
                            JarEntry jarEntry = new JarEntry(entryName);
                            jos.putNextEntry(jarEntry);
                            byte[] content = Files.readAllBytes(langProp);
                            jos.write(content);
                            jos.closeEntry();
                            names.add(entryName);
                            jarFile.stream().filter((e) -> !e.isDirectory()).forEach((e) -> {
                                String $entryName = e.getRealName();
                                if (!names.contains($entryName))
                            try ( InputStream is = jarFile.getInputStream(e)) {
                                    byte[] buffer = is.readAllBytes();

                                    JarEntry $jarEntry = new JarEntry($entryName);
                                    jos.putNextEntry($jarEntry);
                                    jos.write(buffer);
                                    jos.closeEntry();
                                    names.add($entryName);
                                } catch (IOException ex) {
                                    LOGGER.error(ex.getMessage(), ex);
                                }
                            });

                        } catch (IOException ex) {
                            LOGGER.error(ex.getMessage(), ex);
                        }
//                    Files.move(target, original);
                    } catch (IOException ex) {
                        LOGGER.error(ex.getMessage(), ex);
                    }
                }
            });
        });
    }
    private static final String JAR = ".jar";
    private static final String STRUCTURE_FILENAME = "structure.json";
    private static final String BUNDLE_NAME = "Bundle.properties";

}

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
import java.util.Arrays;
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
 * @author Diego Silva Limaco <diego.silva at apuntesdejava.com>
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    // prefixes for the command line options
    private static final String COMMAND_PREFIX = "--cmd=";
    private static final String NETBEANS_PREFIX = "--netbeans=";
    private static final String LANGUAGE_PREFIX = "--lang=";
    private static final String HELP_PREFIX = "--help";

    private static final String JAR = ".jar";
    private static final String STRUCTURE_FILENAME = "structure.json";
    private static final String BUNDLE_NAME = "Bundle.properties";

    private static List<EntryLocalization> outputDirs = Collections.emptyList();

    private final Configuration configuration;
    private final Jsonb jsonb;

    private final String bundleL10n;

    private Main(Configuration configuration) {

        this.bundleL10n = StringUtils.isNotBlank(configuration.getLang()) ? ("Bundle_" + configuration.getLang() + ".properties") : null;

        JsonbConfig config = new JsonbConfig()
                .withSerializers(new PathSerializer())
                .withDeserializers(new PathDeserializer());
        this.jsonb = JsonbBuilder.create(config);
        this.configuration = configuration;

        loadOutputDirs();
    }


    public static void main(String[] args) {
        //parse arguments
        String netbeansDir = null;
        String outputDir = null;
        String lang = null;
        String cmd = "init";

        for (String arg : args) {

            if (StringUtils.startsWith(arg, COMMAND_PREFIX)) {
                cmd = StringUtils.substringAfter(arg, COMMAND_PREFIX);
            } else if (StringUtils.startsWith(arg, NETBEANS_PREFIX)) {
                netbeansDir = StringUtils.substringAfter(arg, NETBEANS_PREFIX);
            } else if (StringUtils.startsWith(arg, LANGUAGE_PREFIX)) {
                lang = StringUtils.substringAfter(arg, LANGUAGE_PREFIX);
            } else if (StringUtils.startsWith(arg, HELP_PREFIX)) {
                showHelp();
                return;
            }
        }


        //create configuration
        Configuration configuration;
        if (netbeansDir == null && lang == null && outputDir == null) {
            configuration = Configuration.spanishConfig();
        } else {
            try {
                configuration = new Configuration.ConfigurationBuilder().outputDir(outputDir).lang(lang).netbeansDir(netbeansDir).build();
            } catch (NoSuchFieldException e) {
                LOGGER.error("Missing requires command line parameter", e);
                return;
            }
        }

        Main main = new Main(configuration);

        if (StringUtils.equalsIgnoreCase(cmd, "store")) {
            Storer.store(outputDirs, main.getBundleL10n());
        } else {
            main.start();
        }
    }

    private static void showHelp() {
        LOGGER.info("== NetBeans L10N Tool ==\n--cmd=[init|store]  init: Inicializa el entorno, extrae los bundle\n                   store: Guarda los bundle en el netbeans\n--netbeans={netbeans_dir}  Ruta de NetBeans");
    }


    private void start() {
        if (outputDirs.isEmpty()) {
            create();
        }
    }

    private void create() {
        Collection<Path> files = searchFiles(configuration.getNetbeansDir());
        createStructure(files, configuration.getOutputDir());
        outputDirs.forEach((f) -> LOGGER.info(f.toString()));
        if (!outputDirs.isEmpty()) {
            extractBundles();
        }

    }

    private void search(Set<Path> files, Path dir) {

        try (Stream<Path> walk = Files.walk(dir)) {
            files.addAll(walk.filter(Files::isRegularFile).filter((f) -> {
                if (f.toString().endsWith(JAR)) {
                    try (JarFile jarFile = new JarFile(f.toString())) {

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
        try (Stream<Path> walk = Files.walk(netbeansDir)) {
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
                    Path g = Paths.get(outputDir.toString(), configuration.getNetbeansDir().relativize(f).toString());
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
            try (JarFile jarFile = new JarFile(dir.sourcePath.toString())) {
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

                        try (InputStream is = jarFile.getInputStream(b)) {
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
        try (InputStream is = new FileInputStream(STRUCTURE_FILENAME)) {
            outputDirs = jsonb.fromJson(is, new ArrayList<EntryLocalization>() {
            }.getClass().getGenericSuperclass());
            LOGGER.info("Directorios detectados:{}", outputDirs.size());
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    private void saveOutputDirs() {
        try (OutputStream os = new FileOutputStream(STRUCTURE_FILENAME)) {
            jsonb.toJson(outputDirs, os);
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    public String getBundleL10n() {
        return this.bundleL10n;
    }
}

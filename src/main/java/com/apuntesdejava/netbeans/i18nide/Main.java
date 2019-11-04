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
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Diego Silva Limaco <diego.silva at apuntesdejava.com>
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        String netbeansDir = "c:\\opt\\netbeans-11.2", outputDir = "output";
        String cmd = "init";
        for (String arg : args) {
            if (StringUtils.startsWith(arg, "--cmd=")) {
                cmd = StringUtils.substringAfter(arg, "--cmd=");
            } else if (StringUtils.startsWith(arg, "--netbeans=")) {
                netbeansDir = StringUtils.substringAfter(arg, "--netbeans=");
            } else if (StringUtils.startsWith(arg, "--help")) {
                showHelp();
                return;
            }
        }
        Main main = new Main(netbeansDir, outputDir);
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
    private List<EntryLocalization> outputDirs;
    private final Jsonb jsonb;

    private Main(String netbeansDir, String outputDir) {
        this.netbeansDir = Paths.get(netbeansDir);
        this.outputDir = Paths.get(outputDir);

        JsonbConfig config = new JsonbConfig()
                .withSerializers(new PathSerializer())
                .withDeserializers(new PathDeserializer());
        this.jsonb = JsonbBuilder.create(config);

        loadOutputDirs();
    }

    private void start() {
        if (outputDirs.isEmpty()) {
            create();
        }
    }

    private void create() {
        List<Path> files = searchFiles(netbeansDir);
        createStructure(files, outputDir);
        outputDirs.forEach((f) -> LOGGER.info(f.toString()));
        if (!outputDirs.isEmpty()) {
            extractBundles();
        }

    }

    private void search(List<Path> files, Path dir) {

        try ( Stream<Path> walk = Files.walk(dir)) {
            files.addAll(walk.filter(Files::isRegularFile).filter((f) -> {
                if (f.toString().endsWith(JAR)) {
                    try ( JarFile jarFile = new JarFile(f.toString())) {

                        long cuenta = jarFile.stream().filter((e) -> !e.isDirectory() && e.getRealName().endsWith("Bundle.properties")).count();
                        return cuenta > 0;
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
                return false;
            }).collect(Collectors.toList()));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    private List<Path> searchFiles(Path netbeansDir) {
        List<Path> dirs = null;
        List<Path> files = new ArrayList<>();
        try ( Stream<Path> walk = Files.walk(netbeansDir)) {
            dirs = walk.filter(Files::isDirectory).collect(Collectors.toList());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        if (dirs != null) {
            dirs.forEach((d) -> search(files, d));
        }
        return files;
    }

    private void createStructure(List<Path> files, Path outputDir) {
        outputDirs = new ArrayList<>();
        try {
            Files.createDirectories(outputDir);
            files.forEach((f) -> {

                try {
                    Path g = Paths.get(outputDir.toString(), netbeansDir.relativize(f).toString());
                    outputDirs.add(new EntryLocalization(f, g));
                    Files.createDirectories(g);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            });
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

    }

    private void extractBundles() {
        outputDirs.forEach((i) -> {
            try ( JarFile jarFile = new JarFile(i.sourcePath.toString())) {
                List<JarEntry> bundles = jarFile.stream().filter((e) -> !e.isDirectory() && e.getRealName().endsWith("Bundle.properties")).collect(Collectors.toList());
                bundles.forEach((b) -> {
                    try {
                        String bundleName = b.getName();
                        Path bundleOutputPath = Paths.get(i.outputPath.toString(), bundleName);
                        i.setBundleOutputPath(bundleOutputPath);
                        Path bundleParent = bundleOutputPath.getParent();
                        Files.createDirectories(bundleParent);

                        try ( InputStream is = jarFile.getInputStream(b)) {
                            byte[] buffer = is.readAllBytes();
                            String text = new String(buffer);
                            Files.writeString(bundleOutputPath, text);
                        }
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                });
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
        saveOutputDirs();
    }

    private void loadOutputDirs() {
        try ( InputStream is = new FileInputStream(STRUCTURE_FILENAME)) {
            outputDirs = jsonb.fromJson(is, new ArrayList<EntryLocalization>() {
            }.getClass().getGenericSuperclass());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        LOGGER.log(Level.INFO, "Directorios detectados:{0}", outputDirs.size());
    }

    private void saveOutputDirs() {
        try ( OutputStream os = new FileOutputStream(STRUCTURE_FILENAME)) {
            jsonb.toJson(outputDirs, os);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    private void store() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    private static final String JAR = ".jar";
    private static final String STRUCTURE_FILENAME = "structure.json";

}

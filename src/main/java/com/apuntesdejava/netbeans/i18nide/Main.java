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

import java.io.IOException;
import java.io.InputStream;
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

/**
 *
 * @author Diego Silva Limaco <diego.silva at apuntesdejava.com>
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        String netbeansDir = "c:\\opt\\netbeans-11.2", outputDir = "output";

        new Main(netbeansDir, outputDir).start();
    }
    private final Path netbeansDir;
    private final Path outputDir;
    private List<EntryLocalization> outputDirs;

    private Main(String netbeansDir, String outputDir) {
        this.netbeansDir = Paths.get(netbeansDir);
        this.outputDir = Paths.get(outputDir);
    }

    private void start() {
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
                        i.bundleOutputPath = bundleOutputPath;
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
    }

    static class EntryLocalization {

        Path sourcePath;
        Path outputPath;
        private Path bundleOutputPath;

        public EntryLocalization() {
        }

        public Path getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(Path sourcePath) {
            this.sourcePath = sourcePath;
        }

        public Path getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(Path outputPath) {
            this.outputPath = outputPath;
        }

        public Path getBundleOutputPath() {
            return bundleOutputPath;
        }

        public void setBundleOutputPath(Path bundleOutputPath) {
            this.bundleOutputPath = bundleOutputPath;
        }

        public EntryLocalization(Path sourcePath, Path outputPath) {
            this.sourcePath = sourcePath;
            this.outputPath = outputPath;
        }

        @Override
        public String toString() {
            return "EntryLocalization{" + "sourcePath=" + sourcePath + ", outputPath=" + outputPath + '}';
        }

    }
    private static final String JAR = ".jar";
}

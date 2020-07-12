package com.apuntesdejava.netbeans.i18nide;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Storer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Storer.class);

    public static void store(List<EntryLocalization> outputDirs, String bundleL10n) {
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
                        try (OutputStream os = new FileOutputStream(target.toFile()); JarOutputStream jos = new JarOutputStream(os); JarFile jarFile = new JarFile(backup.toString())) {
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
                                    try (InputStream is = jarFile.getInputStream(e)) {
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
}

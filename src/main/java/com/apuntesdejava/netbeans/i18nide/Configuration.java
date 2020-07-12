package com.apuntesdejava.netbeans.i18nide;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Configuration {
    private final Path netbeansDir;
    private final Path outputDir;
    private final String lang;

    private Configuration(final String netbeansDir, final String outputDir, final String lang) {
        this.lang = lang;
        this.netbeansDir = Paths.get(netbeansDir);
        this.outputDir = Paths.get(outputDir);
    }

    public static class ConfigurationBuilder{
        private String netbeansDir;
        private String outputDir;
        private String lang;

        public ConfigurationBuilder netbeansDir(final String netbeansDir) {
            this.netbeansDir = netbeansDir;
            return this;
        }

        public ConfigurationBuilder outputDir(final String outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public ConfigurationBuilder lang(final String lang) {
            this.lang = lang;
            return this;
        }

        public Configuration build() throws NoSuchFieldException {
            if(isValid()){
                return new Configuration(netbeansDir,outputDir,lang);
            }else {
                throw new NoSuchFieldException("Missing fields");
            }
        }

        private boolean isValid() {
            return false;
        }
    }

    public static Configuration spanishConfig() {
        //language=file-reference
        return new Configuration("C:\\Program Files\\NetBeans-11.3", "es", "output");
    }

    public Path getNetbeansDir() {
        return this.netbeansDir;
    }

    public Path getOutputDir() {
        return this.outputDir;
    }

    public String getLang() {
        return this.lang;
    }
}

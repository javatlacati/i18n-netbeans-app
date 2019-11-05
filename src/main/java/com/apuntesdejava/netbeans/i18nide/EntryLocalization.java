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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Diego Silva Limaco <diego.silva at apuntesdejava.com>
 */
public class EntryLocalization {

    Path sourcePath;
    Path outputPath;
    private List<Path> bundleOutputPath;

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

    public List<Path> getBundleOutputPath() {
        return bundleOutputPath;
    }

    public void setBundleOutputPath(List<Path> bundleOutputPath) {
        this.bundleOutputPath = bundleOutputPath;
    }

    public void addBundleOutputPath(Path item) {
        if (this.bundleOutputPath == null) {
            this.bundleOutputPath = new ArrayList<>();
        }
        this.bundleOutputPath.add(item);
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

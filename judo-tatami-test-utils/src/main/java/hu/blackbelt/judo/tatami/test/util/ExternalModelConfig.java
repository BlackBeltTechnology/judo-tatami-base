package hu.blackbelt.judo.tatami.test.util;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Configuration for an external model used in parametrized testing.
 *
 * <p>This record holds the configuration for a single external model, including:
 * <ul>
 *   <li>modelName - The name of the model (e.g., "rackinspect")</li>
 *   <li>modelDirectory - The resolved absolute path to the model directory</li>
 *   <li>exists - Whether the model directory exists on the filesystem</li>
 *   <li>parameters - Optional parameters (dialect, warmup, iterations)</li>
 * </ul>
 *
 * <p>Example properties file format:
 * <pre>
 * # Simple format
 * rackinspect=../path/to/models
 *
 * # Extended format with parameters
 * mymodel=/path/to/models;dialect=postgresql;warmup=true;iterations=3
 * </pre>
 */
public record ExternalModelConfig(
        String modelName,
        Path modelDirectory,
        boolean exists,
        Map<String, String> parameters
) {

    /**
     * Returns the path to a specific model file.
     *
     * @param modelType the model type suffix (e.g., "psm", "asm", "rdbms")
     * @return the path to the model file (e.g., "rackinspect-psm.model")
     */
    public Path getModelFile(String modelType) {
        // Try "{name}-{modelType}.model" first (e.g., rackinspect-esm.model)
        Path typed = modelDirectory.resolve(modelName + "-" + modelType + ".model");
        if (Files.isRegularFile(typed)) {
            return typed;
        }
        // Fallback: "{name}.model" (e.g., ActionGroupTest.model)
        Path plain = modelDirectory.resolve(modelName + ".model");
        if (Files.isRegularFile(plain)) {
            return plain;
        }
        // Return the typed path (caller will handle non-existence)
        return typed;
    }

    /**
     * Returns the database dialect parameter.
     *
     * @return the dialect parameter value, defaults to "hsqldb"
     */
    public String getDialect() {
        return parameters.getOrDefault("dialect", "hsqldb");
    }

    /**
     * Returns whether warmup is enabled for this model.
     *
     * @return true if warmup is enabled, defaults to false
     */
    public boolean isWarmupEnabled() {
        return Boolean.parseBoolean(parameters.getOrDefault("warmup", "false"));
    }

    /**
     * Returns the number of iterations for performance testing.
     *
     * @return the number of iterations, defaults to 1
     */
    public int getIterations() {
        return Integer.parseInt(parameters.getOrDefault("iterations", "1"));
    }

    /**
     * Returns the model name for display in test reports.
     *
     * @return the model name
     */
    @Override
    public String toString() {
        return modelName;
    }
}

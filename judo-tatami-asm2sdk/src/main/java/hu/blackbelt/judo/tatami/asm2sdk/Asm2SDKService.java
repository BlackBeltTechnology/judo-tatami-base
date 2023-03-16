package hu.blackbelt.judo.tatami.asm2sdk;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
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

import com.google.common.collect.Maps;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;

import static hu.blackbelt.judo.tatami.asm2sdk.Asm2SDK.executeAsm2SDKGeneration;
import static hu.blackbelt.judo.tatami.core.AnsiColor.red;
import static hu.blackbelt.judo.tatami.core.AnsiColor.yellow;

@Component(immediate = true, service = Asm2SDKService.class)
@Slf4j
public class Asm2SDKService {

    Map<AsmModel, Collection<Bundle>> asm2sdkAPIBundles = Maps.newHashMap();

    BundleContext bundleContext;

    File tempDir;

    @Activate
    public void activate(BundleContext bundleContext) {
        tempDir = bundleContext.getBundle().getDataFile("generated");
        tempDir.mkdirs();
        this.bundleContext = bundleContext;
    }

    @Deactivate
    public void deactivate(BundleContext bundleContext) throws IOException {
        if (Files.exists(Paths.get(tempDir.getAbsolutePath()))) {
            Files.walk(Paths.get(tempDir.getAbsolutePath()))
                    .map(Path::toFile)
                    .sorted(Comparator.reverseOrder())
                    .forEach(File::delete);
        }
    }


    public void install(AsmModel asmModel, BundleContext bundleContext) throws Exception {
        StringBuilderLogger logger = new StringBuilderLogger(Slf4jLog.determinateLogLevel(log));
        try {

            java.net.URI scriptUri =
                    this.bundleContext.getBundle()
                            .getEntry("/tatami/asm2sdk/templates/main.egl")
                            .toURI()
                            .resolve(".");
            Asm2SDKBundleStreams bundleStreams = executeAsm2SDKGeneration(Asm2SDK.Asm2SDKParameter.asm2SDKParameter()
                    .asmModel(asmModel)
                    .log(logger)
                    .scriptUri(scriptUri)
                    .sourceCodeOutputDir(tempDir));

            Collection<Bundle> bundles = new HashSet<>();
            bundles.add(bundleContext.installBundle(this.getClass().getName(), bundleStreams.getSdkBundleStream()));
            bundles.add(bundleContext.installBundle(this.getClass().getName(), bundleStreams.getInternalBundleStream()));
            asm2sdkAPIBundles.put(asmModel, bundles);
            log.info(yellow("{}"), logger.getBuffer());
        } catch (Exception e) {
            log.info(red("{}"), logger.getBuffer());
            throw e;
        }
        for (Bundle bundle : asm2sdkAPIBundles.get(asmModel)) {
            bundle.start();
        }
    }

    public void uninstall(AsmModel asmModel) throws BundleException {
        if (asm2sdkAPIBundles.containsKey(asmModel)) {
            for (Bundle bundle : asm2sdkAPIBundles.get(asmModel)) {
                bundle.uninstall();
            }
        } else {
            log.error("ASM model is not installed: " + asmModel.toString());
        }
    }
}

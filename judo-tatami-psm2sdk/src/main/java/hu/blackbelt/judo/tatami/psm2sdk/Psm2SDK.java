package hu.blackbelt.judo.tatami.psm2sdk;

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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.java.embedded.compiler.api.CompilerUtil;
import hu.blackbelt.java.embedded.compiler.api.FullyQualifiedName;
import hu.blackbelt.judo.generator.commons.ModelGenerator;
import hu.blackbelt.judo.generator.commons.TemplateHelperFinder;
import hu.blackbelt.judo.meta.psm.generator.engine.PsmGenerator;
import hu.blackbelt.judo.meta.psm.generator.engine.PsmGeneratorParameter;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.work.MetricsCollector;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.osgi.framework.Constants;

import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static hu.blackbelt.java.embedded.compiler.api.CompilerContext.compilerContextBuilder;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;

@Slf4j
public class Psm2SDK {

    public static final String SCRIPT_ROOT_TATAMI_ASM_2_SDK = "tatami/asm2sdk/templates/";

    @Builder(builderMethodName = "psm2SDKParameter")
    public static final class Psm2SDKParameter {

        @NonNull
        PsmModel psmModel;

        @NonNull
        File sourceCodeOutputDir;

        Log log;

        MetricsCollector metricsCollector;

        @Builder.Default
        Boolean compile = true;

        @Builder.Default
        Boolean createJar = true;

        @Builder.Default
        String packagePrefix = null;

        @Builder.Default
        Boolean addSourceToJar = true;

        @Builder.Default
        Boolean generateSdk = true;

        @Builder.Default
        Boolean generateInternal = true;

        @Builder.Default
        Boolean generateGuice = true;

        @Builder.Default
        Boolean generateSpring = true;

        @Builder.Default
        Boolean generateOptionalTypes = true;

        @Builder.Default
        Boolean generatePayloadValidator = true;

    }

    public static Psm2SDKBundleStreams executePsm2SDKGeneration(Psm2SDKParameter.Psm2SDKParameterBuilder builder) throws Exception {
        return executePsm2SDKGeneration(builder.build());
    }

    public static Psm2SDKBundleStreams executePsm2SDKGeneration(Psm2SDKParameter parameter) throws Exception {

        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                () -> {
                    loggerToBeClosed.set(true);
                    return new BufferedSlf4jLogger(Psm2SDK.log);
                });

        try {
            // TODO Code generation
            // TODO ClassLoader for template classloader


            File testOutput =  new File(TARGET_TEST_CLASSES, NORTHWIND_TEST);

            LinkedHashMap uris = new LinkedHashMap();
            uris.put(new File(TARGET_TEST_CLASSES, BASE).toString(), new File(TARGET_TEST_CLASSES, BASE).toURI());
            uris.put(new File(TARGET_TEST_CLASSES, OVERRIDE).toString(), new File(TARGET_TEST_CLASSES, OVERRIDE).toURI());

            PsmGenerator.generateToDirectory(PsmGeneratorParameter.psmGeneratorParameter()
                    .psmModel(psmModel)
                    .generatorContext(ModelGenerator.createGeneratorContext(
                            ModelGenerator.CreateGeneratorContextArgument.builder()
                                    .descriptorName("test-project")
                                    .uris(uris)
                                    .helpers(TemplateHelperFinder.collectHelpersAsClass(Psm2SDK.class.getClassLoader()))
                                    .build()))
                    .log(log)
                    .targetDirectoryResolver(() -> testOutput)
                    .actorTypeTargetDirectoryResolver( a -> testOutput)
                    .extraContextVariables(() -> ImmutableMap.of("extra", "extra"))
            );

            /*
            @SuppressWarnings("unchecked")
            Set<String> sdkJavaFileNames = ((Set<String>) executionContext.getContext().get("sdkJavaClasses"))
                    .stream().map(s -> s.replaceAll("//", "/")).collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            Set<String> internalJavaFileNames = ((Set<String>) executionContext.getContext().get("internalJavaClasses"))
                    .stream().map(s -> s.replaceAll("//", "/")).collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            Set<String> internalXmlFileNames = ((Set<String>) executionContext.getContext().get("internalScrXmls"))
                    .stream().map(s -> s.replaceAll("//", "/")).collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            Set<String> providedSdkInterfaces = ((Set<String>) executionContext.getContext().get("providedSdkInterfaces"))
                    .stream().map(s -> s.replaceAll("//", "/")).collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            Set<String> guiceDaoProviderModuleFileNames = ((Set<String>) executionContext.getContext().get("guiceDaoProviderModules"))
                    .stream().map(s -> s.replaceAll("//", "/")).collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            Set<String> springDaoModuleJavaFileNames = ((Set<String>) executionContext.getContext().get("springDaoModuleJavas"))
                    .stream().map(s -> s.replaceAll("//", "/")).collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            Set<String> springModuleConfigFileNames = ((Set<String>) executionContext.getContext().get("springModuleConfigurations"))
                    .stream().map(s -> s.replaceAll("//", "/")).collect(Collectors.toSet());

            executionContext.commit();
            executionContext.close();

            Set<String> allJavaFiles = new HashSet<>();
            allJavaFiles.addAll(sdkJavaFileNames);
            allJavaFiles.addAll(internalJavaFileNames);
            if (parameter.generateGuice) {
                allJavaFiles.addAll(guiceDaoProviderModuleFileNames);
            }

            if (parameter.generateSpring) {
                allJavaFiles.addAll(springDaoModuleJavaFileNames);
            }

            Iterable<JavaFileObject> compiled = ImmutableList.of();

            if (parameter.compile) {
                final long compilerStartTs = System.nanoTime();
                boolean compilerFailed = false;
                try {
                    if (parameter.metricsCollector != null) {
                        parameter.metricsCollector.invokedTransformation("SDK-compile");
                    }

                    compiled = compile(parameter.sourceCodeOutputDir, allJavaFiles);
                } catch (Exception ex) {
                    compilerFailed = true;
                    throw ex;
                } finally {
                    if (parameter.metricsCollector != null) {
                        parameter.metricsCollector.stoppedTransformation("SDK-compile", System.nanoTime() - compilerStartTs, compilerFailed);
                    }
                }
            }

            if (parameter.createJar) {
                final Long packagingStartTs = System.nanoTime();
                boolean packagingFailed = false;
                try {
                    if (parameter.metricsCollector != null) {
                        parameter.metricsCollector.invokedTransformation("SDK-package");
                    }

                    // Generating bundle
                    return generateBundlesAsStream(GenerateBundleParameters.builder()
                            .asm2SDKParameter(parameter)
                            .name(parameter.asmModel.getName())
                            .version(parameter.asmModel.getVersion())
                            .sourceCodeOutputDir(parameter.sourceCodeOutputDir)
                            .sdkJavaFileNames(sdkJavaFileNames)
                            .internalJavaFileNames(internalJavaFileNames)
                            .internalXmlFileNames(internalXmlFileNames)
                            .providedSdkInterfaces(providedSdkInterfaces)
                            .guiceDaoProviderModule(guiceDaoProviderModuleFileNames)
                            .springModuleJava(springDaoModuleJavaFileNames)
                            .springModuleConfig(springModuleConfigFileNames)
                            .compiled(compiled)
                            .build());
                } catch (Exception ex) {
                    packagingFailed = true;
                    throw ex;
                } finally {
                    if (parameter.metricsCollector != null) {
                        parameter.metricsCollector.stoppedTransformation("SDK-package", System.nanoTime() - packagingStartTs, packagingFailed);
                    }
                }
            } */
            return null;
        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }
    }

    private static Iterable<JavaFileObject> compile(File sourceDir, Set<String> sourceCodeFiles) throws Exception {
        if (sourceCodeFiles.isEmpty()) {
            return Collections.emptyList();
        }

        Iterable<JavaFileObject> compiled = CompilerUtil.compile(compilerContextBuilder()
                .compilationFiles(fileNamesToFile(sourceDir, sourceCodeFiles))
                .sameClassLoaderAs(Psm2SDK.class)
                .build());
        return compiled;
    }

    private static Iterable<File> fileNamesToFile(File sourceDir, Set<String> sourceCodeFiles) throws Exception {
        return sourceCodeFiles.stream().map(fn -> new File(sourceDir.getAbsolutePath(), fn)).collect(Collectors.toList());
    }


    @Builder
    private static class GenerateBundleParameters {
        @NonNull
        Psm2SDK.Psm2SDKParameter psm2SDKParameter;

        @NonNull
        String name;

        @NonNull
        String version;

        @NonNull
        File sourceCodeOutputDir;

        @NonNull
        Set<String> sdkJavaFileNames;

        @NonNull
        Set<String> internalJavaFileNames;

        @NonNull
        Set<String> internalXmlFileNames;

        @NonNull
        Set<String> providedSdkInterfaces;

        @NonNull
        Set<String> guiceDaoProviderModule;

        @NonNull
        Set<String> springModuleJava;

        @NonNull
        Set<String> springModuleConfig;

        @NonNull
        Iterable<JavaFileObject> compiled;
    }

    private static Psm2SDKBundleStreams generateBundlesAsStream(GenerateBundleParameters parameters) {
        Set<JavaFileObject> compiled = new HashSet<>();
        parameters.compiled.forEach(compiled::add);

        // TODO: Generate one deployed bundle
        return null;

        /*
        Set<String> sdkExportedPackages = parameters.sdkJavaFileNames.stream().filter(f -> f.endsWith(".java"))
                .map(f -> f.replaceAll("/", "."))
                .map(f -> getPackageName(getPackageName(f))).collect(Collectors.toSet());

        Set<String> internalExportedPackages = parameters.internalJavaFileNames.stream().filter(f -> f.endsWith(".java"))
                .map(f -> f.replaceAll("/", "."))
                .map(f -> getPackageName(getPackageName(f))).collect(Collectors.toSet());

        Set<String> guiceExportedPackages = parameters.guiceDaoProviderModule.stream().filter(f -> f.endsWith(".java"))
                .map(f -> f.replaceAll("/", "."))
                .map(f -> getPackageName(getPackageName(f))).collect(Collectors.toSet());

        Set<String> springExportedPackages = parameters.springModuleJava.stream().filter(f -> f.endsWith(".java"))
                .map(f -> f.replaceAll("/", "."))
                .map(f -> getPackageName(getPackageName(f))).collect(Collectors.toSet());

        TinyBundle sdkBundle = generateSdkBundle(parameters, compiled, sdkExportedPackages);
        TinyBundle internalBundle = generateInternalBundle(parameters, compiled, sdkExportedPackages, internalExportedPackages);
        TinyBundle guiceBundle = generateGuiceBundle(parameters, compiled, sdkExportedPackages, internalExportedPackages, guiceExportedPackages);
        TinyBundle springBundle = generateSpringBundle(parameters, compiled, sdkExportedPackages, internalExportedPackages, springExportedPackages);

        if (parameters.asm2SDKParameter.addSourceToJar) {
            addSourceFiles(parameters.sourceCodeOutputDir, parameters.sdkJavaFileNames, sdkBundle);
            addSourceFiles(parameters.sourceCodeOutputDir, parameters.internalJavaFileNames, internalBundle);
            if (parameters.asm2SDKParameter.generateGuice) {
                addSourceFiles(parameters.sourceCodeOutputDir, parameters.guiceDaoProviderModule, guiceBundle);

            }
            if (parameters.asm2SDKParameter.generateSpring) {
                addSourceFiles(parameters.sourceCodeOutputDir, parameters.springModuleJava, springBundle);
                addSourceFiles(parameters.sourceCodeOutputDir, parameters.springModuleConfig, springBundle);
            }
        }

        return new Asm2SDKBundleStreams(new CachingInputStream(sdkBundle.build()),
                new CachingInputStream(internalBundle.build()),
                new CachingInputStream(guiceBundle.build()),
                new CachingInputStream(springBundle.build()));

         */
    }

    private static TinyBundle generateBundleFilteredWithPrefix(GenerateBundleParameters parameters, Collection<JavaFileObject> compiled, Set<String> packagesToFilter) {
        TinyBundle bundle = bundle();
        compiled.stream()
                .filter(c -> packagesToFilter.contains(getPackageName((FullyQualifiedName) c)))
                .forEach(c -> {
                            FullyQualifiedName fullyQualifiedName  = (FullyQualifiedName) c;
                            try {
                                bundle.add(getPathInBundle(fullyQualifiedName), c.openInputStream());
                            } catch (IOException e) {
                                throw new RuntimeException("Could not open: " + fullyQualifiedName, e);
                            }
                        }
                );

        addCommonBundleHeaders(bundle, parameters.version);
        addExportedPackages(bundle, packagesToFilter);
        return bundle;
    }

    private static TinyBundle generateSdkBundle(GenerateBundleParameters parameters, Collection<JavaFileObject> compiled, Set<String> exportedPackages) {
        TinyBundle bundle = generateBundleFilteredWithPrefix(parameters, compiled, exportedPackages);
        bundle.set(Constants.BUNDLE_SYMBOLICNAME, parameters.name + "-asm2sdk-sdk");
        bundle.set(Constants.IMPORT_PACKAGE,
                "org.osgi.framework;version=\"[1.8,3.0)\"," +
                        "hu.blackbelt.judo.dispatcher.api;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.sdk;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.sdk.query;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.structured.map.proxy;version=\"[1.0,3.0)\""
        );
        bundle.add("pom.xml", generatePomXml(parameters.name, parameters.version));
        return bundle;
    }

    private static TinyBundle generateInternalBundle(GenerateBundleParameters parameters, Collection<JavaFileObject> compiled, Set<String> exportedSdkPackages, Set<String> exportedPackages) {
        TinyBundle bundle = generateBundleFilteredWithPrefix(parameters, compiled, exportedPackages);

        bundle.set(Constants.BUNDLE_SYMBOLICNAME, parameters.name + "-asm2sdk-internal");
        bundle.set(Constants.IMPORT_PACKAGE,
                "org.osgi.framework;version=\"[1.8,3.0)\"," +
                        "hu.blackbelt.judo.dao.api;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.dispatcher.api;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.meta.asm.runtime;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.structured.map.proxy;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.sdk;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.sdk.query;version=\"[1.0,3.0)\"," +
                        "org.eclipse.emf.ecore," +
                        "org.eclipse.emf.common," +
                        "org.eclipse.emf.common.util," +
                        "org.slf4j;version=\"[1.7.2,2)\", " +
                        Joiner.on(",").join(exportedSdkPackages));

        addServiceComponentsDesciptors(bundle, parameters.sourceCodeOutputDir, parameters.internalXmlFileNames);
        addProvidedCapabilities(bundle, parameters.providedSdkInterfaces);
        return bundle;
    }


    private static TinyBundle generateGuiceBundle(GenerateBundleParameters parameters, Collection<JavaFileObject> compiled, Set<String> exportedSdkPackages, Set<String> exportedInternalPackages, Set<String> exportedPackages) {
        TinyBundle bundle = generateBundleFilteredWithPrefix(parameters, compiled, exportedPackages);

        bundle.set(Constants.BUNDLE_SYMBOLICNAME, parameters.name + "-asm2sdk-guice");
        bundle.set(Constants.IMPORT_PACKAGE,
                "com.google.inject," +
                        "hu.blackbelt.judo.dao.api;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.dispatcher.api;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.meta.asm.runtime;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.structured.map.proxy;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.sdk;version=\"[1.0,2.0)\"," +
                        "hu.blackbelt.judo.sdk.query;version=\"[1.0,3.0)\"," +
                        "org.eclipse.emf.ecore," +
                        "org.eclipse.emf.common," +
                        "org.eclipse.emf.common.util," +
                        "org.slf4j;version=\"[1.7.2,2)\", " +
                        Joiner.on(",").join(exportedSdkPackages) + ", " +
                        Joiner.on(",").join(exportedInternalPackages)
        );
        addProvidedCapabilities(bundle, parameters.providedSdkInterfaces);
        return bundle;
    }

    private static TinyBundle generateSpringBundle(GenerateBundleParameters parameters, Collection<JavaFileObject> compiled, Set<String> exportedSdkPackages, Set<String> exportedInternalPackages, Set<String> exportedPackages) {
        TinyBundle bundle = generateBundleFilteredWithPrefix(parameters, compiled, exportedPackages);

        bundle.set(Constants.BUNDLE_SYMBOLICNAME, parameters.name + "-asm2sdk-spring");
        bundle.set(Constants.IMPORT_PACKAGE,
                "org.springframework.beans.factory.annotation,"+
                        "org.springframework.context.annotation," +
                        "hu.blackbelt.judo.dao.api;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.dispatcher.api;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.meta.asm.runtime;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.structured.map.proxy;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.sdk;version=\"[1.0,3.0)\"," +
                        "hu.blackbelt.judo.sdk.query;version=\"[1.0,3.0)\"," +
                        "org.eclipse.emf.ecore," +
                        "org.eclipse.emf.common," +
                        "org.eclipse.emf.common.util," +
                        "org.slf4j;version=\"[1.7.2,2)\", " +
                        Joiner.on(",").join(exportedSdkPackages) + ", " +
                        Joiner.on(",").join(exportedInternalPackages)
        );
        addProvidedCapabilities(bundle, parameters.providedSdkInterfaces);
        return bundle;
    }

    private static void addServiceComponentsDesciptors(TinyBundle bundle, File sourceDir, Set<String> xmlFilenames) {
        xmlFilenames.forEach(s -> {
            try {
                bundle.add(s, new FileInputStream(new File(sourceDir.getAbsolutePath(), s)));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("File not found: " + s, e);
            }
        });

        if (xmlFilenames.size() > 0) {
            bundle.set("Service-Component", Joiner.on(",").join(xmlFilenames));
        }
    }

    private static void addProvidedCapabilities(TinyBundle bundle, Set<String> providedServices) {
        bundle.set(Constants.PROVIDE_CAPABILITY,
                providedServices.stream().map(s -> "osgi.service;objectClass:List<String>=\"" + s + "\"").collect(Collectors.joining(",")));
    }

    private static void addCommonBundleHeaders(TinyBundle bundle, String version) {
        bundle.set(Constants.BUNDLE_MANIFESTVERSION, "2")
                .set(Constants.BUNDLE_VERSION, version)
                .set(Constants.REQUIRE_CAPABILITY, "osgi.extender;filter:=\"(&(osgi.extender=osgi.component)(version>=1.3.0)(!(version>=2.0.0)))\"");
    }

    private static void addExportedPackages(TinyBundle bundle, Set<String> packageNames) {
        if (packageNames.size() > 0) {
            bundle.set(Constants.EXPORT_PACKAGE, packageNames.stream().collect(Collectors.joining(",")));
        }
    }

    private static String getPathInBundle(FullyQualifiedName fullyQualifiedName) {
        return fullyQualifiedName.getFullyQualifiedName().replace('.', '/') + ".class";
    }

    private static String getPackageName(FullyQualifiedName fullyQualifiedName) {
        return getPackageName(fullyQualifiedName.getFullyQualifiedName());
    }

    private static String getPackageName(String fullyQualifiedName) {
        if (!fullyQualifiedName.contains(".")) {
            return fullyQualifiedName;
        }
        String packageName = fullyQualifiedName
                .substring(0, fullyQualifiedName.lastIndexOf("."));
        return packageName;
    }

    private static void addSourceFiles(File sourceCodeOutputDir, Set<String> javaFileNames, TinyBundle bundle) {
        javaFileNames.forEach(s -> {
            try {
                bundle.add(s, new FileInputStream(new File(sourceCodeOutputDir.getAbsolutePath(), s)));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("File not found: " + s, e);
            }
        });
    }

    @SuppressWarnings("unused")
    private static InputStream getClassByteCode(@SuppressWarnings("rawtypes") Class clazz) {
        return clazz.getClassLoader().getResourceAsStream(getClassFileName(clazz));
    }

    private static String getClassFileName(@SuppressWarnings("rawtypes") Class clazz) {
        return clazz.getName().replace(".", "/") + ".class";
    }

    private static InputStream generatePomXml(String modelName, String version) {
        String templateFileName = "pom.xml.template";
        InputStream resourceAsStream = Psm2SDK.class.getResourceAsStream(templateFileName);
        try {
            String pomContent = IOUtils.toString(resourceAsStream, "UTF-8")
                    .replace("ARTIFACT_ID", modelName)
                    .replace("VERSION", version + "-SNAPSHOT");
            return new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file from: " + templateFileName, e);
        }
    }
}



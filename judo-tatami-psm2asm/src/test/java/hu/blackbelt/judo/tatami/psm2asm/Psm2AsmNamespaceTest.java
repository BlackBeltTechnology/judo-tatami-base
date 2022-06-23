package hu.blackbelt.judo.tatami.psm2asm;

import com.google.common.collect.ImmutableList;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newPackageBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Psm2AsmNamespaceTest {

    public static final String MODEL_NAME = "Test";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    public static final String NS_URI = "http://blackbelt.hu/judo/" + MODEL_NAME;
    public static final String NS_PREFIX = "runtime" + MODEL_NAME;
    public static final String EXTENDED_METADATA_URI = "http://blackbelt.hu/judo/meta/ExtendedMetadata";
    
    public static final String TEST_MODEL_NAME = "Model";

    PsmModel psmModel;
    AsmModel asmModel;
    AsmUtils asmUtils;

    @BeforeEach
    public void setUp() {
        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .name(MODEL_NAME)
                .build();
        // Create empty ASM model
        asmModel = buildAsmModel()
                .name(MODEL_NAME)
                .build();
        asmUtils = new AsmUtils(asmModel.getResourceSet());
    }

    private void transform(final String testName) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model"))
                .build());
        
        assertTrue(psmModel.isValid());
        try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        assertTrue(asmModel.isValid());
        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model"))
                .build());
    }
    
    @Test
    void testNamespace() throws Exception {
        
    	Package packOfPack = newPackageBuilder().withName("packageB").build();
    	
		Package packOfModel = newPackageBuilder().withPackages(packOfPack).withName("packageA").build();

		Model model = newModelBuilder().withName(TEST_MODEL_NAME).withPackages(packOfModel)
				.withElements(ImmutableList.of()).build();

        psmModel.addContent(model);

        transform("testNamespace");
        
        final String packageANameFirstUpperCase = "PackageA";
        final String packageBNameFirstUpperCase = "PackageB";
        
        final Optional<EPackage> asmPackOfPack = asmUtils.all(EPackage.class).filter(c -> c.getName().equals(packOfPack.getName())).findAny();
        final Optional<EPackage> asmPackOfModel = asmUtils.all(EPackage.class).filter(c -> c.getName().equals(packOfModel.getName())).findAny();
        final Optional<EPackage> asmModel = asmUtils.all(EPackage.class).filter(c -> c.getName().equals(model.getName())).findAny();
        assertTrue(asmPackOfPack.isPresent());
        assertTrue(asmPackOfPack.get().getNsPrefix().equals(NS_PREFIX + TEST_MODEL_NAME + packageANameFirstUpperCase + packageBNameFirstUpperCase));
        assertTrue(asmPackOfPack.get().getNsURI().equals(NS_URI + "/" + TEST_MODEL_NAME + "/" + packOfModel.getName() + "/" + packOfPack.getName()));
        
        assertTrue(asmPackOfModel.isPresent());
        assertTrue(asmPackOfModel.get().getNsPrefix().equals(NS_PREFIX + TEST_MODEL_NAME + packageANameFirstUpperCase));
        assertTrue(asmPackOfModel.get().getNsURI().equals(NS_URI + "/" + TEST_MODEL_NAME + "/" + packOfModel.getName()));
        
        assertTrue(asmModel.isPresent());
        assertTrue(asmModel.get().getNsPrefix().equals(NS_PREFIX + TEST_MODEL_NAME));
        assertTrue(asmModel.get().getNsURI().equals(NS_URI + "/" + TEST_MODEL_NAME));
      
    }
}

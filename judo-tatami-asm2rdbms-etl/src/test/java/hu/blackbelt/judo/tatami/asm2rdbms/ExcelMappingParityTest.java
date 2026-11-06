package hu.blackbelt.judo.tatami.asm2rdbms;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Rdbms :: ETL
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

import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.etl.Asm2Rdbms;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Epsilon-free CSV loader in the core artifact must produce exactly the mapping models the original ETL
 * produced from the .xlsx workbooks (change split-etl-artifacts). Fails if the CSV and the workbooks drift.
 */
class ExcelMappingParityTest {

    private static final Logger log = LoggerFactory.getLogger(ExcelMappingParityTest.class);

    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "postgresql", "oracle"})
    void csvLoaderMatchesEtlOutput(String dialect) throws Exception {
        RdbmsModel etl = newRdbmsModel();
        ExcelMappingEtlReference.injectExcelMappings(etl, log,
                Asm2Rdbms.calculateAsm2RdbmsTransformationScriptURI(),
                // the .xlsx workbooks + spreadsheet configs ship in this -etl artifact (reference source only)
                Asm2Rdbms.calculateURI(Asm2Rdbms.MODEL_ROOT_TATAMI_ASM_2_RDBMS),
                dialect);

        RdbmsModel csv = newRdbmsModel();
        ExcelMappingModels2Rdbms.injectExcelMappings(csv, log, null,
                ExcelMappingModels2Rdbms.calculateExcelMappingModelURI(), dialect);

        List<EObject> expected = etl.getResource().getContents();
        List<EObject> actual = csv.getResource().getContents();

        assertEquals(dump(expected), dump(actual), "mapping models differ for dialect " + dialect);
        assertTrue(EcoreUtil.equals(expected, actual), "EcoreUtil.equals failed for dialect " + dialect);
        assertEquals(3, actual.size());
        assertTrue(actual.get(0).eContents().size() > 0 && actual.get(1).eContents().size() > 0
                && actual.get(2).eContents().size() > 0, "mapping tables must not be empty");
    }

    private static RdbmsModel newRdbmsModel() {
        RdbmsModel model = buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(model.getResourceSet());
        registerRdbmsDataTypesMetamodel(model.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(model.getResourceSet());
        return model;
    }

    /** Readable, order-preserving dump of every set attribute, so a mismatch shows the exact element. */
    private static String dump(List<EObject> roots) {
        return roots.stream().map(root -> root.eClass().getName() + "\n" + root.eContents().stream()
                        .map(e -> "  " + e.eClass().getName() + e.eClass().getEAllAttributes().stream()
                                .filter(e::eIsSet)
                                .map((EAttribute a) -> " " + a.getName() + "=" + e.eGet(a))
                                .collect(Collectors.joining()))
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n"));
    }
}

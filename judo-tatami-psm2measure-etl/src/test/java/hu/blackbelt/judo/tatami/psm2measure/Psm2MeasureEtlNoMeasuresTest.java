package hu.blackbelt.judo.tatami.psm2measure;

import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2measure.etl.Psm2MeasureEtlEngineImpl;
import org.junit.jupiter.api.Test;

import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ETL psm2measure on a PSM without measures: the sorting {@code post} block of psmToMeasure.etl used to call
 * {@code measures.first} unguarded and failed with NoSuchElementException (seen via the JSL loader in ETL mode).
 * See change: split-etl-artifacts.
 */
class Psm2MeasureEtlNoMeasuresTest {

    @Test
    void modelWithoutMeasuresTransforms() throws Exception {
        PsmModel psmModel = PsmModel.buildPsmModel().build();
        psmModel.addContent(newModelBuilder().withName("NoMeasures").build());
        MeasureModel measureModel = buildMeasureModel().name("NoMeasures").build();

        new Psm2MeasureEtlEngineImpl().execute(psmModel, measureModel, null, false, true, false);

        assertTrue(measureModel.getResource().getContents().isEmpty(), "no measure expected");
    }
}

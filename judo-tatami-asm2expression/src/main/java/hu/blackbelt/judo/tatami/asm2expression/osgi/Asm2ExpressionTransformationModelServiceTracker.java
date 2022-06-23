package hu.blackbelt.judo.tatami.asm2expression.osgi;

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.tatami.core.AbstractModelPairTracker;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Slf4j
public class Asm2ExpressionTransformationModelServiceTracker extends AbstractModelPairTracker<AsmModel, MeasureModel> {

    @Reference
    Asm2ExpressionTranformationSerivce asm2ExpressionTranformationSerivce;

    Map<String, ServiceRegistration<ExpressionModel>> registrations = new ConcurrentHashMap<>();
    Map<String, ExpressionModel> models = new HashMap<>();

    @Activate
    protected void activate(ComponentContext contextPar) {
        componentContext = contextPar;
        openTracker(contextPar.getBundleContext());
    }

    @Deactivate
    protected void deactivate() {
        closeTracker();
    }

    private ComponentContext componentContext;


    @Override
    public void install(AsmModel asmModel, MeasureModel measureModel) {
        String key = asmModel.getName();
        ExpressionModel expressionModel = null;
        if (models.containsKey(key)) {
            log.error("Model already loaded: " + asmModel.getName());
            return;
        }

        try {
            expressionModel = asm2ExpressionTranformationSerivce.install(asmModel, measureModel);
            log.info("Registering model: " + expressionModel);
            ServiceRegistration<ExpressionModel> modelServiceRegistration =
                    componentContext.getBundleContext()
                            .registerService(ExpressionModel.class, expressionModel, expressionModel.toDictionary());
            models.put(key, expressionModel);
            registrations.put(key, modelServiceRegistration);
        } catch (Exception e) {
            log.error("Could not register PSM Model: " + asmModel.getName(), e);
        }
    }

    @Override
    public void uninstall(AsmModel asmModel, MeasureModel measureModel) {
        String key = asmModel.getName();
        if (!registrations.containsKey(key)) {
            log.error("Model is not registered: " + asmModel.getName());
        } else {
            registrations.get(key).unregister();
            registrations.remove(key);
            models.remove(key);
        }
    }

    @Override
    public Class<AsmModel> getModelClass1() {
        return AsmModel.class;
    }

    @Override
    public Class<MeasureModel> getModelClass2() {
        return MeasureModel.class;
    }

    @Override
    public Function<AsmModel, String> getModel1NameExtractorFunction() {
        return asmModel -> asmModel.getName();
    }

    @Override
    public Function<MeasureModel, String> getModel2NameExtractorFunction() {
        return measureModel -> measureModel.getName();
    }
}

package hu.blackbelt.judo.tatami.psm2asm.osgi;

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.AbstractModelTracker;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Slf4j
public class Psm2AsmTransformationPsmModelTracker extends AbstractModelTracker<PsmModel> {

    @Reference
    Psm2AsmTransformationSerivce psm2AsmTransformationSerivce;

    Map<String, ServiceRegistration<AsmModel>> registrations = new ConcurrentHashMap<>();
    Map<String, AsmModel> models = new HashMap<>();


    @Activate
    protected void activate(ComponentContext contextPar) {
        componentContext = contextPar;
        openTracker(contextPar.getBundleContext());
    }

    @Deactivate
    protected void deactivate() {
        closeTracker();
        registrations.forEach((k, v) -> { v.unregister(); });
    }

    private ComponentContext componentContext;

    @Override
    public void install(PsmModel psmModel) {
        String key = psmModel.getName();
        if (!needToTransform(psmModel)) {
            return;
        }
        AsmModel asmModel = null;
        if (models.containsKey(key)) {
            log.error("Model already loaded: " + psmModel.getName());
            return;
        }

        try {
            asmModel = psm2AsmTransformationSerivce.install(psmModel);
            log.info("Registering model: " + asmModel);
            ServiceRegistration<AsmModel> modelServiceRegistration =
                    componentContext.getBundleContext()
                            .registerService(AsmModel.class, asmModel, asmModel.toDictionary());
            models.put(key, asmModel);
            registrations.put(key, modelServiceRegistration);
        } catch (Exception e) {
            log.error("Could not register PSM Model: " + psmModel.getName(), e);
        }
    }

    @Override
    public void uninstall(PsmModel psmModel) {
        String key = psmModel.getName();
        if (!needToTransform(psmModel)) {
            return;
        }
        if (!registrations.containsKey(key)) {
            log.error("Model is not registered: " + psmModel.getName());
        } else {
            psm2AsmTransformationSerivce.uninstall(psmModel);
            registrations.get(key).unregister();
            registrations.remove(key);
            models.remove(key);
        }
    }

    @Override
    public Class<PsmModel> getModelClass() {
        return PsmModel.class;
    }

    private boolean needToTransform(PsmModel psmModel) {
        if (psmModel.getTags().isEmpty()
                || (!psmModel.getTags().contains("!transform-psm2asm"))
                || (psmModel.getTags().contains("transform-psm2asm"))
        ) {
            return true;
        }
        return false;
    }

}

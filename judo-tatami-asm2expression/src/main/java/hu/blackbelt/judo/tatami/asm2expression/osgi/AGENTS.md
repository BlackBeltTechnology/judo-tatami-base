# AGENTS.md — asm2expression/osgi

OSGi Declarative Services wrapper around the ASM→Expression transformation.

| File | Purpose |
|---|---|
| `Asm2ExpressionTranformationSerivce.java` | OSGi `@Component(service = ...)` wrapper: `install(AsmModel, MeasureModel) : ExpressionModel` via `Asm2Expression.executeAsm2Expression` + `StringBuilderLogger` log; misspelled name wiring-stable → see `Asm2ExpressionTranformationSerivce.java.AGENTS.md` |
| `Asm2ExpressionTransformationModelServiceTracker.java` | `AbstractModelPairTracker<AsmModel, MeasureModel>` DS component registering `ExpressionModel` via `Asm2ExpressionTranformationSerivce.install`, keyed by `asmModel.getName()` → see `Asm2ExpressionTransformationModelServiceTracker.java.AGENTS.md` |

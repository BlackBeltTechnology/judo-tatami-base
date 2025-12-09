# PSM to Measure Transformation

## Overview

The PSM to Measure transformation extracts measurement units and definitions from the Platform Specific Model (PSM) and creates a dedicated Measure model. This transformation enables type-safe handling of measured values with proper unit conversions.

**Module:** `judo-tatami-psm2measure`  
**Source Model:** PSM (hu.blackbelt.judo.meta.psm)  
**Target Model:** Measure (hu.blackbelt.judo.meta.measure)

## ETL Files

| File | Purpose |
|------|---------|
| `psmToMeasure.etl` | Main orchestrator, imports all modules |
| `modules/measure.etl` | Measure and derived measure transformations |
| `modules/unit.etl` | Unit and duration unit transformations |

## Transformation Rules

### Measure Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateMeasure` | Measure | Measure | Abstract rule for measure transformation |
| `CreateBaseMeasure` | Measure | BaseMeasure | Transform base measures (non-derived) |
| `CreateDerivedMeasure` | DerivedMeasure | DerivedMeasure | Transform derived measures with base measure terms |

### Unit Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateUnit` | Unit | Unit | Transform measurement units with rate conversion |
| `CreateDurationUnit` | DurationUnit | DurationUnit | Transform duration units (time-based) |

## Measure Model Structure

### Base Measures
Base measures represent fundamental measurement dimensions (e.g., length, mass, time).

```
BaseMeasure
  - id: String (derived from PSM ID)
  - namespace: String (fully qualified namespace)
  - name: String
  - symbol: String
  - units: List<Unit>
```

### Derived Measures
Derived measures are composed of base measures with exponents (e.g., velocity = length/time).

```
DerivedMeasure
  - id: String
  - namespace: String
  - name: String
  - symbol: String
  - terms: List<BaseMeasureTerm>
    - baseMeasure: BaseMeasure
    - exponent: Integer
```

### Units
Units define specific measurement scales within a measure.

```
Unit
  - id: String
  - name: String
  - symbol: String
  - rateDividend: BigDecimal
  - rateDivisor: BigDecimal
```

### Duration Units
Special unit type for time durations with predefined types.

```
DurationUnit extends Unit
  - type: DurationType (nanosecond, microsecond, millisecond, second, minute, hour, day, week, month, year)
```

## Duration Types

| PSM Type | Measure Type |
|----------|--------------|
| `nanosecond` | `DurationType#nanosecond` |
| `microsecond` | `DurationType#microsecond` |
| `millisecond` | `DurationType#millisecond` |
| `second` | `DurationType#second` |
| `minute` | `DurationType#minute` |
| `hour` | `DurationType#hour` |
| `day` | `DurationType#day` |
| `week` | `DurationType#week` |
| `month` | `DurationType#month` |
| `year` | `DurationType#year` |

## ID Generation

All generated elements have IDs derived from their PSM source:

| Element Type | ID Pattern |
|--------------|------------|
| BaseMeasure | `(psm/{psmId})/BaseMeasure` |
| DerivedMeasure | `(psm/{psmId})/DerivedMeasure` |
| BaseMeasureTerm | `({derivedMeasureId})_((psm/{baseMeasureId})/BaseMeasureTerm)` |
| Unit | `(psm/{psmId})/Unit` |
| DurationUnit | `(psm/{psmId})/DurationUnit{Type}` |

## Usage Example

```java
// Create source PSM model with measures
PsmModel psmModel = buildPsmModel().build();
// ... add measures to PSM model

// Create target Measure model
MeasureModel measureModel = MeasureModel.buildMeasureModel().build();

// Execute transformation
Psm2MeasureTransformationTrace trace = executePsm2MeasureTransformation(
    psm2MeasureParameter()
        .psmModel(psmModel)
        .measureModel(measureModel)
);

// Access transformed measures
Collection<Measure> measures = measureModel.getMeasureUtils().all(Measure.class);
```

## Zeta Implementation

The Zeta implementation uses Java classes with `@TransformRule` annotations:

```java
@TransformationContext(
    sourceModel = PsmModel.class,
    targetModel = MeasureModel.class
)
public class Psm2MeasureZetaTransformation {
    
    @TransformRule(name = Psm2MeasureRuleNames.CREATE_BASE_MEASURE)
    public BaseMeasure transformBaseMeasure(Measure measure, TransformationContext ctx) {
        if (measure instanceof DerivedMeasure) {
            return null; // Skip derived measures
        }
        BaseMeasure baseMeasure = MeasureFactory.eINSTANCE.createBaseMeasure();
        baseMeasure.setId("(psm/" + measure.getId() + ")/BaseMeasure");
        baseMeasure.setNamespace(getNamespaceString(measure));
        baseMeasure.setName(measure.getName());
        baseMeasure.setSymbol(measure.getSymbol());
        return baseMeasure;
    }
    
    @TransformRule(name = Psm2MeasureRuleNames.CREATE_UNIT)
    public Unit transformUnit(hu.blackbelt.judo.meta.psm.measure.Unit psmUnit, TransformationContext ctx) {
        Unit unit = MeasureFactory.eINSTANCE.createUnit();
        unit.setId("(psm/" + psmUnit.getId() + ")/Unit");
        unit.setName(psmUnit.getName());
        unit.setSymbol(psmUnit.getSymbol());
        unit.setRateDividend(psmUnit.getRateDividend());
        unit.setRateDivisor(psmUnit.getRateDivisor());
        return unit;
    }
}
```

Rule name constants are defined in `Psm2MeasureRuleNames`:

```java
public final class Psm2MeasureRuleNames {
    public static final String CREATE_MEASURE = "CreateMeasure";
    public static final String CREATE_BASE_MEASURE = "CreateBaseMeasure";
    public static final String CREATE_DERIVED_MEASURE = "CreateDerivedMeasure";
    public static final String CREATE_UNIT = "CreateUnit";
    public static final String CREATE_DURATION_UNIT = "CreateDurationUnit";
}
```

# `Psm2AsmHelper.java`

778 lines, `hu.blackbelt.judo.tatami.psm2asm.zeta`. Static helper surface shared by every rule class in `rules/`. Java counterpart of the EOL operations in `../../../../../../../epsilon/transformations/asm/utils/`.

## Identity and naming

`getId(Object)` — synthetic ETL-compatible element id, memoised in `ELEMENT_ID_CACHE`.
`namespaceElementToString(NamespaceElement)` — memoised in `NAMESPACE_ELEMENT_STRING_CACHE`; counterpart of ETL's `psmUtils.namespaceElementToString`.
`getQualifiedName`, `getQualifiedNameWithColons` (`::` separator), `getQualifiedNameWithUnderscore` (`_` separator) — three separators, callers must pick the one the ASM consumer expects.
`getClassifierFQName(EClassifier)`, `getEClassRuleName(NamedElement)` — the latter returns the *rule name* to look up an equivalent with, so it must stay in sync with `Psm2AsmRuleNames`.
`capitalize(String)`.

## Namespace URIs

`computeNsUriFromSource(Package, String baseUri)`, `computeNsPrefixFromSource(Package, String basePrefix)` — derive sub-package nsURI/nsPrefix from the model-level base values held in context attributes `nsURI` / `nsPrefix`.

## Annotations

`getAnnotationUri(String annotationName)` — memoised in `ANNOTATION_URI_CACHE`; equivalent of ETL's `asmUtils.getAnnotationUri`. Never build annotation `source` strings by hand.
`createAnnotation(String id, String source)`, `addAnnotationDetail(EAnnotation, String key, String value)`.

## Type classification

`isInteger(NumericType)`, `isDecimal(NumericType)`, `getIntegerClassName(NumericType)`, `getDecimalClassName(NumericType)`, `resolveTypeByName(String, TransformationContext)`, `resolveDataType(...)`.

## Containment mutators

`addClassifier(EPackage, EClassifier)`, `addSubPackage(EPackage, EPackage)`, `addSuperType(EClass, EClass)`, `addStructuralFeature(EClass, EStructuralFeature)`, `addOperation(EClass, EOperation)`, `addParameter(EOperation, EParameter)`, `addEnumLiteral(EEnum, EEnumLiteral)`, `addAnnotation(EModelElement, EAnnotation)`. Rules must mutate the ASM tree through these, not through raw EMF list access — they carry the ETL-compatible ordering and id-propagation behaviour.

## Lookup

`getContainerPackage(EObject, TransformationContext)` — resolves the ASM `EPackage` the element belongs in.
`getEntityType(EObject)` — walks to the owning PSM `EntityType`.

## Cache lifecycle

`clearCaches()` — resets `ANNOTATION_URI_CACHE`, `NAMESPACE_ELEMENT_STRING_CACHE`, `ELEMENT_ID_CACHE`.

## Contracts

- All three caches are `static` `ConcurrentHashMap`s keyed by `EObject` / `NamespaceElement` instances. They are **global process state**, not per-transformation. `Psm2AsmZetaTransformation.execute()` calls `clearCaches()` as its first phase; any other entry point that skips that call leaks stale ids across models and pins the previous model's `EObject`s in memory.
- Two transformations running concurrently in one JVM share these caches — the class is not safe for parallel multi-model use.
- `getAnnotationUri` results must match `AsmUtils.getAnnotationUri` in the ETL path, or Zeta and ETL outputs diverge on annotation `source`.

# Tasks (TDD Approach - Separated Utilities)

## Phase 1: Analysis and Design

- [x] Analyze current comparison limitations
- [x] Design ModelNode containment structure
- [x] Design ReferenceNode for non-containment references
- [x] Design separation: ModelChecksumCalculator vs ModelComparator
- [x] Design ComparisonResult with resolvable EObjects
- [x] Design JSON export format

## Phase 2: Write Failing Tests (RED)

### 2.1 Unit Tests for ModelNode

- [x] Test: `modelNode_storesEObjectReference()`
- [x] Test: `modelNode_findByPath_findsNestedChild()`
- [x] Test: `modelNode_findByPath_returnsNullIfNotFound()`
- [x] Test: `modelNode_findChild_findsByRefAndId()`
- [x] Test: `modelNode_addContainment_groupsByRefName()`
- [x] Test: `modelNode_addReference_groupsByRefName()`
- [x] Test: `modelNode_addAttribute()`
- [x] Test: `modelNode_checksum()`

### 2.2 Unit Tests for ReferenceNode

- [x] Test: `referenceNode_storesTargetInfo()`
- [x] Test: `referenceNode_resolvesToTargetNode()`
- [x] Test: `referenceNode_resolveEObject_returnsSourceObject()`
- [x] Test: `referenceNode_resolve_cachesResult()`
- [x] Test: `referenceNode_clearResolution()`
- [x] Test: `referenceNode_resolve_returnsNullIfNotFound()`
- [x] Test: `referenceNode_toString()`

### 2.3 Unit Tests for ModelChecksumCalculator

- [x] Test: `calculate_buildsContainmentTree()`
- [x] Test: `calculate_identicalModels_sameChecksum()`
- [x] Test: `calculate_differentAttribute_differentChecksum()`
- [x] Test: `calculate_differentType_differentChecksum()`
- [x] Test: `calculate_multiValuedReferenceOrder_sameChecksum()`
- [x] Test: `calculate_containmentsSeparateFromReferences()`
- [x] Test: `calculate_withIgnoredAttribute_excludesFromChecksum()`
- [x] Test: `calculate_withIgnoredReference_patternMatchingWorks()`
- [x] Test: `toJson_producesValidJson()`
- [x] Test: `toJson_includesReferences()`
- [x] Test: `toJson_optionsExcludeAttributes()`

### 2.4 Unit Tests for CalculatorOptions and FeaturePattern

#### Feature Pattern Tests
- [x] Test: `featurePattern_parsesValidPattern()`
- [x] Test: `featurePattern_rejectsInvalidPattern_noHash()`
- [x] Test: `featurePattern_rejectsInvalidPattern_noDot()`
- [x] Test: `featurePattern_rejectsInvalidPattern_emptyFeature()`
- [x] Test: `featurePattern_rejectsInvalidPattern_emptyPackage()`
- [x] Test: `featurePattern_matchesExactPattern()`
- [x] Test: `featurePattern_wildcardPackage_matchesAnyPackage()`
- [x] Test: `featurePattern_wildcardClass_matchesAnyClass()`
- [x] Test: `featurePattern_globalWildcard_matchesAll()`
- [x] Test: `featurePattern_equals_hashCode()`

#### Calculator Options Tests
- [x] Test: `calculatorOptions_fluentChaining()`
- [x] Test: `calculatorOptions_ignoreAll()`
- [x] Test: `calculatorOptions_isIgnored_checksAllPatterns()`
- [x] Test: `calculatorOptions_customIdentifierResolver()`
- [x] Test: `calculatorOptions_defaultIdentifierUsesName()`
- [x] Test: `calculatorOptions_defaultIdentifierFallsBackToTypeIndex()`
- [x] Test: `calculatorOptions_immutableIgnoredFeatures()`

### 2.5 Unit Tests for StructuralModelComparator

#### Basic Difference Type Tests
- [x] Test: `compare_identicalModels_noErrors()`
- [x] Test: `compare_missingElement_reportsDifference()`
- [x] Test: `compare_extraElement_reportsDifference()`
- [x] Test: `compare_attributeMismatch_includesValues()`
- [x] Test: `compare_referenceMismatch_differentTargets()`

#### Multi-Layer Containment Tests
- [x] Test: `compare_multiLayerContainment_identicalStructure()`
- [x] Test: `compare_multiLayerContainment_differenceAtLeaf()`

#### Inheritance Tests
- [x] Test: `compare_inheritance_multipleSuperTypes_orderIndependent()`

#### Checksum Optimization Tests
- [x] Test: `compare_checksumOptimization_identicalLargeModel()`

#### Combined Scenario Tests
- [x] Test: `compare_combined_multipleDifferenceTypes()`
- [x] Test: `compare_getDifferences_filters()`

## Phase 3: Implementation (GREEN)

### 3.1 Data Structures

- [x] Create `ModelNode` class
- [x] Create `ReferenceNode` class
- [x] Create `Difference` class with `DifferenceType` enum
- [x] Create `ComparisonResult` class
- [x] Create `CalculatorOptions` class
- [x] Create `FeaturePattern` class
- [x] Create `JsonOptions` class

### 3.2 ModelChecksumCalculator (Single Model → Structure)

- [x] Implement `calculate(Resource) → ModelNode`
  - Build containment tree recursively
  - Build path index for O(1) lookups
  - Compute checksums bottom-up (leaves first)
  - Add non-containment references
  - Recompute checksums with reference info
- [x] Implement `shouldIncludeReference(EReference)`
- [x] Implement `toJson(ModelNode)` and `toJson(ModelNode, JsonOptions)`

### 3.3 StructuralModelComparator (Two Structures → Differences)

- [x] Implement `compare(ModelNode expected, ModelNode actual) → ComparisonResult`
  - Checksum shortcut for identical subtrees
  - Compare types
  - Compare attributes
  - Compare containments (match by identifier)
  - Compare references (order independent)

## Phase 4: Refactor (REFACTOR)

- [x] Extract common identifier logic (via CalculatorOptions.defaultIdentifier)
- [x] Optimize path index building (built during tree construction)
- [x] Add null-safety checks
- [x] Ensure immutability where appropriate (CalculatorOptions returns unmodifiable collections)
- [x] Run all tests to confirm no regressions (54 tests passing)

## Phase 5: Documentation

- [x] Add Javadoc to `ModelChecksumCalculator`
- [x] Add Javadoc to `StructuralModelComparator`
- [x] Add Javadoc to `ModelNode`
- [x] Add Javadoc to `ReferenceNode`
- [x] Add Javadoc to `Difference` and `DifferenceType`
- [x] Add Javadoc to `ComparisonResult`
- [x] Add Javadoc to `CalculatorOptions`
- [x] Add Javadoc to `FeaturePattern`
- [x] Add Javadoc to `JsonOptions`
- [ ] Document JSON format in user docs
- [ ] Add example usage in agent-docs/patterns/TESTING.md

## Phase 6: Validation

- [ ] Test with Psm2AsmExternalModelTest models
- [ ] Compare ETL vs ZETA output
- [ ] Verify JSON export is valid JSON
- [ ] Benchmark performance on large models
- [ ] Test difference resolution returns correct EObjects

## Phase 7: New Features (fromJson + LLM Output)

### 7.1 JSON Deserialization (fromJson)

#### Positive Test Cases
- [x] Test: `fromJson_reconstructsModelNodeTree()`
- [x] Test: `fromJson_preservesChecksums()`
- [x] Test: `fromJson_preservesContainments()`
- [x] Test: `fromJson_preservesReferences()`
- [x] Test: `fromJson_preservesAttributes()`
- [x] Test: `fromJson_handlesEmptyModel()`
- [x] Test: `fromJson_roundTrip_toJsonFromJson()`
- [x] Test: `fromJson_withoutResource_eObjectsAreNull()`
- [x] Test: `fromJson_withResource_rematchesEObjects()`
- [x] Test: `fromJson_withResource_partialRematch_whenStructureChanged()`
- [x] Test: `fromJson_withResource_nullResourceBehavior()`
- [x] Test: `fromJson_deeplyNestedContainments()`
- [x] Test: `fromJson_multipleReferencesPerNode()`

#### Negative Test Cases
- [x] Test: `fromJson_nullJson_throwsException()`
- [x] Test: `fromJson_emptyJson_throwsException()`
- [x] Test: `fromJson_malformedJson_throwsException()`
- [x] Test: `fromJson_missingRequiredFields_throwsException()`
- [x] Test: `fromJson_invalidChecksum_loadsWithWarning()`
- [x] Test: `fromJson_corruptedStructure_throwsException()`
- [x] Test: `fromJson_withNullResource_sameAsWithoutResource()` (replaces incompatibleVersion)

#### Implementation
- [x] Implement `ModelChecksumCalculator.fromJson(String) → ModelNode`
- [x] Implement `ModelChecksumCalculator.fromJson(String, Resource) → ModelNode`
- [x] Add JSON version header for compatibility checking (handled via schema validation)

### 7.2 LLM-Friendly Output Format (formatForLLM)

#### Positive Test Cases
- [x] Test: `formatForLLM_producesValidXmlStructure()`
- [x] Test: `formatForLLM_includesSummary()`
- [x] Test: `formatForLLM_includesAllDifferenceTypes()`
- [x] Test: `formatForLLM_includesContextAndSuggestions()`
- [x] Test: `formatForLLM_handlesNoDifferences()`
- [x] Test: `formatForLLM_escapesSpecialCharacters()`
- [x] Test: `formatForLLM_respectsFormatOptions()` (combines option tests)
- [x] Test: `formatForLLM_multipleDifferences_allIncluded()`
- [x] Test: `formatForLLM_attributeMismatch_showsExpectedAndActual()`
- [x] Test: `formatForLLM_referenceMismatch_showsTargetPaths()`

#### Negative Test Cases
- [x] Test: `formatForLLM_nullResult_throwsException()`
- [x] Test: `formatForLLM_nullOptions_usesDefaults()`
- [x] Test: `formatForLLM_maxDifferencesZero_returnsOnlySummary()`
- [x] Test: `formatForLLM_negativeMaxDifferences_throwsException()`

#### Implementation
- [x] Implement `StructuralModelComparator.formatForLLM(ComparisonResult) → String`
- [x] Implement `FormatOptions` class for customization
- [ ] Add severity levels to Difference enum (deferred - not required for initial release)

### 7.3 Incremental Model Handling (saveJson/loadJson)

#### Positive Test Cases
- [x] Test: `saveJson_writesValidJsonFile()`
- [x] Test: `saveJson_includesTimestamp()`
- [x] Test: `saveJson_includesModelChecksums()`
- [x] Test: `saveJson_preservesAllDifferences()`
- [x] Test: `loadJson_reconstructsComparisonResult()`
- [x] Test: `loadJson_roundTrip_saveAndLoad()`
- [x] Test: `saveLoadJson_emptyDifferences()`
- [x] Test: `saveLoadJson_manyDifferences()`

#### Negative Test Cases
- [x] Test: `saveJson_nullResult_throwsException()`
- [x] Test: `saveJson_nullPath_throwsException()`
- [x] Test: `saveJson_invalidPath_throwsIOException()`
- [x] Test: `loadJson_nullPath_throwsException()`
- [x] Test: `loadJson_nonExistentFile_throwsException()`
- [x] Test: `loadJson_emptyFile_throwsException()`
- [x] Test: `loadJson_malformedJson_throwsException()`
- [x] Test: `loadJson_incompatibleVersion_throwsException()`
- [x] Test: `loadJson_corruptedFile_throwsException()`

#### Implementation
- [x] Implement `StructuralModelComparator.saveJson(ComparisonResult, Path) → void`
- [x] Implement `StructuralModelComparator.loadJson(Path) → ComparisonResult`
- [x] Add JSON schema version for forward compatibility

### 7.4 Integration Tests

- [ ] Test: `fullWorkflow_calculateToJsonFromJsonCompareFormat()`
- [ ] Test: `fullWorkflow_incrementalComparison_detectsRegressions()`
- [ ] Test: `fullWorkflow_cachedBaseline_matchesLiveCalculation()`
- [ ] Test: `fullWorkflow_llmOutput_parsableByAgent()`
- [ ] Update Javadoc for new methods
- [ ] Add usage examples to documentation

## Implementation Summary

All core implementation tasks are complete:

- **Location**: `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/comparison/`
- **Tests**: `judo-tatami-test-utils/src/test/java/hu/blackbelt/judo/tatami/test/util/comparison/`

### Classes Created:
1. `ModelNode` - Containment tree node with EObject reference
2. `ReferenceNode` - Non-containment reference with lazy resolution
3. `Difference` - Comparison difference with resolvable EObjects
4. `ComparisonResult` - Collection of differences with filtering
5. `CalculatorOptions` - Configuration for ignore patterns and identifier resolution
6. `FeaturePattern` - Wildcard pattern matching for feature filtering
7. `JsonOptions` - JSON export configuration
8. `FormatOptions` - LLM formatting configuration
9. `ModelChecksumCalculator` - Builds structural representation with checksums
   - Includes `fromJson(String)` and `fromJson(String, Resource)` for JSON deserialization
10. `StructuralModelComparator` - Compares two ModelNode trees
    - Includes `formatForLLM(ComparisonResult)` for LLM-friendly XML output
    - Includes `saveJson(ComparisonResult, Path)` and `loadJson(Path)` for incremental handling

### Test Coverage:
- 105 tests total, all passing
- Test classes:
  - `ModelNodeTest` (8 tests)
  - `ReferenceNodeTest` (7 tests)
  - `FeaturePatternTest` (10 tests)
  - `CalculatorOptionsTest` (7 tests)
  - `ModelChecksumCalculatorTest` (11 tests)
  - `ModelChecksumCalculatorFromJsonTest` (20 tests)
  - `StructuralModelComparatorTest` (11 tests)
  - `StructuralModelComparatorFormatTest` (14 tests)
  - `StructuralModelComparatorJsonTest` (17 tests)

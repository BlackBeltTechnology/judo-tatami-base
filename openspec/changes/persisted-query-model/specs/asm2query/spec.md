## ADDED Requirements

### Requirement: Query builder module
The system SHALL provide the OSGi bundle `judo-tatami-asm2query` exporting `hu.blackbelt.judo.tatami.asm2query.*`. Its POM and its main sources SHALL NOT depend directly on epsilon, `mapper-api` or `judo-dispatcher-api`.

#### Scenario: No direct epsilon
- **WHEN** the module POM and `src/main` are inspected
- **THEN** the POM declares no `epsilon-*` artifact and `src/main` contains no `org.eclipse.epsilon` reference

#### Scenario: Exported package
- **WHEN** the bundle manifest is generated
- **THEN** `Export-Package` contains `hu.blackbelt.judo.tatami.asm2query.*`

### Requirement: Builder classes relocated
The query builder (`FeatureFactory`, `JoinFactory`, `Context`, `Constants`, the feature converters, the build part of `QueryFactory`) and the binding classes (`TransferObjectTypeBindingsCollector`, `EntityTypeExpressions`, the bindings) SHALL live in `hu.blackbelt.judo.tatami.asm2query.*`. The `FeatureFactory` and `ConstantToFeatureConverter` constructors SHALL NOT take a `Coercer`.

#### Scenario: Moved types compile in the new package
- **WHEN** the module is compiled
- **THEN** the moved builder and binding classes resolve under `hu.blackbelt.judo.tatami.asm2query`

### Requirement: Asm2Query builds the query model
`Asm2Query` SHALL build the logical query model over a given ASM, measure and expression `ResourceSet` into a resource with the logical URI `<name>-query.model`, never `query:in-memory`. It SHALL accept custom join definitions with its own value type holding three strings.

#### Scenario: Logical resource URI
- **WHEN** `Asm2Query` builds the query model for the ASM model `NorthwindDemo`
- **THEN** the query resource URI is `NorthwindDemo-query.model`

#### Scenario: Custom join value type
- **WHEN** a custom join definition is supplied for an `EReference`
- **THEN** the build uses it during construction in the same way as the runtime-core builder did

### Requirement: Asm2Query emits QueryModelInfo
The build SHALL add one `QueryModelInfo` root holding `formatVersion` and `modelName`, the query, navigation and data query entries, the ordered transfer relations, the static entity attribute and reference sets, the entity expression entries including empty ones, and the source and target counters. The static sets SHALL hold exactly the features evaluated with an empty variable list.

#### Scenario: Every field matches the built index
- **GIVEN** the Northwind ASM and expression models
- **WHEN** `Asm2Query` builds the query model
- **THEN** every `QueryModelInfo` field equals the corresponding value of the built index

#### Scenario: Static sets exclude unevaluated and non-static features
- **WHEN** a feature was never evaluated or has a non-empty variable list
- **THEN** it is in neither static set

### Requirement: Binding index replaces the per-call scan
`TransferObjectTypeBindingsCollector` SHALL build once an index of binding class to type FQN (`namespace::name`) to bindings, covering the whole expression `ResourceSet` in `getAllContents()` encounter order exactly as the scan, and answer every lookup from it. Results SHALL be identical, in elements and order, to the old scan, which SHALL remain only as a test-only reference.

#### Scenario: Index equals the reference scan
- **GIVEN** the Northwind expression model
- **WHEN** every collector lookup runs through the index and through the reference scan
- **THEN** the elements and their order are identical for every binding class and type FQN

### Requirement: Mapped transfer object graph resolution
The `TransferObjectTypeBindingsCollector.getTransferObjectGraph()` method SHALL resolve the complete expression tree for a mapped transfer object type including all attribute and reference bindings.

#### Scenario: Resolving a mapped transfer object type
- **GIVEN** a `TransferObjectTypeBindingsCollector` initialized with valid ASM and expression resource sets, and a mapped transfer object type with attribute and reference bindings
- **WHEN** `getTransferObjectGraph(mappedTransferObjectType)` is called
- **THEN** an `Optional<MappedTransferObjectTypeBindings>` is returned containing the entity type, getter/setter attribute expressions, getter/setter reference expressions, and nested reference bindings

#### Scenario: Rejecting an unmapped or entity type
- **GIVEN** a mapped transfer object type that has no mapped entity type
- **WHEN** `getTransferObjectGraph(mappedTransferObjectType)` is called
- **THEN** `Optional.empty()` is returned

#### Scenario: Circular reference handling
- **GIVEN** two mapped transfer object types that reference each other
- **WHEN** `getTransferObjectGraph(typeA)` is called
- **THEN** the second type is resolved from the `processedMappedTransferObjectTypeBindings` cache without infinite recursion

### Requirement: Unmapped transfer object bindings resolution
The `TransferObjectTypeBindingsCollector.getTransferObjectBindings()` method SHALL resolve expression bindings for unmapped (non-mapped, non-entity) transfer object types.

#### Scenario: Resolving an unmapped transfer object type
- **GIVEN** an unmapped transfer object type with derived attributes and references that have expression bindings
- **WHEN** `getTransferObjectBindings(unmappedTransferObjectType)` is called
- **THEN** an `Optional<UnmappedTransferObjectTypeBindings>` is returned with `dataExpressions` containing `DataExpression` bindings for derived attributes and `navigationExpressions` containing `ReferenceExpression` bindings for derived references

#### Scenario: Rejecting a mapped or entity type
- **GIVEN** a mapped transfer object type or an entity type
- **WHEN** `getTransferObjectBindings(type)` is called
- **THEN** `Optional.empty()` is returned

### Requirement: Attribute binding role classification
The collector SHALL classify attribute bindings into getter and setter roles based on the `AttributeBindingRole` of the expression model.

#### Scenario: Getter attribute binding
- **GIVEN** an `AttributeBinding` with role `GETTER` and a `DataExpression`
- **WHEN** the transfer object graph is resolved
- **THEN** the binding is placed in `getterAttributeExpressions` of the `MappedTransferObjectTypeBindings`

#### Scenario: Setter attribute binding
- **GIVEN** an `AttributeBinding` with role `SETTER` and a `DataExpression`
- **WHEN** the transfer object graph is resolved
- **THEN** the binding is placed in `setterAttributeExpressions` of the `MappedTransferObjectTypeBindings`

#### Scenario: Invalid getter binding type
- **GIVEN** an `AttributeBinding` with role `GETTER` whose expression is not a `DataExpression`
- **WHEN** the transfer object graph is resolved
- **THEN** an `IllegalStateException` is thrown with message "Getter binding is not a DataExpression"

### Requirement: Reference binding role classification
The collector SHALL classify reference bindings into getter and setter roles based on the `ReferenceBindingRole` of the expression model.

#### Scenario: Getter reference binding
- **GIVEN** a `ReferenceBinding` with role `GETTER` and a `ReferenceExpression`
- **WHEN** the transfer object graph is resolved
- **THEN** the binding is placed in `getterReferenceExpressions` of the `MappedTransferObjectTypeBindings`

#### Scenario: Setter reference binding
- **GIVEN** a `ReferenceBinding` with role `SETTER` and a `ReferenceExpression`
- **WHEN** the transfer object graph is resolved
- **THEN** the binding is placed in `setterReferenceExpressions` of the `MappedTransferObjectTypeBindings`

### Requirement: Filter binding resolution
The collector SHALL resolve `FilterBinding` expressions and set them as the filter on the `MappedTransferObjectTypeBindings`.

#### Scenario: Resolving a filter binding
- **GIVEN** a `FilterBinding` for a mapped transfer object type with a `LogicalExpression`
- **WHEN** the transfer object graph is resolved
- **THEN** `MappedTransferObjectTypeBindings.getFilter()` returns the `LogicalExpression`

#### Scenario: Invalid filter expression type
- **GIVEN** a `FilterBinding` whose expression is not a `LogicalExpression`
- **WHEN** the transfer object graph is resolved
- **THEN** an `IllegalStateException` is thrown with message "Filter binding is not a LogicalExpression"

### Requirement: Static expression detection
The `TransferObjectTypeBindingsCollector` SHALL determine whether an attribute or reference expression is static (has no variables in scope).

#### Scenario: Static attribute expression
- **GIVEN** an entity attribute whose getter expression has no variables in scope
- **WHEN** `isStaticAttribute(attribute)` is called
- **THEN** `true` is returned

#### Scenario: Dynamic attribute expression
- **GIVEN** an entity attribute whose getter expression has variables in scope
- **WHEN** `isStaticAttribute(attribute)` is called
- **THEN** `false` is returned

#### Scenario: Unknown attribute
- **GIVEN** an entity attribute that has no registered expression binding
- **WHEN** `isStaticAttribute(attribute)` is called
- **THEN** `null` is returned

### Requirement: Entity type expressions map
The `TransferObjectTypeBindingsCollector` SHALL maintain an unmodifiable map of entity types to their getter expressions.

#### Scenario: Accessing entity type expressions
- **GIVEN** a fully initialized `TransferObjectTypeBindingsCollector`
- **WHEN** `getEntityTypeExpressionsMap()` is called
- **THEN** an unmodifiable `Map<EClass, EntityTypeExpressions>` is returned containing all entity types with their getter attribute and reference expressions

### Requirement: Expression-to-feature conversion
The `hu.blackbelt.judo.tatami.asm2query.FeatureFactory.convert()` method SHALL dispatch expression conversion to the appropriate `ExpressionToFeatureConverter` based on the expression's runtime type.

#### Scenario: Converting a supported expression
- **GIVEN** a `FeatureFactory` with registered converters and a `Concatenate` expression
- **WHEN** `convert(concatenateExpression, context, targetMapping)` is called
- **THEN** a `Feature` object is returned representing the concatenation operation, with the `targetMapping` added to `feature.getTargetMappings()`

#### Scenario: Converting an unsupported expression
- **GIVEN** a `FeatureFactory` and an expression type that has no registered converter
- **WHEN** `convert(unknownExpression, context, targetMapping)` is called
- **THEN** an `IllegalStateException` is thrown with message "Unsupported expression: <className>"

### Requirement: Navigation-to-join conversion
The `hu.blackbelt.judo.tatami.asm2query.JoinFactory.convertNavigationToJoins()` method SHALL convert a `ReferenceExpression` into a chain of `Join` objects representing the navigation path.

#### Scenario: Simple reference navigation
- **GIVEN** a `ReferenceExpression` representing a single non-derived reference step
- **WHEN** `convertNavigationToJoins(context, container, expression, false)` is called
- **THEN** a `ReferencedJoin` is added to the container's joins, and `PathEnds.getPartner()` returns the terminal join node

#### Scenario: Navigation with cast
- **GIVEN** a `ReferenceExpression` containing a `CastObject` or `CastCollection` step
- **WHEN** `convertNavigationToJoins(context, container, expression, false)` is called
- **THEN** a `CastJoin` is added to the join chain with the target type

#### Scenario: Navigation with filter
- **GIVEN** a `ReferenceExpression` containing a `CollectionFilterExpression` with a condition
- **WHEN** `convertNavigationToJoins(context, container, expression, false)` is called
- **THEN** a `Filter` is added to the container's filters with the condition converted to a `Feature`

#### Scenario: Navigation with ordering
- **GIVEN** a `ReferenceExpression` containing a `SortExpression` with order-by items
- **WHEN** `convertNavigationToJoins(context, container, expression, false)` is called
- **THEN** `OrderBy` objects are added to the container and `PathEnds.isOrdered()` returns `true`

#### Scenario: Navigation with limit and offset
- **GIVEN** a `ReferenceExpression` containing a `SubCollectionExpression` with integer constant limit and offset
- **WHEN** `convertNavigationToJoins(context, container, expression, false)` is called
- **THEN** the limit and offset are set on the enclosing `SubSelect` or returned in `PathEnds`

#### Scenario: Derived reference rejected in navigation
- **GIVEN** a navigation path that includes a derived reference step
- **WHEN** `convertNavigationToJoins` processes the step
- **THEN** an `IllegalArgumentException` is thrown indicating derived references must be resolved by the expression builder

### Requirement: Query context management
The `hu.blackbelt.judo.tatami.asm2query.Context` SHALL provide variable scoping and node management for query construction.

#### Scenario: Cloning context with a new variable
- **GIVEN** a `Context` with variables `{self: selectNode}`
- **WHEN** `clone("iterator", joinNode)` is called
- **THEN** a new `Context` is returned with variables `{self: selectNode, iterator: joinNode}` and `node` set to `joinNode`, while the original context remains unchanged

#### Scenario: Adding a feature to a Select node
- **GIVEN** a `Context` with `node` set to a `Select`
- **WHEN** `addFeature(feature)` is called
- **THEN** the feature is added to the Select's features list

#### Scenario: Adding a feature to a Join node
- **GIVEN** a `Context` with `node` set to a `Join`
- **WHEN** `addFeature(feature)` is called
- **THEN** the feature is added to the Join's base Select's features list

### Requirement: QueryIndex restores the runtime index
`QueryIndex` SHALL be restored from `QueryModelInfo` using today's concurrent types, and SHALL answer the lookups, ordering, counters and the entity expression map like the built index. `isStaticAttribute` and `isStaticReference` SHALL be `getMappedX(feature).map(staticSet::contains).orElse(true)`.

#### Scenario: Built and restored index are equal
- **GIVEN** a Northwind query model that was built, saved and loaded
- **WHEN** a `QueryIndex` is restored from it
- **THEN** `getQuery` for every mapped transfer type, `getNavigation`, `getDataQuery`, `isOrdered` and the counters equal the built ones, and the entity expression entry count and expression object identity are equal

#### Scenario: Static answers for every ASM feature
- **WHEN** restored and built `isStaticAttribute` and `isStaticReference` are compared for every declared ASM `EAttribute` and `EReference`
- **THEN** there are 0 mismatches

### Requirement: PersistedQueryModel locator
`PersistedQueryModel` SHALL carry the logical model name and exactly one supplier of the query artifact stream. The supplier SHALL NOT be invoked until the load-or-build decision needs the stream.

#### Scenario: Locator is lazy
- **WHEN** a `PersistedQueryModel` is created
- **THEN** its stream supplier has not been called

### Requirement: Save rewrites model URIs
`QueryModelPersistence` save SHALL rewrite only the ASM and expression resource URIs, to `<name>-asm.model` and `<name>-expression.model`, so every reference is written relative to the sibling files. References into Ecore via package `nsURI` SHALL stay untouched.

#### Scenario: Hrefs use runtime file names
- **GIVEN** the Northwind ASM and expression models under in-memory URIs
- **WHEN** `QueryModelPersistence.save` writes `NorthwindDemo-query.model`
- **THEN** every ASM href starts with `NorthwindDemo-asm.model#`, every expression href with `NorthwindDemo-expression.model#`, and no other prefix occurs except Ecore `nsURI`s

### Requirement: Save reference guard
Before writing, save SHALL accept a non-containment target only if it is an ASM object whose `xmi:id` occurs exactly once (counted over the whole ASM resource), an expression object with a non-null id occurring once, an Ecore object, or an object of the query resource. Otherwise it SHALL write nothing, delete a stale file and log.

#### Scenario: Duplicate id on an unreferenced element
- **GIVEN** a Northwind ASM with a duplicate `xmi:id` on an unreferenced annotation
- **WHEN** the query model is saved
- **THEN** the artifact is written

#### Scenario: Duplicate id on a referenced element
- **GIVEN** a Northwind ASM with a duplicate `xmi:id` on a referenced `EAttribute`
- **WHEN** the query model is saved
- **THEN** the build succeeds, no artifact is written, and a warning contains the duplicated id

#### Scenario: Foreign resource
- **WHEN** the query graph holds a reference into a third resource and is saved
- **THEN** no artifact is written and the warning names the foreign resource URI

### Requirement: Save stamps the artifact
Save SHALL record `formatVersion`, `modelName`, the owner-only `generator` identity (the module version, plus the build timestamp for a SNAPSHOT), and the `asmDigest`, `measureDigest` and `expressionDigest`. An absent measure model SHALL give `measureDigest` of `none`.

#### Scenario: Stamps present
- **WHEN** a query model is saved
- **THEN** the root carries the format version, model name, generator and all three digests

#### Scenario: Measure model absent
- **GIVEN** a Northwind build without a measure model
- **WHEN** the model is saved and restored without a measure model
- **THEN** `measureDigest` is `none` on both sides and the restore succeeds

### Requirement: Generator identity resource
The module SHALL ship a build-filtered `asm2query-generator.properties` with its version and build timestamp, and SHALL read the generator identity only from its own copy. An unreadable or missing resource SHALL count as a mismatch.

#### Scenario: Unreadable resource
- **WHEN** the properties resource cannot be read
- **THEN** the generator identity is reported as a mismatch

### Requirement: Load resolves against the live models
`QueryModelPersistence` load SHALL read a stream under the logical URI `<name>-query.model` in a dedicated `ResourceSet`, map `<name>-asm.model` and `<name>-expression.model` onto the live resources, call `EcoreUtil.resolveAll` once and report the unresolved proxy count.

#### Scenario: Round trip from file, jar URL and nested path
- **WHEN** the saved artifact is loaded from a file, a jar URL and a nested path
- **THEN** 0 proxies are unresolved and every ASM reference is identical to the live ASM object

#### Scenario: Expression references resolve
- **WHEN** the artifact is loaded against the live expression model
- **THEN** every `QueryModelInfo` reference into the expression model resolves

#### Scenario: Mismatched ASM
- **WHEN** the artifact is loaded against a different ASM
- **THEN** the unresolved proxy count is greater than 0

### Requirement: Digest helper over complete streams
The persistence API SHALL provide a digest helper computing the SHA-256 hex over a stream drained to EOF, for loaders to digest model files while loading.

#### Scenario: Trailing bytes included
- **WHEN** a parser stops reading before the end of a file
- **THEN** the helper still digests all bytes of the file

### Requirement: Constant decoding allow-list
Load SHALL decode `EJavaObject` values through an `XMLHelper` override with a per-stream `ObjectInputFilter` that allows only the `java.lang` number, boolean and string types, `BigInteger`, `BigDecimal`, `java.time.*` including `java.time.Ser`, and primitive arrays, within depth and length limits. Any other type SHALL fail the load.

#### Scenario: Allowed constant types
- **GIVEN** constants of `BigInteger`, `BigDecimal`, `Boolean`, `Integer`, `String`, `LocalDate`, `LocalTime`, `LocalDateTime` and `OffsetDateTime`
- **WHEN** the artifact is loaded through the filter
- **THEN** all load and each value keeps its `getClass()`

#### Scenario: Disallowed constant type
- **GIVEN** a `Constant.value` hex-encoding a `java.util.Date`, or a custom `Serializable`
- **WHEN** the artifact is loaded
- **THEN** the load fails with a filter rejection

### Requirement: Asm2QueryWork produces the query model
`Asm2QueryWork` SHALL read `AsmModel`, an optional `MeasureModel` (an empty set when absent) and the `ExpressionModel` created in the same run (per the `ExpressionModelOrigin` context value), and put a `QueryModel` into the context. It SHALL NOT use `EmfUtils` or change the `ExpressionModel`. Absent or preloaded: skipped with a WARN, no `QueryModel`.

#### Scenario: Expression model left unchanged
- **GIVEN** a Northwind context after `asm2expression` and a copy of its expression model
- **WHEN** `Asm2QueryWork` runs
- **THEN** `getByClass(ExpressionModel.class)` is the same instance, `EqualityHelper`-equal to the copy, and one `QueryModel` is present

#### Scenario: Expression model absent
- **WHEN** the context has no `ExpressionModel`
- **THEN** the work is skipped with a WARN and no `QueryModel` is put into the context

#### Scenario: Preloaded expression model
- **GIVEN** a Northwind ASM and a saved `-expression.model` put into the context before `Asm2ExpressionWork`, so `ExpressionModelOrigin` is preloaded
- **WHEN** `Asm2QueryWork` runs
- **THEN** it is skipped with a WARN "expression model not extracted in this run" and no `QueryModel` is put into the context

### Requirement: Asm2QueryConfiguration
`Asm2QueryConfiguration` SHALL be an optional context entry for the work. The query build SHALL use the expression model of the run, extracted with `resolveOnlyCurrentLambdaScope` of `false` by default.

#### Scenario: Default extraction setting
- **GIVEN** the default `Asm2ExpressionConfiguration` and `Asm2Expression` run on Northwind
- **WHEN** `Asm2Query` then builds the query model
- **THEN** the extraction used `resolveOnlyCurrentLambdaScope=false` and the query resource URI is `NorthwindDemo-query.model`

### Requirement: Build and restore timing is measured
The module tests SHALL log the wall time of a restore and of a build on the Northwind artifacts, five restores after one warm-up, with no pass or fail threshold.

#### Scenario: Timing logged
- **WHEN** the timing test runs
- **THEN** both complete, five iterations are measured and the median is logged

### Requirement: Output predicate in the workflow helpers
`asm2QueryOutputPredicate()` SHALL be defined in each `WorkflowHelper` copy next to `asm2ExpressionOutputPredicate()`, as the workflow modules own the output predicates. The `judo-tatami-asm2query` module SHALL NOT define it.

#### Scenario: Predicate beside its sibling
- **WHEN** a workflow module's `WorkflowHelper` is inspected
- **THEN** `asm2QueryOutputPredicate()` is defined next to `asm2ExpressionOutputPredicate()` and `Asm2QueryWork` has no such method

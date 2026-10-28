# exact-xmiid-api Specification

## Purpose
Defines the TransformationContext API for source-based target creation with deterministic XMI IDs, including createTarget with source+suffix and buildSourceBasedId for inline elements.

## Requirements

### Requirement: Source-based target creation with suffix
The TransformationContext SHALL provide a `createTarget(Class<T>, EObject source, String suffix)` method that creates a target element with an XMI ID constructed as `{sourcePath}/{suffix}`, where `sourcePath` is derived from the source element's registered resource alias and XMI ID.

#### Scenario: createTarget with source and suffix produces correct ID
- **WHEN** `registerResource("psm", sourceResourceSet)` has been called and source element has XMI ID `_abc123`
- **AND** `ctx.createTarget(EClass.class, source, "EntityClass")` is called
- **THEN** the created element has XMI ID `(psm/_abc123)/EntityClass`

#### Scenario: createTarget with source uses resource alias from registerResource
- **WHEN** `registerResource("asm", sourceResourceSet)` has been called and source element has XMI ID `_def456`
- **AND** `ctx.createTarget(RdbmsTable.class, source, "Table")` is called
- **THEN** the created element has XMI ID `(asm/_def456)/Table`

#### Scenario: createTarget with source delegates to existing createTarget with customId
- **WHEN** `ctx.createTarget(EClass.class, source, "EntityClass")` is called
- **THEN** the element is registered in the resolution cache, pending XMI IDs, and inherits all behavior of `createTarget(Class, String)`

### Requirement: Source-based ID construction for inline elements
The TransformationContext SHALL provide a `buildSourceBasedId(EObject source, String suffix)` method that returns the string `{sourcePath}/{suffix}` without creating any element.

#### Scenario: buildSourceBasedId returns correct ID string
- **WHEN** `registerResource("psm", sourceResourceSet)` has been called and source element has XMI ID `_abc123`
- **AND** `ctx.buildSourceBasedId(source, "EntityAnnotationClass")` is called
- **THEN** it returns the string `(psm/_abc123)/EntityAnnotationClass`

#### Scenario: buildSourceBasedId used with setElementId for inline elements
- **WHEN** an inline annotation is created via EMF factory (not via `createTarget`)
- **AND** `ctx.setElementId(annotation, ctx.buildSourceBasedId(source, "EntityAnnotationClass"))` is called
- **THEN** the annotation has XMI ID `(psm/_abc123)/EntityAnnotationClass`

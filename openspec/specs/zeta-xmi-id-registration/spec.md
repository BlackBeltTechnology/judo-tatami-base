# zeta-xmi-id-registration Specification

## Purpose
Defines the correct patterns for XMI ID registration in Zeta transformation rules, ensuring createTarget with ID is used instead of setUuid alone, and that extending rules properly override parent IDs.

## Requirements

### Requirement: Zeta transformation rules MUST use createTarget with ID to register XMI IDs

When a Zeta transformation rule creates a target element and needs a specific XMI ID, it MUST pass the ID at creation time via `ctx.createTarget(Type.class, id)`. Calling `t.setUuid(id)` after creation SHALL NOT be used as the sole mechanism for XMI ID registration, because `setUuid()` only sets an EMF structural feature and does not update `TransformationContext.pendingXmiIds` or `XMLResource.setID()`.

#### Scenario: createTarget with ID registers XMI ID in XMLResource

- **WHEN** a rule calls `ctx.createTarget(RdbmsTable.class, "(asm/.../Table)")`
- **THEN** `TransformationContext.pendingXmiIds` maps the element to `"(asm/.../Table)"`
- **AND** at commit time `commitStagedElements()` calls `XMLResource.setID(element, "(asm/.../Table)")`
- **AND** `ModelComparator.getXmiId(element)` returns `"(asm/.../Table)"`

#### Scenario: setUuid alone does NOT register XMI ID

- **WHEN** a rule calls `ctx.createTarget(RdbmsTable.class)` followed by `t.setUuid("(asm/.../Table)")`
- **THEN** `TransformationContext.pendingXmiIds` contains the auto-generated rule-name ID (e.g., `"(asm/.../EClassToRdbmsTable)"`)
- **AND** `ModelComparator.getXmiId(element)` returns the auto-generated ID, NOT the uuid field value

### Requirement: Extending Zeta rules MUST override the parent rule's XMI ID suffix

When a concrete Zeta rule extends an abstract rule via `@Extends` and `ctx.executeParentRule()`, and the ETL concrete rule sets a different XMI ID suffix than the abstract rule, the Zeta concrete rule MUST call `ctx.setElementId(t, id)` after `executeParentRule()` to override the parent's ID.

#### Scenario: EAttributeToTableValueField overrides EAttributeToRdbmsField XMI ID

- **WHEN** `eAttributeToTableValueField()` executes for an EAttribute
- **THEN** it calls `ctx.executeParentRule(EATTRIBUTE_TO_RDBMS_FIELD, s)` to get the `RdbmsValueField`
- **AND** it calls `ctx.setElementId(t, "(asm/<sourceId>)/TableValueField")` to override the parent ID
- **AND** `ModelComparator.getXmiId(t)` returns `"(asm/<sourceId>)/TableValueField"` (not `/RdbmsField`)

#### Scenario: Overriding XMI ID does not collide with parent ID

- **WHEN** `ctx.setElementId(t, newId)` is called to override a pending ID
- **THEN** `setElementIdInternal()` removes the old ID from `pendingXmiIdIndex`
- **AND** stores the new ID in both `pendingXmiIds` and `pendingXmiIdIndex`
- **AND** no stale entries remain for the old auto-generated ID

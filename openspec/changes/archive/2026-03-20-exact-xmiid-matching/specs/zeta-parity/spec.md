## ADDED Requirements

### Requirement: Zeta rules produce ETL-identical XMI ID suffixes
Every Zeta transformation rule SHALL use the ETL rule name as the XMI ID suffix in `createTarget(Class, EObject, String)` calls, so that the resulting XMI ID is byte-identical to the ETL-produced ID for the same source element.

#### Scenario: psm2asm Zeta rule produces same ID as ETL
- **WHEN** a PSM EntityType with XMI ID `_abc123` is transformed by both ETL and Zeta
- **THEN** both produce a target EClass with XMI ID `(psm/_abc123)/EntityClass`

#### Scenario: asm2rdbms Zeta rule produces same ID as ETL
- **WHEN** an ASM EClass with XMI ID `_def456` is transformed by both ETL and Zeta
- **THEN** both produce a target RdbmsTable with XMI ID `(asm/_def456)/Table`

#### Scenario: Split operation rules use original ETL suffix
- **WHEN** Zeta has separate BTO and UO rules for an ETL rule named `BoundOperationAnnotation`
- **THEN** both Zeta variants (BoundTransferOperation and UnboundOperation) use suffix `BoundOperationAnnotation`
- **AND** the full XMI IDs remain unique because the source element IDs differ

### Requirement: Zeta rules use createTarget 3-arg API for ID construction
All Zeta transformation rules SHALL use `ctx.createTarget(Class, source, suffix)` instead of manually constructing ID strings with `"(prefix/" + getId(s) + ")/Suffix"`.

#### Scenario: No manual ID string construction in rules
- **WHEN** any Zeta rule in psm2asm or asm2rdbms creates a target element
- **THEN** it uses `ctx.createTarget(Type.class, source, "Suffix")` or `ctx.buildSourceBasedId(source, "Suffix")` for inline elements
- **AND** no rule contains hardcoded prefix strings like `"(psm/"` or `"(asm/"`

### Requirement: UUID field parity preserved
For RDBMS model elements that have a `uuid` field, Zeta rules SHALL continue to call `setUuid()` with the same ID value to maintain field parity with ETL output.

#### Scenario: RDBMS element has uuid matching XMI ID
- **WHEN** a Zeta asm2rdbms rule creates a target element with `ctx.createTarget(RdbmsTable.class, source, "Table")`
- **THEN** it also calls `t.setUuid(ctx.buildSourceBasedId(source, "Table"))` with the same ID value

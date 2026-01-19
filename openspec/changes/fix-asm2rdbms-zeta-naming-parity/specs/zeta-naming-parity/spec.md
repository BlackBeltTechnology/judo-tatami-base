# ZETA Naming Parity Specification

## ADDED Requirements

### Requirement: ZETA SQL Table Names MUST Match ETL

The ZETA transformation MUST generate SQL table names that match the ETL transformation output exactly.

#### Scenario: Entity class table name generation
Given an entity class `rackinspect.entities.User`
When the ZETA transformation generates a table
Then the SQL name must be `T_ENTITIES_USER` (matching ETL)
And the name must use the pattern `tablePrefix + abbreviate(packageSqlName + "_" + classSqlLongName)`

#### Scenario: Table name with simple name mode
Given `createSimpleName=true` configuration
And an entity class `rackinspect.entities.User`
When the ZETA transformation generates a table
Then the SQL name must use only the class name without package prefix (e.g., `T_USER`)

#### Scenario: Table name with shortName annotation
Given an entity class with `shortName=USR` annotation
When the ZETA transformation generates a table
Then the SQL name must use the shortName value instead of the class name

### Requirement: ZETA SQL Column Names MUST Match ETL

The ZETA transformation MUST generate SQL column names using proper CamelCase to UPPER_SNAKE_CASE conversion.

#### Scenario: Attribute column name generation
Given an attribute `primaryUserAddress` on entity `User`
When the ZETA transformation generates a column
Then the SQL name must be `C_PRIMARY_USER_ADDRESS` (not `C_PRIMARYUSERADDRESS`)
And CamelCase boundaries must be converted to underscores

#### Scenario: Reference foreign key column name generation
Given a reference `primaryUserAddress` on entity `User`
When the ZETA transformation generates a foreign key column
Then the SQL name must be `C_PRIMARY_USER_ADDRESS_ID`
And must follow the pattern `columnPrefix + sqlLongName + "_ID"`

#### Scenario: Column name with shortName annotation
Given an attribute with `shortName=ADDR` annotation
When the ZETA transformation generates a column
Then the SQL name must use the shortName value (e.g., `C_ADDR`)

### Requirement: ZETA Foreign Key Constraint Names MUST Match ETL

The ZETA transformation MUST generate foreign key constraint names using class+reference abbreviation, not MD5 hashes.

#### Scenario: Foreign key constraint name generation
Given a reference `primaryUserAddress` from entity `User`
When the ZETA transformation generates a foreign key constraint
Then the constraint name must be `FK_ENTITIES_USER_PRIMARY_USER_ADDRESS`
And must NOT use MD5 hash in the name
And must follow the pattern `foreignKeyPrefix + abbreviate(classSqlName + "_" + referenceSqlLongName)`

#### Scenario: Inverse foreign key constraint name generation
Given an inverse reference `user` from entity `Address`
When the ZETA transformation generates an inverse foreign key constraint
Then the constraint name must use the pattern `inverseForeignKeyPrefix + abbreviate(classSqlName + "_" + referenceSqlLongName)`

### Requirement: ZETA Junction Table Names MUST Match ETL

The ZETA transformation MUST generate junction table names for many-to-many relationships that match ETL.

#### Scenario: Bidirectional many-to-many junction table
Given a bidirectional many-to-many reference between `User.roles` and `Role.users`
When the ZETA transformation generates a junction table
Then the SQL name must follow the pattern `J_<CLASS1>_<REF1>_<CLASS2>_<REF2>`
And both sides of the relationship must be included in the name

#### Scenario: Unidirectional many-to-many junction table
Given a unidirectional many-to-many reference `ErrorItemRow.groups`
When the ZETA transformation generates a junction table
Then the SQL name must follow the pattern `J_<CONTAINING_CLASS>_<REF>`

### Requirement: ZETA UUIDs MUST Use XMI Resource IDs

The ZETA transformation MUST generate UUIDs using XMI resource IDs, not element names.

#### Scenario: Table UUID generation
Given an entity class with XMI ID `_kKzs0AuCEe6iTZ0mjWdZMg`
When the ZETA transformation generates a table UUID
Then the UUID must be `(asm/_kKzs0AuCEe6iTZ0mjWdZMg)/Table`
And must NOT use the element name

#### Scenario: Field UUID generation
Given an attribute with XMI ID `_abc123`
When the ZETA transformation generates a field UUID
Then the UUID must contain the XMI ID, not the attribute name

### Requirement: ZETA Index Names MUST Match ETL

The ZETA transformation MUST generate index names using MD5 hash of the element UUID.

#### Scenario: Index name generation
Given an attribute with UUID `(asm/_abc123)/Index`
When the ZETA transformation generates an index
Then the SQL name must be `IDX_<MD5 of UUID>`
And since UUIDs will match ETL, index names will automatically match

### Requirement: ZETA Field Sizes MUST Match ETL

The ZETA transformation MUST properly set field sizes from type mapping annotations.

#### Scenario: String field size from annotation
Given a String attribute with `constraints:maxLength=255` annotation
When the ZETA transformation generates a value field
Then the field size must be 255
And must NOT default to 0

#### Scenario: Numeric field precision and scale
Given a Decimal attribute with precision and scale annotations
When the ZETA transformation generates a value field
Then precision and scale must be set from annotations

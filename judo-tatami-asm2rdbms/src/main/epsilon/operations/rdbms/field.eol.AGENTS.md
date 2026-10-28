# `field.eol`

Fills an `RDBMS!RdbmsField` from a `RDBMS!TypeMapping` looked up by `asmType`. Exports `RdbmsField.fillType(type : String)`, `RdbmsField.fillType(type : String, attribute : ASM!EAttribute)`, `RdbmsField.fromType(type : RDBMS!RdbmsField)`, `String.parseAnnotationName()`, `String.parseAnnotationDetailsKey()`.
Both `fillType` overloads throw `"Cannot resolve typemapping: <type>"` when no mapping matches, so the RDBMS model must carry a `TypeMapping` for every ASM type in use.
A `rdbmsSize`/`rdbmsPrecision`/`rdbmsScale` value starting with `#` is an indirection of form `#<annotation>:<key>` resolved off the attribute's extension annotation — the two-arg overload reads the real value, the one-arg overload cannot and falls back to size 255 for `java.lang.String` only, leaving other `#`-typed fields unsized.
`storageByte` is always forced to `-1`. `parseAnnotation*` assume the `#a:b` shape and index out of range otherwise.
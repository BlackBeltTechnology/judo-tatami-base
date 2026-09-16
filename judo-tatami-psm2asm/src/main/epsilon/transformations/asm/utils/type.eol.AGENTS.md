# `type.eol`

Resolves the ASM `EDataType` for a PSM `Primitive`. Exports `@cached asmEquivalent()` dispatching on `isString()`, `isInteger()`, `isDecimal()`, `isBoolean()`, kind-of `EnumerationType`, `isDate()`, `isTimestamp()`, `isTime()`, kind-of `BinaryType`, falling through to `CreateCustomType`.
Dispatch order is load-bearing: `EnumerationType` is checked after the numeric predicates, and the final `else` swallows any unrecognised primitive into `CreateCustomType` rather than failing.
`PasswordType` and `XMLType` have rules in `../modules/type.etl` but no branch here — they land in the `CreateCustomType` fallback.
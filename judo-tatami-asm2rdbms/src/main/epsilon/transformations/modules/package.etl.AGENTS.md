# `package.etl`

Maps the ROOT `ASM!EPackage` (guard `eSuperPackage.isUndefined()`) onto the two RDBMS roots: `rootPackegeToModel` creates `RDBMS!RdbmsModel` with `name` from the package and `version` from program parameter `modelVersion`; `rootPackegeToConfiguration` creates `RDBMS!RdbmsConfiguration` with `dialect` from program parameter `dialect` and attaches it as `model.configuration`.

`class.etl` resolves tables through `s.root().equivalent("rootPackegeToModel")`, so exactly one root package must exist. Rule names misspell "Package".
# `_importUtils.eol`

Single import aggregator for the EOL helper set. Imports `derived.eol`, `operation.eol`, `package.eol`, `transferObject.eol`, `type.eol`, `id.eol`.
Every module in `../modules/` imports this file only — add a new helper file here or rule modules will not see it. Order is not significant; there is no `data.eol`, so entity/attribute/relation rules resolve equivalents by explicit `s.equivalent("<Rule>")` calls instead.
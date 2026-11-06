# AGENTS.md — `QueryModelPersistence.java`

Sidecar of `QueryModelPersistence.java` (pull-only). Save and load of the persisted query model artifact
`<name>-query.model`. See change: persisted-query-model.

## Save — `save(SaveArguments)` → `SaveResult(written, problems, generator)`

- `SaveArguments` (`@Builder saveArguments()`): `queryModel`, `asmModel`, `expressionModel`, `asmDigest`, `measureDigest`
  (null → `none`), `expressionDigest`, and exactly one of `file` / `outputStream`.
- Rewrites only the ASM and expression resource URIs through an `XMLResource.OPTION_URI_HANDLER` (`<name>-asm.model#id`,
  `<name>-expression.model#id`, `name = queryModel.getName()`). Live resources are never touched. Ecore hrefs stay.
- Reference guard (`guard(...)`, package-visible): every non-containment target (cross references of the query resource, incl.
  `QueryModelInfo`) must be an ASM object with a non-null `xmi:id` occurring exactly once (counted over the whole ASM resource,
  not the in-memory id map), an expression object with a non-null, once-occurring id, an Ecore object (root `EPackage`
  registered / `EcorePackage`) or an object of the query resource. Proxies, objects outside a resource and foreign resources
  are problems (max 20, the foreign resource URI is named). The default query save option discards dangling hrefs silently,
  so the guard is what keeps a broken artifact from being written.
- Any problem (or an unreadable generator identity, or `modelName` ≠ name) → nothing written, a `file` target is deleted
  (stale), WARN logged, `written=false`. Never throws for this.
- Stamps on the single `QueryModelInfo` root: `generator`, `asmDigest`, `measureDigest`, `expressionDigest`
  (`formatVersion`, `modelName` come from the build).
- File target: written to `<file>.tmp` next to the target, then moved (atomic where supported); parent dirs created.

## Load — `load(name, stream, asmResource, expressionResource)` → `LoadResult`

- Dedicated `ResourceSet` (`LiveResourceSet`): `uriResourceMap` `{<name>-asm.model → live ASM, <name>-expression.model → live
  expression}`, served without demand loading (a built-in-memory resource is not "loaded"; live resource sets are not modified).
- Stream is read under the logical URI `<name>-query.model`; the factory is `QueryModelResourceSupport`'s with an `XMLHelperImpl`
  whose `createFromString` decodes `EJavaObject` through `ConstantDecoder` (D7).
- Fails (`IOException`) on unreadable / not well-formed XML, resource errors, or a rejected constant ("constant value
  rejected: filter status: REJECTED"). `IllegalStateException` for 0 or 2+ `QueryModelInfo` roots (`QueryIndex.getQueryModelInfo`).
- `EcoreUtil.resolveAll` once, then `UnresolvedProxyCrossReferencer`: `LoadResult.getUnresolvedProxyCount()` / `getUnresolvedProxySamples()`
  (first 10 URIs). The caller treats `> 0` as unusable. Load does not run EMF validation.

## Stamps and identity

- `checkStamps(info, modelName, asmDigest, measureDigest, expressionDigest)` → list of mismatches (empty = match). An unset
  stamp (optional in the metamodel) is a mismatch, never a match; `formatVersion` unset reads as 0.
- `generatorIdentity()` → `judo-tatami-asm2query:<version>` (+ `:<buildTimestamp>` for a `-SNAPSHOT`) read only from this
  bundle's `asm2query-generator.properties`; `null` when missing / unreadable / unfiltered placeholder.

## Digests

- `DigestInputStream` wraps a model stream; `digest()` drains the rest to EOF first (parser read-ahead cannot skip bytes) and
  returns SHA-256 hex. `sha256Hex(InputStream|byte[])`, `newDigest()` (for `DigestOutputStream` savers), `toHex`.

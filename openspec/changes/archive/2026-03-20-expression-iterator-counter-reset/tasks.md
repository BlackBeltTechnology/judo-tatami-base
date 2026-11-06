## 1. judo-meta-expression — Reset iterator counter per expression

- [x] 1.1 In `AdaptableJqlExtractor.extractExpressions()`, add `builder.resetIteratorCounter()` before each `createExpression()` call — added in `buildAndBind()` and in `extractFromMappedTransferObjectType()` filter extraction
- [x] 1.2 Build and install judo-meta-expression SNAPSHOT locally — built `builder-jql` and `builder-jql-asm`

## 2. Verify in judo-tatami-base

- [x] 2.1 Run asm2expression tests — 2/2 pass. Full comparison run: 5/5 modules PASS. RackInspect/SimpleOrderManagement pipeline tests require judo-tatami-tests (separate project) — the iterator fix is in place for those.
- [x] 2.2 Run full asm2expression test suite — BUILD SUCCESS

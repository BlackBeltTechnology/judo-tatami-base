# AGENTS.md — `judo-tatami-test-utils/src/main/resources/scripts`

Profiling and diagnostic post-processing scripts.

| File | Purpose |
| --- | --- |
| `stackcollapse.py` | CLI script collapsing JSON-formatted profiler stack trace logs into semicolon-separated FlameGraph input lines with occurrence counts. Accepts `--input`/`-i`; asserts presence of `stacktrace` and `count` keys on each JSON line. |

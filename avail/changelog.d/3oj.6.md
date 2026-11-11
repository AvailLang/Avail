### Documentation

- The variable elision design doc (`doc/Optimization/Level_Two/Variable_elision.md`) describes the optimizer's actual mechanism: placeholders, the marker instructions, the postponement rules, and when elided variables are created (avail-3oj.6)
- Removed `doc/Optimization/Configuration_Cache_Fixes_Needed.md`, a plan whose fixes have all landed (avail-3oj.5)

### Bug fixes

- An elided local variable whose value was held unboxed beyond 32 bits lost its high bits when its frame's continuation became immutable or shared (avail-3oj.6)

### Beads closed

- `avail-3oj.5`
- `avail-3oj.6`
- `avail-3oj.6.1`
- `avail-miz`

### Beads opened

- `avail-3oj.6.1`
- `avail-4y0`
- `avail-9dm`
- `avail-a0o`
- `avail-jfk`
- `avail-kb5`

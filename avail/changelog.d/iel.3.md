### Standard library

- `Restart_`, which restarted a label with its original arguments, is removed; restart a label with `Restart_with_`, passing the arguments explicitly (avail-iel.3)
- Every loop in the standard library, its tests, and the examples that used `Restart_` now passes its own parameters to `Restart_with_`; a generator's starting function now takes one ignored argument, so either kind of execution state can be restarted the same way (avail-iel.3)

### VM and optimizer

- A label no longer captures its function's arguments: its argument slots hold nil, the compiler no longer keeps arguments alive for a later label, and the L2 label instruction no longer reads them (avail-iel.3)

### Internal architecture

- `P_RestartContinuation` and `L2_RESTART_CONTINUATION` are removed (avail-iel.3)
- The regenerated bootstrap primitive names drop stranded keys, including a malformed `*` entry and a truncated `P_TupleAppend` comment (avail-iel.3)

### Beads closed

- `avail-iel.3`

### Beads opened

- `avail-zv7.5`
- `avail-iel`
- `avail-iel.1`
- `avail-iel.2`
- `avail-iel.3`

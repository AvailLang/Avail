### Documentation

- The value manifest design doc, renamed from `Manifest_ValueClass_Redesign.md` to `doc/Optimization/Level_Two/Value_manifest.md`, now describes the manifest as it is: its data model, merging and congruence, narrowing, postponed instructions, control flow merges, the equivalence search, and its consistency checks (avail-3oj.1)

### Internal architecture

- The value manifest merges classes through one routine, so every merge reconciles each register kind's representation the same way, keeps the impossible-restriction count, merges congruent invocations, and propagates a merged restriction that is narrower than its inputs (avail-d33)
- Congruence uses the manifest's index of each value's users instead of scanning every primitive invocation in the manifest (avail-d33)

### Bug fixes

- Merging two congruent primitive invocations in the optimizer kept only one of their states, losing the other's members, registers, and restriction (avail-d33)
- The optimizer forgot which primitive invocations read a value whenever that value was narrowed or given a register, so those invocations were no longer narrowed with it or merged when it merged (avail-d33)
- Agglomerating values whose restrictions are disjoint did not mark the position as unreachable (avail-d33)

### Beads closed

- `avail-3oj.1`
- `avail-d33`

### Beads opened

- `avail-3pt`
- `avail-puj`

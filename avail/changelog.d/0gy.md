### VM and optimizer

- A branch whose paths all lead to the same place is now replaced by a jump even when the paths pass through blocks holding only a jump, so the work that computed the test disappears too; the final edge adjustment alternates with dead code removal until nothing changes (avail-0gy)
- Dead code removal after phi moves edits the graph in place instead of regenerating it (avail-0gy)
- Reading the current function no longer counts as a side effect, so it is postponed until needed and removed when unused (avail-0gy)

### Documentation

- Comments throughout the VM, optimizer, and library that still described a label as a continuation (with a program counter, frame slots, or a resume point) now describe labels as they are (avail-0gy)

### Internal architecture

- A primitive now states what it guarantees about its result as a type restriction rather than a type (`restrictionGuaranteedByVM` and `guaranteedRestriction`, formerly `returnTypeGuaranteedByVM` and `guaranteedType`), so a guarantee can say things a type cannot, such as a range with a gap or whether the result can be ⊥ (avail-0gy)
- The context parameter that keeps a primitive's own guarantee callable only through `guaranteedRestriction` is renamed from `GuaranteeContext` to `Protected` (its sole instance `ProtectedInstance`), after the Java access level it emulates (avail-0gy)

### Bug fixes

- Optimized code could fail to compile to JVM bytecode when code splitting gave one reification several save points and an unassigned local's creation was still postponed there; the freeze space in the register dump now sits after the most registers any of those save points saves (avail-0gy)
- Optimized code could fail JVM verification when an instruction left dead by a late branch removal read a register that a reification did not save; liveness for saving registers and for register coloring now counts every instruction's reads (avail-0gy)
- Optimized code could contain unreachable-code markers on paths where a type was ⊥, because the tag of a value whose type is a metatype was assumed never to be ⊥'s tag; that assumption then excluded ⊥ from the value itself at a merge (avail-0gy)

### Beads closed

- `avail-0gy`

### Beads opened

- `avail-apt`

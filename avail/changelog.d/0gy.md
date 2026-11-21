### VM and optimizer

- A branch whose paths all lead to the same place is now replaced by a jump even when the paths pass through blocks holding only a jump, so the work that computed the test disappears too; the final edge adjustment alternates with dead code removal until nothing changes (avail-0gy)
- Dead code removal after phi moves edits the graph in place instead of regenerating it (avail-0gy)
- Reading the current function no longer counts as a side effect, so it is postponed until needed and removed when unused (avail-0gy)

### Documentation

- Comments throughout the VM, optimizer, and library that still described a label as a continuation (with a program counter, frame slots, or a resume point) now describe labels as they are (avail-0gy)

### Bug fixes

- Optimized code could fail to compile to JVM bytecode when code splitting gave one reification several save points and an unassigned local's creation was still postponed there; the freeze space in the register dump now sits after the most registers any of those save points saves (avail-0gy)
- Optimized code could fail JVM verification when an instruction left dead by a late branch removal read a register that a reification did not save; liveness for saving registers and for register coloring now counts every instruction's reads (avail-0gy)

### Documentation

- Comments throughout the VM, optimizer, and library that still described a label as a continuation (with a program counter, frame slots, or a resume point) now describe labels as they are (avail-0gy)

### Bug fixes

- Optimized code could fail to compile to JVM bytecode when code splitting gave one reification several save points and an unassigned local's creation was still postponed there; the freeze space in the register dump now sits after the most registers any of those save points saves (avail-0gy)

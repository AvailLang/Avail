### VM and optimizer

- A label is now its own kind of object, holding only its invocation's caller and function, rather than a continuation with a program counter, stack, and Level Two state (avail-iel.4)
- Restarting a label makes its caller current again and invokes its function afresh with the new arguments, entering it like any other call, interrupt check included (avail-iel.4)
- The optimizer builds a label with one instruction after reifying the caller, no longer saving registers or recording a Level Two resume point for it (avail-iel.4)

### Internal architecture

- `LabelDescriptor` and the `A_Label` protocol, the `L2_CREATE_LABEL` operation, and a `LABEL` serializer operation; the unused restart-with-original-arguments reifier is removed (avail-iel.4)

### Beads closed

- `avail-iel.1`
- `avail-iel.4`

### Beads opened

- `avail-iel.4`
- `avail-iel.5`

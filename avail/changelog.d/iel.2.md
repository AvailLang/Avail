### Standard library

- The primitive method `$_@pc=_stack=_[_]caller=_`, which built a continuation from a function, program counter, stack, and caller, is gone, with its error codes `incorrect-continuation-stack-size` (61) and `cannot-create-continuation-for-infallible-primitive-function` (76); nothing used it since labels split from continuations (avail-iel.2)

### Anvil

- The debugger's variables list shows a label as `label for` its method's name, rather than only its kind (avail-iel.2)

### Internal architecture

- `P_CreateContinuation` and the error codes `E_INCORRECT_CONTINUATION_STACK_SIZE` and `E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION` are retired, leaving their numbers unassigned (avail-iel.2)

### Beads closed

- `avail-iel.2`

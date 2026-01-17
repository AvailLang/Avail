This pattern occurs a lot in the Avail L2 code:
```
    r1 = L2_CLOSE(code, outer1, outer2, ...)
    L2_INVOKE(r1, arg1, ...)
```

In this situation, we can avoid creating the closure by rewriting it to use a new proposed instruction:

```
L2_INVOKE_WITH_OUTERS(code, [outer1, outer2, ...], [arg1, arg2...])
```

We can alter the calling convention for this.  The interpreter currently has an argsBuffer list to hold just the current call's arguments, but we can extend that to include both outers and arguments, and *not* empty it during calls.  When you do a call, you'd push the outers then the arguments, say in reverse order to make indexing easier.  On exit, the callee clears that number of entries (outers + arguments) from the stack.  On reification, the callee also clears those entries, since the code had to move those values into a continuation anyhow.

So `L2_INVOKE_WITH_OUTERS` would push the outers in reverse, then the arguments in reverse.  The Nth argument would be argStack[argStack.size - N], and an A-argument function would have its Oth outer at argStack[argStack.size - A - O].  Since the call stack cannot exceed something like 50 levels (at which point reification is forced), the argStack will likewise not grow too big.  And we can verify that it's empty after clearing the JVM stack.  When we move to a Rust / native implementation of the VM, we'll just allocate 50xN slots in raw memory, and do heap-allocated stuff for the very rare functions that take more than N arguments+outers.  No bounds checks needed.  And we could allocate downward to make the indexing obvious.

A regular `L2_INVOKE` (say, of a function that is passed in rather than closed within the current graph) would simply extract the function's outers into the stack.  The callee still cleans them up.  That cost is proportional to the number of outers, but a function that closes a lot of outers is probably accessing most of them anyhow, so the extra cost won't be noticed.  And in theory we can optimize the pushes and pops for cases where the same function is invoked multiple times (e.g., for iteration) if there was no other stack access between the calls.  Making it be caller-pops instead of callee-pops would make that simpler, and allow combining pops, or leaving values pushed on the stack that will be needed later.  The callee doesn't care what's below the args+outers that are visible to it.

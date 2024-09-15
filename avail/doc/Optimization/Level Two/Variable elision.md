# Variable Elision

Continuations can have nil in variable slots to indicate they haven't been populated yet.  L2 registers that would hold those same slots can contain nil (implicitly) to indicate the same condition.  When reifying a continuation from registers, those nils can usually be conserved.  However, the moment a continuation *becomes immutable or shared*,
1. those variables have to be created and written to the slots, and
2. the continuation has to be downgraded to another path to handle potentially escaped variables.

Simply switching the continuation's chunk to the default L1 executor is probably fine for this, to avoid generating extra code for this presumably cold occurrence.

If a continuation hasn't been constructed yet, or it has but it was still mutable upon resumption, restore the registers and continue with the assumption of non-escape for all variables that were previously non-escaped.  This situation does not create any new variables.

If a continuation has been created and then becomes immutable, variables are immediately created for it, and its resumption chunk falls back to L1, which is the same as it would be if a method it depends on changed membership.  Thus, L1 execution only ever sees actual variables.

If a variable is captured, say to be an outer of a function or an argument to some (non-inlined) function, the variable has to be created, and it's considered "escaped but not shared" at that moment.  When the function returns, all created local variables (not just any created for a particular call) have to be checked, or at least any that are still reachable by this chunk.  If they have become shared, then writes to them may have to trigger write traps, which occupies a considerable portion of the current CFGs, and also inhibits some optimizations.  So if any reachable local variables are now shared, we should construct the rest of the variables and fall back to L1.

Another case is that the called function triggers reification.  When we get our turn to reify the current frame, we can likewise check for any shared variables, and in that event create an L1 frame instead, creating any remaining variables.

Even when some non-shared but possibly immutable local variables have been created in the current frame registers, L2 can still track the most recent values that would have been written to those variables, postponing writes that won't be observed (because write traps are not possible).


## Transitions

### Interrupt at start of method
Use nil in local variable slots, and on resumption know that those slots are still nil, or it would have been marked immutable and returned into L1.

### Continuation --> immutable/shared
Fill in any missing variables and switch its chunk for the L1 chunk.

### Reification in call
Capture the current mix of nils and variables in the new continuation.  Upon return, know that the same mix is present, and none of the present variables were observed, or it would have been marked immutable and returned into L1.

### Capturing or passing local variable for function call
Always create the variable.  After the call (direct return or resuming a reified continuation), check if *any* of the existing local variables have become shared, transitioning to L1 if any were.

### Read/write local variable
Leverage the fact that the variable is definitely not shared, so it cannot cause and read traps or write traps.

### Passing local variable to an inlined primitive invocation
Some primitives can make their arguments (or components of their arguments) shared.  If the primitive might do this, an explicit check for shared variables should be performed after the call, falling back to L1 if any are detected.  Returning a local variable doesn't have to do these checks, since that can't cause it to be shared.  It's still up to the caller to check if any of its variables has become shared.

### Primitive variable readers/writers
Some primitives exist just to get/set/clear/atomically-update a variable that was provided from elsewhere.  None of those primitives, to my knowledge, change the mutability of the passed variables.  Hm, in theory one could have a variable V, immutable, in an immutable tuple like <V>.  If an inlined primitive causes that tuple to be written into a shared variable SV, the tuple would become shared, as would V.  So V would have escaped.  I can't think of a corresponding example that does a read.  Maybe the variable-write primitives just shouldn't be inlined, allowing the normal generated post-return code of a call to just check all the locals for sharedness (and falling back to L1).

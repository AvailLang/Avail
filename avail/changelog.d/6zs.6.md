### VM and optimizer

- A closure is now named in the optimizer by its closing, a new private primitive `P_CloseFunction` taking the raw function and the outers' values, so its raw function and captured values are known through moves, postponement, and code splitting, and a closure that is never needed whole is never made (avail-6zs.6)
- A call of a function that is not known exactly asks code splitting to keep apart paths that close different code, so each copy of the call knows the code it runs (avail-6zs.6)
- A chunk embeds callees at most twenty deep, and stops embedding once it reaches a thousand blocks, finishing the rest with ordinary calls (avail-6zs.6)

### Documentation

- The value manifest design doc describes how a closure is named by its closing (avail-6zs.6)

### Internal architecture

- `L2_CREATE_FUNCTION`, `L2Instruction.getConstantCode`, and the per-instruction `extractFunctionOuter` are gone; `L2GeneratorInterface.determineRawFunction` and `extractFunctionOuter` read the closing from the manifest through `P_CloseFunction.closingIn` and `rawFunctionIn` (avail-6zs.6)
- `KnownExactly.RAW_FUNCTION` and the split conditions it produces, which specialize at a split point to the raw function an edge knows; `knownRawFunctionConditions` builds them (avail-6zs.6)
- `Primitive.describeL2Invocation` lets a primitive say how an `L2_RUN_INFALLIBLE_PRIMITIVE` of it is shown; a closing shows its block decompiled (avail-6zs.6)
- `L2JVMChunk.embeddingDepth` records how deeply a chunk embeds callees (avail-6zs.6)

### Beads closed

- `avail-6zs.6`

### Beads opened

- `avail-tjf`

### Internal architecture

- `L2CallSite` is the interface through which a primitive generates code in place of a call of its function: it delivers the call's answer, invokes another function in its place, or restarts the calling frame.  `Primitive.tryToGenerateSpecialPrimitiveInvocation` and its overrides now generate on any `L2GeneratorInterface` against an `L2CallSite`, instead of on the level one translator against its `CallSiteHelper` (avail-tjf.2)
- When reading a global variable or clearing a variable fails, the generated code invokes the primitive's function, which fails into its own fallback code, rather than resuming level one execution at the call (avail-tjf.2)
- `L2_INVOKE` and `L2_INVOKE_CONSTANT_FUNCTION` optionally write the function that returned, read from the interpreter right after a normal return, so the code after a call no longer reads interpreter state that an answer produced in place would leave stale (avail-tjf.2)

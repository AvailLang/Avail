### VM and optimizer

- A foldable primitive whose arguments all come from small enumerations other than metatypes (at most 256 combinations) is now guaranteed to answer the enumeration of its results on those combinations, for every foldable primitive rather than only addition, subtraction, multiplication, division, and truncating bit shift; combinations on which the primitive fails are excluded (avail-1ct)
- The six bootstrap lexer bodies, token creation, object field getter creation, and block compilation are no longer foldable, since their results depend on the running fiber's lexer or the loading module (avail-1ct)

### Internal architecture

- `Primitive.guaranteedType` is the one entry point for a primitive's guaranteed result type; `returnTypeGuaranteedByVM` now takes a `GuaranteeContext` context parameter that only `guaranteedType` can supply, so nothing else can bypass the narrowing (avail-1ct)
- Folding a primitive on constant arguments is shared in `Primitive.foldedResultOrNull`, which restores the interpreter's function, arguments, and latest result afterward (avail-1ct)

### Beads closed

- `avail-1ct`

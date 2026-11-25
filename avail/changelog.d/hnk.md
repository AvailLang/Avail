### Documentation

- The variable elision design doc explains why freezing a continuation of an invalidated chunk is still correct when the chunk embedded code from method definitions that have since been removed (avail-hnk)

### Internal architecture

- `L2_LOOKUP_BY_VALUES` records the chunks whose code was embedded to bring it into its chunk, and `L2JVMChunk` records the optimization level it was translated at, so a chunk can be reoptimized without being run (avail-hnk)
- An `L2JVMChunk` always has its `A_RawFunction`; the nullable code was left over from when level one ran in a chunk built at startup (avail-hnk)

### Bug fixes

- A chunk that embedded a callee no longer reoptimizes into the same code over and over when lookups inside the embedded code miss its dispatch: each miss is also charged to the embedded callee's chunk, which is reoptimized once enough misses accumulate, so the next embedding picks up its new dispatch (avail-hnk)

### Beads closed

- `avail-hnk`

### Beads opened

- `avail-hnk`
- `avail-vkh`

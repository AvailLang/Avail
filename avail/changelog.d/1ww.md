### VM and optimizer

- A register holds every name in its value's synonym, so the optimizer reuses a register for a value that joined the synonym without being written, rather than requiring a write under that exact name (avail-1ww)

### Documentation

- The value manifest design doc describes two degrees of presence, known and live, in place of three (avail-1ww)
- The post-phi data design doc explains why a block's data is applied again where a replacement continues the block in a new one (avail-1ww)

### Internal architecture

- `isPopulated`, `isPopulatedInAnyKind`, `equivalentPopulatedSemanticValue`, and `getAllDefinitions` are replaced by `hasLiveSemanticValue`, `hasLiveSemanticValueInAnyKind`, `equivalentLiveSemanticValue`, and `getDefinitions`; obsolete postponed-move and defined-member checks are removed (avail-1ww)
- `PostPhiData` and `L2ValueManifest` describe themselves one synonym per line, and the regenerator's check for a read of an unknown value says what was read and what was known (avail-1ww)
- Value manifest tests pin that a name joining a live synonym is live, and that the search for a live equivalent needs a register (avail-1ww)
- The deep manifest check asserts that no invocation of a side-effectless primitive is restricted to ⊤, since `any` is the weakest restriction such a value can have (avail-1ww)

### Bug fixes

- Regeneration applies a block's post-phi data again where an instruction's replacement continues the block in a new block, so a fact recorded nowhere else, such as a label stored in a local, is not lost (avail-1ww)
- Adding values to an existing synonym narrows it before binding them, so a primitive invocation never propagates an interim ⊤ to its arguments; `SimpleOptimizerTest.testChainedComparisonWithLabel` pins it (avail-1ww)

### Beads closed

- `avail-1ww`

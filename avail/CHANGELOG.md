# Changelog

All notable changes to Avail live here, newest first: the language, its
standard library, the compiler, the VM and its optimizer, the Anvil IDE, and
the tooling that builds and distributes them. Version numbers are the build's
own (`version` in `avail/build.gradle.kts`), and each released version is
tagged `vX.Y.Z…` in git.

The file lives at `avail/CHANGELOG.md`.

## Conventions

- **Unreleased entries live in fragments, not in this file.** Each changeset
  records its entries in a file of its own, `avail/changelog.d/<bead>.md`,
  named for the bead it principally lands, with the bead prefix dropped. A
  later changeset for the same bead adds to the existing fragment rather than
  starting a second. No two changesets ever edit the same fragment, so the
  changelog cannot conflict on a merge. The `## [Unreleased]` heading below
  stays empty in the source.
- **A fragment is the section template below without its version heading**:
  `### <group>` headings in template order, each over a bullet list, every
  rule here applying to it as to a version section. Groups with no entries are
  omitted, and nothing else appears in a fragment — no prose above the first
  group and no other heading levels.
- **A release assembles the fragments.** The fragments are merged group by
  group, newest-landed first (by the first-parent history of `main`), into a
  new `## [X.Y.Z…] — YYYY-MM-DD` section beneath the empty `[Unreleased]`, and
  are deleted in the release commit.
- Each entry is **one line**: a terse description of the change, then a
  trailing parenthesized reference if useful — `(avail-xxx)` for a bead,
  `(#42)` for a GitHub issue. Two references are fine when warranted; more
  than two usually means the entry should split.
- **No blank lines between the bullets of a list.** One blank line follows
  each `###` heading and one follows the list; none falls between two entries.
  A blank line there makes CommonMark render the list *loose*, typesetting
  adjacent entries differently for no reason.
- **Pick the dominant group.** A change that spans two groups goes under the
  one it mostly concerns. Refactors, renames, dependency changes, and
  abandoned approaches go under **Internal architecture**; pure build plumbing
  that changes no behavior is omitted unless a future contributor would need
  to know it.
- The **Beads closed**, **Beads opened**, and **Issues closed** groups list
  IDs even when the relevant work is also covered inline above — they are a
  quick at-a-glance dimension for tracking the work's pace.
- **A Beads group is one bare ID per bullet, and carries no prose.** Write
  ``- `avail-xxx` `` and nothing else: no gloss, no priority, no summary of
  what the bead is about. `bd show avail-xxx` answers all of that and stays
  current where a copy here would not. The shape is also what lets the
  fragments' bead lists be concatenated into one.
- Entries are written for a reader outside the project. Technical specificity
  is welcome; jargon should be light enough that a curious newcomer can
  follow.

### Section template

The shape every version section and every fragment follows. (The `Unreleased`
placeholder is shown as `UNRELEASED` here so nothing that finds the live
heading can match this template instead; the live heading below uses the
normal `[Unreleased]` form.)

```
## [UNRELEASED]

### Language
### Standard library
### Compiler
### VM and optimizer
### Anvil
### Build, distribution, and tooling
### Documentation
### Internal architecture
### Bug fixes
### Beads closed
### Beads opened
### Issues closed
```

## [Unreleased]

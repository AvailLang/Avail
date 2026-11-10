### Standard library

- `Synchronize_with file system` and `Synchronize_` on a file writer issue the operating system's ordinary per-file synchronization (`fsync`) rather than, on macOS, a flush of the entire drive's write cache; data reaches the drive but, on macOS, can still be lost if power fails (avail-yjt)

### VM and optimizer

- A file opened for writing holds a second descriptor used only to synchronize it, since the JVM's file channels always flush the whole drive on macOS; a `delete on close` file opened for writing is deleted by Avail after both descriptors are open, or at close where the system refuses to delete an open file (avail-yjt)

### Beads closed

- `avail-yjt`

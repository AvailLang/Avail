Avail Storage
===============================================================================

*TODO THIS IS A WORK IN PROGRESS*

This module provides utility data storage used by Avail that is generally useful
in general application development.

LRU Cache
===============================================================================
The `LRUCache` implements a memory-sensitive least-recently-used cache. All
public operations support concurrent access. It avoids redundant simultaneous
computation of values by racing threads that present the same keys.

## API
LRU is a generic class that is parameterized by a lookup key for a cached value
and the type of the values being cached. Values are not directly added to the 
cache; instead they are calculated via a function, `transformer` that accepts a 
key and produces the associated value. If the value is present in the cache, it 
is simply returned when asked for. If it is not in the cache, it is calculated
by the `transformer` function and added to the cache before being provided to 
the caller. 

```kotlin
class LRUCache<K, V>
```

### Construction
`LRUCache` is constructed using a public constructor that accepts the following 
arguments:
 1. `softCapacity` (`Int`) - The capacity of the cache; the maximum number of 
    cached values that will ever be retained simultaneously. *Must be 
    greater than zero*
 2. `strongCapactiy` (`Int`) - The maximum number of cached values that will be 
    strongly retained to prevent garbage collection. 
 3. `transformer` (`(K) -> V`) - The function responsible for producing new
    values from user supplied keys.
 4. `retirementAction` (`((K, V) -> Unit)?`) - The nullable action responsible
    for retiring a binding expired from the `LRUCache`.

### Functions and Public State
 * `clear()` - Completely clears the caching forcing a run of the retirement 
   action, if present, before removing them.
 * `size` - The number of values currently in the cache.
 * `get(K): V` - A blocking operation that answer's the value associated with 
   the specified key, computing the value from user-supplied `transformer` 
   if the value is not already present in the cache. **NOTE** This function 
   is not reentrant; the `transformer` must not reenter any public operation 
   while computing a value for a specified key.
 * `poll(K): V?` - Immediately answers the value already associated with the
   specified key. This does not execute the user-supplied `transformer`, only 
   answers an already cached value or `null` if
   1. the cached value associated with the key is actually `null` or
   2. no value has been cached for the specified key
 * `remove(K): V?` - Removes the specified key and the value associated with it
   from the cache. If the key is present and the soft reference corresponding to
   the value has not been reclaimed by the garbage collector, then perform the
   `retirementAction` if any.

Indexed File
===============================================================================
`IndexedFile` is an indexable record journal that stores records in the order 
they were added. Records may be 
 * added - writes bytes to the in memory index file
 * committed - writes the data to the underlying file
 * looked up by record index number 
 * add over-writeable metadata to the file
 
Concurrent read access is supported for multiple threads, drivers, and external
processes. Only one writer is permitted.

An `IndexFile` is backed by an actual file.

The exclusive lock on the last indexable byte (2^63-1) of the file is acquired 
automatically when performing an add, a commit, or when modifying the metadata, 
if it isn't already owned. If it wasn't owned, a refresh always takes place, 
ensuring the write is relative to the latest consistent state of the file, 
which the lock secures against change by other processes.  At the end of a 
commit, the lock is always released, which allows other blocked processes to 
mutate the file, having automatically refreshed their own content as above.

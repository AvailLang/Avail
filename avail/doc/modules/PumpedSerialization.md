# Pumped Serialization with Avail Modules (2020.10.20)

Let's consider the fast-loader.  When a module is about to be loaded, all of its
ancestor modules must already be memory-resident.  Each module holds (at least)
the tuple of objects that were constructed when it was compiled or fast-loaded.
This is a *stable* tuple for a module, other than object identities (which
should be stably *isomorphic* after loading).  These tuples can be concatenated
in a canonical order (say depth-first, visiting each predecessor in alphabetical
order).  A tree-tuple can make this very efficient.  Fast-loading can then
proceed with this pumped tuple of previously constructed objects.  Any
particular index into the tuple produces "the same" value each time it loads. In
this way, objects that were constructed in previous modules can be reused by
their index, instead of having to be reconstructed or looked up in some other
way. The deserialization of the module then leads to another tuple of objects
that subsequent modules can use in the same way.

Compilation is already considerably slower than fast-loading, so a goal is not
to slow it down too much.  In order to be able to have the deserializer directly
reuse objects from previous modules, we need to be able to find those objects
quickly during serialization.  We can turn to a bloom filter, a memory-efficient
mechanism for approximating set membership.  Each module has a lazily computed
slot for holding a bloom filter of all the objects that were created for it,
either by fast-loading (deserializing) or by compilation (serializing).  Modules
roll up their predecessors' bloom filters by OR-ing them together, prior to
adding the new objects' hash values.  Note that the hash values are not stable
across deserialization, so the bloom filter must be constructed from the
in-memory objects.  However, the module already needs to hold those in a tuple,
so they're easily enumerated to construct the filter.

Now when a Serializer attempts to serialize object X, it first looks for a newly
created occurrence of X in the map of SerializerInstructions.  If absent, it
fetches the OR-ed bloom filter of the predecessors (recursively computing and
caching them as needed, which is not at all if only the fast-loader is ever
used).  If the bloom filter indicates the object definitively does not occur in
any ancestor module, a new SerializerInstruction is added for it, and its
content will (eventually) be traced and written out.  If the bloom filter
indicates a possible hit, it recurses through chains of modules for which the
filter is a hit, until it reaches a module whose predecessors are all misses. 
It then checks the reverseMap field of the module, and if it's nil it creates
(and caches) a map containing (object → index) for each element of the module's
tuple of objects.  If the object is present in that map, it's a true hit, and
the corresponding index plus the starting offset for that module in the current
canonical ordering is used.  Otherwise, it was a false hit, and the search
continues back up the chain, checking any modules that may have introduced an
actual hit.  If no module actually defined an equal object, it's a true miss,
and a new SerializerInstruction is added.  Note that multiple true hits are
possible, due to sibling modules each introducing the same value.  It doesn't
matter which one we use, but for cleanliness we can visit predecessor modules in
canonical order to ensure we get the same one each time.  When the serialization
is complete, the current module's bloom filter can be updated by adding in each
element of the module's tuple of objects.

To recap, there's a minimal additional memory hit when only the fast-loader is
used: each module holds a tuple of objects that were deserialized for it, most
of which would have stuck around in memory anyhow.  So let's call that
significantly less than 8 bytes per object.

If the compiler gets used, bloom filters have to be calculated for all ancestor
modules (parallel calculation should be fine), and any module that has a bloom
filter hit (either a true or false hit) will also have a reverseMap created for
it (and cached).  Subsequent compilations will be able to reuse these
structures.  The bloom filter will have fixed size per module, maybe 4 KB with
three bits set per element?  The map cost is a minimum of two pointers and
cached hash per element, which comes out to 20 bytes per object.  However, only
indices from 0-255 can currently reuse small IntegerDescriptor instances, so the
integer itself will eat a full AvailObject (24?), plus the longArray (32?) for
~56 additional bytes per element.  Plus the amortized cost of bins.  Hopefully
the memory we save by being able to reuse objects will outweigh this additional
tracking cost.  Also, this is part of the infrastructure for separating block
phrases from the loaded method bodies, which should lead to huge memory savings.
It should also lead to faster fast-loading, as fewer objects need to be
constructed.

## Future improvements

A cuckoo filter doesn't look like it would improve the situation significantly. 
The key problem with both a Bloom filter and a cuckoo filter will most likely be
that a low rate of false positives will eventually cause almost all modules to
have to be searched, even if every search is fruitless.  To balance against that
problem, it might end up being beneficial to have an intermediate state where
the tuple of objects is first converted to a set, to check the false positives
for membership.  This is less space than the map, but takes a similar time to
construct, so it would only make sense if the false positive rate ends up being
too high.

A more direct alternative would be to eliminate the cost of the integers in the
map.  Lazily populating a table of 2^16 slots (or fewer) might work to reduce
the maps' memory footprints.  It's unclear whether volatile access would be
necessary for ensuring a thread would never observe an uninitialized long slot
within an integer object found in this table.  Volatile access to the table
should be *sufficient*, however.

It might also be worthwhile eventually to create variants of map bins that only
happen to contain Int (32-bit) values.

Another alternative would be to create an array of Longs, where the upper half
is a hash value and the lower half is the index into the tuple of objects.  Then
a binary search for hash(x)<<32 would find the position of the first element
with a matching hash, if any, and successive elements would hold the indices of
colliding values.  The Longs could be sorted easily, without worrying about
stability (the lower halves would all be unique).  Since the upper values are
essentially random hashes, a biased search would probably be even faster than a
pure binary search.  Combined with the bloom filter, this might yield the best
actual performance and smallest footprint.


## NEW PLAN: (2026-06-22)

Never serialize the object map, only the tuple of values.  During fast-loading, concatenate the object tuples from the immediate predecessor modules – alphabetical module order, say in the order they're imported.

When a module needs to be compiled, the predecessor modules' tuples of values (produced either by compilation or fast-loading) still need to be present and concatenated.  But for a compilation's serialization to work, the map from objects to indices (in the concatenated tuples) has to be present in memory.  We can use an ordinary Java/Kotlin Map, since it doesn't *have* to stick around after compilation.

In the event that we have to keep recompiling a module (the development loop), we can cache just the pumped map, which is easily reconstructed if needed by inverting the concatenated tuples, producing a map from objects to indices.

For the case where we're compiling a large number of modules, we'll want to cache the map for a module until all *successor* modules have been compiled.

To make the map assembly quicker, the keys can be a structure that combines the serialized object itself and its hash – used for faster hashing in the map, and as a pre-test for equality.  The corresponding values can be a structure containing a reference to the module and the index into its tuple of objects.

## Refinement

One more thing.  Rather than build an entire map from object to index into the concatenated tuple, we could create a much more compact map from each objeect's _hash value_ into the tuple,  Collisions can be tracked in a separate, smaller map.  Note that equal objects appearing in peer modules within the concatenated tuple don't count as a collision.  Maybe use positive `Int`s to indicate a tuple subscript (without a collision), and a negative integer to index a `List` of `IntArray`s that list the (positive) subscripts with candidate objects, perhaps interspersed with the corresponding full hash values, first the hash then the (positive) subscript.  If the IntArray is larger than needed, to amortize growth, the last entry, which would be a positive subscript if the IntArray is full, could instead contain a negative number indicating the current occupied size of the array.

### Incremental growth of map

Every module (version) in a repository can be assigned a unique subrange of the eventual concatenated tuple.  Compiling a module causes the module's object tuple to be created, at which point a range can be allocated for it within a repository-wide tuple subscript listing – stored in the repo's metadata.  HOWEVER, since the available subscripts are reasonably plentiful, and since we can build tree-tuples that contain repeat-tuples (of nil, say) for modules that have space reserved but haven't been loaded yet, the repo's metadata can just bump a counter.   Or we could use a two-tiered index where a high Int specifies a tuple number and a low Int indexes it, and we use a Long as the value stored in the map.  ^^^TODO^^^ Check if we can use the record number for the high int, and how hard it would be to extend Deserializer support to use a Long for object references – it might already be doing that. 

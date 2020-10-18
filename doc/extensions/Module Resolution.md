Module Resolution
--------------------------------------------------------------------------------

Avail has a unique source-linking system. Avail source exists in `ModuleRoot`s. 
A `ModuleRoot` is effectively a directory that contains Avail source packages
that contain Avail source modules and resource files (e.g. image file) used by
the Avail program. Additionally, it may contain regular directories (non-packages) 
that strictly contain resource files only. 

Many `ModuleRoot`s can be used in a single `AvailRuntime`. A `ModuleRoot` can be 
added to Avail's runtime by providing Avail with a valid `URI` (_Universal
Resource Identifier_). The implication is that Avail can use `ModuleRoots` 
located anywhere reachable by a URI. Avail uses `ModuleRootResolver`s to
resolve URI locations and link it with Avail's runtime. 

By default, Avail provides a single standard `ModuleRootResolver`:
`FileSystemModuleRootResolver`. It provides access to URI locations that use the
_file://_ URI scheme. Additional resolvers that support other URI schemes may be
added by implementing a `ModuleRootResolver` for the desired URI scheme. In
order to make the custom `ModuleRootResolver` accessible to Avail, it must
have a corresponding implementation of `ModuleRootResolverFactory` which Avail
uses to create `ModuleRootResolver`s. Additionally, the 
`ModuleRootResolverFactory` must be registered with the
`ModuleRootResolverRegistry`:
```
ModuleRootResolverRegistry.register(MyCustomModuleRootResolverFactory)
```

The use of URIs to reach module source necessitates that all implementations of
`ModuleRootResolver` must be handled asynchronously. The response time of
source access will vary greatly between URI scheme types (_e.g. network vs
local file system_). Avail must guarantee orders of certain operations
involving the compilation of source files. The concurrent nature of the
compiler also necessitates asynchronous implementations be thread-safe. 
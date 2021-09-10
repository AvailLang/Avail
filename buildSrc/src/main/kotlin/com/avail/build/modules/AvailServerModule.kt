package com.avail.build.modules

/**
 * The [dependencies][ModuleDependencies] for the `avail-server` module
 * `build.gradle.kts` file's `dependencies` section.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailServerModule: ModuleDependencies(
	apis = listOf(Libraries.jsr305))

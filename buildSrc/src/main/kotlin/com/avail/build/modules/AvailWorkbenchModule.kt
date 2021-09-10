package com.avail.build.modules

/**
 * The [dependencies][ModuleDependencies] for the `avail-workbench` module
 * `build.gradle.kts` file's `dependencies` section.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailWorkbenchModule: ModuleDependencies(
	apis = listOf(Libraries.jsr305),
	implementations = listOf(Libraries.darklafCore))

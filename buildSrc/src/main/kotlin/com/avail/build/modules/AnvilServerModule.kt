package com.avail.build.modules

/**
 * The [dependencies][ModuleDependencies] for the `anvil-workbench` module
 * `build.gradle.kts` file's `dependencies` section.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AnvilServerModule: ModuleDependencies(
	implementations = listOf(Libraries.jsr305),
	testImplementations = listOf(Libraries.junitJupiter))

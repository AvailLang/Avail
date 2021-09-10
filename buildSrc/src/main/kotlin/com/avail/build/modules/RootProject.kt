package com.avail.build.modules

/**
 * `RootDependencies` is the [Dependencies] for root project's
 * `build.gradle.kts` file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object RootProject: ModuleDependencies(
	implementations = listOf(
		Libraries.jsr305,
		Libraries.asm,
		Libraries.asmAnalysis,
		Libraries.asmTree,
		Libraries.asmUtil,
		Libraries.fileWatcher,
		Libraries.tikka),
	apis = listOf(Libraries.kotlinReflection),
	testImplementations = listOf(
		Libraries.junitJupiterParams, Libraries.junitJupiterEngine),
	compileOnlys = listOf(Libraries.kotlinAnnotations))

package com.avail.build.modules

import org.gradle.kotlin.dsl.DependencyHandlerScope

/**
 * `RootDependencies` is the [Dependencies] for root project's
 * `build.gradle.kts` file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object RootProject: ModuleDependencies()
{
	override val apis: List<Dependency> =
		listOf(
			Libraries.jsr305,
			Libraries.asm,
			Libraries.asmAnalysis,
			Libraries.asmTree,
			Libraries.asmUtil,
			Libraries.fileWatcher)
	override val implementations: List<Dependency> =
		listOf(Libraries.tikka, Libraries.kotlinReflection, Libraries.darklafCore)
	override val testImplementations: List<Dependency> =
		listOf(Libraries.junitJupiterParams, Libraries.junitJupiterEngine)
	override val compileOnlys: List<Dependency> =
		listOf(Libraries.kotlinAnnotations)

	override fun addDependencies (scope: DependencyHandlerScope)
	{
		apis.forEach { scope.addApi(it.versioned) }
		implementations.forEach { scope.addImplementation(it.versioned) }
		compileOnlys.forEach { scope.addCompileOnly(it.versioned) }
		testApis.forEach { scope.addTestApi(it.versioned) }
		testImplementations.forEach {
			scope.addTestImplementation(it.versioned)
		}
	}
}

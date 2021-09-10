package com.avail.build.modules

import Versions
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope

/**
 * Represents an individual dependency that can be imported by a
 * `build.gradle.kts` file in the `dependencies` section.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property base
 *   The base string location of the library.
 * @property versionGetter
 *   A lambda that answers the version of the library.
 */
data class Dependency constructor(
	val base: String, val versionGetter: () -> String)
{
	/**
	 * Provide the `base:version` of this [Dependency].
	 */
	val versioned: String get() = "$base:${versionGetter()}"
}

/**
 * Adds a dependency to the 'api' configuration.
 *
 * @param dependency
 *   The [Dependency] string to add.
 *
 * @see [DependencyHandler.add]
 */
fun DependencyHandlerScope.addApi(dependency: String)
{
	add("api", dependency)
}

/**
 * Adds a dependency to the 'implementation' configuration.
 *
 * @param dependency
 *   The [Dependency] string to add.
 *
 * @see [DependencyHandler.add]
 */
fun DependencyHandlerScope.addImplementation(dependency: String)
{
	add("implementation", dependency)
}

/**
 * Adds a dependency to the 'testImplementation' configuration.
 *
 * @param dependency
 *   The [Dependency] string to add.
 *
 * @see [DependencyHandler.add]
 */
fun DependencyHandlerScope.addTestImplementation(dependency: String)
{
	add("testImplementation", dependency)
}

/**
 * Adds a dependency to the 'testApi' configuration.
 *
 * @param dependency
 *   The [Dependency] string to add.
 *
 * @see [DependencyHandler.add]
 */
fun DependencyHandlerScope.addTestApi(dependency: String)
{
	add("testApi", dependency)
}

/**
 * Adds a dependency to the 'compileOnly' configuration.
 *
 * @param dependency
 *   The [Dependency] string to add.
 *
 * @see [DependencyHandler.add]
 */
fun DependencyHandlerScope.addCompileOnly(dependency: String)
{
	add("compileOnly", dependency)
}

/**
 * The central source of all libraries that are used as project dependencies.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal object Libraries
{
	/** JSR-305 null analysis framework. */
	internal val jsr305 =
		Dependency(
			"com.google.code.findbugs:jsr305") { Versions.jsrVersion }
	/** Type renderer annotations, for debugger view.  Doesn't actually work. */
	internal val kotlinAnnotations =
		Dependency("org.jetbrains:annotations") { Versions.kotlinAnnotations }

	/** ObjectWeb JVM code generation/analysis framework (core). */
	internal val asm = Dependency("org.ow2.asm:asm") { Versions.asmVersion }

	/** ObjectWeb JVM code generation/analysis framework (analysis). */
	internal val asmAnalysis =
		Dependency("org.ow2.asm:asm-analysis") { Versions.asmVersion }

	/** ObjectWeb JVM code generation/analysis framework (tree). */
	internal val asmTree =
		Dependency("org.ow2.asm:asm-tree") { Versions.asmVersion }

	/** ObjectWeb JVM code generation/analysis framework (util). */
	internal val asmUtil =
		Dependency("org.ow2.asm:asm-util") { Versions.asmVersion }

	/** Java's file-watcher mechanism is garbage, so use this instead. */
	internal val fileWatcher =
		Dependency(
			"io.methvin:directory-watcher")
			{ Versions.directoryWatcherVersion }

	/** https://tika.apache.org for file MIME type detection. */
	internal val tikka =
		Dependency("org.apache.tika:tika-core") { Versions.tikaVersion }

//	org.junit.jupiter:junit-jupiter:5.7.0
	/** The JUnit 5 Jupiter engine. */
	internal val junitJupiter =
		Dependency(
			"org.junit.jupiter:junit-jupiter")
		{ Versions.junitVersion }

	/** The JUnit 5 Jupiter engine. */
	internal val junitJupiterEngine =
		Dependency(
			"org.junit.jupiter:junit-jupiter-engine")
			{ Versions.junitVersion }

	/** The JUnit 5 Jupiter params. */
	internal val junitJupiterParams =
		Dependency(
			"org.junit.jupiter:junit-jupiter-params")
			{ Versions.junitVersion }

	/** The Kotlin reflection support. */
	internal val kotlinReflection =
		Dependency("org.jetbrains.kotlin:kotlin-reflect") { Versions.kotlin }

	/**
	 * Dark mode look & feel.  Embed the jar's contents in the fat shadow jar.
	 * This was based on the Darcula module.
	 */
	internal val darklafCore =
		Dependency(
			"com.github.weisj:darklaf-core") { Versions.darklafVersion }
}

/**
 * An abstract grouping of [Dependency]s for a single `build.gradle.kts` file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
sealed class Dependencies
{
	/**
	 * The list of [Dependency]s that should be included using
	 * `DependencyHandler.api`.
	 */
	protected abstract val apis: List<Dependency>

	/**
	 * The list of [Dependency]s that should be included using
	 * `DependencyHandler.implementation`.
	 */
	protected abstract val implementations: List<Dependency>

	/**
	 * The list of [Dependency]s that should be included using
	 * `DependencyHandler.testApi`.
	 */
	protected abstract val testApis: List<Dependency>

	/**
	 * The list of [Dependency]s that should be included using
	 * `DependencyHandler.testImplementation`.
	 */
	protected abstract val testImplementations: List<Dependency>

	/**
	 * The list of [Dependency]s that should be included using
	 * `DependencyHandler.compileOnly`.
	 */
	protected abstract val compileOnlys: List<Dependency>

	/**
	 * Add the [Dependency]s to the provided [DependencyHandlerScope].
	 *
	 * @param scope
	 *   The `DependencyHandlerScope` to add the dependencies to.
	 */
	open fun addDependencies (scope: DependencyHandlerScope)
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

/**
 * `ModuleBuildDependencies` is the [Dependencies] for non-root modules'
 * `build.gradle.kts` file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class ModuleDependencies constructor(
	override val apis: List<Dependency> = listOf(),
	override val implementations: List<Dependency> = listOf(),
	override val testApis: List<Dependency> = listOf(),
	override val testImplementations: List<Dependency> = listOf(),
	override val compileOnlys: List<Dependency> = listOf()
): Dependencies()

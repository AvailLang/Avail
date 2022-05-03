/*
 * Libraries.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.build.modules

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

	/**
	 * Dark mode look & feel using Flatlaf.
	 *
	 * @see <a href="https://github.com/JFormDesigner/FlatLaf">Flatlaf</a>
	 */
	internal val flatlaf =
		Dependency(
			"com.formdev:flatlaf-intellij-themes") { Versions.flatlafVersion }

	/**
	 * Flatlaf themes.
	 *
	 * @see <a href="https://www.formdev.com/flatlaf/themes/">Themes</a>
	 */
	internal val flatlafTheme =
		Dependency(
			"com.formdev:flatlaf") { Versions.flatlafVersion }
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
) : Dependencies()

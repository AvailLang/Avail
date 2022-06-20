/*
 * AvailTest.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.test

import avail.AvailRuntime
import avail.builder.AvailBuilder.CompiledCommand
import avail.builder.RenamesFileParserException
import avail.builder.UnresolvedDependencyException
import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.representation.AvailObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Build the Avail standard library and run all Avail test units.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@TestInstance(Lifecycle.PER_CLASS)
class AvailTest
{
	/** Setup for the test. */
	private val helper = AvailRuntimeTestHelper(false)

	/**
	 * Clear all repositories iff the `clearAllRepositories` system
	 * property is defined.
	 *
	 * @throws FileNotFoundException
	 *   If the renames file was specified but not found.
	 * @throws RenamesFileParserException
	 *   If the renames file exists but could not be interpreted correctly for
	 *   any reason.
	 */
	@BeforeAll
	@Throws(FileNotFoundException::class, RenamesFileParserException::class)
	fun maybeClearAllRepositories()
	{
		if (System.getProperty("clearAllRepositories", null) !== null)
		{
			helper.clearAllRepositories()
		}
	}

	/**
	 * Clear any error detected on the [AvailRuntimeTestHelper.TestErrorChannel].
	 */
	@BeforeEach
	fun clearError()
	{
		helper.clearError()
	}

	/**
	 * Shut down the [AvailRuntime] after the tests.
	 */
	@AfterAll
	fun tearDownRuntime()
	{
		helper.tearDownRuntime()
	}

	/**
	 * Check that we can compile or load the standard Avail libraries.
	 *
	 * @param moduleName
	 *   Each module or package name.
	 * @throws UnresolvedDependencyException
	 *   If a module can't be resolved.
	 */
	@DisplayName("Avail standard libraries")
	@ParameterizedTest(name = "{displayName}: {0}")
	@ValueSource(strings = [
		"/avail/Avail",
		"/avail/Convenient ASCII",
		"/avail/Dimensional Analysis",
		"/avail/Hypertext",
		"/avail/Internationalization and Localization",
		"/avail/Availuator",
		"/examples/Examples"])
	@Throws(UnresolvedDependencyException::class)
	fun testLoadStandardLibraries(moduleName: String)
	{
		val loaded = helper.loadModule(moduleName)
		assertTrue(loaded, "Failed to load module: $moduleName")
		assertFalse(helper.errorDetected())
	}

	/**
	 * Check that we can compile or load the *invalid* builder test modules,
	 * each failing for some reason, writing some message to the error channel.
	 *
	 * @param moduleName
	 *   Each module or package name.
	 * @throws UnresolvedDependencyException
	 *   If a module can't be resolved.
	 */
	@DisplayName("Invalid modules")
	@ParameterizedTest(name = "{displayName}: {0}")
	@MethodSource("eachShouldFailTest")
	@Throws(UnresolvedDependencyException::class)
	fun testBuildInvalidModules(moduleName: String)
	{
		val loaded = helper.loadModule(moduleName)
		assertFalse(
			loaded,
			"Should not have successfully loaded module: $moduleName")
		assertTrue(helper.errorDetected())
	}

	/**
	 * Check that we can compile or load the *valid* builder test modules, none
	 * of which should fail.
	 *
	 * @param moduleName
	 *   Each module or package name.
	 */
	@DisplayName("Valid builder modules")
	@ParameterizedTest(name = "{displayName}: {0}")
	@MethodSource("eachShouldPassTest")
	fun testBuildValidModules(moduleName: String)
	{
		val loaded = helper.loadModule(moduleName)
		assertTrue(
			loaded,
			"Should have successfully loaded module: $moduleName")
		assertFalse(helper.errorDetected())
	}

	/**
	 * Load all Avail tests and verify that they run successfully.
	 *
	 * @throws UnresolvedDependencyException
	 *   If a module can't be resolved.
	 */
	@DisplayName("Avail library unit tests")
	@Test
	@Throws(UnresolvedDependencyException::class)
	fun testAvailUnitTests()
	{
		val testModuleName = "/avail/Avail Tests"
		val loaded = helper.loadModule(testModuleName)
		assertTrue(
			loaded, "Failed to load module: $testModuleName")
		val semaphore = Semaphore(0)
		val ok = AtomicBoolean(false)
		helper.builder.attemptCommand(
			"Run all tests",
			{ commands: List<CompiledCommand>, proceed: (CompiledCommand) -> Unit ->
				proceed(commands[0])
			},
			{ result: AvailObject, cleanup: (() -> Unit) -> Unit ->
				cleanup.invoke {
					ok.set(result.extractBoolean)
					semaphore.release()
				}
			}) { semaphore.release()
		}
		semaphore.acquireUninterruptibly()
		assertTrue(ok.get(), "Some Avail tests failed")
		assertFalse(helper.errorDetected())
		// TODO: [TLS] Runners.avail needs to be reworked so that Avail unit
		// test failures show up on standard error instead of standard output,
		// otherwise this test isn't nearly as useful as it could be.
	}

	companion object
	{
		/**
		 * Answer the fully qualified name of each module that should fail to
		 * load.
		 */
		@Suppress("unused")
		@JvmStatic
		fun eachShouldFailTest(): List<String>
		{
			val projectDirectory = System.getProperty("user.dir")
				.replace("/avail", "")
			val dir = File(projectDirectory)
				.resolve("distro/src/builder-tests/Invalid Tests.avail")
			return dir.list()!!.mapNotNull {
				if (it == "Invalid Tests.avail" || !it.endsWith(".avail"))
				{
					null
				}
				else
				{
					"/builder-tests/Invalid Tests/" + it.split('.')[0]
				}
			}
		}

		/**
		 * Answer the fully qualified name of each module that should load
		 * successfully.
		 */
		@Suppress("unused")
		@JvmStatic
		fun eachShouldPassTest(): List<String>
		{
			val projectDirectory = System.getProperty("user.dir")
				.replace("/avail", "")
			val dir = File(projectDirectory)
				.resolve("distro/src/builder-tests/Valid Tests.avail")
			return dir.list()!!.mapNotNull {
				if (it == "Valid Tests.avail" || !it.endsWith(".avail"))
				{
					null
				}
				else
				{
					"/builder-tests/Valid Tests/" + it.split('.')[0]
				}
			}
		}
	}
}

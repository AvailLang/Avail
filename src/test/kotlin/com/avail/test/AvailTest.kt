/*
 * AvailTest.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.test

import com.avail.AvailRuntime
import com.avail.builder.AvailBuilder.CompiledCommand
import com.avail.builder.RenamesFileParserException
import com.avail.builder.UnresolvedDependencyException
import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.representation.AvailObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
	/** Setup for the test.  */
	var helper: AvailRuntimeTestHelper? = null

	/**
	 * Answer the [AvailRuntimeTestHelper], ensuring it's not `null`.
	 *
	 * @return
	 *   The [AvailRuntimeTestHelper].
	 */
	fun helper(): AvailRuntimeTestHelper =  helper!!

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
		helper = AvailRuntimeTestHelper()
		if (System.getProperty("clearAllRepositories", null) !== null)
		{
			helper().clearAllRepositories()
		}
	}

	/**
	 * Clear any error detected on the [AvailRuntimeTestHelper.TestErrorChannel].
	 */
	@BeforeEach
	fun clearError()
	{
		helper().clearError()
	}

	/**
	 * Shut down the [AvailRuntime] after the tests.
	 */
	@AfterAll
	fun tearDownRuntime()
	{
		helper().tearDownRuntime()
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
		val loaded = helper().loadModule(moduleName)
		Assertions.assertTrue(loaded, "Failed to load module: $moduleName")
		Assertions.assertFalse(helper().errorDetected())
	}

	/**
	 * Check that we can compile or load the builder test modules.  Each of
	 * these should fail, writing some message to the error channel.
	 *
	 * @param moduleName
	 *   Each module or package name.
	 * @throws UnresolvedDependencyException
	 *   If a module can't be resolved.
	 */
	@DisplayName("Invalid modules")
	@ParameterizedTest(name = "{displayName}: {0}")
	@ValueSource(strings = [
		"/experimental/builder tests/MutuallyRecursive1",
		"/experimental/builder tests/MutuallyRecursive2",
		"/experimental/builder tests/MutuallyRecursive3",
		"/experimental/builder tests/UsesMutuallyRecursive1",
		"/experimental/builder tests/UsesUsesMutuallyRecursive1",
		"/experimental/builder tests/ShouldFailCompilation",
		"/experimental/builder tests/ShouldFailDuplicateImportVersion",
		"/experimental/builder tests/ShouldFailDuplicateName",
		"/experimental/builder tests/ShouldFailDuplicateVersion",
		"/experimental/builder tests/ShouldFailPragmas",
		"/experimental/builder tests/ShouldFailScanner",
		"/experimental/builder tests/ShouldFailTrace",
		"/experimental/builder tests/ShouldFailWithWrongModuleName"])
	@Throws(UnresolvedDependencyException::class)
	fun testBuildInvalidModules(moduleName: String)
	{
		val loaded = helper().loadModule(moduleName)
		Assertions.assertFalse(
			loaded,
			"Should not have successfully loaded module: $moduleName"
		)
		Assertions.assertTrue(helper().errorDetected())
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
		val loaded = helper().loadModule(testModuleName)
		Assertions.assertTrue(
			loaded, "Failed to load module: $testModuleName")
		val semaphore = Semaphore(0)
		val ok = AtomicBoolean(false)
		helper().builder.attemptCommand(
			"Run all tests",
			{ commands: List<CompiledCommand>, proceed: (CompiledCommand) -> Unit ->
				proceed.invoke(commands[0])
			},
			{ result: AvailObject, cleanup: (() -> Unit) -> Unit ->
				cleanup.invoke {
					ok.set(result.extractBoolean())
					semaphore.release()
				}
			}) { semaphore.release()
		}
		semaphore.acquireUninterruptibly()
		Assertions.assertTrue(ok.get(), "Some Avail tests failed")
		Assertions.assertFalse(helper().errorDetected())
		// TODO: [TLS] Runners.avail needs to be reworked so that Avail unit
		// test failures show up on standard error instead of standard output,
		// otherwise this test isn't nearly as useful as it could be.
	}
}

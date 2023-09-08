/*
 * CallbackTest.kt
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
import avail.CallbackSystem.Callback
import avail.CallbackSystem.CallbackCompletion
import avail.CallbackSystem.CallbackFailure
import avail.CallbackSystem.Companion.createCallbackFunctionInJava
import avail.builder.RenamesFileParserException
import avail.builder.UnresolvedDependencyException
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.Companion.createFiber
import avail.descriptor.functions.A_Function
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.entryPoints
import avail.descriptor.numbers.A_Number.Companion.divideCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import java.io.FileNotFoundException
import java.util.concurrent.SynchronousQueue

/**
 * Unit tests for calls into Avail from Java code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@TestInstance(Lifecycle.PER_CLASS)
class CallbackTest
{
	/**
	 * The [AvailRuntimeTestHelper] used for the tests.
	 */
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
	 * Look up a loaded [A_Module] by name, and find the entry point in it with
	 * the given name.  Ensure there's only one [A_Definition] of the
	 * [A_Method], and return the body [A_Function].
	 *
	 * @param moduleName
	 *   The [A_String] that names the module defining the entry point.
	 * @param entryPointMethodName
	 *   The [A_String] that names the entry point in the module.
	 * @return
	 *   The [A_Function] that's the body of the sole definition.
	 */
	private fun monomorphicDefinitionBody(
		moduleName: A_String, entryPointMethodName: A_String): A_Function
	{
		val module: A_Module = helper.runtime.moduleAt(moduleName)
		val entryPointsNames = module.entryPoints
		val atom: A_Atom = entryPointsNames.mapAt(entryPointMethodName)
		val definitions: A_Tuple =
			atom.bundleOrNil.bundleMethod.definitionsTuple
		assert(definitions.tupleSize == 1)
		return definitions.tupleAt(1).bodyBlock()
	}

	/**
	 * Check that Avail can call a provided callback function that divides two
	 * numbers.
	 *
	 * @throws UnresolvedDependencyException
	 *   If a module can't be resolved.
	 */
	@DisplayName("Division callback")
	@Test
	@Throws(UnresolvedDependencyException::class)
	fun testDivisionCallback()
	{
		val harnessModuleName = "/builder-tests/Callback Test Harness"
		val loaded = helper.loadModule(harnessModuleName)
		Assertions.assertTrue(
			loaded,
			"Failed to load module: $harnessModuleName")
		Assertions.assertFalse(helper.errorDetected())
		val body = monomorphicDefinitionBody(
			stringFrom(harnessModuleName),
			stringFrom("Invoke Once_with_"))
		val fiber = createFiber(
			Types.NUMBER.o,
			helper.runtime,
			null,
			helper.runtime.textInterface(),
			FiberDescriptor.commandPriority)
		{
			stringFrom("testDivisionCallback")
		}
		val expectedAnswer = fromInt(14)
		val mailbox = SynchronousQueue<Runnable>()
		fiber.setSuccessAndFailure(
			{ result: AvailObject? ->
				try
				{
					mailbox.put(
						Runnable {
							Assertions.assertEquals(
								expectedAnswer,
								result)
						})
				}
				catch (e: InterruptedException)
				{
					// Shouldn't happen.
				}
			}) { failure: Throwable ->
			try
			{
				mailbox.put(Runnable {
					Assertions.fail<Any>("Fiber failed: $failure") })
			}
			catch (e: InterruptedException)
			{
				// Shouldn't happen.
			}
		}
		helper.runtime.runOutermostFunction(
			fiber,
			body,
			listOf(
				divisionCallback(),
				tuple(
					fromInt(42),
					fromInt(3))),
			false)
		try
		{
			mailbox.take().run()
		}
		catch (e: InterruptedException)
		{
			// Shouldn't happen.
		}
	}

	companion object
	{
		/**
		 * Create a callback [A_Function] that extracts the two numbers from a
		 * provided tuple, divides the first by the second, and answers that
		 * quotient.  If it fails with some [Throwable], a suitable exception
		 * should be raised within Avail.
		 *
		 * @return
		 * An [A_Function] that divides two numbers.
		 */
		fun divisionCallback(): A_Function
		{
			val callback = object: Callback
			{
				override fun call(
					argumentsTuple: A_Tuple,
					completion: CallbackCompletion,
					failure: CallbackFailure)
				{
					assert(argumentsTuple.tupleSize == 2)
					val a = argumentsTuple.tupleAt(1)
					val b = argumentsTuple.tupleAt(2)
					try
					{
						val c = a.divideCanDestroy(b, true)
						completion.complete(c)
					}
					catch (e: Throwable)
					{
						failure.failed(e)
					}
				}
			}
			return createCallbackFunctionInJava(
				functionType(
					tuple(
						Types.NUMBER.o,
						Types.NUMBER.o),
					Types.NUMBER.o),
				callback)
		}
	}
}

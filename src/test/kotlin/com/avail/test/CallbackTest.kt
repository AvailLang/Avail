/*
 * CallbackTest.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
import com.avail.CallbackSystem
import com.avail.CallbackSystem.CallbackCompletion
import com.avail.CallbackSystem.CallbackFailure
import com.avail.CallbackSystem.Companion.createCallbackFunction
import com.avail.builder.RenamesFileParserException
import com.avail.builder.UnresolvedDependencyException
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.fiber.FiberDescriptor.Companion.createFiber
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor
import com.avail.interpreter.execution.Interpreter.Companion.runOutermostFunction
import com.avail.utility.Casts
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
	/** Setup for the test.  */
	var helper: AvailRuntimeTestHelper? = null

	/**
	 * Answer the [AvailRuntimeTestHelper], ensuring it's not `null`.
	 *
	 * @return
	 *   The [AvailRuntimeTestHelper].
	 */
	fun helper(): AvailRuntimeTestHelper = helper!!

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
	fun monomorphicDefinitionBody(
		moduleName: A_String, entryPointMethodName: A_String): A_Function
	{
		val module: A_Module = helper().runtime.moduleAt(moduleName)
		val entryPointsNames = module.entryPoints()
		val atom: A_Atom = entryPointsNames.mapAt(entryPointMethodName)
		val definitions: A_Tuple =
			atom.bundleOrNil().bundleMethod().definitionsTuple()
		assert(definitions.tupleSize() == 1)
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
		val harnessModuleName = "/experimental/Callback Test Harness"
		val loaded = helper().loadModule(harnessModuleName)
		Assertions.assertTrue(
			loaded,
			"Failed to load module: $harnessModuleName")
		Assertions.assertFalse(helper().errorDetected())
		val body = monomorphicDefinitionBody(
			stringFrom(harnessModuleName),
			stringFrom("Invoke Once_with_"))
		val fiber = createFiber(
			TypeDescriptor.Types.NUMBER.o(),
			FiberDescriptor.commandPriority,
			NilDescriptor.nil,
			{ stringFrom("testDivisionCallback") },
			helper().runtime)
		val expectedAnswer = fromInt(14)
		val mailbox = SynchronousQueue<Runnable>()
		fiber.setSuccessAndFailureContinuations(
			{ result: AvailObject? ->
				try
				{
					mailbox.put(
						Runnable {
							Assertions.assertEquals(
								expectedAnswer,
								result
							)
						}
					)
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
		runOutermostFunction(
			helper().runtime,
			fiber,
			body,
			listOf(
				divisionCallback(),
				tuple(
					fromInt(42),
					fromInt(3)
				)
			)
		)
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
		 * Create a callback [A_Function] that extracts a number from the sole
		 * element of the tuple of arguments provided to it, adds one, and completes
		 * with that number.
		 *
		 * @return
		 * An [A_Function] that adds two numbers.
		 */
		fun plusOneCallback(): A_Function
		{
			val callback: CallbackSystem.Callback =
				object: CallbackSystem.Callback
				{
					override fun call(
						argumentsTuple: A_Tuple,
						completion: CallbackCompletion,
						failure: CallbackFailure)
					{
						assert(argumentsTuple.tupleSize() == 1)
						val a = argumentsTuple.tupleAt(1)
						try
						{
							val successor = a.plusCanDestroy(
								one(),
								true
							)
							completion.complete(
								Casts.cast(
									successor
								)
							)
						}
						catch (e: Throwable)
						{
							failure.failed(e)
						}
					}
				}
			return createCallbackFunction(
				functionType(
					tuple(TypeDescriptor.Types.NUMBER.o()),
					TypeDescriptor.Types.NUMBER.o()
				),
				callback
			)
		}

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
			val callback: CallbackSystem.Callback =
				object: CallbackSystem.Callback
				{
					override fun call(
						argumentsTuple: A_Tuple,
						completion: CallbackCompletion,
						failure: CallbackFailure)
					{
						assert(argumentsTuple.tupleSize() == 2)
						val a = argumentsTuple.tupleAt(1)
						val b = argumentsTuple.tupleAt(2)
						try
						{
							val c = a.divideCanDestroy(b, true)
							completion.complete(Casts.cast(c))
						}
						catch (e: Throwable)
						{
							failure.failed(e)
						}
					}
				}
			return createCallbackFunction(
				functionType(
					tuple(
						TypeDescriptor.Types.NUMBER.o(),
						TypeDescriptor.Types.NUMBER.o()
					),
					TypeDescriptor.Types.NUMBER.o()
				),
				callback
			)
		}
	}
}
/*
 * CallbackTest.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.test;

import com.avail.CallbackSystem.Callback;
import com.avail.builder.RenamesFileParserException;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.FileNotFoundException;
import java.util.concurrent.SynchronousQueue;

import static com.avail.CallbackSystem.createCallbackFunction;
import static com.avail.descriptor.FiberDescriptor.commandPriority;
import static com.avail.descriptor.FiberDescriptor.createFiber;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.one;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.utility.Casts.cast;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for calls into Avail from Java code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@TestInstance(Lifecycle.PER_CLASS)
public final class CallbackTest
{
	/** Setup for the test. */
	final AvailRuntimeTestHelper helper;

	/**
	 * Construct a {@code CallbackTest}.
	 *
	 * @throws FileNotFoundException
	 *         If the renames file was specified but not found.
	 * @throws RenamesFileParserException
	 *         If the renames file exists but could not be interpreted correctly
	 *         for any reason.
	 */
	@SuppressWarnings("unused")
	public CallbackTest ()
	throws FileNotFoundException, RenamesFileParserException
	{
		helper = new AvailRuntimeTestHelper();
	}

	/**
	 * Create a callback {@link A_Function} that extracts a number from the sole
	 * element of the tuple of arguments provided to it, adds one, and completes
	 * with that number.
	 *
	 * @return An {@link A_Function} that adds two numbers.
	 */
	public A_Function plusOneCallback ()
	{
		final Callback callback = (arguments, completion, failure) ->
		{
			assert arguments.tupleSize() == 1;
			final AvailObject a = arguments.tupleAt(1);
			try
			{
				final A_Number successor = a.plusCanDestroy(one(), true);
				completion.complete(cast(successor));
			}
			catch (final Throwable e)
			{
				failure.failed(e);
			}
		};
		return createCallbackFunction(
			callback,
			functionType(
				tuple(NUMBER.o()),
				NUMBER.o()));
	}

	/**
	 * Create a callback {@link A_Function} that extracts the two numbers from a
	 * provided tuple, divides the first by the second, and answers that
	 * quotient.  If it fails with some {@link Throwable}, a suitable exception
	 * should be raised within Avail.
	 *
	 * @return An {@link A_Function} that divides two numbers.
	 */
	public A_Function divisionCallback ()
	{
		final Callback callback = (arguments, completion, failure) ->
		{
			assert arguments.tupleSize() == 2;
			final AvailObject a = arguments.tupleAt(1);
			final AvailObject b = arguments.tupleAt(2);
			try
			{
				final A_Number c = a.divideCanDestroy(b, true);
				completion.complete(cast(c));
			}
			catch (final Throwable e)
			{
				failure.failed(e);
			}
		};
		return createCallbackFunction(
			callback,
			functionType(
				tuple(NUMBER.o(), NUMBER.o()),
				NUMBER.o()));
	}

	/**
	 * Look up a loaded {@link A_Module} by name, and find the entry point in it
	 * with the given name.  Ensure there's only one {@link A_Definition} of the
	 * {@link A_Method}, and return the body {@link A_Function}.
	 *
	 * @param moduleName
	 *        The {@link A_String} that names the module defining the entry
	 *        point.
	 * @param entryPointMethodName
	 *        The {@link A_String} that names the entry point in the module.
	 * @return The {@link A_Function} that's the body of the sole definition.
	 */
	public A_Function monomorphicDefinitionBody (
		final A_String moduleName,
		final A_String entryPointMethodName)
	{
		final A_Module module = helper.runtime.moduleAt(moduleName);
		final A_Map entryPointsNames = module.entryPoints();
		final A_Atom atom = entryPointsNames.mapAt(entryPointMethodName);
		final A_Tuple definitions =
			atom.bundleOrNil().bundleMethod().definitionsTuple();
		assert definitions.tupleSize() == 1;
		return definitions.tupleAt(1).bodyBlock();
	}

	/**
	 * Check that Avail can call a provided callback function that divides two
	 * numbers.
	 *
	 * @throws UnresolvedDependencyException
	 *         If a module can't be resolved.
	 */
	@Test
	public void testDivisionCallback ()
	throws UnresolvedDependencyException
	{
		final String harnessModuleName = "/experimental/Callback Test Harness";
		final boolean loaded = helper.loadModule(harnessModuleName);
		assertTrue(loaded, "Failed to load module: " + harnessModuleName);
		assertFalse(helper.errorDetected());

		final A_Module module =
			helper.runtime.moduleAt(stringFrom(harnessModuleName));
		final A_Map entryPointsNames = module.entryPoints();

		final A_Atom callOnceAtom = entryPointsNames.mapAt(
			stringFrom("Invoke Once_with_"));
		final A_Tuple definitions =
			callOnceAtom.bundleOrNil().bundleMethod().definitionsTuple();
		assert definitions.tupleSize() == 1;
		final A_Function body = definitions.tupleAt(1).bodyBlock();

		final A_Fiber fiber = createFiber(
			NUMBER.o(),
			commandPriority,
			nil,
			() -> stringFrom("testDivisionCallback"),
			helper.runtime);
		final AvailObject expectedAnswer = fromInt(14);
		final SynchronousQueue<Runnable> mailbox = new SynchronousQueue<>();
		fiber.setSuccessAndFailureContinuations(
			result ->
			{
				try
				{
					mailbox.put(
						() -> assertEquals(expectedAnswer, result));
				}
				catch (final InterruptedException e)
				{
					// Shouldn't happen.
				}
			},
			failure ->
			{
				try
				{
					mailbox.put(() -> fail("Fiber failed: " + failure));
				}
				catch (final InterruptedException e)
				{
					// Shouldn't happen.
				}
			});
		Interpreter.runOutermostFunction(
			helper.runtime,
			fiber,
			body,
			asList(divisionCallback(), tuple(fromInt(42), fromInt(3))));
		try
		{
			mailbox.take().run();
		}
		catch (final InterruptedException e)
		{
			// Shouldn't happen.
		}
	}
}

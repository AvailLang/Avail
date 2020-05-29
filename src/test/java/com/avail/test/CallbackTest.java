/*
 * CallbackTest.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.CallbackSystem.Callback;
import com.avail.builder.RenamesFileParserException;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.fiber.A_Fiber;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.methods.A_Definition;
import com.avail.descriptor.methods.A_Method;
import com.avail.descriptor.module.A_Module;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.interpreter.execution.Interpreter;
import com.avail.test.AvailRuntimeTestHelper.TestErrorChannel;
import com.avail.utility.Nulls;
import kotlin.Unit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.util.concurrent.SynchronousQueue;

import static com.avail.CallbackSystem.createCallbackFunction;
import static com.avail.descriptor.fiber.FiberDescriptor.commandPriority;
import static com.avail.descriptor.fiber.FiberDescriptor.createFiber;
import static com.avail.descriptor.numbers.IntegerDescriptor.fromInt;
import static com.avail.descriptor.numbers.IntegerDescriptor.one;
import static com.avail.descriptor.representation.NilDescriptor.nil;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.StringDescriptor.stringFrom;
import static com.avail.descriptor.types.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.types.TypeDescriptor.Types.NUMBER;
import static com.avail.utility.Casts.cast;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for calls into Avail from Java code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@TestInstance(Lifecycle.PER_CLASS)
public final class CallbackTest
{
	/** Setup for the test. */
	@Nullable AvailRuntimeTestHelper helper = null;

	/**
	 * Answer the {@link AvailRuntimeTestHelper}, ensuring it's not {@code
	 * null}.
	 *
	 * @return The {@link AvailRuntimeTestHelper}.
	 */
	AvailRuntimeTestHelper helper ()
	{
		return Nulls.stripNull(helper);
	}

	/**
	 * Clear all repositories iff the {@code clearAllRepositories} system
	 * property is defined.
	 *
	 * @throws FileNotFoundException
	 *         If the renames file was specified but not found.
	 * @throws RenamesFileParserException
	 *         If the renames file exists but could not be interpreted correctly
	 *         for any reason.
	 */
	@BeforeAll
	void maybeClearAllRepositories ()
	throws FileNotFoundException, RenamesFileParserException
	{
		helper = new AvailRuntimeTestHelper();
		if (System.getProperty("clearAllRepositories", null) != null)
		{
			helper().clearAllRepositories();
		}
	}

	/**
	 * Clear any error detected on the {@link TestErrorChannel}.
	 */
	@BeforeEach
	void clearError ()
	{
		helper().clearError();
	}

	/**
	 * Shut down the {@link AvailRuntime} after the tests.
	 */
	@AfterAll
	void tearDownRuntime ()
	{
		helper().tearDownRuntime();
	}

	/**
	 * Create a callback {@link A_Function} that extracts a number from the sole
	 * element of the tuple of arguments provided to it, adds one, and completes
	 * with that number.
	 *
	 * @return An {@link A_Function} that adds two numbers.
	 */
	@SuppressWarnings("unused")
	public static A_Function plusOneCallback ()
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
			functionType(
				tuple(NUMBER.o()),
				NUMBER.o()),
			callback);
	}

	/**
	 * Create a callback {@link A_Function} that extracts the two numbers from a
	 * provided tuple, divides the first by the second, and answers that
	 * quotient.  If it fails with some {@link Throwable}, a suitable exception
	 * should be raised within Avail.
	 *
	 * @return An {@link A_Function} that divides two numbers.
	 */
	public static A_Function divisionCallback ()
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
			functionType(
				tuple(NUMBER.o(), NUMBER.o()),
				NUMBER.o()),
			callback);
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
		final A_Module module = helper().runtime.moduleAt(moduleName);
		final A_Map entryPointsNames = module.entryPoints();
		final A_Atom atom = entryPointsNames.mapAt(entryPointMethodName);
		final A_Tuple definitions =
			A_Bundle.Companion.bundleMethod(A_Atom.Companion.bundleOrNil(atom)).definitionsTuple();
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
	@DisplayName("Division callback")
	@Test
	public void testDivisionCallback ()
	throws UnresolvedDependencyException
	{
		final String harnessModuleName = "/experimental/Callback Test Harness";
		final boolean loaded = helper().loadModule(harnessModuleName);
		assertTrue(loaded, "Failed to load module: " + harnessModuleName);
		assertFalse(helper().errorDetected());

		final A_Function body = monomorphicDefinitionBody(
			stringFrom(harnessModuleName), stringFrom("Invoke Once_with_"));

		final A_Fiber fiber = createFiber(
			NUMBER.o(),
			commandPriority,
			nil,
			() -> stringFrom("testDivisionCallback"),
			helper().runtime);
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
				return Unit.INSTANCE;
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
				return Unit.INSTANCE;
			});
		Interpreter.runOutermostFunction(
			helper().runtime,
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

/*
 * P_ExecuteAttachedExternalProcess.java
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

package com.avail.interpreter.primitive.processes;

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.ProcessInputChannel;
import com.avail.io.ProcessOutputChannel;
import com.avail.io.TextInterface;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberTypeDescriptor.fiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrOneOf;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_NO_EXTERNAL_PROCESS;
import static com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive</strong>: Execute an attached external {@linkplain Process
 * process}. The forked {@link A_Fiber fiber} is wired to the external process's
 * standard input, output, and error mechanisms.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_ExecuteAttachedExternalProcess
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ExecuteAttachedExternalProcess().init(
			6, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(6);
		final A_Tuple processArgsTuple = interpreter.argument(0);
		final A_Tuple optDir = interpreter.argument(1);
		final A_Tuple optEnvironment = interpreter.argument(2);
		final A_Function succeed = interpreter.argument(3);
		final A_Function fail = interpreter.argument(4);
		final A_Number priority = interpreter.argument(5);
		// Transform the process arguments into native strings.
		final List<String> processArgs = new ArrayList<>(
			processArgsTuple.tupleSize());
		for (final A_String processArg : processArgsTuple)
		{
			processArgs.add(processArg.asNativeString());
		}
		// Set up the process builder, taking care to explicitly redirect the
		// external process's streams to interface with us.
		final ProcessBuilder builder = new ProcessBuilder(processArgs);
		builder.redirectInput(Redirect.PIPE);
		builder.redirectOutput(Redirect.PIPE);
		builder.redirectError(Redirect.PIPE);
		if (optDir.tupleSize() == 1)
		{
			final File dir = new File(optDir.tupleAt(1).asNativeString());
			builder.directory(dir);
		}
		if (optEnvironment.tupleSize() == 1)
		{
			final Map<String, String> newEnvironmentMap = new HashMap<>();
			for (final Entry entry : optEnvironment.tupleAt(1).mapIterable())
			{
				newEnvironmentMap.put(
					entry.key().asNativeString(),
					entry.value().asNativeString());
			}
			final Map<String, String> environmentMap = builder.environment();
			environmentMap.clear();
			environmentMap.putAll(newEnvironmentMap);
		}
		// Create the new fiber that will be connected to the external process.
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = newFiber(
			TOP.o(),
			priority.extractInt(),
			() -> stringFrom("External process execution"));
		newFiber.availLoader(current.availLoader());
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();
		final AvailErrorCode error;
		final AvailRuntime runtime = currentRuntime();
		// Start the process, running the success function on the new fiber if
		// the process launches successfully.
		try
		{
			final Process process = builder.start();
			newFiber.textInterface(
				new TextInterface(
					new ProcessInputChannel(process.getInputStream()),
					new ProcessOutputChannel(
						new PrintStream(process.getOutputStream())),
					new ProcessOutputChannel(
						new PrintStream(process.getOutputStream()))));
			runOutermostFunction(
				runtime, newFiber, succeed, Collections.emptyList());
			return interpreter.primitiveSuccess(newFiber);
		}
		catch (final SecurityException e)
		{
			error = E_PERMISSION_DENIED;
		}
		catch (final IOException e)
		{
			error = E_NO_EXTERNAL_PROCESS;
		}
		// Run the failure function on the new fiber.
		newFiber.textInterface(current.textInterface());
		runOutermostFunction(
			runtime,
			newFiber,
			fail,
			singletonList(error.numericCode()));
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tupleFromArray(
				oneOrMoreOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(
					mapTypeForSizesKeyTypeValueType(
						wholeNumbers(), stringType(), stringType())),
				functionType(
					emptyTuple(),
					TOP.o()),
				functionType(
					tuple(
						enumerationWith(
							set(
								E_PERMISSION_DENIED,
								E_NO_EXTERNAL_PROCESS))),
					TOP.o()),
				bytes()),
			fiberType(TOP.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_PERMISSION_DENIED,
				E_NO_EXTERNAL_PROCESS));
	}
}

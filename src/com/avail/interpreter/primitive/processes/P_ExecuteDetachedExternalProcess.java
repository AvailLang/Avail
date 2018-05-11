/*
 * P_ExecuteDetachedExternalProcess.java
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

import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.MapTypeDescriptor
	.mapTypeForSizesKeyTypeValueType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_NO_EXTERNAL_PROCESS;
import static com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive</strong>: Execute a detached external {@linkplain Process
 * process}. No capability is provided to communicate with this {@linkplain
 * Process process}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_ExecuteDetachedExternalProcess
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ExecuteDetachedExternalProcess().init(
			6, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(6);
		final A_Tuple processArgsTuple = interpreter.argument(0);
		final A_Tuple optDir = interpreter.argument(1);
		final A_Tuple optIn = interpreter.argument(2);
		final A_Tuple optOut = interpreter.argument(3);
		final A_Tuple optError = interpreter.argument(4);
		final A_Tuple optEnvironment = interpreter.argument(5);
		final List<String> processArgs = new ArrayList<>(
			processArgsTuple.tupleSize());
		for (final A_String processArg : processArgsTuple)
		{
			processArgs.add(processArg.asNativeString());
		}
		final ProcessBuilder builder = new ProcessBuilder(processArgs);
		if (optDir.tupleSize() == 1)
		{
			final File dir = new File(optDir.tupleAt(1).asNativeString());
			builder.directory(dir);
		}
		if (optIn.tupleSize() == 1)
		{
			final File in = new File(optIn.tupleAt(1).asNativeString());
			builder.redirectInput(in);
		}
		if (optOut.tupleSize() == 1)
		{
			final File out = new File(optOut.tupleAt(1).asNativeString());
			builder.redirectOutput(out);
		}
		if (optError.tupleSize() == 1)
		{
			final File err = new File(optError.tupleAt(1).asNativeString());
			builder.redirectError(err);
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
		try
		{
			builder.start();
		}
		catch (final SecurityException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_NO_EXTERNAL_PROCESS);
		}
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tupleFromArray(
				oneOrMoreOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(stringType()),
				zeroOrOneOf(
					mapTypeForSizesKeyTypeValueType(
						wholeNumbers(),
						stringType(),
						stringType()))),
			TOP.o());
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

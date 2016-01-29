/**
 * P_ExecuteDetachedExternalProcess.java
 * Copyright © 1993-2016, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.avail.descriptor.*;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.interpreter.*;

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
	public final static Primitive instance =
		new P_ExecuteDetachedExternalProcess().init(
			6, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 6;
		final A_Tuple processArgsTuple = args.get(0);
		final A_Tuple optDir = args.get(1);
		final A_Tuple optIn = args.get(2);
		final A_Tuple optOut = args.get(3);
		final A_Tuple optError = args.get(4);
		final A_Tuple optEnvironment = args.get(5);
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
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.oneOrMoreOf(
					TupleTypeDescriptor.stringType()),
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.stringType()),
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.stringType()),
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.stringType()),
				TupleTypeDescriptor.zeroOrOneOf(
					TupleTypeDescriptor.stringType()),
				TupleTypeDescriptor.zeroOrOneOf(
					MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleTypeDescriptor.stringType(),
						TupleTypeDescriptor.stringType()))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
				E_PERMISSION_DENIED.numericCode(),
				E_NO_EXTERNAL_PROCESS.numericCode())));
	}
}

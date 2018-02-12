/*
 * P_SetImplicitObserveFunction.java
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

package com.avail.interpreter.primitive.variables;

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.VariableDescriptor;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.AvailRuntime.implicitObserveFunctionType;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Set the {@linkplain FunctionDescriptor
 * function} to invoke whenever a {@linkplain VariableDescriptor variable} with
 * {@linkplain VariableAccessReactor write reactors} is written when
 * {@linkplain TraceFlag#TRACE_VARIABLE_WRITES write tracing} is not enabled.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SetImplicitObserveFunction
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SetImplicitObserveFunction().init(
			1, CannotFail, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Function function = interpreter.argument(0);
		function.code().setMethodName(
			stringFrom("«implicit observe function»"));
		// Produce a wrapper that will invoke the supplied function, and then
		// specially resume the calling continuation (which won't be correctly
		// set up for a return).
		final A_Function wrapper = createFunction(rawFunction, tuple(function));
		// Now set the wrapper as the implicit observe function.
		currentRuntime().setImplicitObserveFunction(wrapper);
		return interpreter.primitiveSuccess(nil);
	}

	private static final A_RawFunction rawFunction = createRawFunction();

	/**
	 * Create an {@link A_RawFunction} which has an outer that'll be supplied
	 * during function closure.  The outer is a user-supplied function which
	 * is itself given a function and a tuple of arguments to apply, after which
	 * this generated function will resume the continuation that was interrupted
	 * to invoke this primitive.
	 *
	 * @return The one-outer, two-argument raw function.
	 */
	private static A_RawFunction createRawFunction ()
	{
		final L1InstructionWriter writer = new L1InstructionWriter(nil, 0, nil);
		final int outerIndex = writer.createOuter(implicitObserveFunctionType);
		writer.argumentTypes(mostGeneralFunctionType(), mostGeneralTupleType());
		writer.returnType(bottom());
		writer.write(0, L1Operation.L1_doPushOuter, outerIndex);
		writer.write(0, L1Operation.L1_doPushLocal, 1);
		writer.write(0, L1Operation.L1_doPushLocal, 2);
		writer.write(0, L1Operation.L1_doMakeTuple, 2);
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.APPLY.bundle),
			writer.addLiteral(TOP.o()));
		writer.write(0, L1Operation.L1_doPop);
		writer.write(0, L1Operation.L1Ext_doPushLabel);
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.CONTINUATION_CALLER.bundle),
			writer.addLiteral(variableTypeFor(mostGeneralContinuationType())));
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.GET_VARIABLE.bundle),
			writer.addLiteral(mostGeneralContinuationType()));
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.RESUME_CONTINUATION.bundle),
			writer.addLiteral(bottom()));
		final A_RawFunction code = writer.compiledCode();
		code.setMethodName(stringFrom("«implicit observe function wrapper»"));
		return code;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				implicitObserveFunctionType),
			TOP.o());
	}
}

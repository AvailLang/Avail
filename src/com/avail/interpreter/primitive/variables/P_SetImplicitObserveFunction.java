/**
 * P_SetImplicitObserveFunction.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.*;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

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
	public final static Primitive instance =
		new P_SetImplicitObserveFunction().init(
			1, CannotFail, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Function function = args.get(0);
		function.code().setMethodName(
			StringDescriptor.from("«implicit observe function»"));
		// Produce a wrapper that will invoke the supplied function, and then
		// specially resume the calling continuation (which won't be correctly
		// set up for a return).
		final L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(), 0, NilDescriptor.nil());
		writer.argumentTypes(
			FunctionTypeDescriptor.mostGeneralType(),
			TupleTypeDescriptor.mostGeneralType());
		writer.returnType(BottomTypeDescriptor.bottom());
		writer.write(L1Operation.L1_doPushLiteral, writer.addLiteral(function));
		writer.write(L1Operation.L1_doPushLocal, 1);
		writer.write(L1Operation.L1_doPushLocal, 2);
		writer.write(L1Operation.L1_doMakeTuple, 2);
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.APPLY.bundle),
			writer.addLiteral(TOP.o()));
		writer.write(L1Operation.L1_doPop);
		writer.write(L1Operation.L1Ext_doPushLabel);
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.CONTINUATION_CALLER.bundle),
			writer.addLiteral(
				VariableTypeDescriptor.wrapInnerType(
					ContinuationTypeDescriptor.mostGeneralType())));
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.GET_VARIABLE.bundle),
			writer.addLiteral(ContinuationTypeDescriptor.mostGeneralType()));
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.RESUME_CONTINUATION.bundle),
			writer.addLiteral(BottomTypeDescriptor.bottom()));
		final A_Function wrapper = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		wrapper.code().setMethodName(
			StringDescriptor.from("«implicit observe function wrapper»"));
		// Now set the wrapper as the implicit observe function.
		AvailRuntime.current().setImplicitObserveFunction(wrapper);
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						FunctionTypeDescriptor.mostGeneralType(),
						TupleTypeDescriptor.mostGeneralType()),
					TOP.o())),
			TOP.o());
	}
}

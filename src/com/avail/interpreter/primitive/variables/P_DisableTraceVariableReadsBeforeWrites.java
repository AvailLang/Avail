/*
 * P_DisableTraceVariableReadsBeforeWrites.java
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

import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_ILLEGAL_TRACE_MODE;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Disable {@linkplain
 * TraceFlag#TRACE_VARIABLE_READS_BEFORE_WRITES variable read-before-write
 * tracing} for the {@linkplain FiberDescriptor#currentFiber() current fiber}. To
 * each {@linkplain VariableDescriptor variable} that survived tracing, add a
 * {@linkplain VariableAccessReactor write reactor} that wraps the specified
 * {@linkplain FunctionDescriptor function}, associating it with the specified
 * {@linkplain AtomDescriptor atom} (for potential pre-activation removal).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_DisableTraceVariableReadsBeforeWrites
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_DisableTraceVariableReadsBeforeWrites().init(
			2, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Atom key = interpreter.argument(0);
		final A_Function reactorFunction = interpreter.argument(1);
		if (!interpreter.traceVariableReadsBeforeWrites())
		{
			return interpreter.primitiveFailure(E_ILLEGAL_TRACE_MODE);
		}
		interpreter.setTraceVariableReadsBeforeWrites(false);
		final A_Fiber fiber = interpreter.fiber();
		final A_Set readBeforeWritten = fiber.variablesReadBeforeWritten();
		final VariableAccessReactor reactor =
			new VariableAccessReactor(reactorFunction.makeShared());
		for (final A_Variable var : readBeforeWritten)
		{
			var.addWriteReactor(key, reactor);
		}
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(tuple(ATOM.o(), functionType(
			emptyTuple(),
			TOP.o())), TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_ILLEGAL_TRACE_MODE));
	}
}

/*
 * P_CatchException.java
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
package com.avail.interpreter.primitive.controlflow;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTypeDescriptor.exceptionType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableDescriptor.newVariableWithOuterType;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Always fail. The Avail failure code
 * invokes the {@linkplain FunctionDescriptor body block}. A handler block is
 * only invoked when an exception is raised.
 */
public final class P_CatchException extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CatchException().init(
			3, CatchException, PreserveFailureVariable, PreserveArguments, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
//		final A_BasicObject bodyBlock = interpreter.argument(0);
		final A_Tuple handlerBlocks = interpreter.argument(1);
//		final A_BasicObject ensureBlock = interpreter.argument(2);

		final A_Variable innerVariable = newVariableWithOuterType(
			failureVariableType());

		for (final A_BasicObject block : handlerBlocks)
		{
			if (!block.kind().argsTupleType().typeAtIndex(1).isSubtypeOf(
				exceptionType()))
			{
				innerVariable.setValueNoCheck(
					E_INCORRECT_ARGUMENT_TYPE.numericCode());
				return interpreter.primitiveFailure(innerVariable);
			}
		}
		innerVariable.setValueNoCheck(E_REQUIRED_FAILURE.numericCode());
		return interpreter.primitiveFailure(innerVariable);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				functionType(
					emptyTuple(),
					TOP.o()),
				zeroOrMoreOf(
					functionType(
						tuple(bottom()),
						TOP.o())),
				functionType(
					emptyTuple(),
					TOP.o())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		// Note: The failure value is itself a new variable stuffed into the
		// outer (primitive-failure) variable.
		return
			variableTypeFor(
				enumerationWith(
					set(
						E_REQUIRED_FAILURE,
						E_INCORRECT_ARGUMENT_TYPE,
						E_HANDLER_SENTINEL,
						E_UNWIND_SENTINEL)));
	}
}

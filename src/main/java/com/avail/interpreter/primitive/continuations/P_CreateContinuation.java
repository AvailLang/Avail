/*
 * P_CreateContinuation.java
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
package com.avail.interpreter.primitive.continuations;

import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.ContinuationDescriptor.createContinuationWithFrame;
import static com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_CONTINUATION_STACK_SIZE;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RETURN_INTO;
import static com.avail.interpreter.levelTwo.L2Chunk.unoptimizedChunk;

/**
 * <strong>Primitive:</strong> Create a {@linkplain ContinuationDescriptor
 * continuation}. It will execute as unoptimized code via the {@link
 * L2Chunk#unoptimizedChunk}.  Fail if the provided function is an infallible
 * primitive.
 */
public final class P_CreateContinuation extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreateContinuation().init(
			5, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(5);
		final A_Function function = interpreter.argument(0);
		final A_Number pc = interpreter.argument(1);
		final A_Tuple stack = interpreter.argument(2);
		final A_Number stackp = interpreter.argument(3);
		final A_Variable callerHolder = interpreter.argument(4);

		final A_RawFunction rawFunction = function.code();
		final @Nullable Primitive primitive = rawFunction.primitive();
		if (primitive != null && primitive.hasFlag(CannotFail))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION);
		}
		if (stack.tupleSize() != rawFunction.numSlots())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_CONTINUATION_STACK_SIZE);
		}
		final A_Continuation cont = createContinuationWithFrame(
			function,
			callerHolder.value(),
			pc.extractInt(),
			stackp.extractInt(),
			unoptimizedChunk,
			TO_RETURN_INTO.offsetInDefaultChunk,
			toList(stack),
			0);
		return interpreter.primitiveSuccess(cont);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralFunctionType(),
				wholeNumbers(),
				mostGeneralTupleType(),
				naturalNumbers(),
				variableTypeFor(
					mostGeneralContinuationType())),
			mostGeneralContinuationType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(
			E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION,
			E_INCORRECT_CONTINUATION_STACK_SIZE));
	}
}

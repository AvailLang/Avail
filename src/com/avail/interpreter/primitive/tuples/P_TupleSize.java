/*
 * P_TupleSize.java
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
package com.avail.interpreter.primitive.tuples;

import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_SIZE;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;

/**
 * <strong>Primitive:</strong> Answer the size of the {@linkplain
 * TupleDescriptor tuple}.
 */
@SuppressWarnings("unused")
public final class P_TupleSize
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_TupleSize().init(
			1, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Tuple tuple = interpreter.argument(0);
		return interpreter.primitiveSuccess(fromInt(tuple.tupleSize()));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(mostGeneralTupleType()),
			wholeNumbers());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		return argumentTypes.get(0).sizeRange().typeIntersection(int32());
	}

	@Override
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadBoxedOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadBoxedOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		final L2ReadBoxedOperand tupleReg = arguments.get(0);

		final A_Type returnType = returnTypeGuaranteedByVM(
			rawFunction, argumentTypes);
//		assert returnType.isSubtypeOf(int32());
		final L2ReadIntOperand unboxedValue;
		if (returnType.lowerBound().equals(returnType.upperBound()))
		{
			// If the exact size of the tuple is known, then leverage that
			// information to produce a constant.
			unboxedValue = translator.generator.unboxedIntConstant(
				returnType.lowerBound().extractInt());
		}
		else
		{
			// The exact size of the tuple isn't known, so generate code to
			// extract it.
			final TypeRestriction restriction =
				restrictionForType(returnType, UNBOXED_INT);
			final L2WriteIntOperand writer =
				translator.generator.intWriteTemp(restriction);
			translator.addInstruction(
				L2_TUPLE_SIZE.instance,
				tupleReg,
				writer);
			unboxedValue = translator.currentManifest().readInt(
				writer.semanticValue());
		}
		final L2ReadBoxedOperand boxed =
			translator.generator.readBoxed(unboxedValue.semanticValue());
		callSiteHelper.useAnswer(boxed);
		return true;
	}
}

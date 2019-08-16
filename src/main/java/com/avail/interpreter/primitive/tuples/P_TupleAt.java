/*
 * P_TupleAt.java
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

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_COMPARE_INT;
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_AT_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_AT_NO_FAIL;
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_SIZE;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.optimizer.values.L2SemanticValue;

import java.util.Arrays;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.one;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static com.avail.optimizer.L2Generator.edgeTo;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive:</strong> Look up an element in the {@linkplain
 * TupleDescriptor tuple}.
 */
public final class P_TupleAt extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_TupleAt().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Tuple tuple = interpreter.argument(0);
		final A_Number indexObject = interpreter.argument(1);
		if (!indexObject.isInt())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final int index = indexObject.extractInt();
		if (index > tuple.tupleSize())
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		return interpreter.primitiveSuccess(tuple.tupleAt(index));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralTupleType(),
				naturalNumbers()),
			ANY.o());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tupleType = argumentTypes.get(0);
		final A_Type subscripts = argumentTypes.get(1);

		final A_Number lower = subscripts.lowerBound();
		final A_Number upper = subscripts.upperBound();
		final int lowerInt = lower.isInt() ? lower.extractInt() : 1;
		final int upperInt = upper.isInt()
			? upper.extractInt()
			: Integer.MAX_VALUE;
		final A_Type unionType =
			tupleType.unionOfTypesAtThrough(lowerInt, upperInt);
		unionType.makeImmutable();
		return unionType;
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS));
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tupleType = argumentTypes.get(0);
		final A_Type subscripts = argumentTypes.get(1);

		final A_Type tupleTypeSizes = tupleType.sizeRange();
		final A_Number minTupleSize = tupleTypeSizes.lowerBound();
		if (subscripts.lowerBound().greaterOrEqual(one())
			&& subscripts.upperBound().lessOrEqual(minTupleSize))
		{
			return CallSiteCannotFail;
		}
		return super.fallibilityForArgumentTypes(argumentTypes);
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
		final L2ReadBoxedOperand subscriptReg = arguments.get(1);
		final L2Generator generator = translator.generator;
		if (fallibilityForArgumentTypes(argumentTypes) != CallSiteCannotFail)
		{
			// We can't guarantee success, so do a dynamic bounds check.
			final L2BasicBlock failed = generator.createBasicBlock(
				"failed bounds check");
			final L2SemanticValue semanticSize =
				generator.primitiveInvocation(
					P_TupleSize.instance,
					singletonList(tupleReg));
			final TypeRestriction intSizeRestriction =
				restrictionForType(
					tupleReg.type().sizeRange().typeIntersection(int32()),
					UNBOXED_INT);
			final L2WriteIntOperand sizeWriter = generator.intWrite(
				semanticSize, intSizeRestriction);
			translator.addInstruction(
				L2_TUPLE_SIZE.instance,
				tupleReg,
				sizeWriter);
			final L2ReadIntOperand readSubscript =
				generator.readInt(subscriptReg.semanticValue(), failed);
			// At this position, we have the tuple size and subscript in int
			// registers. Check the lower bound, if necessary.
			if (generator.currentlyReachable() &&
				readSubscript.restriction().type.lowerBound().lessThan(one()))
			{
				final L2BasicBlock success1 =
					generator.createBasicBlock("passed lower bound check");
				generator.addInstruction(
					L2_JUMP_IF_COMPARE_INT.greaterOrEqual,
					readSubscript,
					generator.unboxedIntConstant(1),
					edgeTo(success1),
					edgeTo(failed));
				generator.startBlock(success1);
			}
			// Check the upper bound, if necessary.
			if (generator.currentlyReachable()
				&& subscriptReg.type().upperBound().greaterThan(
					intSizeRestriction.type.lowerBound()))
			{
				final L2BasicBlock success2 =
					generator.createBasicBlock("passed upper bound check");
				generator.addInstruction(
					L2_JUMP_IF_COMPARE_INT.lessOrEqual,
					readSubscript,
					generator.currentManifest().readInt(semanticSize),
					edgeTo(success2),
					edgeTo(failed));
				generator.startBlock(success2);
			}
			if (generator.currentlyReachable())
			{
				final TypeRestriction resultRestriction =
					restrictionForType(
						returnTypeGuaranteedByVM(
							rawFunction,
							Arrays.asList(
								argumentTypes.get(0),
								intSizeRestriction.type)),
						BOXED);
				final L2SemanticValue semanticResult =
					generator.primitiveInvocation(this, arguments);
				final L2WriteBoxedOperand writeResult =
					generator.boxedWrite(semanticResult, resultRestriction);
				generator.addInstruction(
					L2_TUPLE_AT_NO_FAIL.instance,
					tupleReg,
					readSubscript,
					writeResult);
				callSiteHelper.useAnswer(translator.readBoxed(writeResult));
			}
			generator.startBlock(failed);
			if (!generator.currentlyReachable())
			{
				// The failure path can't be reached.
				return true;
			}
			// We failed the dynamic range check, so fall back to a regular call
			// site.
			return super.tryToGenerateSpecialPrimitiveInvocation(
				functionToCallReg,
				rawFunction,
				arguments,
				argumentTypes,
				translator,
				callSiteHelper);
		}
		// The primitive cannot fail at this site.
		final A_Type subscriptType = subscriptReg.type();
		final A_Number lower = subscriptType.lowerBound();
		final A_Number upper = subscriptType.upperBound();
		final L2WriteBoxedOperand writer =
			generator.boxedWriteTemp(
				restrictionForType(
					returnTypeGuaranteedByVM(rawFunction, argumentTypes),
					BOXED));
		if (lower.equals(upper))
		{
			// The subscript is a constant (and it's within range).
			final int subscriptInt = lower.extractInt();
			translator.addInstruction(
				L2_TUPLE_AT_CONSTANT.instance,
				tupleReg,
				new L2IntImmediateOperand(subscriptInt),
				writer);
			callSiteHelper.useAnswer(translator.readBoxed(writer));
			return true;
		}
		// The subscript isn't a constant, but it's known to be in range.
		final L2BasicBlock subscriptConversionFailure =
			generator.createBasicBlock("Should be unreachable");
		final L2ReadIntOperand subscriptIntReg =
			generator.readInt(
				subscriptReg.semanticValue(),
				subscriptConversionFailure);
		assert subscriptConversionFailure.predecessorEdgesCount() == 0;
		translator.addInstruction(
			L2_TUPLE_AT_NO_FAIL.instance,
			tupleReg,
			subscriptIntReg,
			writer);
		callSiteHelper.useAnswer(translator.readBoxed(writer));
		return true;
	}
}

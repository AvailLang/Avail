/**
 * P_IsSubtypeOf.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.types;

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.L1NaiveTranslator;
import com.avail.optimizer.L2BasicBlock;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.AtomDescriptor.*;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.EnumerationTypeDescriptor.falseType;
import static com.avail.descriptor.EnumerationTypeDescriptor.trueType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Answer whether type1 is a subtype of type2
 * (or equal).
 */
public final class P_IsSubtypeOf extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_IsSubtypeOf().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Type type1 = args.get(0);
		final A_Type type2 = args.get(1);
		return interpreter.primitiveSuccess(
			objectFromBoolean(type1.isSubtypeOf(type2)));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				topMeta(),
				topMeta()),
			booleanType());
	}

	/**
	 * The test x ⊆ y is always false if x ∩ y = ⊥ (i.e., if x' ∩ y' ⊆ ⊥'.  The
	 * test is always true if the exact type y1 is known (not a subtype) and
	 * x ⊆ y1'.
	 */
	@Override
	public void generateL2UnfoldableInlinePrimitive (
		final L1NaiveTranslator translator,
		final A_Function primitiveFunction,
		final L2ReadVectorOperand args,
		final int resultSlotIndex,
		final L2ReadVectorOperand preserved,
		final A_Type expectedType,
		final L2WritePointerOperand failureValueWrite,
		final L2BasicBlock successBlock,
		final boolean canFailPrimitive,
		final boolean skipReturnCheck)
	{
		final L2ReadPointerOperand xTypeReg = args.elements().get(0);
		final L2ReadPointerOperand yTypeReg = args.elements().get(1);

		final A_Type xMeta = xTypeReg.type();
		final A_Type yMeta = yTypeReg.type();
		final A_Type xType = xMeta.instance();
		final A_Type yType = yMeta.instance();
		final @Nullable A_Type constantYType =
			(A_Type) yTypeReg.constantOrNull();
		if (constantYType != null)
		{
			assert constantYType.isType();
			assert constantYType.isSubtypeOf(yType);
			if (xType.isSubtypeOf(constantYType))
			{
				// The y type is known precisely, and the x type is constrained
				// to always be a subtype of it.
				translator.moveConstant(
					trueObject(),
					translator.writeSlot(
						resultSlotIndex, trueType(), trueObject()));
				return;
			}
		}
		final @Nullable A_Type constantXType =
			(A_Type) xTypeReg.constantOrNull();
		if (constantXType != null)
		{
			assert constantXType.isType();
			assert constantXType.isSubtypeOf(xType);
			if (!constantXType.isSubtypeOf(yType))
			{
				// In x ⊆ y, the exact type x happens to be known statically,
				// and it is not a subtype of y.  The actual y might be more
				// specific at runtime, but x still can't be a subtype of the
				// stronger y.
				translator.moveConstant(
					falseObject(),
					translator.writeSlot(
						resultSlotIndex, falseType(), falseObject()));
				return;
			}
		}
		super.generateL2UnfoldableInlinePrimitive(
			translator,
			primitiveFunction,
			args,
			resultSlotIndex,
			preserved,
			expectedType,
			failureValueWrite,
			successBlock,
			canFailPrimitive,
			skipReturnCheck);
	}
}

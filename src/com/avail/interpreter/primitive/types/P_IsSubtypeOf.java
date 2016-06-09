/**
 * P_IsSubtypeOf.java
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
package com.avail.interpreter.primitive.types;

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;

/**
 * <strong>Primitive:</strong> Answer whether type1 is a subtype of type2
 * (or equal).
 */
public final class P_IsSubtypeOf extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
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
			AtomDescriptor.objectFromBoolean(
				type1.isSubtypeOf(type2)));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				InstanceMetaDescriptor.topMeta(),
				InstanceMetaDescriptor.topMeta()),
			EnumerationTypeDescriptor.booleanObject());
	}

	/**
	 * The test x ⊆ y is always false if x ∩ y = ⊥ (i.e., if x' ∩ y' ⊆ ⊥'.  The
	 * test is always true if the exact type y1 is known (not a subtype) and
	 * x ⊆ y1'.
	 */
	@Override
	public void generateL2UnfoldableInlinePrimitive (
		final L1NaiveTranslator levelOneNaiveTranslator,
		final A_Function primitiveFunction,
		final L2RegisterVector args,
		final L2ObjectRegister resultRegister,
		final L2RegisterVector preserved,
		final A_Type expectedType,
		final L2ObjectRegister failureValueRegister,
		final L2Instruction successLabel,
		final boolean canFailPrimitive,
		final boolean skipReturnCheck)
	{
		final L2ObjectRegister xTypeReg = args.registers().get(0);
		final L2ObjectRegister yTypeReg = args.registers().get(1);

		final RegisterSet registerSet =
			levelOneNaiveTranslator.naiveRegisters();
		final A_Type xMeta = registerSet.typeAt(xTypeReg);
		final A_Type yMeta = registerSet.typeAt(yTypeReg);
		final A_Type xType = xMeta.instance();
		final A_Type yType = yMeta.instance();
		if (registerSet.hasConstantAt(yTypeReg))
		{
			final A_Type constantYType = registerSet.constantAt(yTypeReg);
			assert constantYType.isType();
			assert constantYType.isSubtypeOf(yType);
			if (xType.isSubtypeOf(constantYType))
			{
				// The y type is known precisely, and the x type is constrained
				// to always be a subtype of it.
				levelOneNaiveTranslator.moveConstant(
					AtomDescriptor.trueObject(),
					resultRegister);
				return;
			}
		}
		if (registerSet.hasConstantAt(xTypeReg))
		{
			final A_Type constantXType = registerSet.constantAt(xTypeReg);
			assert constantXType.isType();
			assert constantXType.isSubtypeOf(xType);
			if (!constantXType.isSubtypeOf(yType))
			{
				// In x ⊆ y, the exact type x happens to be known statically,
				// and it is not a subtype of y.  The actual y might be more
				// specific at runtime, but x still can't be a subtype of the
				// stronger y.
				levelOneNaiveTranslator.moveConstant(
					AtomDescriptor.falseObject(),
					resultRegister);
				return;
			}
		}
		super.generateL2UnfoldableInlinePrimitive(
			levelOneNaiveTranslator,
			primitiveFunction,
			args,
			resultRegister,
			preserved,
			expectedType,
			failureValueRegister,
			successLabel,
			canFailPrimitive,
			skipReturnCheck);
	}
}

/**
 * P_005_LessThan.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive;

import static com.avail.descriptor.AbstractNumberDescriptor.Order.*;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import java.util.Set;
import com.avail.descriptor.*;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;

/**
 * <strong>Primitive 5:</strong> Compare two extended integers and answer a
 * {@linkplain EnumerationTypeDescriptor#booleanObject() boolean}.
 */
public final class P_005_LessThan extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_005_LessThan().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Number a = args.get(0);
		final A_Number b = args.get(1);
		return interpreter.primitiveSuccess(
			AtomDescriptor.objectFromBoolean(a.lessThan(b)));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				NUMBER.o(),
				NUMBER.o()),
			EnumerationTypeDescriptor.booleanObject());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final Set<Order> possible =
			AbstractNumberDescriptor.possibleOrdersWhenComparingInstancesOf(
				argumentTypes.get(0), argumentTypes.get(1));
		final boolean canBeTrue = possible.contains(LESS);
		final boolean canBeFalse =
			possible.contains(EQUAL)
			|| possible.contains(MORE)
			|| possible.contains(INCOMPARABLE);
		assert canBeTrue || canBeFalse;
		return canBeTrue
			? (canBeFalse
				? EnumerationTypeDescriptor.booleanObject()
				: EnumerationTypeDescriptor.trueType())
			: EnumerationTypeDescriptor.falseType();
	}

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
		final L2ObjectRegister firstReg = args.registers().get(0);
		final L2ObjectRegister secondReg = args.registers().get(1);
		final RegisterSet registerSet =
			levelOneNaiveTranslator.naiveRegisters();
		final A_Type firstType = registerSet.typeAt(firstReg);
		final A_Type secondType = registerSet.typeAt(secondReg);
		final Set<Order> possible =
			AbstractNumberDescriptor.possibleOrdersWhenComparingInstancesOf(
				firstType, secondType);
		final boolean canBeTrue = possible.contains(LESS);
		final boolean canBeFalse =
			possible.contains(EQUAL)
			|| possible.contains(MORE)
			|| possible.contains(INCOMPARABLE);
		assert canBeTrue || canBeFalse;
		if (!canBeTrue || !canBeFalse)
		{
			levelOneNaiveTranslator.moveConstant(
				AtomDescriptor.objectFromBoolean(canBeTrue),
				resultRegister);
			return;
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

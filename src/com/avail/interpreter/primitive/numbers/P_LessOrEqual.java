/**
 * P_LessOrEqual.java
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
package com.avail.interpreter.primitive.numbers;

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.EnumerationTypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1NaiveTranslator;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.AbstractNumberDescriptor.Order.*;
import static com.avail.descriptor.AbstractNumberDescriptor
	.possibleOrdersWhenComparingInstancesOf;
import static com.avail.descriptor.AtomDescriptor.objectFromBoolean;
import static com.avail.descriptor.EnumerationTypeDescriptor.*;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Compare two extended integers and answer
 * a {@linkplain EnumerationTypeDescriptor#booleanType() boolean}.
 */
public final class P_LessOrEqual extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_LessOrEqual().init(
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
			objectFromBoolean(a.lessOrEqual(b)));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				NUMBER.o(),
				NUMBER.o()),
			booleanType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final Set<Order> possible = possibleOrdersWhenComparingInstancesOf(
			argumentTypes.get(0), argumentTypes.get(1));
		final boolean canBeTrue =
			possible.contains(LESS) || possible.contains(EQUAL);
		final boolean canBeFalse =
			possible.contains(MORE) || possible.contains(INCOMPARABLE);
		assert canBeTrue || canBeFalse;
		return canBeTrue
			? (canBeFalse ? booleanType() : trueType())
			: falseType();
	}

	@Override
	public @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1NaiveTranslator translator)
	{
		final L2ReadPointerOperand firstReg = arguments.get(0);
		final L2ReadPointerOperand secondReg = arguments.get(1);
		final A_Type firstType = firstReg.type();
		final A_Type secondType = secondReg.type();
		final Set<Order> possible = possibleOrdersWhenComparingInstancesOf(
			firstType, secondType);
		final boolean canBeTrue =
			possible.contains(LESS) || possible.contains(EQUAL);
		final boolean canBeFalse =
			possible.contains(MORE) || possible.contains(INCOMPARABLE);
		assert canBeTrue || canBeFalse;
		if (!canBeTrue || !canBeFalse)
		{
			return translator.constantRegister(objectFromBoolean(canBeTrue));
		}
		return super.tryToGenerateSpecialInvocation(
			functionToCallReg, arguments, argumentTypes, translator);
	}
}

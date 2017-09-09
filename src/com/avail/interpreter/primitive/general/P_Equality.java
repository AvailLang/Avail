/**
 * P_Equality.java
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
package com.avail.interpreter.primitive.general;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.EnumerationTypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.descriptor.AtomDescriptor.*;
import static com.avail.descriptor.EnumerationTypeDescriptor.*;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Compare for equality. Answer a {@linkplain
 * EnumerationTypeDescriptor#booleanType() boolean}.
 */
public final class P_Equality extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_Equality().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_BasicObject a = args.get(0);
		final AvailObject b = args.get(1);
		return interpreter.primitiveSuccess(objectFromBoolean(a.equals(b)));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		assert argumentTypes.size() == 2;
		final A_Type type1 = argumentTypes.get(0);
		final A_Type type2 = argumentTypes.get(1);

		if (type1.typeIntersection(type2).isBottom())
		{
			// The actual values cannot be equal at runtime.
			return falseType();
		}
		if (type1.isEnumeration()
			&& type1.equals(type2)
			&& type1.instanceCount().equalsInt(1))
		{
			final A_BasicObject value = type1.instances().iterator().next();
			// Because of metacovariance, a meta may actually have many
			// instances.  For instance, tuple's type contains not only tuple,
			// but every subtype of tuple (e.g., string, <>'s type, etc.).
			if (!value.isType())
			{
				// The actual values will have to be equal at runtime.
				return trueType();
			}
		}
		return super.returnTypeGuaranteedByVM(argumentTypes);
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
		final A_Type type1 = registerSet.typeAt(firstReg);
		final A_Type type2 = registerSet.typeAt(secondReg);
		if (type1.typeIntersection(type2).isBottom())
		{
			// The actual values cannot be equal at runtime.
			levelOneNaiveTranslator.moveConstant(falseObject(), resultRegister);
			return;
		}
		if (type1.isEnumeration()
			&& type1.equals(type2)
			&& type1.instanceCount().equalsInt(1))
		{
			final A_BasicObject value = type1.instances().iterator().next();
			// Because of metacovariance, a meta may actually have many
			// instances.  For instance, tuple's type contains not only tuple,
			// but every subtype of tuple (e.g., string, <>'s type, etc.).
			if (!value.isType())
			{
				// The actual values will have to be equal at runtime.
				levelOneNaiveTranslator.moveConstant(
					trueObject(), resultRegister);
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

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ANY.o(),
				ANY.o()),
			booleanType());
	}
}

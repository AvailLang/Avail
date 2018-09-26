/*
 * P_Equality.java
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
package com.avail.interpreter.primitive.general;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.EnumerationTypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.AtomDescriptor.*;
import static com.avail.descriptor.EnumerationTypeDescriptor.*;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
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
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_Equality().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_BasicObject a = interpreter.argument(0);
		final AvailObject b = interpreter.argument(1);
		return interpreter.primitiveSuccess(objectFromBoolean(a.equals(b)));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
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
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
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

	@Override
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		final L2ReadPointerOperand firstReg = arguments.get(0);
		final L2ReadPointerOperand secondReg = arguments.get(1);

		if (firstReg.register() == secondReg.register())
		{
			// A value is being compared to itself, even though we might not
			// know anything specific about what it is.
			callSiteHelper.useAnswer(
				translator.generator.constantRegister(trueObject()));
			return true;
		}

		final A_Type type1 = firstReg.type();
		final A_Type type2 = secondReg.type();
		if (type1.typeIntersection(type2).isBottom())
		{
			// The actual values cannot be equal at runtime.
			callSiteHelper.useAnswer(
				translator.generator.constantRegister(falseObject()));
			return true;
		}
		// Because of metacovariance, a meta may actually have many instances.
		// For instance, tuple's type contains not only tuple, but every subtype
		// of tuple (e.g., string, <>'s type, etc.).
		if (type1.equals(type2)
			&& type1.instanceCount().equalsInt(1)
			&& !type1.isInstanceMeta())
		{
			callSiteHelper.useAnswer(
				translator.generator.constantRegister(trueObject()));
			return true;
		}

		// It's contingent.  Eventually we could turn this into an
		// L2_JUMP_IF_OBJECTS_EQUAL, producing true on one path and false on the
		// other, merging with a phi.  That would introduce an opportunity for
		// code splitting back to here, if subsequent uses of the phi register
		// noticed its origins and cared to do something substantially different
		// in the true and false cases (say dispatching to an if/then/else).
		return super.tryToGenerateSpecialPrimitiveInvocation(
			functionToCallReg,
			rawFunction,
			arguments,
			argumentTypes,
			translator,
			callSiteHelper);
	}
}

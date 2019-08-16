/*
 * P_PushArgument3.java
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
package com.avail.interpreter.primitive.privatehelpers;

import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> The third argument is being returned.
 */
public final class P_PushArgument3 extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_PushArgument3().init(
			-1, SpecialForm, Private, CanInline, CannotFail);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		final AvailObject argument = interpreter.argument(2);
		return interpreter.primitiveSuccess(argument);
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		assert argumentTypes.size() >= 3;
		return argumentTypes.get(2);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		// This primitive is suitable for any three-or-more-argument function.
		// It may seem strange that ⊥ is the return type, but that's to allow
		// functions like [x : map | x] to type-check correctly!  The argument
		// x has type map, so the last expression does also.  If we made this
		// primitive say the result must be any, we would be illegally
		// strengthening it by appending a return type declaration like ": map".
		// However, the L2 translator will have to ignore the primitive block
		// type restriction for this particular primitive.
		return bottom();
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
		// The value is available in the third argument register.  Doesn't even
		// need a move. The translator deals with strengthening separately,
		// through the call return type checks.
		callSiteHelper.useAnswer(arguments.get(2));
		return true;
	}
}

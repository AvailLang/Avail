/**
 * P_PushConstant.java
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
package com.avail.interpreter.primitive.privatehelpers;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1Translator;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> The first literal is being returned.
 * Extract the first literal from the {@linkplain CompiledCodeDescriptor
 * compiled code} that the interpreter has squirreled away for this purpose.
 *
 * <p>This mechanism relies on {@link #tryToGenerateSpecialInvocation(
 * L2ReadPointerOperand, List, List, L1Translator)} always producing specialized
 * L2 code – i.e., a constant move.  Note that {@link Flag#CanInline} normally
 * skips making the actual called function available, so we must be careful to
 * expose it for the customized code generator.</p>
 */
public final class P_PushConstant extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_PushConstant().init(
			-1, SpecialForm, Private, CanInline, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		final A_RawFunction code = stripNull(interpreter.function).code();
		assert code.primitive() == this;
		return interpreter.primitiveSuccess(code.literalAt(1));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		// This primitive is suitable for any block signature.
		return bottom();
	}

	@Override
	public @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator)
	{
		// A function that simply returns a constant can't have any outer
		// variables, so functionToCallReg basically always has a constant
		// function.
		final @Nullable A_BasicObject constantFunction =
			functionToCallReg.constantOrNull();
		if (constantFunction != null)
		{
			// The exact function is known.
			final A_Function strongFunction = (A_Function) constantFunction;
			final A_BasicObject constant = strongFunction.code().literalAt(1);
			return translator.constantRegister(constant);
		}
		// The exact function isn't known here, somehow.  And yet we're here, so
		// somehow it's known that the function register must hold a
		// constant-valued function.  Its invocation can't possibly be inlined,
		// so the Interpreter#function will be set up correctly in the call.
		return super.tryToGenerateSpecialInvocation(
			functionToCallReg, arguments, argumentTypes, translator);
	}
}

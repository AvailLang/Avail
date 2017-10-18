/**
 * P_GetGlobalVariableValue.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AvailObject;
import com.avail.exceptions.VariableGetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_GET_VARIABLE;
import com.avail.optimizer.L1Translator;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> A global variable's value is being returned.
 */
public final class P_GetGlobalVariableValue extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_GetGlobalVariableValue().init(
			1, SpecialReturnGlobalValue, CanInline, Private, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		final A_RawFunction code = stripNull(interpreter.function).code();
		final A_Variable literalVariable = code.literalAt(1);
		try
		{
			return interpreter.primitiveSuccess(literalVariable.getValue());
		}
		catch (final VariableGetException e)
		{
			assert false : "A write-only variable must be assigned!";
			throw new RuntimeException(e);
		}
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		// The L2Translator has a special case for invocations of this
		// primitive, so improving this bound would be entirely futile.
		return ANY.o();
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		// This primitive is suitable for any function with any as the return
		// type.  We can't express that yet, so we allow any function.
		return bottom();
	}

	@Override
	public @Nullable L2ReadPointerOperand tryToGenerateSpecialInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator)
	{
		final @Nullable A_Function function =
			(A_Function) functionToCallReg.constantOrNull();
		if (function == null)
		{
			// We have to know the specific function to know what variable to
			// read from, since it's the first literal.
			return null;
		}
		final A_Variable variable = function.code().literalAt(1);
		if (variable.isInitializedWriteOnceVariable())
		{
			// The variable is permanently set to this value.
			return translator.constantRegister(variable.getValue());
		}
		final L2WritePointerOperand valueRegisterWriter =
			translator.newObjectRegisterWriter(
				variable.kind().readType(),
				null);
		translator.emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			translator.constantRegister(variable),
			valueRegisterWriter);
		return valueRegisterWriter.read();
	}
}

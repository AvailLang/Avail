/*
 * L2ReadBoxedOperand.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;

import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.BOXED;

/**
 * An {@code L2ReadBoxedOperand} is an operand of type {@link
 * L2OperandType#READ_BOXED}. It holds the actual {@link L2BoxedRegister}
 * that is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2ReadBoxedOperand
extends L2ReadOperand<L2BoxedRegister>
{
	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READ_BOXED;
	}

	/**
	 * Construct a new {@code L2ReadBoxedOperand} for the specified {@link
	 * L2SemanticValue} and {@link TypeRestriction}, using information from the
	 * given {@link L2ValueManifest}.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} that is being read when an {@link
	 *        L2Instruction} uses this {@link L2Operand}.
	 * @param restriction
	 *        The {@link TypeRestriction} to constrain this particular read.
	 *        This restriction has been guaranteed by the VM at the point where
	 *        this operand's instruction occurs.
	 * @param manifest
	 *        The {@link L2ValueManifest} from which to extract a suitable
	 *        definition instruction.
	 */
	public L2ReadBoxedOperand (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction,
		final L2ValueManifest manifest)
	{
		super(
			semanticValue,
			restriction,
			manifest.getDefinition(semanticValue, BOXED));
		assert restriction.isBoxed();
	}

	/**
	 * Construct a new {@code L2ReadBoxedOperand} with an explicit definition
	 * register {@link L2WriteBoxedOperand}.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} that is being read when an
	 *        {@link L2Instruction} uses this {@link L2Operand}.
	 * @param restriction
	 *        The {@link TypeRestriction} that bounds the value being read.
	 * @param register
	 *        The {@link L2BoxedRegister} being read by this operand.
	 */
	public L2ReadBoxedOperand (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction,
		final L2BoxedRegister register)
	{
		super(semanticValue, restriction, register);
	}

	@Override
	public L2ReadBoxedOperand copyForSemanticValue (
		final L2SemanticValue newSemanticValue)
	{
		return new L2ReadBoxedOperand(
			newSemanticValue, restriction(), register());
	}

	@Override
	public L2ReadBoxedOperand copyForRegister (
		final L2Register newRegister)
	{
		return new L2ReadBoxedOperand(
			semanticValue(), restriction(), (L2BoxedRegister) newRegister);
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public RegisterKind registerKind ()
	{
		return BOXED;
	}

	/**
	 * See if we can determine the exact type required as the first argument of
	 * the function produced by this read.  If the exact type is known, answer
	 * it, otherwise {@code null}.
	 *
	 * @return Either {@code null} or an exact {@link A_Type} to compare some
	 *         value against in order to determine whether the one-argument
	 *         function will accept the given argument.
	 */
	public @Nullable A_Type exactSoleArgumentType ()
	{
		final @Nullable A_Function constantFunction = constantOrNull();
		if (constantFunction != null)
		{
			// Function is a constant.
			final A_Type functionType = constantFunction.code().functionType();
			return functionType.argsTupleType().typeAtIndex(1);
		}
		final L2Instruction originOfFunction = definitionSkippingMoves(true);
		if (originOfFunction.operation() == L2_MOVE_CONSTANT.boxed)
		{
			final A_Function function =
				L2_MOVE_CONSTANT.constantOf(originOfFunction);
			final A_Type functionType = function.code().functionType();
			return functionType.argsTupleType().typeAtIndex(1);
		}
		if (originOfFunction.operation() == L2_CREATE_FUNCTION.instance)
		{
			final A_RawFunction code =
				L2_CREATE_FUNCTION.constantRawFunctionOf(originOfFunction);
			final A_Type functionType = code.functionType();
			return functionType.argsTupleType().typeAtIndex(1);
		}
		return null;
	}
}

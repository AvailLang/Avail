/*
 * L2ReadFloatOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.L2SemanticValue;

import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.FLOAT;

/**
 * An {@code L2ReadFloatOperand} is an operand of type {@link
 * L2OperandType#READ_FLOAT}. It holds the actual {@link L2FloatRegister} that
 * is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2ReadFloatOperand
extends L2ReadOperand<L2FloatRegister>
{
	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READ_FLOAT;
	}

	/**
	 * Construct a new {@code L2ReadFloatOperand} for the specified {@link
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
	public L2ReadFloatOperand (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction,
		final L2ValueManifest manifest)
	{
		super(
			semanticValue,
			restriction,
			manifest.getDefinition(semanticValue, FLOAT));
		assert restriction.isUnboxedFloat();
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public RegisterKind registerKind ()
	{
		return FLOAT;
	}

	@Override
	public L2_MOVE<L2FloatRegister> phiMoveOperation ()
	{
		return L2_MOVE.unboxedFloat;
	}
}

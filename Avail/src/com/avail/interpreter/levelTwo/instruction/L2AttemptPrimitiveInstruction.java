/**
 * L2AttemptPrimitiveInstruction.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.levelTwo.instruction;

import static com.avail.interpreter.levelTwo.L2Operation.L2_doAttemptPrimitive_withArguments_result_failure_ifFail_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2AttemptPrimitiveInstruction} attempts to execute the {@linkplain
 * Primitive primitive} with the specified number.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2AttemptPrimitiveInstruction
extends L2Instruction
{
	/** The {@linkplain Primitive primitive} number. */
	private final int primitiveNumber;

	/**
	 * The {@linkplain L2RegisterVector arguments} to the {@linkplain Primitive
	 * primitive}.
	 */
	private final @NotNull L2RegisterVector primitiveArguments;

	/**
	 * The {@linkplain L2ObjectRegister register} to which the result of the
	 * {@linkplain Primitive primitive} will be written in the event of success.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * The {@linkplain L2ObjectRegister register} to which the failure output
	 * of the {@linkplain Primitive primitive} will be written in the event of
	 * failure.
	 */
	private final @NotNull L2ObjectRegister failureValueRegister;

	/**
	 * The {@linkplain L2LabelInstruction target} to which execution should jump
	 * in the event that the {@linkplain Primitive primitive} fails.
	 */
	private final @NotNull L2LabelInstruction failureLabel;

	/**
	 * Construct a new {@link L2AttemptPrimitiveInstruction}.
	 *
	 * @param primitiveNumber
	 *            The {@linkplain Primitive primitive} number.
	 * @param primitiveArguments
	 *            The {@linkplain L2RegisterVector arguments} to the {@linkplain
	 *            Primitive primitive}.
	 * @param destinationRegister
	 *            The {@linkplain L2ObjectRegister register} to which the result
	 *            of the {@linkplain Primitive primitive} will be written in the
	 *            event of success.
	 * @param failureValueRegister
	 *            The {@linkplain L2ObjectRegister register} to which the
	 *            failure output of the {@linkplain Primitive primitive} will be
	 *            written in the event of failure.
	 * @param failureLabel
	 *            The {@linkplain L2LabelInstruction target} to which execution
	 *            should jump in the event that the {@linkplain Primitive
	 *            primitive} fails.
	 */
	public L2AttemptPrimitiveInstruction (
			final int primitiveNumber,
			final L2RegisterVector primitiveArguments,
			final L2ObjectRegister destinationRegister,
			final L2ObjectRegister failureValueRegister,
			final L2LabelInstruction failureLabel)
	{
		this.primitiveNumber = primitiveNumber;
		this.primitiveArguments = primitiveArguments;
		this.destinationRegister = destinationRegister;
		this.failureValueRegister = failureValueRegister;
		this.failureLabel = failureLabel;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		List<L2Register> result = new ArrayList<L2Register>(
			primitiveArguments.registers().size());
		result.addAll(primitiveArguments.registers());
		return result;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Since a call can clear all registers, we could try to list all
	 * registers as destinations. Instead, we treat calls as the ends of the
	 * basic blocks during flow analysis.</p>
	 */
	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		List<L2Register> result = new ArrayList<L2Register>(2);
		result.add(destinationRegister);
		result.add(failureValueRegister);
		return result;
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitL2Operation(
			L2_doAttemptPrimitive_withArguments_result_failure_ifFail_);
		codeGenerator.emitPrimitiveNumber(primitiveNumber);
		codeGenerator.emitVector(primitiveArguments);
		codeGenerator.emitObjectRegister(destinationRegister);
		codeGenerator.emitObjectRegister(failureValueRegister);
		codeGenerator.emitWordcodeOffsetOf(failureLabel);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.removeTypeForRegister(destinationRegister);
		translator.removeConstantForRegister(destinationRegister);
	}
}

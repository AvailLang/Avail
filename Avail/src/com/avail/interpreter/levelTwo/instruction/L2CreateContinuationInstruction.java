/**
 * interpreter/levelTwo/instruction/L2CreateContinuationInstruction.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2CreateContinuationInstruction} creates a new {@linkplain
 * ContinuationDescriptor continuation} from a calling continuation, a
 * {@linkplain ClosureDescriptor closure}, a level one program counter and stack
 * pointer, some slot information, and a level two program counter.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2CreateContinuationInstruction
extends L2Instruction
{
	/**
	 * The {@linkplain L2ObjectRegister register} containing the calling
	 * {@linkplain ContinuationDescriptor continuation}.
	 */
	private final @NotNull L2ObjectRegister caller;

	/**
	 * The {@linkplain L2ObjectRegister register} containing the {@linkplain
	 * ClosureDescriptor closure}.
	 */
	private final @NotNull L2ObjectRegister closure;

	/** The level one Avail program counter. */
	private final int pc;

	/** The level one Avail stack pointer. */
	private final int stackp;

	/** The number of slots to reserve. */
	private final int size;

	/** The contents of the slots. */
	private final @NotNull L2RegisterVector slotsVector;

	/**
	 * The program counter within the executing {@linkplain L2ChunkDescriptor
	 * chunk} at which execution should resume following a return from the new
	 * {@linkplain ContinuationDescriptor continuation}.
	 */
	private final @NotNull L2LabelInstruction continuationLabel;

	/**
	 * The {@linkplain L2ObjectRegister register} into which the new {@linkplain
	 * ContinuationDescriptor continuation} will be written.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2CreateContinuationInstruction}.
	 *
	 * @param caller
	 *        The {@linkplain L2ObjectRegister register} containing the calling
	 *        {@linkplain ContinuationDescriptor continuation}.
	 * @param closure
	 *        The {@linkplain L2ObjectRegister register} containing the
	 *        {@linkplain ClosureDescriptor closure}.
	 * @param pc
	 *        The level one Avail program counter.
	 * @param stackp
	 *        The level one Avail stack pointer.
	 * @param size
	 *        The number of slots to reserve.
	 * @param slotsVector
	 *        The contents of the slots.
	 * @param continuationLabel
	 *        The program counter within the executing {@linkplain
	 *        L2ChunkDescriptor chunk} at which execution should resume
	 *        following a return from the new {@linkplain ContinuationDescriptor
	 *        continuation}.
	 * @param destinationRegister
	 *        The {@linkplain L2ObjectRegister register} into which the new
	 *        {@linkplain ContinuationDescriptor continuation} will be written.
	 */
	public L2CreateContinuationInstruction  (
		final @NotNull L2ObjectRegister caller,
		final @NotNull L2ObjectRegister closure,
		final int pc,
		final int stackp,
		final int size,
		final @NotNull L2RegisterVector slotsVector,
		final @NotNull L2LabelInstruction continuationLabel,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.caller = caller;
		this.closure = closure;
		this.pc = pc;
		this.stackp = stackp;
		this.size = size;
		this.slotsVector = slotsVector;
		this.continuationLabel = continuationLabel;
		this.destinationRegister = destinationRegister;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		final List<L2Register> result = new ArrayList<L2Register>(
			2 + slotsVector.registers().size());
		result.add(caller);
		result.add(closure);
		result.addAll(slotsVector.registers());
		return result;
	}

	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.<L2Register>singletonList(destinationRegister);
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitWord(
			L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_.ordinal());
		codeGenerator.emitObjectRegister(caller);
		codeGenerator.emitObjectRegister(closure);
		codeGenerator.emitWord(pc);
		codeGenerator.emitWord(stackp);
		codeGenerator.emitWord(size);
		codeGenerator.emitVector(slotsVector);
		codeGenerator.emitWord(continuationLabel.offset());
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.registerTypeAtPut(
			destinationRegister,
			ContinuationTypeDescriptor.forClosureType(
				translator.code().closureType()));
		translator.removeConstantForRegister(destinationRegister);
	}
}

/**
 * interpreter/levelTwo/instruction/L2Instruction.java
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

import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2Register;

/**
 * {@code L2Instruction} is the foundation for all instructions understood by
 * the {@linkplain L2Interpreter level two Avail interpreter}. These
 * instructions are model objects generated and manipulated by the {@linkplain
 * L2Translator translator} and the {@linkplain L2CodeGenerator code generator}.
 *
 * <p>It implements a mechanism for establishing and interrogating the position
 * of the instruction within its {@linkplain L2ChunkDescriptor chunk}'s
 * {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode stream}. It
 * defines responsibilities for interrogating the source and destination
 * {@linkplain L2Register registers} used by the instruction and emitting the
 * instruction on a code generator. Lastly it specifies an entry point for
 * describing type and constant value propagation to a translator.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public abstract class L2Instruction
{
	/**
	 * The position of the {@linkplain L2Instruction instruction} in its
	 * {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode stream}.
	 */
	private int offset = -1;

	/**
	 * Answer the position of the {@linkplain L2Instruction instruction} in its
	 * {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode stream}.
	 *
	 * @return The position of the {@linkplain L2Instruction instruction} in its
	 *         {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode
	 *         stream}.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Set the final position of the {@linkplain L2Instruction instruction}
	 * within its {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode
	 * stream}.
	 *
	 * @param offset
	 *        The final position of the {@linkplain L2Instruction instruction}
	 *        within its {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES
	 *        wordcode stream}.
	 */
	public void setOffset (final int offset)
	{
		this.offset = offset;
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain L2Register registers}
	 * read by this {@linkplain L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register registers}.
	 */
	public abstract @NotNull List<L2Register> sourceRegisters ();

	/**
	 * Answer the {@linkplain List list} of {@linkplain L2Register registers}
	 * modified by this {@linkplain L2Instruction instruction}.
	 *
	 * @return The destination {@linkplain L2Register registers}.
	 */
	public abstract @NotNull List<L2Register> destinationRegisters ();

	/**
	 * Emit this {@linkplain L2Instruction instruction} to the specified
	 * {@linkplain L2CodeGenerator code generator}.
	 *
	 * @param codeGenerator A {@linkplain L2CodeGenerator code generator}.
	 */
	public abstract void emitOn (
		final @NotNull L2CodeGenerator codeGenerator);

	/**
	 * Propagate {@linkplain TypeDescriptor type} and constant value information
	 * from source {@linkplain L2Register registers} to the destination
	 * registers.
	 *
	 * @param translator The {@linkplain L2Translator translator}.
	 */
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		// Be conservative by default: Do nothing.
	}
}

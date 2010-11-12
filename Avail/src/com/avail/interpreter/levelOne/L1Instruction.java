/**
 * interpreter/levelOne/L1Instruction.java
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

package com.avail.interpreter.levelOne;

import com.avail.annotations.NotNull;

/**
 * {@code L1Instruction} combines an {@link L1Operation} with the operands.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class L1Instruction
{
	/** The {@link L1Operation}. */
	private final @NotNull L1Operation operation;
	
	/** The operands. */
	private final @NotNull int [] operands;

	/**
	 * Answer the {@link L1Operation}.
	 * 
	 * @return The {@link L1Operation}.
	 */
	public L1Operation operation ()
	{
		return operation;
	}

	/**
	 * Answer the operands.
	 * 
	 * @return The operands.
	 */
	public int [] operands ()
	{
		return operands;
	}
	
	/**
	 * Construct an {@link L1Instruction}.
	 * 
	 * @param operation The {@link L1Operation}.
	 * @param operands The operands.
	 */
	public L1Instruction (
		final @NotNull L1Operation operation,
		final int ... operands)
	{
		assert operation.operandTypes().length == operands.length;

		this.operation = operation;
		this.operands = operands;
	}
}
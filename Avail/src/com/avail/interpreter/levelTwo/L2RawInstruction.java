/**
 * L2RawInstruction.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.levelTwo;


/**
 * An {@code L2RawInstruction} is a combination of an {@link L2Operation} and an
 * array of {@code int}s encoding its operands.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2RawInstruction
{
	/**
	 * The {@link L2Operation} invoked by this instruction.
	 */
	private final L2Operation operation;

	/**
	 * An array of {@code int}s encoding the instruction's operands.
	 */
	private final int [] operands;

	/**
	 * Answer this instruction's {@link L2Operation}.
	 *
	 * @return An {@code L2Operation}.
	 */
	public L2Operation operation ()
	{
		return operation;
	}

	/**
	 * Answer this instruction's {@code int}-encoded operands.  They should
	 * correspond in purpose to the operation's {@link
	 * L2Operation#operandTypes() operand types}.
	 *
	 * @return An array of {@code int}s encoding the operands.
	 */
	public int [] operands ()
	{
		return operands;
	}

	/**
	 * Construct a new {@link L2RawInstruction} from the {@link L2Operation} and
	 * the array of {@code int}-encoded operands.
	 *
	 * @param operation The {@code L2Operation} to use.
	 * @param operands Its operands, encoded as {@code int}s.
	 */
	public L2RawInstruction (
		final L2Operation operation,
		final int ... operands)
	{
		assert operation.operandTypes().length == operands.length;

		this.operation = operation;
		this.operands = operands;
	}
}
/*
 * AvailPushVariable.java
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

package com.avail.compiler.instruction;

import com.avail.descriptor.*;

/**
 * Push a variable of some sort.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AvailPushVariable extends AvailInstructionWithIndex
{
	/**
	 * Whether this instruction is the last use of its variable.
	 */
	protected boolean isLastAccess = false;

	/**
	 * Set the flag indicating whether this instruction is the last use of its
	 * variable.
	 *
	 * @param newFlag The new value for {@link #isLastAccess}.
	 */
	public void isLastAccess (
		final boolean newFlag)
	{
		this.isLastAccess = newFlag;
	}

	/**
	 * Answer whether this instruction is the last use of its variable.
	 *
	 * @return Whether this is a last use.
	 */
	public boolean isLastAccess ()
	{
		return isLastAccess;
	}

	/**
	 * Construct a new {@code AvailPushVariable}.
	 *
	 * @param relevantTokens
	 *        The {@link A_Tuple} of {@link A_Token}s that are associated with
	 *        this instruction.
	 * @param variableIndex
	 *        The index that the variable will occupy at runtime, either within
	 *        a {@linkplain ContinuationDescriptor continuation} for arguments
	 *        and locals, or in the outer variables of a {@linkplain
	 *        FunctionDescriptor function}, or as a literal stored directly in
	 *        {@linkplain CompiledCodeDescriptor compiled code}.
	 */
	public AvailPushVariable (
		final A_Tuple relevantTokens,
		final int variableIndex)
	{
		super(relevantTokens, variableIndex);
	}
}

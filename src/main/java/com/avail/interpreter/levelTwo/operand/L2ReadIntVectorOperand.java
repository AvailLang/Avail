/*
 * L2ReadIntVectorOperand.java
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

import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.register.L2IntRegister;

import java.util.List;

import static com.avail.utility.Casts.cast;
import static java.util.stream.Collectors.toList;

/**
 * An {@code L2ReadIntVectorOperand} is an operand of type {@link
 * L2OperandType#READ_INT_VECTOR}. It holds a {@link List} of {@link
 * L2ReadIntOperand}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2ReadIntVectorOperand
extends L2ReadVectorOperand<L2ReadIntOperand, L2IntRegister>
{
	/**
	 * Construct a new {@code L2ReadIntVectorOperand} with the specified {@link
	 * List} of {@link L2ReadIntOperand}s.
	 *
	 * @param elements
	 *        The list of {@link L2ReadIntOperand}s.
	 */
	public L2ReadIntVectorOperand (
		final List<L2ReadIntOperand> elements)
	{
		super(elements);
	}

	@Override
	public L2ReadIntVectorOperand clone ()
	{
		return new L2ReadIntVectorOperand(
			elements.stream()
				.<L2ReadIntOperand>map(read -> cast(read.clone()))
				.collect(toList()));
	}

	@Override
	public L2ReadIntVectorOperand clone (
		final List<L2ReadIntOperand> replacementElements)
	{
		return new L2ReadIntVectorOperand(replacementElements);
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READ_INT_VECTOR;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}
}

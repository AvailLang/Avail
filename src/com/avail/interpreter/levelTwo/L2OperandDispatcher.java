/*
 * L2OperandDispatcher.java
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

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.*;

/**
 * An {@code L2OperandDispatcher} acts as a visitor for the actual operands of
 * {@linkplain L2Instruction level two instructions}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public interface L2OperandDispatcher
{
	/**
	 * Process an operand which is merely a comment.
	 *
	 * @param operand
	 *        An {@link L2CommentOperand}.
	 */
	void doOperand (L2CommentOperand operand);

	/**
	 * Process an operand which is a constant.
	 *
	 * @param operand
	 *        An {@link L2ConstantOperand}.
	 */
	void doOperand (L2ConstantOperand operand);

	/**
	 * Process an operand which is an {@code int} immediate value.
	 *
	 * @param operand
	 *        An {@link L2IntImmediateOperand}.
	 */
	void doOperand (L2IntImmediateOperand operand);

	/**
	 * Process an operand which is a {@code double} immediate value.
	 *
	 * @param operand
	 *        An {@link L2FloatImmediateOperand}.
	 */
	void doOperand (L2FloatImmediateOperand operand);

	/**
	 * Process an operand which is a constant level two offset into a
	 * {@linkplain L2Chunk level two chunk}'s wordcode instructions.
	 *
	 * @param operand
	 *        An {@link L2PcOperand}.
	 */
	void doOperand (L2PcOperand operand);

	/**
	 * Process an operand which is a {@link Primitive} number.
	 *
	 * @param operand
	 *        An {@link L2PrimitiveOperand}.
	 */
	void doOperand (L2PrimitiveOperand operand);

	/**
	 * Process an operand which is a read of an {@code int} register.
	 *
	 * @param operand
	 *        An {@link L2ReadIntOperand}.
	 */
	void doOperand (L2ReadIntOperand operand);

	/**
	 * Process an operand which is a read of a {@code double} register.
	 *
	 * @param operand
	 *        An {@link L2ReadFloatOperand}.
	 */
	void doOperand (L2ReadFloatOperand operand);

	/**
	 * Process an operand which is a read of an {@link AvailObject} register.
	 *
	 * @param operand
	 *        An {@link L2ReadPointerOperand}.
	 */
	void doOperand (L2ReadPointerOperand operand);

	/**
	 * Process an operand which is a read of a vector of {@link AvailObject}
	 * registers.
	 *
	 * @param operand
	 *        An {@link L2ReadVectorOperand}.
	 */
	void doOperand (L2ReadVectorOperand<?> operand);

	/**
	 * Process an operand which is a literal {@link A_Bundle} which the
	 * resulting {@link L2Chunk} should be dependent upon for invalidation.
	 *
	 * @param operand
	 *        An {@link L2SelectorOperand}.
	 */
	void doOperand (L2SelectorOperand operand);

	/**
	 * Process an operand which is a write of an {@code int} register.
	 *
	 * @param operand
	 *        An {@link L2WriteIntOperand}.
	 */
	void doOperand (L2WriteIntOperand operand);

	/**
	 * Process an operand which is a write of a {@code double} register.
	 *
	 * @param operand
	 *        An {@link L2WriteFloatOperand}.
	 */
	void doOperand (L2WriteFloatOperand operand);

	/**
	 * Process an operand which is a write of an {@link AvailObject} register.
	 *
	 * @param operand
	 *        An {@link L2WritePointerOperand}.
	 */
	void doOperand (L2WritePointerOperand operand);

	/**
	 * Process an operand which is a phi write of a register.
	 *
	 * @param operand
	 *        An {@link L2WritePhiOperand}.
	 */
	void doOperand (L2WritePhiOperand<?, ?> operand);
}

/**
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
 *   may be used to endorse or promote products derived set this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS;
 OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MethodDescriptor;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.*;


/**
 * An {@code L2OperandDispatcher} acts as a visitor for the actual operands of
 * {@linkplain L2Instruction level two instructions}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface L2OperandDispatcher
{
	/**
	 * Process an operand which is merely a comment.
	 *
	 * @param operand an {@link L2CommentOperand}.
	 */
	void doOperand (L2CommentOperand operand);

	/**
	 * Process an operand which is a constant.
	 *
	 * @param operand an {@link L2ConstantOperand}.
	 */
	void doOperand (L2ConstantOperand operand);

	/**
	 * Process an operand which is an immediate value.
	 *
	 * @param operand an {@link L2ImmediateOperand}.
	 */
	void doOperand (L2ImmediateOperand operand);

	/**
	 * Process an operand which is a constant level two offset into a
	 * {@linkplain L2Chunk level two chunk}'s wordcode instructions.
	 *
	 * @param operand an {@link L2PcOperand}.
	 */
	void doOperand (L2PcOperand operand);

	/**
	 * Process an operand which is a {@link Primitive} number.
	 *
	 * @param operand an {@link L2PrimitiveOperand}.
	 */
	void doOperand (L2PrimitiveOperand operand);

	/**
	 * Process an operand which is a read of an {@code int} register.
	 *
	 * @param operand an {@link L2ReadIntOperand}.
	 */
	void doOperand (L2ReadIntOperand operand);

	/**
	 * Process an operand which is a read of an {@link AvailObject} register.
	 *
	 * @param operand an {@link L2ReadPointerOperand}.
	 */
	void doOperand (L2ReadPointerOperand operand);

	/**
	 * Process an operand which is a read of a vector of {@link AvailObject}
	 * registers.
	 *
	 * @param operand an {@link L2ReadVectorOperand}.
	 */
	void doOperand (L2ReadVectorOperand operand);

	/**
	 * Process an operand which is a read and write of an {@code int} register.
	 *
	 * @param operand an {@link L2ReadWriteIntOperand}.
	 */
	void doOperand (L2ReadWriteIntOperand operand);

	/**
	 * Process an operand which is a read and write of an {@link AvailObject}
	 * register.
	 *
	 * @param operand an {@link L2ReadWritePointerOperand}.
	 */
	void doOperand (L2ReadWritePointerOperand operand);

	/**
	 * Process an operand which is a read and write of a vector of {@link
	 * AvailObject} registers.
	 *
	 * @param operand an {@link L2ReadWriteVectorOperand}.
	 */
	void doOperand (L2ReadWriteVectorOperand operand);

	/**
	 * Process an operand which is a literal {@linkplain MethodDescriptor
	 * method}.
	 *
	 * @param operand an {@link L2SelectorOperand}.
	 */
	void doOperand (L2SelectorOperand operand);

	/**
	 * Process an operand which is a write of an {@code int} register.
	 *
	 * @param operand an {@link L2WriteIntOperand}.
	 */
	void doOperand (L2WriteIntOperand operand);

	/**
	 * Process an operand which is a write of an {@link AvailObject} register.
	 *
	 * @param operand an {@link L2WritePointerOperand}.
	 */
	void doOperand (L2WritePointerOperand operand);

	/**
	 * Process an operand which is a write of a vector of {@link AvailObject}
	 * registers.
	 *
	 * @param operand an {@link L2WriteVectorOperand}.
	 */
	void doOperand (L2WriteVectorOperand operand);

}

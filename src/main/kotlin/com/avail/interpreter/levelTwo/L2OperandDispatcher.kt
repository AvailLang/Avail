/*
 * L2OperandDispatcher.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo

import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelTwo.operand.L2CommentOperand
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.interpreter.levelTwo.register.L2IntRegister

/**
 * An `L2OperandDispatcher` acts as a visitor for the actual operands of
 * [level&#32;two&#32;instructions][L2Instruction].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface L2OperandDispatcher
{
	/**
	 * Process an operand which is merely a comment.
	 *
	 * @param operand
	 *   An [L2CommentOperand].
	 */
	fun doOperand(operand: L2CommentOperand)

	/**
	 * Process an operand which is a constant.
	 *
	 * @param operand
	 *   An [L2ConstantOperand].
	 */
	fun doOperand(operand: L2ConstantOperand)

	/**
	 * Process an operand which is an [Int] immediate value.
	 *
	 * @param operand
	 *   An [L2IntImmediateOperand].
	 */
	fun doOperand(operand: L2IntImmediateOperand)

	/**
	 * Process an operand which is a `double` immediate value.
	 *
	 * @param operand
	 *   An [L2FloatImmediateOperand].
	 */
	fun doOperand(operand: L2FloatImmediateOperand)

	/**
	 * Process an operand which is a constant level two offset into a
	 * [level&#32;two&#32;chunk][L2Chunk]'s [L2Instruction] sequence.
	 *
	 * @param operand
	 *   An [L2PcOperand].
	 */
	fun doOperand(operand: L2PcOperand)

	/**
	 * Process an operand which is a [Primitive] number.
	 *
	 * @param operand
	 *   An [L2PrimitiveOperand].
	 */
	fun doOperand(operand: L2PrimitiveOperand)

	/**
	 * Process an operand which is a read of an [Int] register.
	 *
	 * @param operand
	 *   An [L2ReadIntOperand].
	 */
	fun doOperand(operand: L2ReadIntOperand)

	/**
	 * Process an operand which is a read of a `double` register.
	 *
	 * @param operand
	 *   An [L2ReadFloatOperand].
	 */
	fun doOperand(operand: L2ReadFloatOperand)

	/**
	 * Process an operand which is a read of an [AvailObject] register.
	 *
	 * @param operand
	 *   An [L2ReadBoxedOperand].
	 */
	fun doOperand(operand: L2ReadBoxedOperand)

	/**
	 * Process an operand which is a read of a vector of [L2BoxedRegister]s.
	 *
	 * @param operand
	 *   An [L2ReadBoxedVectorOperand].
	 */
	fun doOperand(operand: L2ReadBoxedVectorOperand)

	/**
	 * Process an operand which is a read of a vector of [L2IntRegister]s.
	 *
	 * @param operand
	 *   An [L2ReadIntVectorOperand].
	 */
	fun doOperand(operand: L2ReadIntVectorOperand)

	/**
	 * Process an operand which is a read of a vector of [L2FloatRegister]s.
	 *
	 * @param operand
	 *   An [L2ReadFloatVectorOperand].
	 */
	fun doOperand(operand: L2ReadFloatVectorOperand)

	/**
	 * Process an operand which is a literal [A_Bundle] which the resulting
	 * [L2Chunk] should be dependent upon for invalidation.
	 *
	 * @param operand
	 *   An [L2SelectorOperand].
	 */
	fun doOperand(operand: L2SelectorOperand)

	/**
	 * Process an operand which is a write of an [Int] register.
	 *
	 * @param operand
	 *   An [L2WriteIntOperand].
	 */
	fun doOperand(operand: L2WriteIntOperand)

	/**
	 * Process an operand which is a write of a `double` register.
	 *
	 * @param operand
	 *  An [L2WriteFloatOperand].
	 */
	fun doOperand(operand: L2WriteFloatOperand)

	/**
	 * Process an operand which is a write of an [AvailObject] register.
	 *
	 * @param operand
	 *   An [L2WriteBoxedOperand].
	 */
	fun doOperand(operand: L2WriteBoxedOperand)
}

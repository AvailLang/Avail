/*
 * L2ReadFloatVectorOperand.kt
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
package com.avail.interpreter.levelTwo.operand

import com.avail.interpreter.levelTwo.L2OperandDispatcher
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.utility.cast

/**
 * An `L2ReadFloatVectorOperand` is an operand of type
 * [L2OperandType.READ_FLOAT_VECTOR]. It holds a [List] of
 * [L2ReadFloatOperand]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `L2ReadFloatVectorOperand` with the specified [List] of
 * [L2ReadFloatOperand]s.
 *
 * @param elements
 *   The list of [L2ReadFloatOperand]s.
 */
class L2ReadFloatVectorOperand constructor(
		elements: List<L2ReadFloatOperand>)
	: L2ReadVectorOperand<L2FloatRegister, L2ReadFloatOperand>(elements)
{
	override fun clone(): L2ReadFloatVectorOperand =
		L2ReadFloatVectorOperand(
			// Requires explicit parameter typing
			elements.map<L2ReadFloatOperand, L2ReadFloatOperand> {
				it.clone().cast()
			})

	override fun clone(replacementElements: List<L2ReadFloatOperand>) =
		L2ReadFloatVectorOperand(replacementElements)

	override fun operandType(): L2OperandType = L2OperandType.READ_FLOAT_VECTOR

	override fun dispatchOperand(dispatcher: L2OperandDispatcher)
	{
		dispatcher.doOperand(this)
	}
}

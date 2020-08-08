/*
 * L2ConstantOperand.kt
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.types.CompiledCodeTypeDescriptor
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import com.avail.interpreter.levelOne.L1Decompiler.Companion.decompile
import com.avail.interpreter.levelTwo.L2OperandDispatcher
import com.avail.interpreter.levelTwo.L2OperandType

/**
 * An `L2ConstantOperand` is an operand of type [L2OperandType.CONSTANT].  It
 * also holds the actual [AvailObject] that is the constant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `L2ConstantOperand` with the specified
 * [constant][AvailObject].
 *
 * @param constant
 *   The constant value.
 */
class L2ConstantOperand(constant: A_BasicObject) : L2Operand()
{
	/**
	 * The actual constant value.
	 */
	@JvmField
	val constant: AvailObject = constant.makeShared()

	override fun operandType(): L2OperandType = L2OperandType.CONSTANT

	override fun dispatchOperand(dispatcher: L2OperandDispatcher)
	{
		dispatcher.doOperand(this)
	}

	override fun appendTo(builder: StringBuilder) =
		with(builder)
		{
			append("$(")
			if (constant.isInstanceOf(mostGeneralCompiledCodeType()))
			{
				append(decompile(constant))
			}
			else
			{
				append(constant)
			}
			append(")")
			Unit
		}
}

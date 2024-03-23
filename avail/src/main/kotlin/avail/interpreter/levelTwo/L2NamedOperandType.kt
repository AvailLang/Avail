/*
 * L2NamedOperandType.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo

import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION

/**
 * An `L2NamedOperandType` is used to specify both an [L2OperandType] and a
 * [String] naming its purpose with respect to some [L2Operation].  This
 * effectively allows operations to declare named operands, increasing the
 * descriptiveness of the level two instruction set. The names are not used in
 * any way at runtime.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property operandType
 *   The [L2OperandType] that the receiver decorates.
 * @property name
 *   The [String] that names the receiver within an [L2Operation].
 * @property purpose
 *   The [Purpose] that best describes the [L2NamedOperandType], if any.
 *
 * @constructor
 * Construct a new `L2NamedOperandType`.
 *
 * @param operandType
 *   The [L2OperandType] to wrap.
 * @param name
 *   The name of this operand.
 * @param purpose
 *   The [Purpose] that best describes the [L2NamedOperandType], if any.
 */
class L2NamedOperandType internal constructor(
	val operandType: L2OperandType,
	val name: String,
	val purpose: Purpose?)
{
	/**
	 * Answer the [L2OperandType] that this decorates.
	 *
	 * @return
	 *   The L2OperandType.
	 */
	fun operandType(): L2OperandType
	{
		return operandType
	}

	/**
	 * Answer the [String] that names the receiver.
	 *
	 * @return
	 *   The receiver's name.
	 */
	fun name(): String
	{
		return name
	}

	/**
	 * A `Purpose` specifies additional semantic meaning for an
	 * [L2NamedOperandType].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	enum class Purpose
	{
		/**
		 * Indicates that a [PC][L2OperandType.PC] codes for a successful
		 * operation.
		 */
		SUCCESS,

		/**
		 * Indicates that a [PC][L2OperandType.PC] codes for a failed operation.
		 * This represents ordinary failure, not "catastrophic" failure which
		 * requires an off-ramp.
		 */
		FAILURE,

		/**
		 * Indicates that a [PC][L2OperandType.PC] codes for an off-ramp.
		 */
		OFF_RAMP,

		/**
		 * Indicates that a [PC][L2OperandType] codes for an on-ramp.
		 */
		ON_RAMP,

		/**
		 * This target address is not directly used here, but is converted to an
		 * integer constant so it can be passed elsewhere, typically to an
		 * [L2_CREATE_CONTINUATION].
		 */
		REFERENCED_AS_INT
	}

	override fun toString(): String
	{
		return operandType::class.simpleName + "(" + name + ")"
	}

	init
	{
		assert(purpose === null || operandType.canHavePurpose)
	}
}

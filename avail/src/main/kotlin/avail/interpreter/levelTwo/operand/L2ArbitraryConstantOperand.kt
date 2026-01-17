/*
 * L2ArbitraryConstantOperand.kt
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
package avail.interpreter.levelTwo.operand

import avail.descriptor.representation.Descriptor.Companion.brief
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.CONSTANT
import java.lang.reflect.Array.get
import java.lang.reflect.Array.getLength

/**
 * An [L2ArbitraryConstantOperand] is an operand of type
 * [L2OperandType.ARBITRARY_CONSTANT].  It holds an arbitrary value as a
 * constant constrained to a generic type [T].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [L2ArbitraryConstantOperand] with the specified [constant].
 *
 * @param constant
 *   The constant value.
 */
class L2ArbitraryConstantOperand<T>
constructor(
	constant: T
) : L2Operand()
{
	/**
	 * The actual constant value.
	 */
	val constant: T = constant

	override val operandType: L2OperandType get() = CONSTANT

	override fun dispatchOperand(dispatcher: L2OperandDispatcher) =
		dispatcher.doOperand(this)

	override fun appendTo(builder: StringBuilder): Unit = with(builder) {
		append("?(")
		brief {
			when
			{
				constant == null -> append("null")
				constant.javaClass.isArray ->
				{
					val elementType = constant.javaClass.componentType
					(0 until getLength(constant))
						.map { get(constant, it) }
						.joinTo(
							buffer = this,
							separator = ", ",
							prefix = "${elementType.simpleName}[",
							postfix = "]",
							limit = 10)
				}
				else -> append(constant)
			}
		}
		append(")")
	}

	override fun simpleAppendOperand(
		commands: MutableList<String>,
		sources: MutableList<String>,
		targets: MutableList<String>)
	{
		sources.add(buildString { appendTo(this) })
	}

	override fun equivalentTo(other: L2Operand) =
		other is L2ArbitraryConstantOperand<*>
			&& constant == other.constant

	override val equivalentHash: Int get() = constant.hashCode()
}

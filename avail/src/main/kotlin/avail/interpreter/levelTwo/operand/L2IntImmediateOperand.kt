/*
 * L2IntImmediateOperand.kt
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

import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE

/**
 * An [L2IntImmediateOperand] is an operand of type
 * [L2OperandType.INT_IMMEDIATE], which holds an integer value representing
 * itself.  Most immediates are indices or program counters and are naturally
 * [Int]s, but constant integers destined for 64-bit int registers may need the
 * full [Long] range.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property value
 *   The actual [Long] value.
 *
 * @constructor
 * Construct a new `L2IntImmediateOperand` with the specified [Long] value.
 *
 * @param value
 *   The constant [Long] itself.
 */
class L2IntImmediateOperand
constructor(
	val value: Long
) : L2Operand()
{
	/**
	 * Construct a new `L2IntImmediateOperand` with the specified [Int] value.
	 *
	 * @param value
	 *   The constant [Int] itself.
	 */
	constructor(value: Int) : this(value.toLong())

	/**
	 * The [value] as an [Int], for immediates that are naturally ints, such as
	 * indices, program counters, and flags.  Fail if the value doesn't fit.
	 */
	val intValue: Int
		get()
		{
			val intValue = value.toInt()
			check(intValue.toLong() == value) {
				"Immediate $value does not fit in an Int"
			}
			return intValue
		}


	override val operandType: L2OperandType get() = INT_IMMEDIATE

	override fun dispatchOperand(dispatcher: L2OperandDispatcher) =
		dispatcher.doOperand(this)

	override fun appendTo(builder: StringBuilder)
	{
		builder.append("#").append(value)
	}

	override fun simpleAppendOperand(
		commands: MutableList<String>,
		sources: MutableList<String>,
		targets: MutableList<String>)
	{
		sources.add(value.toString())
	}

	override fun equivalentTo(other: L2Operand) =
		other is L2IntImmediateOperand && value.equals(other.value)

	override val equivalentHash: Int get() = value.hashCode()
}

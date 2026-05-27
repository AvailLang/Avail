/*
 * Offset.kt
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
package avail.interpreter.levelTwoSimple.instructions.registers

import avail.descriptor.functions.A_Function
import avail.interpreter.levelTwoSimple.instructions.L2SimpleInstruction
import avail.interpreter.primitive.controlflow.P_ExitContinuationIf

/**
 * A field of an [L2SimpleInstruction] has this type if it represents a target
 * offset for a jump into a chunk's instructions.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@JvmInline
value class Offset(val value: Int)
{
	override fun toString(): String =
		when (this)
		{
			NEXT -> "O:Next"
			RETURN_NOW -> "O:Return"
			REIFY_NOW -> "O:Reify"
			else -> "O:$value"
		}

	companion object
	{
		/**
		 * The largest offset that an [L2SimpleInstruction] can have.  Larger
		 * values are used as sentinels.
		 */
		const val HIGHEST_LEGAL_OFFSET_int = Int.MAX_VALUE - 2

		val HIGHEST_LEGAL_OFFSET = Offset(HIGHEST_LEGAL_OFFSET_int)

		/**
		 * A sentinel value returned by an instruction's [step] to indicate
		 * a null should be returned from the current [A_Function], indicating
		 * a request for reification.
		 */
		const val REIFY_NOW_int = Int.MAX_VALUE - 1

		val REIFY_NOW = Offset(REIFY_NOW_int)

		/**
		 * A sentinel value returned by an instruction's [step] to indicate
		 * the current [A_Function] should return immediately, answering the value
		 * in the highest numbered register.  Note that instructions that run
		 * a [P_ExitContinuationIf] or such must clobber that register slot,
		 * even if they wouldn't normally push their result there.
		 */
		const val RETURN_NOW_int = Int.MAX_VALUE

		val RETURN_NOW = Offset(RETURN_NOW_int)

		/**
		 * A sentinel value plugged into instructions during initial generation,
		 * so that postponed instructions won't end up with the wrong offsets.
		 * A subsequent pass corrects this value to mean it's a fall-through to
		 * the next generated instruction.
		 */
		const val NEXT_int = Int.MIN_VALUE

		val NEXT = Offset(NEXT_int)
	}
}

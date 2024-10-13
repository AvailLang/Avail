/*
 * L2SemanticLocalCleanValue.kt
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
package avail.optimizer.values

import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.L2Entity.PrimaryVisualSortKey

/**
 * When present, this represents the value of a local variable upon arriving at
 * some L1 pc.  It's a clean value, so either the variable is still elided and
 * the clean value is bound to nil, or the variable exists and this value is
 * known to be the actual content of the variable (i.e., the value inside the
 * actualy variable object's value slot) at this pc.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Create a new [L2SemanticLocalCleanValue].
 */
internal class L2SemanticLocalCleanValue
internal constructor(
	frame: Frame,
	val localIndex: Int,
	val pc: Int
) : L2FrameSpecificSemanticValue(frame, combine3(localIndex, pc, -0x2D13D021))
{
	override fun equalsSemanticValue(other: L2SemanticValue<*>) =
		other is L2SemanticLocalCleanValue &&
			frame == other.frame &&
			localIndex == other.localIndex &&
			pc == other.pc

	override fun toString(): String = "Clean local $localIndex @$pc"

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue<BOXED_KIND>) -> L2SemanticValue<BOXED_KIND>,
		frameTransformer: (Frame) -> Frame
	): L2SemanticBoxedValue =
		frameTransformer(frame).let { newFrame ->
			if (newFrame == frame) this
			else L2SemanticLocalCleanValue(newFrame, localIndex, pc)
		}

	override val defaultRestriction: TypeRestriction
		get() = TypeRestriction.topRestriction

	override val isUsefulForGlobalValueNumbering: Boolean = true

	override val primaryVisualSortKey get() =
		PrimaryVisualSortKey.CLEAN_LOCAL_VALUE
}

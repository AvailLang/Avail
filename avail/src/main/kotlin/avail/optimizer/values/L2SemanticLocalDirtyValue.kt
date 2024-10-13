/*
 * L2SemanticLocalDirtyValue.kt
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
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.L2Entity.PrimaryVisualSortKey

/**
 * When present, this represents the value that was conceptually but not
 * physically written to a local variable upon arriving at some L1 pc.  It's a
 * dirty value, so it should generally disagree with any clean
 * ([L2SemanticLocalCleanValue]) value extant for the same local, and it should
 * be treated as the correct value of the variable if someone attempts to read
 * from it.
 *
 * If the variable is still elided, this can be used as the initial value of the
 * variable, should it need to be created before the variable is reassigned or
 * cleared (which is implemented as a write of [nil]).  If both a clean and
 * dirty semantic value are present for the same slot at the same pc, the dirty
 * one is considered the correct value, and the clean one is considered an
 * accurate indication of what is still in the physical variable.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Create a new [L2SemanticLocalDirtyValue].
 */
internal class L2SemanticLocalDirtyValue
internal constructor(
	frame: Frame,
	val localIndex: Int,
	val pc: Int
) : L2FrameSpecificSemanticValue(frame, combine3(localIndex, pc, 0x7A69D0E8))
{
	override fun equalsSemanticValue(other: L2SemanticValue<*>) =
		other is L2SemanticLocalDirtyValue &&
			frame == other.frame &&
			localIndex == other.localIndex &&
			pc == other.pc

	override fun toString(): String = "Dirty local $localIndex @$pc"

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue<BOXED_KIND>) -> L2SemanticValue<BOXED_KIND>,
		frameTransformer: (Frame) -> Frame
	): L2SemanticBoxedValue =
		frameTransformer(frame).let { newFrame ->
			if (newFrame == frame) this
			else L2SemanticLocalDirtyValue(newFrame, localIndex, pc)
		}

	override val defaultRestriction: TypeRestriction
		get() = TypeRestriction.topRestriction

	override val isUsefulForGlobalValueNumbering: Boolean = true

	override val primaryVisualSortKey get() =
		PrimaryVisualSortKey.DIRTY_LOCAL_VALUE
}

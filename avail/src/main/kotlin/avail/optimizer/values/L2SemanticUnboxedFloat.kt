/*
 * L2SemanticUnboxedFloat.kt
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

import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedFloat
import avail.utility.cast

/**
 * A semantic value which represents the [privateBoxed] semantic value, but unboxed as
 * a float (in some [L2FloatRegister].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Create a new `L2SemanticUnboxedFloat` semantic value.
 *
 * @param boxedValue
 *   The unboxed semantic value from which this unboxed value is derived.
 */
class L2SemanticUnboxedFloat
constructor(
	val boxedValue: L2SemanticValue<BOXED_KIND>
) : L2SemanticValue<FLOAT_KIND>(boxedValue.hash xor 0x27F6F766)
{
	/** The strengthened boxed value provided by the constructor. */
	val privateBoxed: L2SemanticBoxedValue = boxedValue.cast()

	override val kind get() = FLOAT_KIND

	override fun equalsSemanticValue(other: L2SemanticValue<*>) =
		other is L2SemanticUnboxedFloat
			&& privateBoxed.equalsSemanticValue(other.privateBoxed)

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue<BOXED_KIND>) -> L2SemanticValue<BOXED_KIND>,
		frameTransformer: (Frame) -> Frame
	): L2SemanticUnboxedFloat =
		semanticValueTransformer(privateBoxed).let {
			if (it == privateBoxed) this else it.unboxedFloat
		}

	override val toBoxed: L2SemanticBoxedValue get() = boxed

	override val isConstant: Boolean get() = privateBoxed.isConstant

	override val defaultRestriction: TypeRestriction
		get() = constantRestrictionOrNull ?: floatRestriction

	override val isUsefulForGlobalValueNumbering: Boolean get() =
		privateBoxed.isUsefulForGlobalValueNumbering

	override val constantRestrictionOrNull: TypeRestriction?
		get() = privateBoxed.constantRestrictionOrNull?.forUnboxedFloat()

	override fun hasRepresentationIn(
		manifest: L2ValueManifest
	): Boolean = manifest.hasFloatRepresentation(this)

	override fun toString(): String = "Float($privateBoxed)"

	companion object
	{
		/** The corresponding boxed form of this float semantic value. */
		val L2SemanticValue<FLOAT_KIND>.boxed: L2SemanticBoxedValue
			get() = (this as L2SemanticUnboxedFloat).privateBoxed

		/** Default resstriction for float semanticc values. */
		val floatRestriction =
			boxedRestrictionForType(Types.DOUBLE()).forUnboxedFloat()
	}
}

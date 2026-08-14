/*
 * L2SemanticUnboxedInt.kt
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

import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.optimizer.L2ValueManifest
import avail.optimizer.ValueClass
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.utility.cast

/**
 * A semantic value which represents the [privateBoxed] semantic value, but unboxed as
 * an int (in some [L2IntRegister].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Create a new `L2SemanticUnboxedInt` semantic value.
 *
 * @param boxedValue
 *   The unboxed semantic value from which this unboxed value is derived.
 */
class L2SemanticUnboxedInt
constructor(
	boxedValue: L2SemanticValue<BOXED_KIND>
) : L2SemanticValue<INTEGER_KIND>(boxedValue.hash xor 0x27F6F766)
{
	/** The strengthened boxed value provided by the constructor. */
	val privateBoxed: L2SemanticBoxedValue = boxedValue.cast()

	override val kind get() = INTEGER_KIND

	override fun equalsSemanticValue(other: L2SemanticValue<*>) =
		other is L2SemanticUnboxedInt
			&& privateBoxed.equalsSemanticValue(other.privateBoxed)

	override fun transform(
		semanticValueTransformer:
			(L2SemanticValue<BOXED_KIND>) -> L2SemanticValue<BOXED_KIND>,
		frameTransformer: (Frame) -> Frame
	): L2SemanticUnboxedInt =
		semanticValueTransformer(privateBoxed).let {
			if (it == privateBoxed) this else it.unboxedInt
		}

	override val toBoxed: L2SemanticBoxedValue get() = boxed

	override val isConstant: Boolean get() = privateBoxed.isConstant

	override val defaultRestriction: TypeRestriction
		get() = constantRestrictionOrNull ?: i32Restriction

	override val isUsefulForGlobalValueNumbering: Boolean get() =
		privateBoxed.isUsefulForGlobalValueNumbering

	/**
	 * An unboxed int is a *representation* of its boxed form, not a value
	 * derived from something else, so whatever the boxed form is derived from
	 * applies here unchanged.  This delegation disappears along with this class,
	 * once a `ValueState`'s int [Representation] is how an int is reached.
	 */
	override fun recordDerivationIn(
		manifest: L2ValueManifest,
		valueClass: ValueClass
	) = privateBoxed.recordDerivationIn(manifest, valueClass)

	override fun hasRepresentationIn(
		manifest: L2ValueManifest
	): Boolean = manifest.hasIntRepresentation(this)

	override val constantRestrictionOrNull: TypeRestriction?
		get() = privateBoxed.constantRestrictionOrNull?.forUnboxedInt()

	override fun toString(): String = "Int($privateBoxed)"

	companion object
	{
		/** The corresponding boxed form of this int semantic value. */
		val L2SemanticValue<INTEGER_KIND>.boxed: L2SemanticBoxedValue
			get() = (this as L2SemanticUnboxedInt).privateBoxed

		/** The default restriction for int semantic values. */
		val i32Restriction = intRestrictionForType(i32)
	}
}

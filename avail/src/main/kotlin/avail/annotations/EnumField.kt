/*
 * EnumField.kt
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
package avail.annotations

import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerEnumSlotDescriptionEnum
import avail.descriptor.representation.IntegerSlotsEnum
import avail.optimizer.jvm.ReferencedInGeneratedCode
import kotlin.reflect.KClass

/**
 * `EnumField` annotation is used to indicate which enumeration should be used
 * to describe an integer value embedded in an integer slot that has this
 * annotation.  This is used for pretty-printing [AvailObject]s.
 *
 * @property describedBy
 *   This annotation field indicates the [Enum] responsible for describing the
 *   integer slot to which the annotation is applied.  The value of the field
 *   (an `int`) should equal an [ordinal][Enum.ordinal] of a member of the
 *   specified `enum`.  If a [lookupMethodName] is also specified then the int
 *   value may be something other than the Enum's ordinal.
 * @property lookupMethodName
 *   This optional annotation field indicates the name of a static method
 *   defined within the [describing enumeration][describedBy]. The method should
 *   take an `int` argument and return an instance of the `#describedBy()`
 *   enumeration or null.  If null, only the numeric value is displayed,
 *   otherwise the enumeration value's name is displayed.  If this annotation
 *   field is omitted, the value of the field is treated as the
 *   [ordinal][Enum.ordinal] to look up. Similarly, in this case an ordinal that
 *   is out of range will only display its numeric value.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class EnumField(
	val describedBy: KClass<out IntegerEnumSlotDescriptionEnum>,
	val lookupMethodName: String = "")
{
	/**
	 * A helper class for presenting values for [IntegerSlotsEnum] and
	 * [BitField]s in alternative ways, for example as decimal numbers.
	 *
	 * To use it this way, add an annotation like this to the slot or bit field:
	 *
	 * ```
	 * @EnumField(
	 *   describedBy = Converter.class,
	 *   lookupMethodName = "decimal")
	 * ```
	 *
	 * @property string
	 *   The [String] to present for this field.
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Create a new instance for presenting the given [String].
	 *
	 * @param string
	 *   The [String] to present.
	 */
	class Converter private constructor (val string: String)
		: IntegerEnumSlotDescriptionEnum
	{
		override val fieldName get() = string

		// Shouldn't be used.
		override val fieldOrdinal get() = -1

		companion object
		{
			/**
			 * Present the `int` value in decimal.
			 *
			 * @param value
			 *   The int to present.
			 * @return
			 *   An instance that presents it in decimal.
			 */
			@ReferencedInGeneratedCode
			@JvmStatic
			fun decimal (value: Int) = Converter(value.toString())
		}
	}
}

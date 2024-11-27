/*
 * IntegerSlotsEnum.kt
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
package avail.descriptor.representation

import avail.annotations.EnumField
import avail.descriptor.representation.AbstractSlotsEnum.Companion.fieldName
import avail.utility.cast

/**
 * The `IntegerSlotsEnum` is an interface that helps ensure that object
 * representations and access are consistent and correct.  In particular, some
 * operations in AvailObject (such as [AvailObject.slot]) are expected to
 * operate on enumerations defined as inner classes within the [Descriptor]
 * class for which the slot layout is specified.
 *
 * Additionally, AvailObject is implemented with both object slots and integer
 * slots in such a way that the two should not be confused; i.e., their ordinals
 * are used as indices into either an [Array] of [AvailObject] or a [LongArray].
 * A related interface [ObjectSlotsEnum] helps to keep these uses disjoint.
 *
 * This class includes support for customizing how to render integer slots in
 * the Kotlin (and Avail) debugger.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface IntegerSlotsEnum : AbstractSlotsEnum
{
	/**
	 * Describe the integer field onto the provided [StringBuilder]. The
	 * pre-extracted `long` value is provided, as well as the containing
	 * [AvailObject] and the [IntegerSlotsEnum] instance. Take into account
	 * annotations on the slot enumeration object which may define the way
	 * it should be described.
	 *
	 * @param self
	 *   The object containing the `int` value in some slot.
	 * @param value
	 *   The [Long] value in the slot.
	 * @param subscript
	 *   The [Int] subscript for this field occurrence, or `0` if it's not an
	 *   indexable field.
	 * @param bitFields
	 *   The slot's [BitField]s, if any.
	 * @param builder
	 *   Where to write the description.
	 */
	fun describeIntegerSlot (
		self: AvailObject,
		value: Long,
		subscript: Int,
		bitFields: List<BitField>,
		builder: StringBuilder)
	{
		try
		{
			val slotName = fieldName
			if (bitFields.isEmpty())
			{
				val slotMirror = javaClass.getField(slotName)
				val enumAnnotation =
					slotMirror.getAnnotation(EnumField::class.java)
				var numBits = 64
				if (enumAnnotation !== null)
				{
					val enumClass = enumAnnotation.describedBy.java
					val enumValues = enumClass.enumConstants
					numBits =
						64 - enumValues.size.toLong().countLeadingZeroBits()
				}
				builder.append(" = ")
				describeIntegerField(
					value, numBits, enumAnnotation, builder)
			}
			else
			{
				builder.append("(")
				var first = true
				for (bitField in bitFields)
				{
					val fieldValue = self[bitField]
					val string = when (val presenter = bitField.presenter)
					{
						null -> buildString {
							describeIntegerField(
								fieldValue.toLong(),
								bitField.bits,
								bitField.enumField,
								this)
						}
						else -> presenter(fieldValue) ?: continue
					}
					if (!first)
					{
						builder.append(", ")
					}
					builder.append(bitField.name)
					builder.append("=")
					builder.append(string)
					first = false
				}
				builder.append(")")
			}
		}
		catch (e: SecurityException)
		{
			throw RuntimeException(e)
		}
		catch (e: IllegalArgumentException)
		{
			throw RuntimeException(e)
		}
		catch (e: ReflectiveOperationException)
		{
			throw RuntimeException(e)
		}
	}

	/**
	 * Write a description of an integer field to the [StringBuilder].
	 *
	 * @param value
	 *   The value of the field, a `long`.
	 * @param numBits
	 *   The number of bits to show for this field.
	 * @param enumAnnotation
	 *   The optional [EnumField] annotation that was found on the field.
	 * @param builder
	 *   Where to write the description.
	 * @throws ReflectiveOperationException
	 *   If the [EnumField.lookupMethodName] is incorrect.
	 */
	@Throws(ReflectiveOperationException::class)
	private fun describeIntegerField (
		value: Long,
		numBits: Int,
		enumAnnotation: EnumField?,
		builder: StringBuilder
	) = with(builder) {
		if (enumAnnotation !== null)
		{
			val describingClass = enumAnnotation.describedBy.java
			val lookupName = enumAnnotation.lookupMethodName
			if (lookupName.isEmpty())
			{
				// Look it up by ordinal (must be an actual Enum).
				val allValues: Array<IntegerEnumSlotDescriptionEnum> =
					describingClass.enumConstants.cast()
				if (value in allValues.indices)
				{
					append(allValues[value.toInt()].fieldName)
				}
				else
				{
					append("(enum out of range: ")
					describeLong(value, numBits, builder)
					append(")")
				}
			}
			else
			{
				// Look it up via the specified static lookup method.  It's
				// only required to be an IntegerEnumSlotDescriptionEnum in
				// this case, not necessarily an Enum.
				val lookupMethod = describingClass.getMethod(
					lookupName, Int::class.javaPrimitiveType)
				when (val lookedUp = lookupMethod(null, value.toInt()))
				{
					is IntegerEnumSlotDescriptionEnum ->
						append(lookedUp.fieldName)
					else -> append("null")
				}
			}
		}
		else
		{
			describeLong(value, numBits, builder)
		}
	}

	companion object
	{
		/**
		 * Write a description of this [Long] to the builder, taking note that
		 * the value is constrained to contain only numBits of content.  Use
		 * conventions such as grouping into groups of at most four hex digits.
		 *
		 * @param value
		 *   The [Long] to output.
		 * @param numBits
		 *   The number of bits contained in value.
		 * @param builder
		 *   Where to describe the number.
		 */
		fun describeLong (
			value: Long,
			numBits: Int,
			builder: StringBuilder
		): Unit = with(builder) {
			// Present signed byte as unsigned, and unsigned byte unchanged.
			if (numBits <= 8 && -0x80 <= value && value <= 0xFF)
			{
				append(String.format("0x%02X", value and 0xFF))
				return
			}
			// Present signed short as unsigned, and unsigned short unchanged.
			if (numBits <= 16 && -0x8000 <= value && value <= 0xFFFF)
			{
				append(String.format("0x%04X", value and 0xFFFF))
				return
			}
			// Present signed int as unsigned, and unsigned int unchanged.
			if (numBits <= 32 && -0x80000000 <= value && value <= 0xFFFFFFFFL)
			{
				append(String.format(
					"0x%04X_%04X",
					value ushr 16 and 0xFFFF,
					value and 0xFFFF))
				return
			}
			// Present a long as unsigned.
			append(String.format(
				"0x%04X_%04X_%04X_%04X",
				value ushr 48 and 0xFFFF,
				value ushr 32 and 0xFFFF,
				value ushr 16 and 0xFFFF,
				value and 0xFFFF))
		}
	}
}

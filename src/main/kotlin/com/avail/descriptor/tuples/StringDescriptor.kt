/*
 * StringDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.tuples

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import com.avail.descriptor.representation.*
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeDescriptor
import com.avail.serialization.SerializerOperation
import com.avail.utility.MutableInt
import com.avail.utility.json.JSONWriter

/**
 * `StringDescriptor` has Avail strings as its instances. The actual
 * representation of Avail strings is determined by subclasses.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see ByteStringDescriptor
 *
 * @see TwoByteStringDescriptor
 *
 * @constructor
 * Construct a new `StringDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 */
abstract class StringDescriptor protected constructor(
		mutability: Mutability,
		objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
		integerSlotsEnumClass: Class<out IntegerSlotsEnum>?)
	: TupleDescriptor(mutability, objectSlotsEnumClass, integerSlotsEnumClass)
{
	override fun o_IsString(self: AvailObject): Boolean = true

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation
	{
		val size = self.tupleSize()
		for (i in 1 .. size)
		{
			val codePoint = self.rawShortForCharacterAt(i)
			if (codePoint >= 256)
			{
				return SerializerOperation.SHORT_STRING
			}
		}
		return SerializerOperation.BYTE_STRING
	}

	abstract override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean =
			(TypeDescriptor.Types.CHARACTER.o().isSubtypeOf(type)
				|| super.o_TupleElementsInRangeAreInstancesOf(
					self, startIndex, endIndex, type))

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		throw unsupportedOperationException()
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.write(self.asNativeString())
	}

	companion object
	{
		/**
		 * Convert the specified Java [String] to an Avail [A_String], but
		 * keeping any Java surrogate pairs as two distinct values in the Avail
		 * string.  Note that such a string is semantically different from what
		 * would be produced by [stringFrom], and isn't even necessarily the
		 * same length.  This operation is intended for compatibility with Java
		 * (and JavaScript) strings.
		 *
		 * NB: The [descriptor][AbstractDescriptor] type of the actual instance
		 * returned varies with the contents of the Java `String`. If the Java
		 * `String` contains only Latin-1 characters, then the descriptor will
		 * be [ByteStringDescriptor]; otherwise it will be
		 * [TwoByteStringDescriptor].
		 *
		 * @param aNativeString
		 *   A Java [String].
		 * @return
		 *   An Avail `StringDescriptor string` having the same length, but with
		 *   surrogate pairs (D800-DBFF and DC00-DFFF) preserved in the Avail
		 *   string.
		 */
		fun stringWithSurrogatesFrom(aNativeString: String): A_String
		{
			val charCount = aNativeString.length
			if (charCount == 0)
			{
				return emptyTuple()
			}
			var maxChar = 0
			var index = 0
			while (index < charCount)
			{
				val aChar = aNativeString[index]
				maxChar = maxChar.coerceAtLeast(aChar.toInt())
				index++
			}
			return if (maxChar <= 255)
			{
				ByteStringDescriptor
					.mutableObjectFromNativeByteString(aNativeString)
			}
			else
			{
				TwoByteStringDescriptor
					.generateTwoByteString(aNativeString.length)
						{ aNativeString[it - 1].toInt() }
			}
			// Pack it into a TwoByteString, preserving surrogates.
		}

		/**
		 * Convert the specified Java [String] to an Avail [A_String].
		 *
		 * NB: The [descriptor][AbstractDescriptor] type of the actual instance
		 * returned varies with the contents of the Java `String`. If the Java
		 * `String` contains only Latin-1 characters, then the descriptor will
		 * be [ByteStringDescriptor]; otherwise it will be
		 * [TwoByteStringDescriptor].
		 *
		 * @param aNativeString
		 *   A Java [String].
		 * @return
		 *   A corresponding Avail `StringDescriptor string`.
		 */
		@JvmStatic
		fun stringFrom(aNativeString: String): A_String
		{
			val charCount = aNativeString.length
			if (charCount == 0)
			{
				return emptyTuple()
			}
			var maxCodePoint = 0
			var count = 0
			var index = 0
			while (index < charCount)
			{
				val codePoint = aNativeString.codePointAt(index)
				maxCodePoint = Math.max(maxCodePoint, codePoint)
				count++
				index += Character.charCount(codePoint)
			}
			if (maxCodePoint <= 255)
			{
				return ByteStringDescriptor
					.mutableObjectFromNativeByteString(aNativeString)
			}
			if (maxCodePoint <= 65535)
			{
				return TwoByteStringDescriptor
					.mutableObjectFromNativeTwoByteString(aNativeString)
			}
			// Fall back to building a general object tuple containing Avail
			// character objects.
			val charIndex = MutableInt(0)
			return ObjectTupleDescriptor.generateObjectTupleFrom(count) {
				val codePoint = aNativeString.codePointAt(charIndex.value)
				charIndex.value += Character.charCount(codePoint)
				fromCodePoint(codePoint)
			}
		}

		/**
		 * Given a Java [String] containing a [substitution
		 * format][String.format] and its arguments, perform pattern
		 * substitution and produce the corresponding Avail [string][A_String].
		 *
		 * @param pattern
		 *   A substitution pattern.
		 * @param args
		 *   The arguments to substitute into the pattern.
		 * @return
		 *   An Avail string.
		 */
		fun formatString(pattern: String, vararg args: Any): A_String =
			stringFrom(String.format(pattern, *args))
	}
}
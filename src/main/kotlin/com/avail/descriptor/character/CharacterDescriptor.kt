/*
 * CharacterDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.character

import com.avail.descriptor.character.CharacterDescriptor.IntegerSlots.Companion.CODE_POINT
import com.avail.descriptor.character.CharacterDescriptor.IntegerSlots.Companion.HASH
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.computeHashOfInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.exceptions.MarshalingException
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * [CharacterDescriptor] implements an Avail character. Avail characters are
 * Unicode characters, and their code points fall in the range 0..0x10FFFF,
 * (decimal 0..1,114,111), which includes the Supplementary Multilingual Planes.
 *
 * Unlike their use in some languages, characters in Avail are not themselves
 * considered numeric.  They are not a subrange of
 * [integers][IntegerDescriptor], and are intended to be treated as different
 * sorts of entities than integers, despite there being simple ways to translate
 * between characters and integers.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class CharacterDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability, TypeTag.CHARACTER_TAG, null, IntegerSlots::class.java
) {
	/** The layout of integer slots for my instances. */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The Unicode code point.  Don't bother with a [BitField], as all uses
		 * should restrict this to a valid Unicode range, which fits in 21 bits.
		 */
		CODE_POINT_AND_HASH;

		companion object {
			/**
			 * This character's Unicode code point, in the range 0..1,411,111.
			 */
			val CODE_POINT = BitField(CODE_POINT_AND_HASH, 0, 32)

			/**
			 * This character's hash value, which is eagerly computed.
			 */
			val HASH = BitField(CODE_POINT_AND_HASH, 32, 32)
		}
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	): Unit = with(builder) {
		append("¢")
		val codePoint = self.slot(CODE_POINT)
		// Check for linefeed, carriage return, tab, double quote ("), and
		// backslash (\).  These have pretty escape forms inside string
		// literals.
		val escapeIndex = "\n\r\t\\\"".indexOf(codePoint.toChar())
		if (escapeIndex != -1) {
			append("\"\\")
			append("nrt\\\""[escapeIndex])
			append('"')
		}
		else {
			when (Character.getType(codePoint)) {
				Character.COMBINING_SPACING_MARK.toInt(),
				Character.CONTROL.toInt(),
				Character.ENCLOSING_MARK.toInt(),
				Character.FORMAT.toInt(),
				Character.NON_SPACING_MARK.toInt(),
				Character.PARAGRAPH_SEPARATOR.toInt(),
				Character.PRIVATE_USE.toInt(),
				Character.SPACE_SEPARATOR.toInt(),
				Character.SURROGATE.toInt(),
				Character.UNASSIGNED.toInt() ->
					append(String.format("\"\\(%x)\"", codePoint))
				else -> appendCodePoint(codePoint)
			}
		}
	}

	override fun o_CodePoint(self: AvailObject): Int = self.slot(CODE_POINT)

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = another.equalsCharacterWithCodePoint(self.slot(CODE_POINT))

	override fun o_EqualsCharacterWithCodePoint(
		self: AvailObject,
		aCodePoint: Int
	): Boolean = self.slot(CODE_POINT) == aCodePoint

	override fun o_Hash(self: AvailObject): Int = self.slot(HASH)

	override fun o_IsCharacter(self: AvailObject): Boolean = true

	override fun o_MakeImmutable(self: AvailObject): AvailObject {
		if (isMutable) {
			// Make the object shared instead.
			self.setDescriptor(shared)
		}
		return self
	}

	override fun o_MakeShared(self: AvailObject): AvailObject {
		if (!isShared) {
			self.setDescriptor(shared)
		}
		return self
	}

	override fun o_Kind(self: AvailObject): A_Type = Types.CHARACTER.o()

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any {
		val codePoint = self.slot(CODE_POINT)
		// Force marshaling to Java's primitive int type.
		return when (classHint) {
			Int::class.javaPrimitiveType,
			Int::class.java ->
				codePoint
			Char::class.javaPrimitiveType,
			Char::class.java -> {
				// Force marshaling to Java's primitive char type, throwing an
				// exception if the code point is out of range.
				if (codePoint > 65535) {
					throw MarshalingException()
				}
				codePoint.toChar()
			}
			// Only understand Unicode code points in the basic multilingual
			// plane (BMP) as marshaling to Java's primitive char type. Use
			// Java's primitive int type for all others.
			else -> {
				assert(classHint == null)
				// Only understand Unicode code points in the basic multilingual
				// plane (BMP) as marshaling to Java's primitive char type.
				if (codePoint < 65536) codePoint.toChar()
				// Use Java's primitive int type for all others.
				else codePoint
			}
		}
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		when (self.slot(CODE_POINT)) {
			in 0..255 -> SerializerOperation.BYTE_CHARACTER
			in 0..65535 -> SerializerOperation.SHORT_CHARACTER
			else -> SerializerOperation.LARGE_CHARACTER
		}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.write(tuple(self))

	override fun mutable() = mutable

	/** There is no immutable variant; answer the shared descriptor. */
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Answer the hash of the Avail [A_Character] with the specified
		 * Unicode code point.
		 *
		 * @param codePoint
		 *   A Unicode code point.
		 * @return
		 *   THe character's hashed [Int].
		 */
		@JvmStatic
		fun computeHashOfCharacterWithCodePoint(codePoint: Int): Int =
			computeHashOfInt(codePoint xor -0x297166b9)

		/**
		 * Answer the hash of the Avail [A_Character] with the specified
		 * unsigned 8-bit Unicode code point.
		 *
		 * @param codePoint
		 *   An unsigned 8-bit Unicode code point.
		 * @return
		 *   A hash.
		 */
		@JvmStatic
		fun hashOfByteCharacterWithCodePoint(codePoint: Short): Int {
			assert(codePoint in 0..255)
			return hashesOfByteCharacters[codePoint.toInt()]
		}

		/** The mutable [CharacterDescriptor]. */
		private val mutable = CharacterDescriptor(Mutability.MUTABLE)

		/** The shared [CharacterDescriptor]. */
		private val shared = CharacterDescriptor(Mutability.SHARED)

		/**
		 * Answer a shared Avail [character][A_Character] for the specified
		 * Unicode code point.
		 *
		 * @param codePoint
		 *   A Unicode code point.
		 * @return
		 *   An [AvailObject].
		 */
		@JvmStatic
		fun fromCodePoint(codePoint: Int): A_Character {
			if (codePoint in 0..255) {
				return byteCharacters[codePoint]
			}
			// First look it up in the cache while holding a read lock.
			characterCacheLock.read {
				characterCache[codePoint]?.let { return it }
			}
			// We didn't find it while holding the read lock.  Try it again while
			// holding the write lock, creating and adding it if not found.
			return characterCacheLock.write {
				characterCache.computeIfAbsent(codePoint) { cp: Int ->
					mutable.create().run {
						setSlot(CODE_POINT, cp)
						setSlot(
							HASH,
							computeHashOfCharacterWithCodePoint(codePoint))
						makeShared()
					}
				}
			}
		}

		/**
		 * Answer an already instantiated Avail [character][A_Character] for the
		 * specified unsigned 8-bit Unicode code point.
		 *
		 * @param codePoint
		 *   An unsigned 8-bit Unicode code point.
		 * @return
		 *   An [AvailObject].
		 */
		@JvmStatic
		fun fromByteCodePoint(codePoint: Short): A_Character? {
			assert(codePoint in 0..255)
			return byteCharacters[codePoint.toInt()]
		}

		/** The first 256 Unicode characters. */
		private val byteCharacters = Array(256) {
			mutable.create().run {
				setSlot(CODE_POINT, it)
				setSlot(HASH, computeHashOfCharacterWithCodePoint(it))
				makeShared()
			}
		}

		/** The hashes of the first 256 Unicode characters. */
		private val hashesOfByteCharacters = IntArray(256) {
			computeHashOfCharacterWithCodePoint(it)
		}

		/**
		 * A cache of non-byte characters that have been encountered so far
		 * during this session.
		 */
		@GuardedBy("characterCacheLock")
		private val characterCache = mutableMapOf<Int, A_Character>()

		/** Protection for accessing the [characterCache]. */
		private val characterCacheLock = ReentrantReadWriteLock()

		/** The maximum code point value as an [Int]. */
		const val maxCodePointInt = Character.MAX_CODE_POINT

		/** A type that contains all ASCII decimal digit characters. */
		private val digitsType: A_Type =
			enumerationWith(stringFrom("0123456789").asSet()).makeShared()

		/** The type for non-empty strings of ASCII decimal digits. */
		@JvmField
		val nonemptyStringOfDigitsType: A_Type = oneOrMoreOf(digitsType)
	}
}

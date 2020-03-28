/*
 * AvailObjectRepresentation.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.descriptor.representation

import com.avail.descriptor.*
import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.TypeTag
import com.avail.utility.visitor.MarkUnreachableSubobjectVisitor
import sun.misc.Unsafe
import java.lang.Integer.numberOfTrailingZeros
import java.util.*
import kotlin.math.min

/**
 * `AvailObjectRepresentation` is the representation used for all Avail
 * objects.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class AvailObjectRepresentation protected constructor(
	descriptor: AbstractDescriptor,
	objectSlotsSize: Int,
	integerSlotsCount: Int
) : AbstractAvailObject(descriptor), A_BasicObject {
	/** A `long` array encoding all of my digital state.  */
	private var longSlots: LongArray = when (integerSlotsCount) {
		0 -> emptyIntegerSlots
		else -> LongArray(integerSlotsCount)
	}

	/** An array of all my references to other [AvailObject]s.  */
	private var objectSlots: Array<AvailObject?> = when (objectSlotsSize) {
		0 -> emptyObjectSlots
		else -> arrayOfNulls(objectSlotsSize)
	}

	/**
	 * Helper method for transferring this object's longSlots into an
	 * [L1InstructionDecoder].  The receiver's descriptor must be a
	 * [CompiledCodeDescriptor].
	 *
	 * @param instructionDecoder The [L1InstructionDecoder] to populate.
	 */
	open fun setUpInstructionDecoder(
		instructionDecoder: L1InstructionDecoder) {
		// assert descriptor instanceof CompiledCodeDescriptor;
		instructionDecoder.encodedInstructionsArray = longSlots
	}

	/**
	 * Turn the receiver into an [indirection][IndirectionDescriptor]
	 * to the specified [object][AvailObject].
	 *
	 *
	 * **WARNING:** This alters the receiver's slots and
	 * descriptor.
	 *
	 *
	 * **WARNING:** A [shared][Mutability.SHARED]
	 * object may not become an indirection. The caller must ensure that this
	 * method is not sent to a shared object.
	 *
	 * @param anotherObject An object.
	 */
	override fun becomeIndirectionTo(anotherObject: A_BasicObject) {
		assert(!descriptor().isShared)
		// Yes, this is really gross, but it's the simplest way to ensure that
		// objectSlots can remain private ...
		val traversed = traversed()
		val anotherTraversed = anotherObject.traversed()
		if (traversed.sameAddressAs(anotherTraversed)) {
			return
		}
		val oldSlotsSize = objectSlotsCount()
		if (oldSlotsSize == 0) {
			// Java-specific mechanism for now.  Requires more complex solution
			// when Avail starts using raw memory again.
			objectSlots = arrayOfNulls(1)
			objectSlots[0] = NilDescriptor.nil
		}
		if (descriptor().isMutable) {
			scanSubobjects(
				MarkUnreachableSubobjectVisitor(anotherObject))
			setDescriptor(IndirectionDescriptor.mutable(
				anotherTraversed.descriptor().typeTag))
			objectSlots[0] = anotherTraversed
		} else {
			anotherObject.makeImmutable()
			setDescriptor(IndirectionDescriptor.mutable(
				anotherTraversed.descriptor().typeTag))
			objectSlots[0] = anotherTraversed
			setDescriptor(IndirectionDescriptor.immutable(
				anotherTraversed.descriptor().typeTag))
		}
	}

	/**
	 * Verify that the object slot is an appropriate way to access this object
	 * (i.e., that the slot is defined in an enumeration within the class of
	 * this object's descriptor).  It fails (an assertion) if it's
	 * inappropriate, and if [.shouldCheckSlots] is enabled.
	 *
	 * @param field The object slot to validate for the receiver.
	 */
	private fun checkSlot(field: ObjectSlotsEnum) {
		if (shouldCheckSlots) {
			val debugSlots = descriptor().debugObjectSlots
			val permittedFields = debugSlots[field.fieldOrdinal()]
			if (permittedFields != null) {
				for (permittedField in permittedFields) {
					if (permittedField === field) {
						return
					}
				}
			}
			// Check it the slow way.
			val definitionClass = field.javaClass.enclosingClass
			assert(definitionClass.isInstance(descriptor()))
			// Cache that field for next time.
			val newPermittedFields: Array<ObjectSlotsEnum>
			when (permittedFields) {
				null -> newPermittedFields = arrayOf(field)
				else -> {
					newPermittedFields = Arrays.copyOf(
						permittedFields, permittedFields.size + 1)
					newPermittedFields[permittedFields.size] = field
				}
			}
			debugSlots[field.fieldOrdinal()] = newPermittedFields
		}
	}

	/**
	 * Verify that the integer slot is an appropriate way to access this object
	 * (i.e., that the slot is defined in an enumeration within the class of
	 * this object's descriptor).  It fails (an assertion) if it's
	 * inappropriate, and if [.shouldCheckSlots] is enabled.
	 *
	 * @param field The integer slot to validate for the receiver.
	 */
	private fun checkSlot(field: IntegerSlotsEnum) {
		if (shouldCheckSlots) {
			val debugSlots = descriptor().debugIntegerSlots
			val permittedFields = debugSlots[field.fieldOrdinal()]
			if (permittedFields != null) {
				for (permittedField in permittedFields) {
					if (permittedField === field) {
						return
					}
				}
			}
			// Check it the slow way.
			val definitionClass = field.javaClass.enclosingClass
			assert(definitionClass.isInstance(descriptor()))
			// Cache that field for next time.
			val newPermittedFields: Array<IntegerSlotsEnum?>
			when (permittedFields) {
				null -> newPermittedFields = arrayOf(field)
				else -> {
					newPermittedFields = Arrays.copyOf(
						permittedFields, permittedFields.size + 1)
					newPermittedFields[permittedFields.size] = field
				}
			}
			debugSlots[field.fieldOrdinal()] = newPermittedFields
		}
	}

	/**
	 * Extract the value of the [BitField] of the receiver.  Note that
	 * it's an `int` even though the underlying longSlots array contains
	 * `long`s.
	 *
	 * @param bitField
	 * A `BitField` that defines the object's layout.
	 * @return An `int` extracted from this object.
	 */
	fun slot(bitField: BitField): Int {
		checkSlot(bitField.integerSlot)
		val fieldValue = longSlots[bitField.integerSlotIndex]
		return bitField.extractFromLong(fieldValue)
	}

	/**
	 * Replace the value of the [BitField] within this object.
	 *
	 * @param bitField A `BitField` that defines the object's layout.
	 * @param anInteger An `int` to store in the indicated bit field.
	 */
	fun setSlot(
		bitField: BitField,
		anInteger: Int
	) {
		checkWriteForField(bitField.integerSlot)
		checkSlot(bitField.integerSlot)
		var value = longSlots[bitField.integerSlotIndex]
		value = bitField.replaceBits(value, anInteger)
		longSlots[bitField.integerSlotIndex] = value
	}

	/**
	 * Extract the byte at the given one-based byte subscript within the
	 * specified field. Always use little-endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @return The unsigned byte as a short.
	 */
	fun byteSlot(
		field: IntegerSlotsEnum,
		byteSubscript: Int
	): Short {
		checkSlot(field)
		val zeroBasedSubscript = byteSubscript - 1
		val wordIndex = field.fieldOrdinal() + (zeroBasedSubscript shr 3)
		val word = longSlots[wordIndex]
		val rightShift = zeroBasedSubscript and 0x07 shl 3
		return (word ushr rightShift and 0xFFL).toShort()
	}

	/**
	 * Replace the byte at the given one-based byte subscript within the
	 * specified field. Always use little endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @param aByte The unsigned byte to write, passed as a short.
	 */
	fun setByteSlot(
		field: IntegerSlotsEnum,
		byteSubscript: Int,
		aByte: Short
	) {
		assert(aByte.toInt() == aByte.toInt() and 0xFF)
		checkWriteForField(field)
		checkSlot(field)
		val zeroBasedSubscript = byteSubscript - 1
		val wordIndex = field.fieldOrdinal() + (zeroBasedSubscript shr 3)
		var word = longSlots[wordIndex]
		val leftShift = zeroBasedSubscript and 0x07 shl 3
		word = word and (0xFFL shl leftShift).inv()
		word = word or (aByte.toLong() shl leftShift)
		longSlots[wordIndex] = word
	}

	/**
	 * Extract a (16-bit unsigned) `short` at the given short-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param shortIndex The one-base index in shorts.
	 * @return The unsigned `short` (as an `int` found at the given
	 * short-index.
	 */
	fun shortSlot(
		field: IntegerSlotsEnum,
		shortIndex: Int
	): Int {
		checkSlot(field)
		val word = longSlots[field.fieldOrdinal() + (shortIndex - 1 ushr 2)]
		return (word ushr (shortIndex - 1 and 3 shl 4) and 0xFFFF).toInt()
	}

	/**
	 * Store the (16-bit unsigned) `short` at the given short-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param shortIndex The one-based index in shorts.
	 * @param aShort The `short` to store at the given short-index, passed
	 * as an `int` for safety.
	 */
	fun setShortSlot(
		field: IntegerSlotsEnum,
		shortIndex: Int,
		aShort: Int
	) {
		checkWriteForField(field)
		checkSlot(field)
		val shift = shortIndex - 1 and 3 shl 4
		val wordIndex = field.fieldOrdinal() + (shortIndex - 1 ushr 2)
		var word = longSlots[wordIndex]
		word = word and (0xFFFFL shl shift).inv()
		word = word or (aShort.toLong() shl shift)
		longSlots[wordIndex] = word
	}

	/**
	 * Extract a (32-bit signed) `int` at the given int-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param intIndex The one-base index in ints.
	 * @return The signed `int` found at the given int-index.
	 */
	fun intSlot(
		field: IntegerSlotsEnum,
		intIndex: Int
	): Int {
		checkSlot(field)
		val word = longSlots[field.fieldOrdinal() + (intIndex - 1 ushr 1)]
		return (word shr (intIndex - 1 and 1 shl 5)).toInt()
	}

	/**
	 * Store the (32-bit signed) `int` at the given int-index of the
	 * receiver.
	 *
	 * @param field The enumeration value that identifies the base field.
	 * @param intIndex The one-based index in ints.
	 * @param anInt The `int` to store at the given int-index.
	 */
	fun setIntSlot(
		field: IntegerSlotsEnum,
		intIndex: Int,
		anInt: Int
	) {
		checkWriteForField(field)
		checkSlot(field)
		val shift = intIndex - 1 and 1 shl 5
		val wordIndex = field.fieldOrdinal() + (intIndex - 1 ushr 1)
		var word = longSlots[wordIndex]
		word = word and (0xFFFFFFFFL shl shift).inv()
		word = word or (anInt.toLong() and 0xFFFFFFFFL shl shift)
		longSlots[wordIndex] = word
	}

	override fun integerSlotsCount(): Int {
		return longSlots.size
	}

	/**
	 * Extract the (signed 64-bit) integer for the given field `enum`
	 * value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return A `long` extracted from this object.
	 */
	fun slot(field: IntegerSlotsEnum): Long {
		checkSlot(field)
		return longSlots[field.fieldOrdinal()]
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field `enum` value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anInteger A `long` to store in the indicated slot.
	 */
	fun setSlot(
		field: IntegerSlotsEnum,
		anInteger: Long
	) {
		checkWriteForField(field)
		checkSlot(field)
		longSlots[field.fieldOrdinal()] = anInteger
	}

	/**
	 * Extract the (signed 64-bit) integer at the given field enum value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return A `long` extracted from this object.
	 */
	fun slot(
		field: IntegerSlotsEnum,
		subscript: Int
	): Long {
		checkSlot(field)
		return longSlots[field.fieldOrdinal() + subscript - 1]
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field `enum` value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger A `long` to store in the indicated slot.
	 */
	fun setSlot(
		field: IntegerSlotsEnum,
		subscript: Int,
		anInteger: Long
	) {
		checkWriteForField(field)
		checkSlot(field)
		longSlots[field.fieldOrdinal() + subscript - 1] = anInteger
	}

	/**
	 * Extract the (signed 64-bit) integer for the given field `enum`
	 * value, using volatile-read semantics if the receiver is shared.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return A `long` extracted from this object.
	 */
	fun mutableSlot(field: IntegerSlotsEnum): Long {
		checkSlot(field)
		return if (descriptor().isShared) {
			VolatileSlotHelper.volatileRead(longSlots, field.fieldOrdinal())
		} else {
			longSlots[field.fieldOrdinal()]
		}
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field `enum` value. If the receiver is [ ][Mutability.SHARED], then acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anInteger A `long` to store in the indicated slot.
	 */
	fun setMutableSlot(
		field: IntegerSlotsEnum,
		anInteger: Long
	) {
		checkWriteForField(field)
		checkSlot(field)
		if (descriptor().isShared) {
			VolatileSlotHelper.volatileWrite(
				longSlots, field.fieldOrdinal(), anInteger)
		} else {
			longSlots[field.fieldOrdinal()] = anInteger
		}
	}

	/**
	 * Extract an integer (at most 32 bits) from the given [BitField].
	 * If the receiver is [shared][Mutability.SHARED], then
	 * acquire its monitor.
	 *
	 * @param bitField A [BitField].
	 * @return An `int` extracted from this object.
	 */
	fun mutableSlot(bitField: BitField): Int {
		val fieldValue = mutableSlot(bitField.integerSlot)
		return bitField.extractFromLong(fieldValue)
	}

	/**
	 * Store the (signed 32-bit) integer into the specified [BitField].
	 * If the receiver is [shared][Mutability.SHARED], then acquire
	 * its monitor.
	 *
	 * @param bitField A [BitField].
	 * @param anInteger An `int` to store in the indicated slot.
	 */
	fun setMutableSlot(
		bitField: BitField,
		anInteger: Int
	) {
		checkWriteForField(bitField.integerSlot)
		checkSlot(bitField.integerSlot)
		if (descriptor().isShared) {
			var oldFieldValue: Long
			var newFieldValue: Long
			do {
				oldFieldValue = mutableSlot(bitField.integerSlot)
				newFieldValue = bitField.replaceBits(oldFieldValue, anInteger)
			} while (!VolatileSlotHelper.compareAndSet(
					longSlots,
					bitField.integerSlotIndex,
					oldFieldValue,
					newFieldValue))
		} else {
			var value = longSlots[bitField.integerSlotIndex]
			value = bitField.replaceBits(value, anInteger)
			longSlots[bitField.integerSlotIndex] = value
		}
	}

	/**
	 * Extract the (signed 64-bit) integer at the given field enum value. If the
	 * receiver is [shared][Mutability.SHARED], then acquire its
	 * monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return A `long` extracted from this object.
	 */
	fun mutableSlot(
		field: IntegerSlotsEnum,
		subscript: Int
	): Long {
		checkSlot(field)
		return if (descriptor().isShared) {
			VolatileSlotHelper.volatileRead(
				longSlots, field.fieldOrdinal() + subscript - 1)
		} else {
			longSlots[field.fieldOrdinal() + subscript - 1]
		}
	}

	/**
	 * Store the (signed 64-bit) integer in the eight bytes starting at the
	 * given field `enum` value. If the receiver is [ ][Mutability.SHARED], then acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger A `long` to store in the indicated slot.
	 */
	fun setMutableSlot(
		field: IntegerSlotsEnum,
		subscript: Int,
		anInteger: Long
	) {
		checkWriteForField(field)
		checkSlot(field)
		if (descriptor().isShared) {
			VolatileSlotHelper.volatileWrite(
				longSlots, field.fieldOrdinal() + subscript - 1, anInteger)
		} else {
			longSlots[field.fieldOrdinal() + subscript - 1] = anInteger
		}
	}

	override fun objectSlotsCount(): Int {
		return objectSlots.size
	}

	/**
	 * Extract the [object][AvailObject] at the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return The object found at the specified slot in the receiver.
	 */
	fun slot(
		field: ObjectSlotsEnum
	): AvailObject {
		checkSlot(field)
		return objectSlots[field.fieldOrdinal()]!!
	}

	/**
	 * Store the [object][AvailObject] in the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	fun setSlot(
		field: ObjectSlotsEnum,
		anAvailObject: A_BasicObject
	) {
		// If the receiver is shared, then the new value must become shared
		// before it can be stored.
		assert(!descriptor().isShared || anAvailObject.descriptor().isShared)
		checkSlot(field)
		checkWriteForField(field)
		objectSlots[field.fieldOrdinal()] = anAvailObject as AvailObject
	}

	/**
	 * Store the specified [continuation][ContinuationDescriptor] in
	 * the receiver, which must be a [fiber][FiberDescriptor].  This
	 * is the only circumstance in all of Avail in which a field of a
	 * (potentially) [shared][Mutability.SHARED] object may hold a
	 * non-shared object.
	 *
	 *
	 * When we have our own memory manager with thread-specific arenas for
	 * the unshared heap, those arenas will be associated with the fiber that
	 * was running during their allocation.  When the fiber exits, if the fiber
	 * was shared, the fiber's result will be copied out to shared space prior
	 * to deletion of its arenas.  But prior to that time, the shared fiber
	 * guards its unshared continuation from prying eyes.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param aContinuation The object to store at the specified slot.
	 */
	fun setContinuationSlotOfFiber(
		field: ObjectSlotsEnum,
		aContinuation: A_Continuation
	) {
		// If the receiver is shared, then the new value must become shared
		// before it can be stored.
		assert(field === FiberDescriptor.ObjectSlots.CONTINUATION)
		checkSlot(field)
		checkWriteForField(field)
		objectSlots[field.fieldOrdinal()] = aContinuation as AvailObject
	}

	/**
	 * Extract the [object][AvailObject] at the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return The object found at the specified slot in the receiver.
	 */
	fun slot(
		field: ObjectSlotsEnum,
		subscript: Int
	): AvailObject {
		checkSlot(field)
		return objectSlots[field.fieldOrdinal() + subscript - 1]!!
	}

	/**
	 * Store the [object][AvailObject] in the specified slot of the
	 * receiver.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	fun setSlot(
		field: ObjectSlotsEnum,
		subscript: Int,
		anAvailObject: A_BasicObject
	) {
		// If the receiver is shared, then the new value must become shared
		// before it can be stored.
		assert(!descriptor().isShared || anAvailObject.descriptor().isShared)
		checkSlot(field)
		checkWriteForField(field)
		objectSlots[field.fieldOrdinal() + subscript - 1] =
			anAvailObject as AvailObject
	}

	/**
	 * Write elements from the given [List] into consecutively numbered
	 * object slots.
	 *
	 * @param field
	 * The repeated object slot into which to write elements.
	 * @param startSubscript
	 * The positive one-based subscript at which to start writing
	 * elements from the [List].
	 * @param sourceList
	 * The [List] of objects to write into the slots.
	 * @param zeroBasedStartSourceSubscript
	 * The zero-based subscript into the sourceList from which to start
	 * reading.
	 * @param count
	 * How many values to transfer.
	 */
	fun setSlotsFromList(
		field: ObjectSlotsEnum,
		startSubscript: Int,
		sourceList: List<A_BasicObject>,
		zeroBasedStartSourceSubscript: Int,
		count: Int
	) {


		assert(!descriptor().isShared
			|| sourceList.all { it.descriptor().isShared })
		checkSlot(field)
		checkWriteForField(field)
		// If the receiver is shared, then the new value must become shared
		// before it can be stored.
		var slotIndex = field.fieldOrdinal() + startSubscript - 1
		var listIndex = zeroBasedStartSourceSubscript
		for (i in 0 until count) {
			objectSlots[slotIndex++] = sourceList[listIndex++] as AvailObject
		}
	}

	/**
	 * Read elements from consecutive slots of an array, writing them to
	 * consecutive slots of the receiver.  It's the client's responsibility to
	 * ensure the values are suitably immutable or shared.
	 *
	 * @param <T>
	 * The type of array to copy from.
	 * @param targetField
	 * The field of the receiver into which to write values.
	 * @param startTargetSubscript
	 * The positive one-based subscript into the target field at which to
	 * start writing.
	 * @param sourceArray
	 * The array supplying values for consecutive slots.
	 * @param zeroBasedStartSourceSubscript
	 * The zero-based subscript in the sourceArray from which to start
	 * reading.
	 * @param count
	 * How many values to transfer.
	</T> */
	fun <T : A_BasicObject> setSlotsFromArray(
		targetField: ObjectSlotsEnum,
		startTargetSubscript: Int,
		sourceArray: Array<T>,
		zeroBasedStartSourceSubscript: Int,
		count: Int
	) {
		assert(!descriptor().isShared) { "Block-transfers into shared objects is not supported" }
		checkSlot(targetField)
		checkWriteForField(targetField)
		System.arraycopy(
			sourceArray,
			zeroBasedStartSourceSubscript,
			objectSlots,
			targetField.fieldOrdinal() + startTargetSubscript - 1,
			count)
	}

	/**
	 * Read elements from consecutive slots of a long array, writing them to
	 * consecutive slots of the receiver.
	 *
	 * @param targetField
	 * The integer field of the receiver into which to write longs.
	 * @param startTargetSubscript
	 * The positive one-based subscript into the target field at which to
	 * start writing.
	 * @param sourceArray
	 * The long[] array supplying longs for consecutive slots.
	 * @param zeroBasedStartSourceSubscript
	 * The zero-based subscript in the sourceArray from which to start
	 * reading.
	 * @param count
	 * How many longs to transfer.
	 */
	fun setSlotsFromArray(
		targetField: IntegerSlotsEnum,
		startTargetSubscript: Int,
		sourceArray: LongArray,
		zeroBasedStartSourceSubscript: Int,
		count: Int
	) {
		assert(!descriptor().isShared) {
			"Block-transfers into shared objects is not supported"
		}
		checkSlot(targetField)
		checkWriteForField(targetField)
		System.arraycopy(
			sourceArray,
			zeroBasedStartSourceSubscript,
			longSlots,
			targetField.fieldOrdinal() + startTargetSubscript - 1,
			count)
	}

	/**
	 * Read consecutive long slots from the receiver, writing them into slots of
	 * a long array.
	 *
	 * @param sourceField
	 * The integer field of the receiver from which to read longs.
	 * @param startSourceSubscript
	 * The positive one-based subscript in the target field at which to
	 * start reading.
	 * @param targetArray
	 * The long[] array into which to write longs.
	 * @param zeroBasedStartTargetSubscript
	 * The zero-based subscript in the sourceArray at which to start
	 * writing.
	 * @param count
	 * How many longs to transfer.
	 */
	fun slotsIntoArray(
		sourceField: IntegerSlotsEnum,
		startSourceSubscript: Int,
		targetArray: LongArray,
		zeroBasedStartTargetSubscript: Int,
		count: Int
	) {
		checkSlot(sourceField)
		System.arraycopy(
			longSlots,
			sourceField.fieldOrdinal() + startSourceSubscript - 1,
			targetArray,
			zeroBasedStartTargetSubscript,
			count)
	}

	/**
	 * Read elements from consecutive slots of a tuple, writing them to
	 * consecutive slots of the receiver.  It's the client's responsibility to
	 * ensure the values are suitably immutable or shared.
	 *
	 * @param targetField
	 * The field of the receiver into which to write values.
	 * @param startTargetSubscript
	 * The positive one-based subscript into the target field at which to
	 * start writing.
	 * @param sourceTuple
	 * The tuple supplying values in consecutive slots.
	 * @param startSourceSubscript
	 * The positive one-based subscript into the sourceTuple from which
	 * to start reading.
	 * @param count
	 * How many values to transfer.
	 */
	fun setSlotsFromTuple(
		targetField: ObjectSlotsEnum,
		startTargetSubscript: Int,
		sourceTuple: A_Tuple,
		startSourceSubscript: Int,
		count: Int
	) {
		assert(!descriptor().isShared) {
			"Block-transfers into shared objects is not supported"
		}
		checkSlot(targetField)
		checkWriteForField(targetField)
		var slotIndex = targetField.fieldOrdinal() + startTargetSubscript - 1
		var tupleIndex = startSourceSubscript
		for (i in 0 until count) {
			objectSlots[slotIndex++] = sourceTuple.tupleAt(tupleIndex++)
		}
	}

	/**
	 * Read elements from consecutive slots of the sourceObject, writing them to
	 * consecutive slots of the receiver.  It's the client's responsibility to
	 * ensure the values are suitably immutable or shared.
	 *
	 * @param targetField
	 * The field of the receiver into which to write values.
	 * @param startTargetSubscript
	 * The positive one-based subscript into the target field at which to
	 * start writing.
	 * @param sourceObject
	 * The object supplying values in consecutive slots.
	 * @param sourceField
	 * The repeating field of the sourceObject.
	 * @param startSourceSubscript
	 * The positive one-based subscript into the sourceObject from which
	 * to start reading.
	 * @param count
	 * How many values to transfer.
	 */
	fun setSlotsFromObjectSlots(
		targetField: ObjectSlotsEnum,
		startTargetSubscript: Int,
		sourceObject: A_BasicObject,
		sourceField: ObjectSlotsEnum,
		startSourceSubscript: Int,
		count: Int
	) {
		assert(!descriptor().isShared) {
			"Block-transfers into shared objects is not supported"
		}
		checkSlot(targetField)
		checkWriteForField(targetField)
		val sourceRep = sourceObject as AvailObjectRepresentation
		sourceRep.checkSlot(sourceField)
		System.arraycopy(
			sourceRep.objectSlots,
			sourceField.fieldOrdinal() + startSourceSubscript - 1,
			objectSlots,
			targetField.fieldOrdinal() + startTargetSubscript - 1,
			count)
	}

	/**
	 * Read elements from consecutive integer slots of the sourceObject, writing
	 * them to consecutive slots of the receiver.  It's the client's
	 * responsibility to ensure the values are suitably immutable or shared.
	 *
	 * @param targetField
	 * The field of the receiver into which to write longs.
	 * @param startTargetSubscript
	 * The positive one-based subscript into the target field at which to
	 * start writing.
	 * @param sourceObject
	 * The object supplying values in consecutive long slots.
	 * @param sourceField
	 * The repeating integer field of the sourceObject.
	 * @param startSourceSubscript
	 * The positive one-based subscript into the sourceObject from which
	 * to start reading longs.
	 * @param count
	 * How many longs to transfer.
	 */
	fun setSlotsFromLongSlots(
		targetField: IntegerSlotsEnum,
		startTargetSubscript: Int,
		sourceObject: A_BasicObject,
		sourceField: IntegerSlotsEnum,
		startSourceSubscript: Int,
		count: Int
	) {
		assert(!descriptor().isShared) {
			"Block-transfers into shared objects is not supported"
		}
		checkSlot(targetField)
		checkWriteForField(targetField)
		val sourceRep = sourceObject as AvailObjectRepresentation
		sourceRep.checkSlot(sourceField)
		System.arraycopy(
			sourceRep.longSlots,
			sourceField.fieldOrdinal() + startSourceSubscript - 1,
			longSlots,
			targetField.fieldOrdinal() + startTargetSubscript - 1,
			count)
	}

	/**
	 * Store the [object][AvailObject] in the specified slots of the
	 * receiver.  The caller is responsible for ensuring the value has been
	 * marked [Mutability.IMMUTABLE] if necessary.
	 *
	 * @param field
	 * An enumeration value that defines the field ordering.
	 * @param startSubscript
	 * The positive one-based subscript to apply.
	 * @param count
	 * The number of consecutive slots to write the value into.
	 * @param anAvailObject
	 * The object to store in the specified slots.
	 */
	fun fillSlots(
		field: ObjectSlotsEnum,
		startSubscript: Int,
		count: Int,
		anAvailObject: A_BasicObject
	) {
		if (count == 0) {
			return
		}
		assert(!descriptor().isShared || anAvailObject.descriptor().isShared)
		checkSlot(field)
		checkWriteForField(field)
		val startSlotIndex = field.fieldOrdinal() + startSubscript - 1
		Arrays.fill(
			objectSlots,
			startSlotIndex,
			startSlotIndex + count,
			anAvailObject)
	}

	/**
	 * Extract the [object][AvailObject] at the specified slot of the
	 * receiver. If the receiver is [shared][Mutability.SHARED], then
	 * acquire its monitor.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return The object found at the specified slot in the receiver.
	 */
	fun mutableSlot(field: ObjectSlotsEnum): AvailObject {
		checkSlot(field)
		return if (descriptor().isShared) {
			VolatileSlotHelper.volatileRead(objectSlots, field.fieldOrdinal())
		} else {
			objectSlots[field.fieldOrdinal()]!!
		}
	}

	/**
	 * Provide fast volatile and atomic access to long and AvailObject slots.
	 * Java left a huge implementation gap where you can't access normal array
	 * slots with volatile access, but there are ways around it.  For now, use
	 * Sun's Unsafe class.
	 *
	 *
	 * In sand-boxed environments where this is not possible we'll need to
	 * change this to use subclassing, and do volatile reads or nilpotent
	 * compare-and-set writes of the descriptor field at the appropriate times
	 * to ensure happens-before/after.  However, compare-and-set semantics will
	 * be much harder to accomplish.
	 */
	private object VolatileSlotHelper {
		/**
		 * This is used for atomic access to slots.  It's not allowed to be used
		 * by non-system code in sand-boxed contexts, so we'll need a
		 * poorer-performing solution there.
		 */
		private val unsafe =
			with(Unsafe::class.java.getDeclaredField("theUnsafe")) {
				setAccessible(true)
				get(null) as Unsafe
			}

		private val longArrayBaseOffset =
			unsafe.arrayBaseOffset(LongArray::class.java)

		private val longArrayShift = with(unsafe) {
			val delta = arrayIndexScale(LongArray::class.java)
			assert(delta and delta - 1 == 0) {
				"The reserved size of a long wasn't a power of two"
			}
			numberOfTrailingZeros(delta)
		}

		private val objectArrayBaseOffset =
			unsafe.arrayBaseOffset(Array<AvailObject>::class.java)

		private val objectArrayShift = with(unsafe) {
			val delta = arrayIndexScale(Array<AvailObject>::class.java)
			assert(delta and delta - 1 == 0) {
				"The reserved size of a slot in an object array wasn't " +
					"a power of two"
			}
			numberOfTrailingZeros(delta)
		}

		fun volatileRead(
			longs: LongArray,
			subscript: Int
		): Long {
			assert(0 <= subscript && subscript < longs.size)
			val byteOffset =
				(subscript.toLong() shl longArrayShift) + longArrayBaseOffset
			return unsafe.getLongVolatile(longs, byteOffset)
		}

		fun volatileWrite(
			longs: LongArray,
			subscript: Int,
			value: Long
		) {
			assert(0 <= subscript && subscript < longs.size)
			val byteOffset =
				(subscript.toLong() shl longArrayShift) + longArrayBaseOffset
			unsafe.putLongVolatile(longs, byteOffset, value)
		}

		fun compareAndSet(
			longs: LongArray,
			subscript: Int,
			expected: Long,
			value: Long
		): Boolean {
			assert(0 <= subscript && subscript < longs.size)
			val byteOffset = (subscript.toLong() shl longArrayShift) +
				longArrayBaseOffset
			return unsafe.compareAndSwapLong(
				longs, byteOffset, expected, value)
		}

		fun volatileRead(
			objects: Array<AvailObject?>,
			subscript: Int
		): AvailObject {
			assert(0 <= subscript && subscript < objects.size)
			val byteOffset = (subscript.toLong() shl objectArrayShift) +
				objectArrayBaseOffset
			return unsafe.getObjectVolatile(objects, byteOffset) as AvailObject
		}

		fun volatileWrite(
			objects: Array<AvailObject?>,
			subscript: Int,
			value: AvailObject
		) {
			assert(0 <= subscript && subscript < objects.size)
			val byteOffset = (subscript.toLong() shl objectArrayShift) +
				objectArrayBaseOffset
			unsafe.putObjectVolatile(objects, byteOffset, value)
		}

		fun compareAndSet(
			objects: Array<AvailObject>,
			subscript: Int,
			expected: AvailObject,
			value: AvailObject
		): Boolean {
			assert(0 <= subscript && subscript < objects.size)
			val byteOffset = (subscript.toLong() shl objectArrayShift) +
				objectArrayBaseOffset
			return unsafe.compareAndSwapObject(
				objects, byteOffset, expected, value)
		}
	}

	/**
	 * Extract the [object][AvailObject] at the specified slot of the
	 * receiver.  Use volatile semantics for the read.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The one-based subscript to offset the field.
	 * @return The object found at the specified slot in the receiver.
	 */
	fun volatileSlot(
		field: ObjectSlotsEnum,
		subscript: Int
	): AvailObject {
		checkSlot(field)
		return if (descriptor().isShared) {
			VolatileSlotHelper.volatileRead(
				objectSlots, field.fieldOrdinal() + subscript - 1)
		} else {
			objectSlots[field.fieldOrdinal() + subscript - 1]!!
		}
	}

	/**
	 * Extract the [object][AvailObject] at the specified slot of the
	 * receiver.  Use volatile semantics for the read.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @return The object found at the specified slot in the receiver.
	 */
	fun volatileSlot(
		field: ObjectSlotsEnum
	): AvailObject {
		checkSlot(field)
		return if (descriptor().isShared) {
			VolatileSlotHelper.volatileRead(objectSlots, field.fieldOrdinal())
		} else {
			objectSlots[field.fieldOrdinal()]!!
		}
	}

	/**
	 * Store the [object][AvailObject] in the specified slot of the
	 * receiver.  Use volatile write semantics.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The one-based subscript to offset the field.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	fun setVolatileSlot(
		field: ObjectSlotsEnum,
		subscript: Int,
		anAvailObject: A_BasicObject
	) {
		checkSlot(field)
		checkWriteForField(field)
		if (descriptor().isShared) {
			// The receiver is shared, so the new value must become shared
			// before it can be stored.
			VolatileSlotHelper.volatileWrite(
				objectSlots,
				field.fieldOrdinal() + subscript - 1,
				anAvailObject.makeShared())
		} else {
			objectSlots[field.fieldOrdinal() + subscript - 1] =
				anAvailObject as AvailObject
		}
	}

	/**
	 * Store the [object][AvailObject] in the specified slot of the
	 * receiver.  Use volatile write semantics.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	fun setVolatileSlot(
		field: ObjectSlotsEnum,
		anAvailObject: A_BasicObject
	) {
		checkSlot(field)
		checkWriteForField(field)
		if (descriptor().isShared) {
			// The receiver is shared, so the new value must become shared
			// before it can be stored.
			VolatileSlotHelper.volatileWrite(
				objectSlots, field.fieldOrdinal(), anAvailObject.makeShared())
		} else {
			objectSlots[field.fieldOrdinal()] = anAvailObject as AvailObject
		}
	}

	/**
	 * Store the [object][AvailObject] in the specified slot of the
	 * receiver, using volatile-write semantics if the receiver is shared.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	fun setMutableSlot(
		field: ObjectSlotsEnum,
		anAvailObject: A_BasicObject
	) = setVolatileSlot(field, anAvailObject)

	/**
	 * Extract the [object][AvailObject] at the specified slot of the
	 * receiver, using volatile-read semantics if the receiver is shared.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return The object found at the specified slot in the receiver.
	 */
	fun mutableSlot(
		field: ObjectSlotsEnum,
		subscript: Int
	) = volatileSlot(field, subscript)

	/**
	 * Write an equivalent replacement object into an
	 * [object&#32;field][ObjectSlotsEnum] of this object.  Since the
	 * replacement is semantically equivalent to the previous content, don't
	 * acquire a lock.  Any necessary write barriers and other memory
	 * synchronizations are the responsibility of the caller.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anAvailObject The object to store unchecked in the slot.
	 */
	fun writeBackSlot(
		field: ObjectSlotsEnum,
		subscript: Int,
		anAvailObject: AvailObject
	) {
		checkSlot(field)
		objectSlots[field.fieldOrdinal() + subscript - 1] = anAvailObject
	}

	/**
	 * Reduce the number of `long` slots occupied by this object.  In a
	 * raw memory model we would split the object representation into two
	 * objects, one at the original address, and a separate filler object
	 * occupying the long slots that were chopped off.
	 *
	 * In the current Java object implementation, we simply shorten the int
	 * array by replacing it.
	 */
	override fun truncateWithFillerForNewIntegerSlotsCount(
		newIntegerSlotsCount: Int
	) {
		val oldIntegerSlotsCount = integerSlotsCount()
		assert(newIntegerSlotsCount < oldIntegerSlotsCount)
		val newLongSlots = LongArray(newIntegerSlotsCount)
		System.arraycopy(longSlots, 0, newLongSlots, 0, newIntegerSlotsCount)
		longSlots = newLongSlots
	}

	/**
	 * Slice the current [object][AvailObject] into two objects, the
	 * left one (at the same starting address as the input), and the right
	 * one (a [filler object][FillerDescriptor] that nobody should
	 * ever create a pointer to). The new Filler can have zero post-header slots
	 * (i.e., just the header), but the left object must not, since it may turn
	 * into an [indirection][IndirectionDescriptor] some day and will
	 * require at least one slot for the target pointer.
	 */
	override fun truncateWithFillerForNewObjectSlotsCount(
		newObjectSlotsCount: Int
	) {
		assert(newObjectSlotsCount > 0)
		val oldObjectSlotsCount = objectSlotsCount()
		assert(newObjectSlotsCount < oldObjectSlotsCount)
		// final int fillerSlotCount =
		//   oldObjectSlotsCount - newObjectSlotsCount - 1;
		// Here's where we would write a filler header into raw memory.
		// Slots *filler =
		//   (Slots *)(_pointer.address() + 4 + (newSlotsSize << 2));
		// filler->descriptorId() = FillerDescriptor.mutable().id();
		// filler->sizeInLongs() = fillerSlotCount;
		val newObjectSlots = arrayOfNulls<AvailObject>(newObjectSlotsCount)
		System.arraycopy(objectSlots, 0, newObjectSlots, 0, newObjectSlotsCount)
		objectSlots = newObjectSlots
	}

	/**
	 * Search for the key in the 32-bit ints encoded within the [longSlots] that
	 * occur within those slots identified with the specified
	 * [IntegerSlotsEnum].  The int slots must be in ascending sorted order, and
	 * must be distinct.  If the exact int is found, answer its zero-based index
	 * within this repeated slot (i.e., ≥0).  If the exact int is not found,
	 * answer (-n-1), where n is the zero-based position of the leftmost element
	 * of the repeated slot which is greater than the key (if it was equal, the
	 * "if found" case would have applied).
	 *
	 * @param slot
	 * The final integer slot, which must be the variable-length part
	 * of the longSlots array.
	 * @param slotCount
	 * The number of valid int-sized slots (starting at the specified
	 * slot's ordinal).
	 * @param key
	 * The long value to seek in the designated region of the
	 * longSlots array.
	 * @return
	 * The zero-based index of the key within the variable-length
	 * repeated slot if found, or else (-n-1) where n is the
	 * zero-based index of the leftmost int that is greater than the
	 * key.
	 */
	fun intBinarySearch(
		slot: IntegerSlotsEnum,
		slotCount: Int,
		key: Int
	): Int {
		val fromIntIndex = slot.fieldOrdinal() shl 1
		val toIntIndex = fromIntIndex + slotCount
		var low = fromIntIndex
		var high = toIntIndex - 1
		while (low <= high) {
			// Use a logical right shift to compensate for overflow in midpoint
			// calculation.
			val mid = low + high ushr 1
			val longMidVal = longSlots[mid ushr 1]
			// The following shift maintains the little-Endian convention set up
			// by intSlot() and setIntSlot().
			val midVal = (longMidVal ushr (mid and 1 shl 5)).toInt()
			// key found
			when {
				midVal < key -> low = mid + 1
				midVal > key -> high = mid - 1
				else -> return mid - fromIntIndex  // key not found
			}
		}
		return -(low - fromIntIndex + 1) // key not found.
	}

	/**
	 * {@inheritDoc}
	 *
	 * This comparison operation takes an [Object] as its argument to avoid
	 * accidentally calling this with, say, a [String] literal. We mark it as
	 * deprecated to ensure we don't accidentally invoke this method when we
	 * really mean the version that takes an `AvailObject` as an argument.
	 *
	 * IntelliJ conveniently shows such invocations with a struck-through font.
	 * That's a convenient warning for the programmer, even though it actually
	 * works correctly.
	 */
	@Deprecated(
		message = "Don't compare AvailObject and arbitrary Object",
		replaceWith = ReplaceWith("equals(AvailObject)"))
	override fun equals(other: Any?): Boolean {
		return other is AvailObject
			&& descriptor().o_Equals(
				this as AvailObject,
				other)
	}

	override fun hashCode(): Int = descriptor().o_Hash(this as AvailObject)

	/**
	 * Extract the type tag for this object.  Does not answer [ ][TypeTag.UNKNOWN_TAG].
	 *
	 *
	 * It's usually sufficient to access this descriptor's typeTag, but
	 * rarely it may be necessary to invoke computeTypeTag().
	 *
	 * @return The [TypeTag] of this object.
	 */
	fun typeTag(): TypeTag {
		// First, directly access the descriptor's typeTag, which will be
		// something other than UNKNOWN_TAG in the vast majority of attempts.
		val tag = descriptor().typeTag
		return when(tag) {
			TypeTag.UNKNOWN_TAG ->
				descriptor().o_ComputeTypeTag(this as AvailObject)
			else -> tag
		}
		// Fall back on computing the tag with a slower polymorphic method.
	}

	companion object {
		/**
		 * This static switch enables paranoid checks to ensure objects are only
		 * being accessed via slot definitions appropriate for the object's actual
		 * descriptor.  This check slows the system considerably, but it's
		 * occasionally valuable to enable for a short time, especially right after
		 * introducing new descriptor subclasses.
		 */
		const val shouldCheckSlots = false

		/**
		 * Create a new [AvailObject] with the specified [ ], the specified number of object slots, and
		 * the specified number of long slots.  Also copy the fields from the
		 * specified object, which must have a descriptor of the same class.  If the
		 * sizes of the long arrays differ, only transfer the minimum of the two
		 * sizes; do the same for the object slots.
		 *
		 *
		 *
		 * It is the client's responsibility to mark the shared fields as immutable
		 * if necessary.  Also, any new `long` fields beyond the end of the
		 * original array will be initialized to 0, and any new `AvailObject`
		 * slots will contain a Java `null` (requiring initialization by the
		 * client).
		 *
		 *
		 * @param descriptor
		 * A descriptor.
		 * @param objectToCopy
		 * The object from which to copy corresponding fields.
		 * @param deltaObjectSlots
		 * How many AvailObject fields to add (or if negative, to
		 * subtract).
		 * @param deltaIntegerSlots
		 * How many long fields to add (or if negative, to subtract).
		 * @return A new object.
		 */
		@JvmStatic
		fun newLike(
			descriptor: AbstractDescriptor,
			objectToCopy: AvailObjectRepresentation,
			deltaObjectSlots: Int,
			deltaIntegerSlots: Int
		): AvailObject {
			assert(deltaObjectSlots == 0 || descriptor.hasVariableObjectSlots)
			assert(deltaIntegerSlots == 0 || descriptor.hasVariableIntegerSlots)
			assert(descriptor.javaClass == objectToCopy.descriptor().javaClass)
			val newObjectSlotCount = objectToCopy.objectSlots.size + deltaObjectSlots
			assert(newObjectSlotCount >= descriptor.numberOfFixedObjectSlots)
			val newIntegerSlotCount = objectToCopy.longSlots.size + deltaIntegerSlots
			assert(newIntegerSlotCount >= descriptor.numberOfFixedIntegerSlots)
			val newObject = AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				newObjectSlotCount - descriptor.numberOfFixedObjectSlots,
				newIntegerSlotCount - descriptor.numberOfFixedIntegerSlots,
				descriptor)
			// Even though we define the private fields in this class we aren't
			// allowed to access them in an instance of something that we know is a
			// subclass!  This surprising situation is probably related to separate
			// compilation and local verification of correctness by the bytecode
			// verifier.
			val weakerNewObject: AvailObjectRepresentation = newObject
			System.arraycopy(
				objectToCopy.longSlots,
				0,
				weakerNewObject.longSlots,
				0,
				min(
					objectToCopy.longSlots.size,
					weakerNewObject.longSlots.size))
			System.arraycopy(
				objectToCopy.objectSlots,
				0,
				weakerNewObject.objectSlots,
				0,
				min(
					objectToCopy.objectSlots.size,
					weakerNewObject.objectSlots.size))
			return newObject
		}

		/**
		 * A reusable empty array of [AvailObject]s for objects that have no
		 * object slots.
		 */
		private val emptyObjectSlots = arrayOfNulls<AvailObject>(0)

		/**
		 * A reusable empty array of `int`s for objects that have no int
		 * slots.
		 */
		private val emptyIntegerSlots = LongArray(0)
	}
}
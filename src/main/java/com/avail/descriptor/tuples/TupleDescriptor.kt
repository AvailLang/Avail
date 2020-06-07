/*
 * TupleDescriptor.java
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
package com.avail.descriptor.tuples

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.*
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import com.avail.descriptor.tuples.TupleDescriptor.IntegerSlots
import com.avail.descriptor.types.*
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.IteratorNotNull
import com.avail.utility.MutableInt
import com.avail.utility.json.JSONWriter
import java.nio.ByteBuffer
import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * `TupleDescriptor` is an abstract descriptor class under which all tuple
 * representations are defined (not counting [bottom][BottomTypeDescriptor] and
 * [transparent indirections][IndirectionDescriptor]).  It defines a
 * [HASH_OR_ZERO][IntegerSlots.HASH_OR_ZERO] integer slot which must be defined
 * in all subclasses.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `TupleDescriptor`.
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
@Suppress("UNCHECKED_CAST")
abstract class TupleDescriptor protected constructor(
	mutability: Mutability?,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum?>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum?>?) : Descriptor(
	mutability!!,
	TypeTag.TUPLE_TAG,
	objectSlotsEnumClass,
	integerSlotsEnumClass)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32 can
		 * be used by other [BitField]s in subclasses of [TupleDescriptor].
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
			e === IntegerSlots.HASH_AND_MORE

	override fun o_NameForDebugger(self: AvailObject): String =
		"${super.o_NameForDebugger(self)}: tupleSize=${self.tupleSize()}"

	override fun o_SetHashOrZero(self: AvailObject, value: Int)
	{
		if (isShared)
		{
			synchronized(self) {
				// The synchronized section is only to ensure other BitFields
				// in the same long slot don't get clobbered.
				self.setSlot(IntegerSlots.HASH_OR_ZERO, value)
			}
		}
		else
		{
			self.setSlot(IntegerSlots.HASH_OR_ZERO, value)
		}
	}

	// If the tuple is shared, its elements can't be in flux, so its hash is
	// stably computed by any interested thread.  And seeing a zero when the
	// hash has been computed by another thread is safe, since it forces the
	// reading thread to recompute the hash.  On the other hand, if the
	// tuple isn't shared then only one thread can be reading or writing the
	// hash field.  So either way we don't need synchronization.
	override fun o_HashOrZero(self: AvailObject): Int =
		self.slot(IntegerSlots.HASH_OR_ZERO)

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		val size = self.tupleSize()
		if (size == 0)
		{
			builder.append("<>")
			return
		}
		if (self.isString)
		{
			builder.append('"')
			for (i in 1 .. size)
			{
				when (val c = self.tupleCodePointAt(i))
				{
					'\"'.toInt(), '\\'.toInt() ->
					{
						builder.appendCodePoint('\\'.toInt())
						builder.appendCodePoint(c)
					}
					'\n'.toInt() -> builder.append("\\n")
					'\r'.toInt() -> builder.append("\\r")
					'\t'.toInt() -> builder.append("\\t")
					in 0 .. 31, 127 -> builder.append(String.format("\\(%x)", c))
					else -> builder.appendCodePoint(c)
				}
			}
			builder.appendCodePoint('"'.toInt())
			return
		}
		val strings = mutableListOf<String>()
		var totalChars = 0
		var anyBreaks = false
		for (i in 1 .. size)
		{
			val element: A_BasicObject = self.tupleAt(i)
			val localBuilder = StringBuilder()
			element.printOnAvoidingIndent(
				localBuilder,
				recursionMap,
				indent + 1)
			totalChars += localBuilder.length
			if (!anyBreaks)
			{
				anyBreaks = localBuilder.indexOf("\n") >= 0
			}
			strings.add(localBuilder.toString())
		}
		builder.append('<')
		val breakElements = (strings.size > 1
							 && (anyBreaks || totalChars > 60))
		for (i in strings.indices)
		{
			if (i > 0)
			{
				builder.append(",")
				if (!breakElements)
				{
					builder.append(" ")
				}
			}
			if (breakElements)
			{
				builder.append("\n")
				for (j in indent downTo 1)
				{
					builder.append("\t")
				}
			}
			builder.append(strings[i])
		}
		builder.append('>')
	}

	abstract override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean

	override fun o_EqualsAnyTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean
	{
		// Compare this arbitrary Tuple and the given arbitrary tuple.
		if (self.sameAddressAs(aTuple))
		{
			return true
		}
		// Compare sizes...
		val size = self.tupleSize()
		if (size != aTuple.tupleSize())
		{
			return false
		}
		if (o_Hash(self) != aTuple.hash())
		{
			return false
		}
		for (i in 1 .. size)
		{
			if (!o_TupleAt(self, i).equals(aTuple.tupleAt(i)))
			{
				return false
			}
		}
		if (self.isBetterRepresentationThan(aTuple))
		{
			if (!aTuple.descriptor().isShared)
			{
				self.makeImmutable()
				aTuple.becomeIndirectionTo(self)
			}
		}
		else
		{
			if (!isShared)
			{
				aTuple.makeImmutable()
				self.becomeIndirectionTo(aTuple)
			}
		}
		return true
	}

	// Default to generic tuple comparison.
	override fun o_EqualsByteString(
		self: AvailObject,
		aByteString: A_String): Boolean = o_EqualsAnyTuple(self, aByteString)

	// Default to generic tuple comparison.
	override fun o_EqualsByteTuple(
		self: AvailObject,
		aByteTuple: A_Tuple): Boolean = o_EqualsAnyTuple(self, aByteTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsByteArrayTuple(
		self: AvailObject,
		aByteArrayTuple: A_Tuple): Boolean =
			o_EqualsAnyTuple(self, aByteArrayTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsByteBufferTuple(
		self: AvailObject,
		aByteBufferTuple: A_Tuple): Boolean =
			o_EqualsAnyTuple(self, aByteBufferTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsIntegerIntervalTuple(
		self: AvailObject,
		anIntegerIntervalTuple: A_Tuple): Boolean =
			o_EqualsAnyTuple(self, anIntegerIntervalTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsIntTuple(
		self: AvailObject,
		anIntTuple: A_Tuple): Boolean = o_EqualsAnyTuple(self, anIntTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsReverseTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean = o_EqualsAnyTuple(self, aTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsSmallIntegerIntervalTuple(
		self: AvailObject,
		aSmallIntegerIntervalTuple: A_Tuple): Boolean =
			o_EqualsAnyTuple(self, aSmallIntegerIntervalTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsRepeatedElementTuple(
		self: AvailObject,
		aRepeatedElementTuple: A_Tuple): Boolean =
			o_EqualsAnyTuple(self, aRepeatedElementTuple)

	// Default to generic comparison.
	override fun o_EqualsNybbleTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean = o_EqualsAnyTuple(self, aTuple)

	// Default to generic comparison.
	override fun o_EqualsObjectTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean = o_EqualsAnyTuple(self, aTuple)

	// Default to generic tuple comparison.
	override fun o_EqualsTwoByteString(
		self: AvailObject,
		aString: A_String): Boolean = o_EqualsAnyTuple(self, aString)

	// Given two objects that are known to be equal, is the first one in a
	// better form (more compact, more efficient, older generation) than
	// the second one?
	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean =
			self.bitsPerEntry() < (anotherObject as A_Tuple).bitsPerEntry()

	override fun o_IsInstanceOfKind(self: AvailObject, aType: A_Type): Boolean
	{
		if (aType.isSupertypeOfPrimitiveTypeEnum(
				TypeDescriptor.Types.NONTYPE))
		{
			return true
		}
		if (!aType.isTupleType)
		{
			return false
		}
		// See if it's an acceptable size...
		val tupleSize = self.tupleSize()
		if (!aType.sizeRange().rangeIncludesInt(tupleSize))
		{
			return false
		}
		// The tuple's size is in range.
		val typeTuple = aType.typeTuple()
		val breakIndex = tupleSize.coerceAtMost(typeTuple.tupleSize())
		for (i in 1 .. breakIndex)
		{
			if (!self.tupleAt(i).isInstanceOf(typeTuple.tupleAt(i)))
			{
				return false
			}
		}
		if (breakIndex + 1 > tupleSize)
		{
			return true
		}
		val defaultTypeObject = aType.defaultType()
		return (defaultTypeObject.isSupertypeOfPrimitiveTypeEnum(
				TypeDescriptor.Types.ANY)
			|| self.tupleElementsInRangeAreInstancesOf(
		breakIndex + 1, tupleSize, defaultTypeObject))
	}

	// We could synchronize if the object isShared(), but why bother?  The
	// hash computation is stable, so we'll only compute and write what
	// other threads might already be writing.  Even reading a zero after
	// reading the true hash isn't a big deal.
	override fun o_Hash(self: AvailObject): Int = hash(self)

	override fun o_Kind(self: AvailObject): A_Type
	{
		val tupleOfTypes = self.copyAsMutableObjectTuple()
		val tupleSize = self.tupleSize()
		for (i in 1 .. tupleSize)
		{
			tupleOfTypes.tupleAtPuttingCanDestroy(
				i,
				AbstractEnumerationTypeDescriptor
					.instanceTypeOrMetaOn(self.tupleAt(i)),
				true)
		}
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			fromInt(self.tupleSize()).kind(),
			tupleOfTypes,
			BottomTypeDescriptor.bottom())
	}

	abstract override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean

	override fun o_CompareFromToWithAnyTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		var index1 = startIndex1
		var index2 = startIndex2
		while (index1 <= endIndex1)
		{
			if (!self.tupleAt(index1).equals(aTuple.tupleAt(index2)))
			{
				return false
			}
			index1++
			index2++
		}
		return true
	}

	override fun o_CompareFromToWithByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aByteString,
				startIndex2)

	override fun o_CompareFromToWithByteTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aByteTuple,
				startIndex2)

	override fun o_CompareFromToWithByteArrayTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteArrayTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aByteArrayTuple,
				startIndex2)

	// Compare sections of two tuples. Default to generic comparison.
	override fun o_CompareFromToWithByteBufferTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteBufferTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aByteBufferTuple,
				startIndex2)

	// Compare sections of two tuples. Default to generic comparison.
	override fun o_CompareFromToWithIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				anIntegerIntervalTuple,
				startIndex2)

	// Compare sections of two tuples. Default to generic comparison.
	override fun o_CompareFromToWithIntTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self, startIndex1, endIndex1, anIntTuple, startIndex2)

	// Compare sections of two tuples. Default to generic comparison.
	override fun o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aSmallIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aSmallIntegerIntervalTuple,
				startIndex2)

	// Compare sections of two tuples. Default to generic comparison.
	override fun o_CompareFromToWithRepeatedElementTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aRepeatedElementTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aRepeatedElementTuple,
				startIndex2)

	override fun o_CompareFromToWithNybbleTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aNybbleTuple,
				startIndex2)

	// Compare sections of two tuples. Default to generic comparison.
	override fun o_CompareFromToWithObjectTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				anObjectTuple,
				startIndex2)

	override fun o_CompareFromToWithTwoByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTwoByteString: A_String,
		startIndex2: Int): Boolean =
			o_CompareFromToWithAnyTupleStartingAt(
				self,
				startIndex1,
				endIndex1,
				aTwoByteString,
				startIndex2)

	override fun o_ConcatenateTuplesCanDestroy(
		self: AvailObject,
		canDestroy: Boolean): A_Tuple
	{
		// Take a tuple of tuples and answer one big tuple constructed by
		// concatenating the subtuples together.
		val tupleSize = self.tupleSize()
		if (tupleSize == 0)
		{
			return emptyTuple()
		}
		var accumulator: A_Tuple = self.tupleAt(1)
		if (canDestroy)
		{
			for (i in 2 .. tupleSize)
			{
				accumulator = accumulator.concatenateWith(
					self.tupleAt(i), true)
			}
		}
		else
		{
			self.makeImmutable()
			for (i in 2 .. tupleSize)
			{
				accumulator = accumulator.concatenateWith(
					self.tupleAt(i).makeImmutable(), true)
			}
		}
		return accumulator
	}

	/**
	 * Subclasses should override to deal with short subranges and efficient
	 * copying techniques.  Here we pretty much just create a [subrange
	 * tuple][SubrangeTupleDescriptor].
	 */
	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		val tupleSize = self.tupleSize()
		assert(1 <= start && start <= end + 1 && end <= tupleSize)
		val size = end - start + 1
		if (size == 0)
		{
			if (isMutable && canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			return emptyTuple()
		}
		if (size == tupleSize)
		{
			if (isMutable && !canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		return SubrangeTupleDescriptor.createSubrange(self, start, size)
	}

	override fun o_ExtractNybbleFromTupleAt(
		self: AvailObject, index: Int): Byte
	{
		// Get the element at the given index in the tuple object, and extract a
		// nybble from it. Fail if it's not a nybble. Obviously overridden for
		// speed in NybbleTupleDescriptor.
		val nyb = self.tupleIntAt(index)
		assert(nyb and 15.inv() == 0)
		return nyb.toByte()
	}

	// Compute object's hash value over the given range.
	override fun o_HashFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): Int =
			if (startIndex == 1 && endIndex == self.tupleSize()) self.hash()
			else self.computeHashFromTo(startIndex, endIndex)

	abstract override fun o_TupleAt(self: AvailObject, index: Int): AvailObject

	abstract override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple

	override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int =
		self.tupleAt(index).codePoint()

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int =
		self.tupleAt(index).extractInt()

	override fun o_AsSet(self: AvailObject): A_Set =
		generateSetFrom(self.tupleSize(), self.iterator())

	override fun o_IsTuple(self: AvailObject): Boolean = true

	override fun o_IsString(self: AvailObject): Boolean
	{
		val limit = self.tupleSize()
		for (i in 1 .. limit)
		{
			if (!self.tupleAt(i).isCharacter)
			{
				return false
			}
		}
		return true
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple =
		ReverseTupleDescriptor.createReverseTuple(self)

	abstract override fun o_TupleSize(self: AvailObject): Int

	@ThreadSafe
	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation
	{
		val size = self.tupleSize()
		if (size == 0)
		{
			return SerializerOperation.NYBBLE_TUPLE
		}

		// Examine the first element to detect homogenous cases like numeric
		// tuples or strings.
		val firstElement = self.tupleAt(1)
		if (firstElement.isCharacter)
		{
			// See if we can use a string-like representation.
			var maxCodePoint: Int = firstElement.codePoint()
			for (i in 2 .. size)
			{
				val element = self.tupleAt(i)
				if (!element.isCharacter)
				{
					return SerializerOperation.GENERAL_TUPLE
				}
				maxCodePoint = maxCodePoint.coerceAtLeast(element.codePoint())
			}
			return when
			{
				maxCodePoint <= 255 -> SerializerOperation.BYTE_STRING
				maxCodePoint <= 65535 -> SerializerOperation.SHORT_STRING
				else -> SerializerOperation.ARBITRARY_STRING
			}
		}
		if (firstElement.isInt)
		{
			// See if we can use a numeric-tuple representation.
			var maxInteger = firstElement.extractInt()
			if (maxInteger < 0)
			{
				return SerializerOperation.GENERAL_TUPLE
			}
			for (i in 2 .. size)
			{
				val element = self.tupleAt(i)
				if (!element.isInt)
				{
					return SerializerOperation.GENERAL_TUPLE
				}
				val intValue = element.extractInt()
				if (intValue < 0)
				{
					return SerializerOperation.GENERAL_TUPLE
				}
				maxInteger = maxInteger.coerceAtLeast(intValue)
			}
			return when
			{
				maxInteger <= 15 -> SerializerOperation.NYBBLE_TUPLE
				maxInteger <= 255 -> SerializerOperation.BYTE_TUPLE
				else -> SerializerOperation.INT_TUPLE
			}
		}
		return SerializerOperation.GENERAL_TUPLE
	}

	/**
	 * Compute the hash value from the object's data. The result should be an
	 * `int`.  To keep the rehashing cost down for concatenated tuples, we
	 * use a non-commutative hash function. If the tuple has elements with hash
	 * values
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <msub><mi>h</mi><mn>1</mn></msub>
	 * <mi></mi>
	 * <msub><mi>h</mi><mi>n</mi></msub></mrow></math> ,
	 * we use the formula
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <mrow>
	 * <msub><mi>h</mi><mn>1</mn></msub>
	 * <mo></mo>
	 * <msup><mi>a</mi><mn>1</mn></msup></mrow>
	 * <mo>+</mo>
	 * <mrow>
	 * <msub><mi>h</mi><mn>2</mn></msub>
	 * <mo></mo>
	 * <msup><mi>a</mi><mn>2</mn></msup></mrow>
	 * <mo>+</mo>
	 * <mi></mi>
	 * <mo>+</mo>
	 * <mrow>
	 * <msub><mi>h</mi><mi>n</mi></msub>
	 * <mo></mo>
	 * <msup><mi>a</mi><mi>n</mi></msup></mrow></mrow> /</math>.
	 * This can be rewritten as
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <munderover>
	 * <mo></mo>
	 * <mrow><mi>i</mi><mo>=</mo><mn>1</mn></mrow>
	 * <mi>n</mi></munderover>
	 * <mrow>
	 * <msub><mi>h</mi><mi>i</mi></msub>
	 * <mo></mo>
	 * <msup><mi>a</mi><mi>i</mi></msup></mrow></mrow></math>
	 * ). The constant `a` is chosen as a primitive element of the group
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <mfenced>
	 * <msub>
	 * <mo></mo>
	 * <msup><mn>2</mn><mn>32</mn></msup></msub>
	 * <mo></mo></mfenced> </mrow></math>,
	 * specifically 1,664,525, as taken from <cite>Knuth, The Art of Computer
	 * Programming, Vol. 2, 2<sup>nd</sup> ed., page 102, row 26</cite>. See
	 * also pages 19, 20, theorems B and C. The period of this cycle is
	 * 2<sup>30</sup>.
	 *
	 * To append an (n+1)<sup>st</sup> element to a tuple, one can compute
	 * the new hash by adding
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <msub>
	 * <mi>h</mi>
	 * <mrow><mi>n</mi><mo></mo><mn>1</mn></mrow></msub>
	 * <mo></mo>
	 * <msup>
	 * <mi>a</mi>
	 * <mrow><mi>n</mi><mo></mo><mn>1</mn></mrow></msup></mrow></math>
	 * to the previous hash.  Similarly, concatenating two tuples of length x
	 * and y is a simple matter of multiplying the right tuple's hash by
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <msup><mi>a</mi><mi>x</mi></msup></mrow></math>
	 * and adding it to the left tuple's hash.
	 *
	 * The element hash values are exclusive-ored with
	 * [a randomly chosen constant][preToggle] before being used, to
	 * help prevent similar nested tuples from producing equal hashes.
	 *
	 * @param self
	 *   The object containing elements to hash.
	 * @param start
	 *   The first index of elements to hash.
	 * @param end
	 *   The last index of elements to hash.
	 */
	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		var hash = 0
		for (index in end downTo start)
		{
			val itemHash = self.tupleAt(index).hash() xor preToggle
			hash = (hash + itemHash) * AvailObject.multiplier
		}
		return hash
	}

	override fun o_AsNativeString(self: AvailObject): String
	{
		val size = self.tupleSize()
		val builder = StringBuilder(size)
		for (i in 1 .. size)
		{
			builder.appendCodePoint(self.tupleCodePointAt(i))
		}
		return builder.toString()
	}

	/**
	 * Answer a mutable copy of object that holds ints.
	 */
	override fun o_CopyAsMutableIntTuple(self: AvailObject): A_Tuple
	{
		val size = self.tupleSize()
		val result = IntTupleDescriptor.generateIntTupleFrom(
			size) { index: Int -> self.tupleIntAt(index) }
		result.setHashOrZero(self.hashOrZero())
		return result
	}

	/**
	 * Answer a mutable copy of object that holds arbitrary objects.
	 */
	override fun o_CopyAsMutableObjectTuple(self: AvailObject): A_Tuple
	{
		val size = self.tupleSize()
		val result = ObjectTupleDescriptor.generateObjectTupleFrom(size)
		{ index: Int -> self.tupleAt(index) }
		result.setHashOrZero(self.hashOrZero())
		return result
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean
	{
		for (index in startIndex .. endIndex)
		{
			if (!self.tupleAt(index).isInstanceOf(type))
			{
				return false
			}
		}
		return true
	}

	/**
	 * A simple [Iterator] over a tuple's elements.
	 *
	 * @property tuple
	 *   The tuple over which to iterate.
	 *
	 * @constructor
	 * Construct a [TupleIterator].
	 *
	 * @param tuple
	 *   The tuple over which to iterate.
	 */
	private class TupleIterator internal constructor(
		private val tuple: AvailObject) : IteratorNotNull<AvailObject>
	{
		/**
		 * The size of the tuple.
		 */
		private val size: Int = tuple.tupleSize()

		/**
		 * The index of the next [element][AvailObject].
		 */
		var index = 1

		override fun hasNext(): Boolean = index <= size

		override fun next(): AvailObject
		{
			if (index > size)
			{
				throw NoSuchElementException()
			}
			return tuple.tupleAt(index++)
		}

		override fun remove()
		{
			throw UnsupportedOperationException()
		}

	}

	/**
	 * Index-based split-by-two, lazily initialized Spliterator
	 *
	 * @property tuple
	 *   The tuple being spliterated.
	 * @property index
	 *   The current one-based index into the tuple.
	 * @property fence
	 *   One past the last one-based index to visit.
	 *
	 * @constructor
	 * Create an instance for spliterating over the tuple starting at the
	 * origin and stopping just before the fence.  Both indices are
	 * one-based.
	 *
	 * @param tuple
	 *   The tuple to spliterate.
	 * @param index
	 *   The starting one-based index.
	 * @param fence
	 *   One past the last index to visit.
	 */
	private class TupleSpliterator internal constructor(
		private val tuple: A_Tuple,
		private var index: Int,
		private val fence: Int) : Spliterator<AvailObject>
	{

		override fun trySplit(): TupleSpliterator?
		{
			val remaining = fence - index
			if (remaining < 2)
			{
				return null
			}
			val oldIndex = index
			index += remaining ushr 1
			return TupleSpliterator(tuple, oldIndex, index)
		}

		override fun tryAdvance(action: Consumer<in AvailObject>): Boolean
		{
			if (index < fence)
			{
				action.accept(tuple.tupleAt(index++))
				return true
			}
			return false
		}

		override fun forEachRemaining(action: Consumer<in AvailObject>)
		{
			for (i in index until fence)
			{
				action.accept(tuple.tupleAt(i))
			}
			index = fence
		}

		override fun estimateSize(): Long = (fence - index).toLong()

		override fun characteristics(): Int =
			(Spliterator.ORDERED
				or Spliterator.SIZED
				or Spliterator.SUBSIZED
				or Spliterator.NONNULL
				or Spliterator.IMMUTABLE)
	}

	override fun o_Iterator(self: AvailObject): IteratorNotNull<AvailObject>
	{
		self.makeImmutable()
		return TupleIterator(self)
	}

	override fun o_Spliterator(self: AvailObject): Spliterator<AvailObject>
	{
		self.makeImmutable()
		return TupleSpliterator(self, 1, self.tupleSize() + 1)
	}

	override fun o_Stream(self: AvailObject): Stream<AvailObject>
	{
		self.makeImmutable()
		return StreamSupport.stream(self.spliterator(), false)
	}

	override fun o_ParallelStream(self: AvailObject): Stream<AvailObject>
	{
		self.makeImmutable()
		return StreamSupport.stream(self.spliterator(), true)
	}

	override fun o_MarshalToJava(
		self: AvailObject, classHint: Class<*>?): Any? =
			if (self.isString)
			{
				self.asNativeString()
			}
			else super.o_MarshalToJava(self, classHint)

	override fun o_ShowValueInNameForDebugger(self: AvailObject): Boolean =
		self.isString

	/**
	 * Construct a new tuple of arbitrary [Avail objects][AvailObject] based on
	 * the given tuple, but with an additional element appended.  The elements
	 * may end up being shared between the original and the copy, so the client
	 * must ensure that either the elements are marked immutable, or one of the
	 * copies is not kept after the call.
	 */
	abstract override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple

	// TreeTupleDescriptor overrides this.
	override fun o_TreeTupleLevel(self: AvailObject): Int = 0

	abstract override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple

	/**
	 * Transfer the specified range of bytes into the provided [ByteBuffer].
	 * The `ByteBuffer` should have enough room to store the required number of
	 * bytes.
	 */
	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		for (index in startIndex .. endIndex)
		{
			outputByteBuffer.put(self.tupleIntAt(index).toByte())
		}
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		if (self.isString)
		{
			writer.write(self.asNativeString())
		}
		else
		{
			writer.startArray()
			for (o in self)
			{
				o.writeTo(writer)
			}
			writer.endArray()
		}
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		if (self.isString)
		{
			writer.write(self.asNativeString())
		}
		else
		{
			writer.startArray()
			for (o in self)
			{
				o.writeSummaryTo(writer)
			}
			writer.endArray()
		}
	}

	/** A static inner type that delays initialization until first use.  */
	private object Empty
	{
		/** The empty tuple.  */
		var emptyTuple: AvailObject

		init
		{
			// Create the empty tuple.
			val t: A_Tuple = NybbleTupleDescriptor.generateNybbleTupleFrom(0)
			{
				assert(false) { "This should be an empty nybble tuple" }
				0
			}
			t.hash()
			emptyTuple = t.makeShared()
		}
	}

	companion object
	{
		/**
		 * The hash value is stored raw in the object's hashOrZero slot if it
		 * has been computed, otherwise that slot is zero. If a zero is
		 * detected, compute the hash and store it in hashOrZero. Note that the
		 * hash can (extremely rarely) be zero, in which case the hash has to be
		 * computed each time.
		 *
		 * @param self
		 *   An object.
		 * @return
		 *   The hash.
		 */
		private fun hash(self: A_Tuple): Int
		{
			var hash = self.hashOrZero()
			if (hash == 0 && self.tupleSize() > 0)
			{
				hash = computeHashForObject(self)
				self.setHashOrZero(hash)
			}
			return hash
		}

		/**
		 * Compute the object's hash value.
		 *
		 * @param self
		 *   The object to hash.
		 * @return
		 *   The hash value.
		 */
		private fun computeHashForObject(self: A_Tuple): Int =
			self.computeHashFromTo(1, self.tupleSize())

		/**
		 * Return the empty `TupleDescriptor tuple`.  Other empty tuples can be
		 * created, but if you know the tuple is empty you can save time and
		 * space by returning this one.
		 *
		 * @return
		 *   The tuple of size zero.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun emptyTuple(): AvailObject = Empty.emptyTuple

		/**
		 * Construct a Java [List] from the specified [tuple][TupleDescriptor].
		 * The elements are not made immutable.
		 *
		 * @param tuple
		 *   A tuple.
		 * @return
		 *   The corresponding list of objects.
		 * @param X
		 *   The Java type of the elements.
		 */
		@JvmStatic
		fun <X : A_BasicObject?> toList(tuple: A_Tuple): MutableList<X>
		{
			val list: MutableList<X> = ArrayList(tuple.tupleSize())
			for (element in tuple)
			{
				list.add(element as X)
			}
			return list
		}

		/**
		 * Construct an [AvailObject[]][AvailObject] from the specified
		 * [A_Tuple]. The elements are not made immutable.
		 *
		 * @param tuple
		 *   A tuple.
		 * @return
		 *   The corresponding Java array of AvailObjects.
		 */
		fun toArray(tuple: A_Tuple): Array<AvailObject?>
		{
			val size = tuple.tupleSize()
			val array = arrayOfNulls<AvailObject>(size)
			for (i in 0 until size)
			{
				array[i] = tuple.tupleAt(i + 1)
			}
			return array
		}

		/**
		 * Construct a new tuple of arbitrary [Avail objects][AvailObject] based
		 * on the given tuple, but with an occurrence of the specified element
		 * missing, if it was present at all.  The elements may end up being
		 * shared between the original and the copy, so the client must ensure
		 * that either the elements are marked immutable, or one of the copies
		 * is not kept after the call.  If the element is not found, then answer
		 * the original tuple.
		 *
		 * @param originalTuple
		 *   The original tuple of [Avail objects][AvailObject] on which to base
		 *   the new tuple.
		 * @param elementToExclude
		 *   The element that should should have an occurrence excluded from the
		 *   new tuple, if it was present.
		 * @return
		 *   The new tuple.
		 */
		fun tupleWithout(
			originalTuple: A_Tuple,
			elementToExclude: A_BasicObject?): A_Tuple
		{
			val originalSize = originalTuple.tupleSize()
			for (seekIndex in 1 .. originalSize)
			{
				if (originalTuple.tupleAt(seekIndex).equals(elementToExclude))
				{
					val index = MutableInt(1)
					return ObjectTupleDescriptor.generateObjectTupleFrom(
						originalSize - 1
					) { ignored: Int ->
						if (index.value == seekIndex)
						{
							// Skip that element.
							index.value++
						}
						originalTuple.tupleAt(index.value++)
					}
				}
			}
			return originalTuple
		}

		/**
		 * Construct a new tuple of ints. Use the most compact representation
		 * that can still represent each supplied [Integer].
		 *
		 * @param list
		 *   The list of Java [Integer]s to assemble in a tuple.
		 * @return
		 *   A new mutable tuple of integers.
		 */
		@JvmStatic
		fun tupleFromIntegerList(list: List<Int>): A_Tuple
		{
			if (list.isEmpty())
			{
				return emptyTuple()
			}

			val minValue = list.min()!!
			if (minValue >= 0)
			{
				val maxValue = list.max()!!
				if (maxValue <= 15)
				{
					return NybbleTupleDescriptor
						.generateNybbleTupleFrom(list.size) { list[it - 1] }
				}
				if (maxValue <= 255)
				{
					return ByteTupleDescriptor.generateByteTupleFrom(list.size)
						{ list[it - 1] }
				}
			}
			return IntTupleDescriptor
				.generateIntTupleFrom(list.size) { list[it - 1] }
		}

		/**
		 * Four tables, each containing powers of [AvailObject.multiplier]. The
		 * 0th table contains M^i for i=0..255, the 1st table contains M^(256*i)
		 * for i=0..255,... and the 3rd table contains M^((256^3)*i) for
		 * i=0..255.
		 */
		private val powersOfMultiplier = Array(4) { IntArray(256) }

		/**
		 * Compute [AvailObject.multiplier] raised to the specified power,
		 * truncated to an int.
		 *
		 * @param anInteger
		 *   The exponent by which to raise the base [AvailObject.multiplier].
		 * @return
		 *   [AvailObject.multiplier] raised to the specified power.
		 */
		@JvmStatic
		fun multiplierRaisedTo(anInteger: Int): Int =
			(powersOfMultiplier[0][anInteger and 0xFF]
				* powersOfMultiplier[1][anInteger shr 8 and 0xFF]
				* powersOfMultiplier[2][anInteger shr 16 and 0xFF]
				* powersOfMultiplier[3][anInteger shr 24 and 0xFF])

		/**
		 * The constant by which each element's hash should be XORed prior to
		 * combining them.  This reduces the chance of systematic collisions due
		 * to using the same elements in different patterns of nested tuples.
		 */
		const val preToggle = 0x71E570A6

		init
		{
			var scaledMultiplier = AvailObject.multiplier
			for (subtable in powersOfMultiplier)
			{
				var power = 1
				for (i in 0 .. 255)
				{
					subtable[i] = power
					power *= scaledMultiplier
				}
				scaledMultiplier = power
			}
		}
	}
}
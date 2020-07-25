/*
 * RepeatedElementTupleDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
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

import com.avail.annotations.HideFieldInDebugger
import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithRepeatedElementTupleStartingAt
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ByteStringDescriptor.Companion.generateByteString
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.RepeatedElementTupleDescriptor.ObjectSlots.ELEMENT
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import com.avail.descriptor.tuples.TwoByteStringDescriptor.Companion.generateTwoByteString
import com.avail.descriptor.types.A_Type
import java.util.*

/**
 * `RepeatedElementTupleDescriptor` represents a tuple with a single ELEMENT
 * repeated SIZE times. Note that SIZE is the number of tuple slots containing
 * the element and is therefore the size of the tuple.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 * Construct a new `RepeatedElementTupleDescriptor`.
 *
 * @param mutability
 *   How its instances can be shared or modified.
 */
class RepeatedElementTupleDescriptor private constructor(mutability: Mutability)
	: TupleDescriptor(
		mutability, ObjectSlots::class.java, IntegerSlots::class.java)
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
		@HideFieldInDebugger
		HASH_AND_MORE;

		companion object
		{
			/**
			 * The number of elements in the tuple.
			 *
			 * The API's [tuple size accessor][A_Tuple.tupleSize] currently
			 * returns a Java `int`, because there wasn't much of a problem
			 * limiting manually-constructed tuples to two billion elements.
			 * This restriction will eventually be removed.
			 */
			@JvmField
			val SIZE = BitField(HASH_AND_MORE, 32, 32)

			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)

			init
			{
				assert(TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
						   == HASH_AND_MORE.ordinal)
				assert(TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
					HASH_OR_ZERO))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/** The element to be repeated.  */
		ELEMENT
	}

	override fun o_IsRepeatedElementTuple(self: AvailObject): Boolean = true

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		val size = self.slot(IntegerSlots.SIZE)
		if (size < minimumRepeatSize)
		{
			super.printObjectOnAvoidingIndent(
				self,
				builder,
				recursionMap,
				indent)
		}
		else
		{
			builder.append(size)
			builder.append(" of ")
			self.slot(ELEMENT).printOnAvoidingIndent(
				builder,
				recursionMap,
				indent + 1)
		}
	}

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		// Ensure parameters are in bounds
		val oldSize = self.slot(IntegerSlots.SIZE)
		assert(1 <= start && start <= end + 1 && end <= oldSize)
		val newSize = end - start + 1

		// If the requested copy is a proper subrange, create it.
		if (newSize != oldSize)
		{
			if (isMutable && canDestroy)
			{
				// Recycle the object.
				self.setSlot(IntegerSlots.SIZE, newSize)
				return self
			}
			return createRepeatedElementTuple(
				newSize, self.slot(ELEMENT))
		}

		// Otherwise, this method is requesting a full copy of the original.
		if (isMutable && !canDestroy)
		{
			self.makeImmutable()
		}
		return self
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean
	{
		return anotherObject.compareFromToWithRepeatedElementTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			self,
			startIndex1)
	}

	override fun o_CompareFromToWithRepeatedElementTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aRepeatedElementTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(aRepeatedElementTuple))
		{
			// The objects refer to the same memory, and the lengths being
			// compared are the same, and we don't care about the offsets, so
			// they're equal.
			return true
		}
		if (self.slot(ELEMENT).equals(
				aRepeatedElementTuple.tupleAt(1)))
		{
			// The elements are the same, so the subranges must be as well.
			// Coalesce equal tuples as a nicety.
			if (self.slot(IntegerSlots.SIZE)
				== aRepeatedElementTuple.tupleSize())
			{
				// Indirect one to the other if it is not shared.
				if (!isShared)
				{
					aRepeatedElementTuple.makeImmutable()
					self.becomeIndirectionTo(aRepeatedElementTuple)
				}
				else if (!aRepeatedElementTuple.descriptor().isShared)
				{
					self.makeImmutable()
					aRepeatedElementTuple.becomeIndirectionTo(self)
				}
			}
			// Regardless of the starting positions, the subranges are the same.
			return true
		}

		// The elements differ, so the subranges must differ.
		return false
	}

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple
	{
		if (!canDestroy)
		{
			self.makeImmutable()
			otherTuple.makeImmutable()
		}

		// Assess the possibility that the concatenation will still be a
		// repeated element tuple.
		if (otherTuple.isRepeatedElementTuple)
		{
			val otherDirect = otherTuple.traversed()
			val element = self.slot(ELEMENT)

			// If the other's element is the same as mine,
			if (element.equals(otherDirect.slot(ELEMENT)))
			{
				// then we can be concatenated.
				val newSize = self.slot(IntegerSlots.SIZE) +
							  otherDirect.slot(IntegerSlots.SIZE)

				// If we can do replacement in place,
				// use me for the return value.
				if (isMutable)
				{
					self.setSlot(IntegerSlots.SIZE, newSize)
					self.setHashOrZero(0)
					return self
				}
				// Or the other one.
				if (otherTuple.descriptor().isMutable)
				{
					otherDirect.setSlot(IntegerSlots.SIZE, newSize)
					otherDirect.setHashOrZero(0)
					return otherDirect
				}

				// Otherwise, create a new repeated element tuple.
				return createRepeatedElementTuple(newSize, element)
			}
		}
		return if (otherTuple.treeTupleLevel() == 0)
		{
			if (otherTuple.tupleSize() == 0)
			{
				// Trees aren't allowed to have empty subtuples.
				self
			}
			else
			{
				createTwoPartTreeTuple(self, otherTuple, 1, 0)
			}
		}
		else
		{
			concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsRepeatedElementTuple(self)

	override fun o_EqualsRepeatedElementTuple(
		self: AvailObject,
		aRepeatedElementTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		if (self.sameAddressAs(aRepeatedElementTuple))
		{
			return true
		}

		// If the objects do not refer to the same memory, check if the tuples
		// are identical.
		val secondTraversed = aRepeatedElementTuple.traversed()

		// Check that the slots match.
		val firstHash = self.slot(IntegerSlots.HASH_OR_ZERO)
		val secondHash = secondTraversed.slot(IntegerSlots.HASH_OR_ZERO)
		if (firstHash != 0 && secondHash != 0 && firstHash != secondHash)
		{
			return false
		}
		if (self.slot(IntegerSlots.SIZE) != secondTraversed.slot(
				IntegerSlots.SIZE))
		{
			return false
		}
		if (!self.slot(ELEMENT).equals(
				secondTraversed.slot(ELEMENT)))
		{
			return false
		}

		// All the slots match. Indirect one to the other if it is not shared.
		if (!isShared)
		{
			aRepeatedElementTuple.makeImmutable()
			self.becomeIndirectionTo(aRepeatedElementTuple)
		}
		else if (!aRepeatedElementTuple.descriptor().isShared)
		{
			self.makeImmutable()
			aRepeatedElementTuple.becomeIndirectionTo(self)
		}
		return true
	}

	// Consider a billion-element tuple. Since a repeated element tuple
	// requires only O(1) storage, irrespective of its size, the average
	// bits per entry is 0.
	override fun o_BitsPerEntry(self: AvailObject): Int = 0

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the value at the given index in the tuple object.
		// Every element in this tuple is identical.
		assert(index >= 1 && index <= self.slot(IntegerSlots.SIZE))
		return self.slot(ELEMENT)
	}

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		val size = self.slot(IntegerSlots.SIZE)
		assert(index in 1 .. size)
		val element = self.slot(ELEMENT)
		if (element.equals(newValueObject))
		{
			// Replacement is the same as the repeating element.
			if (!canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		if (size < 64)
		{
			// The result will be reasonably small, so make it flat.
			element.makeImmutable()
			var result: A_Tuple? = null
			if (element.isInt)
			{
				// Make it a numeric tuple.
				result = tupleFromIntegerList(
					Collections.nCopies(size, element.extractInt()))
			}
			else if (element.isCharacter)
			{
				// Make it a string.
				val codePoint: Int = element.codePoint()
				if (codePoint <= 255)
				{
					result = generateByteString(size) { codePoint }
				}
				else if (codePoint <= 65535)
				{
					result = generateTwoByteString(size) { codePoint }
				}
			}
			if (result === null)
			{
				result = generateObjectTupleFrom(size) { element }
			}
			// Replace the element, which might need to switch representation in
			// some cases which we assume are infrequent.
			return result.tupleAtPuttingCanDestroy(index, newValueObject, true)
		}
		// Otherwise, a flat tuple would be unacceptably large, so use append
		// and concatenate to construct what will probably be a tree tuple.
		val left = self.copyTupleFromToCanDestroy(
			1, index - 1, false)
		val right = self.copyTupleFromToCanDestroy(
			index + 1, size, false)
		return left.appendCanDestroy(newValueObject, true).concatenateWith(
			right, true)
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		if (self.slot(ELEMENT).equals(newElement))
		{
			val result =
				if (canDestroy && isMutable) self
				else newLike(mutable, self, 0, 0)
			result.setSlot(IntegerSlots.SIZE, self.slot(IntegerSlots.SIZE) + 1)
			result.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			return result
		}
		// Transition to a tree tuple.
		val singleton = tuple(newElement)
		return self.concatenateWith(singleton, canDestroy)
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		assert(1 <= index && index <= self.slot(IntegerSlots.SIZE))
		return self.slot(ELEMENT).extractInt()
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long
	{
		assert(1 <= index && index <= self.slot(IntegerSlots.SIZE))
		return self.slot(ELEMENT).extractLong()
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple = self

	override fun o_TupleSize(self: AvailObject): Int =
		self.slot(IntegerSlots.SIZE)

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean
	{
		return self.slot(ELEMENT).isInstanceOf(type)
	}

	override fun mutable(): RepeatedElementTupleDescriptor = mutable

	override fun immutable(): RepeatedElementTupleDescriptor = immutable

	override fun shared(): RepeatedElementTupleDescriptor = shared

	companion object
	{
		/**
		 * The minimum size for repeated element tuple creation. All tuples
		 * requested below this size will be created as standard tuples or the
		 * empty tuple.
		 */
		private const val minimumRepeatSize = 2

		/** The mutable [RepeatedElementTupleDescriptor].  */
		val mutable = RepeatedElementTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [RepeatedElementTupleDescriptor].  */
		private val immutable =
			RepeatedElementTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [RepeatedElementTupleDescriptor].  */
		private val shared = RepeatedElementTupleDescriptor(Mutability.SHARED)

		/**
		 * Create a new repeated element tuple according to the parameters.
		 *
		 * @param size
		 *   The number of repetitions of the element.
		 * @param element
		 *   The value to be repeated.
		 * @return
		 *   The new repeated element tuple.
		 */
		@JvmStatic
		fun createRepeatedElementTuple(
			size: Int,
			element: A_BasicObject): A_Tuple
		{
			// If there are no members in the range, return the empty tuple.
			if (size == 0)
			{
				return emptyTuple()
			}

			// If there are fewer than minimumRepeatSize members in this tuple,
			// create a normal tuple with them in it instead.
			return if (size < minimumRepeatSize)
			{
				tupleFromList<A_BasicObject>(Collections.nCopies(size, element))
			}
			else forceCreate(size, element)

			// No other efficiency shortcuts. Create a repeated element tuple.
		}

		/**
		 * Create a new RepeatedElement using the supplied arguments,
		 * regardless of the suitability of other representations.
		 *
		 * @param size
		 *   The number of repetitions of the element.
		 * @param element
		 *   The value to be repeated.
		 * @return
		 *   The new repeated element tuple.
		 */
		fun forceCreate(
			size: Int,
			element: A_BasicObject?
		): A_Tuple = mutable.create {
			setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			setSlot(IntegerSlots.SIZE, size)
			setSlot(ELEMENT, element!!)
		}
	}
}

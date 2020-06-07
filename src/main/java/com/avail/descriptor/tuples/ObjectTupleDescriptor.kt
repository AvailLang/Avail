/*
 * ObjectTupleDescriptor.java
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

import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.utility.IteratorNotNull
import java.util.*

/**
 * This is a representation for [tuples][TupleDescriptor] that can consist of
 * arbitrary [AvailObject]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ObjectTupleDescriptor`.
 *
 * @param mutability
 * The [mutability][Mutability] of the new descriptor.
 */
class ObjectTupleDescriptor private constructor(mutability: Mutability)
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
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
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
		/**
		 * The tuple elements themselves.
		 */
		TUPLE_AT_
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val originalSize = self.tupleSize()
		if (originalSize >= maximumCopySize)
		{
			// Transition to a tree tuple.
			val singleton = tuple(newElement)
			return self.concatenateWith(singleton, canDestroy)
		}
		if (!canDestroy)
		{
			newElement.makeImmutable()
			if (isMutable)
			{
				self.makeImmutable()
			}
		}
		val newTuple = newLike(mutable, self, 1, 0)
		newTuple.setSlot(ObjectSlots.TUPLE_AT_, originalSize + 1, newElement)
		newTuple.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
		return newTuple
	}

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 64

	override fun o_CompareFromToWithObjectTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		// Compare sections of two object tuples.
		if (self.sameAddressAs(anObjectTuple) && startIndex1 == startIndex2)
		{
			return true
		}
		// Compare actual entries.
		var index1 = startIndex1
		var index2 = startIndex2
		while (index1 <= endIndex1)
		{
			if (!self.tupleAt(index1).equals(anObjectTuple.tupleAt(index2)))
			{
				return false
			}
			index1++
			index2++
		}
		return true
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean =
			anotherObject.compareFromToWithObjectTupleStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				self,
				startIndex1)

	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		// See comment in superclass.  This method must produce the same value.
		var hash = 0
		for (index in end downTo start)
		{
			val itemHash = self.tupleAt(index).hash() xor preToggle
			hash = (hash + itemHash) * AvailObject.multiplier
		}
		return hash
	}

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple
	{
		val size1 = self.tupleSize()
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable()
			}
			return otherTuple
		}
		val size2 = otherTuple.tupleSize()
		if (size2 == 0)
		{
			if (!canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		val newSize = size1 + size2
		if (newSize <= maximumCopySize)
		{
			// Copy the objects.
			val deltaSlots = newSize - self.variableObjectSlotsCount()
			val result = newLike(mutable(), self, deltaSlots, 0)
			result.setSlotsFromTuple(ObjectSlots.TUPLE_AT_, size1 + 1, otherTuple, 1, size2)
			result.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			return result
		}
		if (!canDestroy)
		{
			self.makeImmutable()
			otherTuple.makeImmutable()
		}
		return if (otherTuple.treeTupleLevel() == 0)
		{
			TreeTupleDescriptor.createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			TreeTupleDescriptor.concatenateAtLeastOneTree(
				self, otherTuple, true)
		}
	}

	/**
	 * Answer a mutable copy of object that holds arbitrary objects.
	 */
	override fun o_CopyAsMutableObjectTuple(self: AvailObject): A_Tuple =
		newLike(mutable, self, 0, 0)

	/**
	 * If a subrange ends up getting constructed from this object tuple then it
	 * may leak memory.  The references that are out of bounds of the subrange
	 * might no longer be semantically reachable by Avail, but Java won't be
	 * able to collect them.  Eventually we'll have an Avail-specific garbage
	 * collector again, at which point we'll solve this problem for real – along
	 * with many others, I'm sure.
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
		if (size in 1 until tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable entries out.  In theory we could use
			// newLike() if start is 1.
			val result = createUninitialized(size)
			result.setSlotsFromObjectSlots(
				ObjectSlots.TUPLE_AT_,
				1,
				self,
				ObjectSlots.TUPLE_AT_,
				start,
				size)
			if (canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			else
			{
				result.makeSubobjectsImmutable()
			}
			result.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			return result
		}
		return super.o_CopyTupleFromToCanDestroy(
			self, start, end, canDestroy)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsObjectTuple(self)

	override fun o_EqualsObjectTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean
	{
		when
		{
			self.sameAddressAs(aTuple) -> return true
			o_TupleSize(self) != aTuple.tupleSize() -> return false
			o_Hash(self) != aTuple.hash() -> return false
			!self.compareFromToWithObjectTupleStartingAt(
				1,
				self.tupleSize(),
				aTuple,
				1) -> return false
			aTuple.isBetterRepresentationThan(self) ->
			{
				if (!isShared)
				{
					aTuple.makeImmutable()
					self.becomeIndirectionTo(aTuple)
				}
			}
			else ->
			{
				if (!aTuple.descriptor().isShared)
				{
					self.makeImmutable()
					aTuple.becomeIndirectionTo(self)
				}
			}
		}
		return true
	}

	override fun o_IsByteTuple(self: AvailObject): Boolean
	{
		// If it's cheap to check my elements, just do it.  This can help keep
		// representations smaller and faster when concatenating short, quickly
		// built object tuples that happen to only contain bytes onto the start
		// or end of other byte tuples.
		val tupleSize = self.tupleSize()
		if (tupleSize <= 5)
		{
			for (i in 1 .. tupleSize)
			{
				if (!self.slot(ObjectSlots.TUPLE_AT_, i).isUnsignedByte)
				{
					return false
				}
			}
			return true
		}
		return false
	}

	override fun o_IsIntTuple(self: AvailObject): Boolean
	{
		// If it's cheap to check my elements, just do it.  This can help keep
		// representations smaller and faster when concatenating short, quickly
		// built object tuples that happen to only contain ints onto the start
		// or end of other int tuples.
		val tupleSize = self.tupleSize()
		if (tupleSize <= 5)
		{
			for (i in 1 .. tupleSize)
			{
				if (!self.slot(ObjectSlots.TUPLE_AT_, i).isInt)
				{
					return false
				}
			}
			return true
		}
		return false
	}

	/**
	 * A simple [Iterator] over an object-tuple's elements.
	 *
	 * @property tuple
	 *   The tuple over which to iterate.
	 *
	 * @constructor
	 * Construct an [ObjectTupleIterator].
	 *
	 * @param tuple
	 *   The tuple over which to iterate.
	 */
	private class ObjectTupleIterator internal constructor(
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

		override fun hasNext(): Boolean =  index <= size

		override fun next(): AvailObject
		{
			if (index > size)
			{
				throw NoSuchElementException()
			}

			// It's safe to access the slot directly.  If the tuple is mutable
			// or immutable, no other thread can be changing it (and the caller
			// shouldn't while iterating), and if the tuple is shared, its
			// descriptor cannot be changed.
			return tuple.slot(ObjectSlots.TUPLE_AT_, index++)
		}

		override fun remove()
		{
			throw UnsupportedOperationException()
		}

	}

	override fun o_Iterator(self: AvailObject): IteratorNotNull<AvailObject>
	{
		self.makeImmutable()
		return ObjectTupleIterator(self)
	}

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject =
		self.slot(ObjectSlots.TUPLE_AT_, index)

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert(index >= 1 && index <= self.tupleSize())
		val result: AvailObject
		if (canDestroy && isMutable)
		{
			result = self
		}
		else
		{
			result = newLike(mutable, self, 0, 0)
			if (isMutable)
			{
				result.setSlot(ObjectSlots.TUPLE_AT_, index, NilDescriptor.nil)
				result.makeSubobjectsImmutable()
			}
		}
		result.setSlot(ObjectSlots.TUPLE_AT_, index, newValueObject)
		result.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
		return result
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val size = self.tupleSize()
		return if (size >= maximumCopySize)
		{
			super.o_TupleReverse(self)
		}
		else
		{
			generateReversedFrom(size)
				{ self.slot(ObjectSlots.TUPLE_AT_, size + 1 - it) }
		}
	}

	// Answer the number of elements in the object (as a Kotlin int).
	override fun o_TupleSize(self: AvailObject): Int =
		self.variableObjectSlotsCount()

	override fun mutable(): ObjectTupleDescriptor = mutable

	override fun immutable(): ObjectTupleDescriptor = immutable

	override fun shared(): ObjectTupleDescriptor = shared

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating  a new tuple.
		 */
		const val maximumCopySize = 32

		/**
		 * Create an `ObjectTupleDescriptor object tuple` whose slots
		 * have not been initialized.
		 *
		 * @param size
		 *   The number of elements in the resulting tuple.
		 * @return
		 *   An uninitialized object tuple of the requested size.
		 */
		private fun createUninitialized(size: Int): AvailObject =
			mutable.create(size)

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of `ObjectTupleDescriptor`.  Run the generator for each
		 * position in ascending order to produce the [AvailObject]s with which
		 * to populate the tuple.
		 *
		 * @param size
		 *   The size of the object tuple to create.
		 * @param generator
		 *   A generator to provide [AvailObject]s to store.
		 * @return
		 *   The new object tuple.
		 */
		@JvmStatic
		fun generateObjectTupleFrom(
			size: Int,
			generator: (Int) -> A_BasicObject): AvailObject
		{
			val result = createUninitialized(size)
			// Initialize it for safe GC within the loop below.  Might be
			// unnecessary if the substrate already initialized it safely.
			result.fillSlots(ObjectSlots.TUPLE_AT_, 1, size, NilDescriptor.nil)
			for (i in 1 .. size)
			{
				result.setSlot(ObjectSlots.TUPLE_AT_, i, generator(i))
			}
			return result
		}

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of `ObjectTupleDescriptor`.  Run the generator for each
		 * position in descending order (passing a descending index) to produce
		 * the [AvailObject]s with which to populate the tuple.
		 *
		 * @param size
		 *   The size of the object tuple to create.
		 * @param generator
		 *   A generator to provide [AvailObject]s to store.
		 * @return
		 *   The new object tuple.
		 */
		fun generateReversedFrom(
			size: Int,
			generator: (Int) -> A_BasicObject): AvailObject
		{
			val result = createUninitialized(size)
			// Initialize it for safe GC within the loop below.  Might be
			// unnecessary if the substrate already initialized it safely.
			result.fillSlots(ObjectSlots.TUPLE_AT_, 1, size, NilDescriptor.nil)
			for (i in size downTo 1)
			{
				result.setSlot(ObjectSlots.TUPLE_AT_, i, generator(i))
			}
			return result
		}

		/**
		 * Create a tuple with the specified elements. The elements are not made
		 * immutable first, nor is the new tuple.
		 *
		 * @param elements
		 *   The array of Avail values from which to construct a tuple.
		 * @return
		 *   The new mutable tuple.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun tupleFromArray(vararg elements: A_BasicObject): A_Tuple
		{
			val size = elements.size
			if (size == 0)
			{
				return emptyTuple()
			}
			val result = createUninitialized(size)
			result.setSlotsFromArray<A_BasicObject>(
				ObjectSlots.TUPLE_AT_, 1, arrayOf(*elements), 0, size)
			return result
		}

		/** Access to the [tupleFromArray] method.  */
		var tupleFromArrayMethod = staticMethod(
			ObjectTupleDescriptor::class.java,
			"tupleFromArray",
			A_Tuple::class.java,
			Array<A_BasicObject>::class.java)

		/**
		 * Create a tuple with the specified sole element. The element is not
		 * made immutable first, nor is the new tuple.
		 *
		 * @param element1
		 *   The value for the first element of the tuple.
		 * @return
		 *   The new mutable tuple.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun tuple(element1: A_BasicObject): A_Tuple
		{
			val result = createUninitialized(1)
			result.setSlot(ObjectSlots.TUPLE_AT_, 1, element1)
			return result
		}

		/**
		 * The [CheckedMethod] for [tuple].
		 */
		val tuple1Method = staticMethod(
			ObjectTupleDescriptor::class.java,
			"tuple",
			A_Tuple::class.java,
			A_BasicObject::class.java)

		/**
		 * Create a tuple with the specified two elements. The elements are not
		 * made immutable first, nor is the new tuple.
		 *
		 * @param element1
		 *   The value for the first element of the tuple.
		 * @param element2
		 *   The value for the second element of the tuple.
		 * @return
		 *   The new mutable tuple.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun tuple(element1: A_BasicObject, element2: A_BasicObject): A_Tuple
		{
			val result = createUninitialized(2)
			result.setSlot(ObjectSlots.TUPLE_AT_, 1, element1)
			result.setSlot(ObjectSlots.TUPLE_AT_, 2, element2)
			return result
		}

		/**
		 * The [CheckedMethod] for [tuple].
		 */
		val tuple2Method = staticMethod(
			ObjectTupleDescriptor::class.java,
			"tuple",
			A_Tuple::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java)

		/**
		 * Create a tuple with the specified three elements. The elements are
		 * not made immutable first, nor is the new tuple.
		 *
		 * @param element1
		 *   The value for the first element of the tuple.
		 * @param element2
		 *   The value for the second element of the tuple.
		 * @param element3
		 *   The value for the third element of the tuple.
		 * @return
		 *   The new mutable tuple.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun tuple(
			element1: A_BasicObject,
			element2: A_BasicObject,
			element3: A_BasicObject): A_Tuple
		{
			val result = createUninitialized(3)
			result.setSlot(ObjectSlots.TUPLE_AT_, 1, element1)
			result.setSlot(ObjectSlots.TUPLE_AT_, 2, element2)
			result.setSlot(ObjectSlots.TUPLE_AT_, 3, element3)
			return result
		}

		/**
		 * The [CheckedMethod] for [tuple].
		 */
		val tuple3Method = staticMethod(
			ObjectTupleDescriptor::class.java,
			"tuple",
			A_Tuple::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java)

		/**
		 * Create a tuple with the specified four elements. The elements are not
		 * made immutable first, nor is the new tuple.
		 *
		 * @param element1
		 *   The value for the first element of the tuple.
		 * @param element2
		 *   The value for the second element of the tuple.
		 * @param element3
		 *   The value for the third element of the tuple.
		 * @param element4
		 *   The value for the fourth element of the tuple.
		 * @return
		 *   The new mutable tuple.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun tuple(
			element1: A_BasicObject,
			element2: A_BasicObject,
			element3: A_BasicObject,
			element4: A_BasicObject): A_Tuple
		{
			val result = createUninitialized(4)
			result.setSlot(ObjectSlots.TUPLE_AT_, 1, element1)
			result.setSlot(ObjectSlots.TUPLE_AT_, 2, element2)
			result.setSlot(ObjectSlots.TUPLE_AT_, 3, element3)
			result.setSlot(ObjectSlots.TUPLE_AT_, 4, element4)
			return result
		}

		/**
		 * The [CheckedMethod] for [tuple].
		 */
		val tuple4Method = staticMethod(
			ObjectTupleDescriptor::class.java,
			"tuple",
			A_Tuple::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java)

		/**
		 * Create a tuple with the specified five elements. The elements are not
		 * made immutable first, nor is the new tuple.
		 *
		 * @param element1
		 *   The value for the first element of the tuple.
		 * @param element2
		 *   The value for the second element of the tuple.
		 * @param element3
		 *   The value for the third element of the tuple.
		 * @param element4
		 *   The value for the fourth element of the tuple.
		 * @param element5
		 *   The value for the fifth element of the tuple.
		 * @return
		 *   The new mutable tuple.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun tuple(
			element1: A_BasicObject,
			element2: A_BasicObject,
			element3: A_BasicObject,
			element4: A_BasicObject,
			element5: A_BasicObject): A_Tuple
		{
			val result = createUninitialized(5)
			result.setSlot(ObjectSlots.TUPLE_AT_, 1, element1)
			result.setSlot(ObjectSlots.TUPLE_AT_, 2, element2)
			result.setSlot(ObjectSlots.TUPLE_AT_, 3, element3)
			result.setSlot(ObjectSlots.TUPLE_AT_, 4, element4)
			result.setSlot(ObjectSlots.TUPLE_AT_, 5, element5)
			return result
		}

		/**
		 * The [CheckedMethod] for [tuple].
		 */
		val tuple5Method = staticMethod(
			ObjectTupleDescriptor::class.java,
			"tuple",
			A_Tuple::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java)

		/**
		 * Construct a new tuple of arbitrary [Avail objects][AvailObject]
		 * passed in a list.  The elements are not made immutable first, nor is
		 * the new tuple necessarily made immutable.
		 *
		 * @param list
		 *   The list of [Avail objects][AvailObject] from which to construct a
		 *   tuple.
		 * @return
		 *   The corresponding tuple of objects.
		 * @param E
		 *   The specialization of the input [List]'s elements.
		 */
		@JvmStatic
		fun <E : A_BasicObject?> tupleFromList(list: List<E>): A_Tuple
		{
			val size = list.size
			if (size == 0)
			{
				return emptyTuple()
			}
			val result = createUninitialized(size)
			result.setSlotsFromList(ObjectSlots.TUPLE_AT_, 1, list, 0, size)
			return result
		}

		/** The mutable `ObjectTupleDescriptor`.  */
		val mutable = ObjectTupleDescriptor(Mutability.MUTABLE)

		/** The immutable `ObjectTupleDescriptor`.  */
		private val immutable = ObjectTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared `ObjectTupleDescriptor`.  */
		private val shared = ObjectTupleDescriptor(Mutability.SHARED)
	}
}
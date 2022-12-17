/*
 * A_Tuple.kt
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
package avail.descriptor.tuples

import avail.descriptor.character.A_Character
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.IndirectionDescriptor
import avail.descriptor.sets.A_Set
import avail.descriptor.types.A_Type
import avail.descriptor.types.TypeDescriptor
import avail.utility.cast
import java.nio.ByteBuffer
import java.util.Spliterator
import java.util.stream.Stream

/**
 * `A_Tuple` is an interface that specifies the tuple-specific operations that
 * an [AvailObject] must implement.  It's a sub-interface of [A_BasicObject],
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Tuple : A_BasicObject, Iterable<AvailObject>
{
	companion object
	{
		/**
		 * Create a tuple with the same elements as the receiver, but with the
		 * specified newElement appended.
		 *
		 * @param newElement
		 *   The element to append to the receiver to produce a new tuple.
		 * @param canDestroy
		 *   Whether the receiver may be destroyed if it's mutable.
		 * @return
		 *   The tuple containing all the elements that the receiver had, plus
		 *   the newElement.
		 */
		fun A_Tuple.appendCanDestroy(
			newElement: A_BasicObject,
			canDestroy: Boolean
		): A_Tuple = dispatch { o_AppendCanDestroy(it, newElement, canDestroy) }

		/**
		 * Construct a Java [set][Set] from the receiver, a
		 * [tuple][TupleDescriptor].
		 *
		 * @return
		 *   A set containing each element in the tuple.
		 */
		val A_Tuple.asSet: A_Set get() = dispatch { o_AsSet(it) }

		/**
		 * Answer the approximate memory cost in bits per element of this tuple.
		 * This is used to decide the direction of
		 * [indirections][IndirectionDescriptor] after determining two objects
		 * are equal.
		 *
		 * @return
		 *   The approximate cost in bits per element.
		 */
		val A_Tuple.bitsPerEntry: Int get() = dispatch { o_BitsPerEntry(it) }

		/**
		 * Extract the backing `byte[]` from this tuple.  Only applicable if the
		 * tuple's descriptor answers true to [A_Tuple.isByteArrayTuple].
		 *
		 * @return
		 *   This tuple's byte array.  Don't modify it.
		 */
		val A_Tuple.byteArray: ByteArray get() = dispatch { o_ByteArray(it) }

		/**
		 * The receiver must be a
		 * [byte&#32;buffer&#32;tuple][ByteBufferTupleDescriptor]; answer its
		 * backing [ByteBuffer].
		 *
		 * @return
		 *   The receiver's [ByteBuffer].
		 */
		val A_Tuple.byteBuffer: ByteBuffer get() = dispatch { o_ByteBuffer(it) }

		/**
		 * Answer the N<sup>th</sup> child of this
		 * [tree tuple][TreeTupleDescriptor].
		 *
		 * @param childIndex
		 *   Which child tuple to fetch.
		 * @return
		 *   The specified child of the tree tuple node.
		 */
		fun A_Tuple.childAt(childIndex: Int): A_Tuple =
			dispatch { o_ChildAt(it, childIndex) }

		/**
		 * Answer the number of children this
		 * [tree&#32;tuple][TreeTupleDescriptor] contains.
		 *
		 * @return
		 *   The width of this tree tuple node.
		 */
		val A_Tuple.childCount: Int get() = dispatch { o_ChildCount(it) }

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [tuple][TupleDescriptor]. The size of the subrange of both
		 * objects is determined by the index range supplied for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aTuple
		 *   The tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithAnyTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithAnyTupleStartingAt(
				it, startIndex1, endIndex1, aTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [byte array tuple][ByteArrayTupleDescriptor]. The size of
		 * the subrange of both objects is determined by the index range
		 * supplied for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aByteArrayTuple
		 *   The [byte array tuple][ByteArrayTupleDescriptor] used in the
		 *   comparison
		 * @param startIndex2
		 *   The inclusive lower bound of the tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithByteArrayTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aByteArrayTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithByteArrayTupleStartingAt(
				it, startIndex1, endIndex1, aByteArrayTuple, startIndex2)
		}

		/**
		 * Test whether the subtuple of the receiver from startIndex1 to
		 * endIndex1 equals the subtuple of the [byte buffer
		 * tuple][ByteBufferTupleDescriptor] of the same length starting at
		 * startIndex2.
		 *
		 * @param startIndex1
		 *   The first index to examine from the receiver.
		 * @param endIndex1
		 *   The last index to examine from the receiver.
		 * @param aByteBufferTuple
		 *   The byte buffer tuple to which to compare elements.
		 * @param startIndex2
		 *   The first index into the byte buffer tuple at which comparison
		 *   should take place.
		 * @return
		 *   Whether the two subtuples are equal.
		 */
		fun A_Tuple.compareFromToWithByteBufferTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aByteBufferTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithByteBufferTupleStartingAt(
				it, startIndex1, endIndex1, aByteBufferTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [byte string][ByteStringDescriptor]. The size of the
		 * subrange of both objects is determined by the index range supplied
		 * for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aByteString
		 *   The byte string used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the byte string's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithByteStringStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aByteString: A_String,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithByteStringStartingAt(
				it, startIndex1, endIndex1, aByteString, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [byte tuple][ByteTupleDescriptor]. The size of the subrange
		 * of both objects is determined by the index range supplied for the
		 * receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aByteTuple
		 *   The byte tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the byte tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithByteTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aByteTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithByteTupleStartingAt(
				it, startIndex1, endIndex1, aByteTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [integer interval tuple][IntegerIntervalTupleDescriptor].
		 * The size of the subrange of both objects is determined by the index
		 * range supplied for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param anIntegerIntervalTuple
		 *   The integer interval tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the byte tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithIntegerIntervalTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			anIntegerIntervalTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithIntegerIntervalTupleStartingAt(
				it, startIndex1, endIndex1, anIntegerIntervalTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [int tuple][IntTupleDescriptor]. The size of the subrange
		 * of both objects is determined by the index range supplied for the
		 * receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param anIntTuple
		 *   The int tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the int tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithIntTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			anIntTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithIntTupleStartingAt(
				it, startIndex1, endIndex1, anIntTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [nybble tuple][NybbleTupleDescriptor]. The size of the
		 * subrange of both objects is determined by the index range supplied
		 * for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aNybbleTuple
		 *   The nybble tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the nybble tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithNybbleTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aNybbleTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithNybbleTupleStartingAt(
				it, startIndex1, endIndex1, aNybbleTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [object tuple][ObjectTupleDescriptor]. The size of the
		 * subrange of both objects is determined by the index range supplied
		 * for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param anObjectTuple
		 *   The object tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the object tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithObjectTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			anObjectTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithObjectTupleStartingAt(
				it, startIndex1, endIndex1, anObjectTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [repeated element tuple][RepeatedElementTupleDescriptor].
		 * The size of the subrange of both objects is determined by the index
		 * range supplied for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aRepeatedElementTuple
		 *   The repeated element tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the byte tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithRepeatedElementTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aRepeatedElementTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithRepeatedElementTupleStartingAt(
				it, startIndex1, endIndex1, aRepeatedElementTuple, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given
		 * [small&#32;integer&#32;interval&#32;tuple][SmallIntegerIntervalTupleDescriptor].
		 * The size of the subrange of both objects is determined by the index
		 * range supplied for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aSmallIntegerIntervalTuple
		 *   The small integer interval tuple used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the byte tuple's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithSmallIntegerIntervalTupleStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aSmallIntegerIntervalTuple: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
				it,
				startIndex1,
				endIndex1,
				aSmallIntegerIntervalTuple,
				startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * another object. The size of the subrange of both objects is
		 * determined by the index range supplied for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param anotherObject
		 *   The other object used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the other object's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			anotherObject: A_Tuple,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithStartingAt(
				it, startIndex1, endIndex1, anotherObject, startIndex2)
		}

		/**
		 * Compare a subrange of the [receiver][AvailObject] with a subrange of
		 * the given [two-byte string][TwoByteStringDescriptor]. The size of the
		 * subrange of both objects is determined by the index range supplied
		 * for the receiver.
		 *
		 * @param startIndex1
		 *   The inclusive lower bound of the receiver's subrange.
		 * @param endIndex1
		 *   The inclusive upper bound of the receiver's subrange.
		 * @param aTwoByteString
		 *   The two-byte string used in the comparison.
		 * @param startIndex2
		 *   The inclusive lower bound of the two-byte string's subrange.
		 * @return
		 *   `true` if the contents of the subranges match exactly, `false`
		 *   otherwise.
		 */
		fun A_Tuple.compareFromToWithTwoByteStringStartingAt(
			startIndex1: Int,
			endIndex1: Int,
			aTwoByteString: A_String,
			startIndex2: Int
		): Boolean = dispatch {
			o_CompareFromToWithTwoByteStringStartingAt(
				it, startIndex1, endIndex1, aTwoByteString, startIndex2)
		}

		/**
		 * Compute the hash of the specified subrange of this tuple.
		 *
		 * @param start
		 *   The first index to contribute to the hash.
		 * @param end
		 *   The last index to consider, inclusive.
		 * @return
		 *   The hash of the specified tuple subrange.
		 */
		fun A_Tuple.computeHashFromTo(start: Int, end: Int): Int =
			dispatch { o_ComputeHashFromTo(it, start, end) }

		/**
		 * Given a tuple of tuples, concatenate all the inner tuples to
		 * construct one new tuple.  May destroy the original tuple of tuples if
		 * so indicated.
		 *
		 * @param canDestroy
		 *   Whether the input may be destroyed or reused.
		 * @return
		 *   The concatenation of this tuple's elements, all tuples.
		 */
		fun A_Tuple.concatenateTuplesCanDestroy(canDestroy: Boolean): A_Tuple =
			dispatch { o_ConcatenateTuplesCanDestroy(it, canDestroy) }

		/**
		 * Concatenate the receiver and the argument otherTuple to form a new
		 * tuple. Assume that the two input tuples may be destroyed or recycled
		 * if they're mutable.
		 *
		 * @param otherTuple
		 *   The tuple to append.
		 * @param canDestroy
		 *   Whether the input tuples can be destroyed or reused.
		 * @return
		 *   The concatenation of the two tuples.
		 */
		fun A_Tuple.concatenateWith(
			otherTuple: A_Tuple,
			canDestroy: Boolean
		): A_Tuple = dispatch { o_ConcatenateWith(it, otherTuple, canDestroy) }

		/**
		 * Make a mutable copy of the tuple but in a form that accepts any
		 * objects.
		 *
		 * @return
		 *   The new mutable [object tuple][ObjectTupleDescriptor].
		 */
		fun A_Tuple.copyAsMutableObjectTuple(): A_Tuple =
			dispatch { o_CopyAsMutableObjectTuple(it) }

		/**
		 * Make a mutable copy of the tuple but in a form that accepts [Int]s.
		 *
		 * @return
		 *   The new mutable [int tuple][IntTupleDescriptor].
		 */
		fun A_Tuple.copyAsMutableIntTuple(): A_Tuple =
			dispatch { o_CopyAsMutableIntTuple(it) }

		/**
		 * Make a mutable copy of the tuple but in a form that accepts [Long]s.
		 *
		 * @return
		 *   The new mutable [long&#32;tuple][LongTupleDescriptor].
		 */
		fun A_Tuple.copyAsMutableLongTuple(): A_Tuple =
			dispatch { o_CopyAsMutableLongTuple(it) }

		/**
		 * Make a tuple that only contains the given range of elements of the
		 * given tuple.  Subclasses have different strategies for how to
		 * accomplish this efficiently.
		 *
		 * @param start
		 *   The start of the range to extract.
		 * @param end
		 *   The end of the range to extract.
		 * @param canDestroy
		 *   Whether the original object may be destroyed if mutable.
		 * @return
		 *   The subtuple.
		 */
		fun A_Tuple.copyTupleFromToCanDestroy(
			start: Int,
			end: Int,
			canDestroy: Boolean
		): A_Tuple = dispatch {
			o_CopyTupleFromToCanDestroy(it, start, end, canDestroy)
		}

		/**
		 * Extract the specified element from the tuple.  The element must be an
		 * integer in the range [0..15], and is returned as a Java `byte`.
		 *
		 * @param index
		 *   The index into the tuple.
		 * @return
		 *   The nybble as a Java byte.
		 */
		fun A_Tuple.extractNybbleFromTupleAt(index: Int): Byte =
			dispatch { o_ExtractNybbleFromTupleAt(it, index) }

		/**
		 * Search for a particular value in the tuple, starting at the given
		 * index and working forward.
		 *
		 * @param value
		 *   The value to search for in the receiver.
		 * @param startIndex
		 *   The position at which to start scanning.
		 * @param endIndex
		 *   The position at which to stop scanning, which should be >= the
		 *   startIndex for a non-empty range.
		 * @return
		 *   The first encountered index of the code point when scanning forward
		 *   from the [startIndex].  If the value is not found, answer 0.
		 */
		fun A_Tuple.firstIndexOf(
			value: A_BasicObject,
			startIndex: Int,
			endIndex: Int
		): Int = dispatch { o_FirstIndexOf(it, value, startIndex, endIndex) }

		/**
		 * Calculate the hash of the subtuple spanning the two indices.
		 *
		 * @param startIndex
		 *   The first index of the subtuple.
		 * @param endIndex
		 *   The last index of the subtuple.
		 * @return
		 *   The hash of the subtuple.
		 */
		fun A_Tuple.hashFromTo(startIndex: Int, endIndex: Int): Int =
			dispatch { o_HashFromTo(it, startIndex, endIndex) }

		/**
		 * Given two objects that are known to be equal, is the first one in a
		 * better form (more compact, more efficient, older generation) than the
		 * second one?
		 *
		 * @param anotherObject
		 *   An object equal to this, but perhaps in a better or worse
		 *   representation.
		 * @return
		 *   Whether the receiver has a representation that is superior (less
		 *   space, faster access) to the argument.
		 */
		fun A_Tuple.isBetterRepresentationThan(
			anotherObject: A_BasicObject
		): Boolean = dispatch {
			o_IsBetterRepresentationThan(it, anotherObject)
		}

		/**
		 * Search for a particular value in the tuple, starting at the given
		 * one-based index and working backward.
		 *
		 * @param value
		 *   The value to search for in the receiver.
		 * @param startIndex
		 *   The position at which to start scanning backward.
		 * @param endIndex
		 *   The position at which to stop scanning; should be <= startIndex
		 *   for a non-empty range.
		 * @return
		 *   The first encountered index of the code point when scanning
		 *   backward from the [startIndex].  If the value is not found, answer
		 *   0.
		 */
		fun A_Tuple.lastIndexOf(
			value: A_BasicObject,
			startIndex: Int,
			endIndex: Int
		): Int = dispatch { o_LastIndexOf(it, value, startIndex, endIndex) }

		/**
		 * Returns a possibly parallel `Stream` with this tuple as its source.
		 * It is allowable for this method to return a sequential stream.
		 *
		 * @return
		 *   A [Spliterator] of [AvailObject]s.
		 */
		fun A_Tuple.parallelStream(): Stream<AvailObject> =
			dispatch { o_ParallelStream(it) }

		/**
		 * The receiver is a [byte string][ByteStringDescriptor]; extract the
		 * [code&#32;point][A_Character.codePoint] of the
		 * [character][A_Character] at the given index as an unsigned byte.
		 *
		 * @param index
		 *   The index of the character to extract.
		 * @return
		 *   The code point of the character at the given index, as a Java
		 *   `short` in the range `[0..255]`.
		 */
		fun A_Tuple.rawByteForCharacterAt(index: Int): Short =
			dispatch { o_RawByteForCharacterAt(it, index) }

		/**
		 * Replace the first child of this
		 * [tree&#32;tuple][TreeTupleDescriptor]. Make a copy to modify if the
		 * receiver is immutable.  Answer the modified original or copy.
		 *
		 * @param newFirst
		 *   The new child tuple.
		 * @return
		 *   The tree tuple with the first child replaced, potentially
		 *   destructively.
		 */
		fun A_Tuple.replaceFirstChild(newFirst: A_Tuple): A_Tuple =
			dispatch { o_ReplaceFirstChild(it, newFirst) }

		/**
		 * Returns a sequential `Stream` with this tuple as its source.
		 *
		 * @return
		 *   A [Stream] of [AvailObject]s.
		 */
		fun A_Tuple.stream(): Stream<AvailObject> = dispatch { o_Stream(it) }

		/**
		 * Transfer the specified subrange of this tuple of bytes into the
		 * provided [ByteBuffer].  There should be sufficient room to write the
		 * bytes.
		 *
		 * @param startIndex
		 *   The subscript of the first byte to write.
		 * @param endIndex
		 *   The subscript of the last byte to write.
		 * @param outputByteBuffer
		 *   The `ByteBuffer` in which to write.
		 */
		fun A_Tuple.transferIntoByteBuffer(
			startIndex: Int,
			endIndex: Int,
			outputByteBuffer: ByteBuffer
		) = dispatch {
			o_TransferIntoByteBuffer(it, startIndex, endIndex, outputByteBuffer)
		}

		/**
		 * Return the height of this [tree&#32;tuple][TreeTupleDescriptor]. Flat
		 * tuples and subranges have height 0, and tree tuples have heights from
		 * 1 to 10.  All of a tree tuple's children have a height of one less
		 * than the parent tree tuple.
		 *
		 * @return
		 *   The height of the tree tuple.
		 */
		val A_Tuple.treeTupleLevel: Int
			get() = dispatch { o_TreeTupleLevel(it) }

		/**
		 * Answer the specified element of the tuple.
		 *
		 * @param index
		 *   Which element should be extracted.
		 * @return
		 *   The element of the tuple.
		 */
		fun A_Tuple.tupleAt(index: Int): AvailObject =
			dispatch { o_TupleAt(it, index) }

		/**
		 * Answer a new tuple like the receiver but with a single element
		 * replaced at the specified index.  If the receiver is mutable and
		 * canDestroy is true, then the receiver may be modified or destroyed.
		 *
		 * @param index
		 *   The index at which to replace an element.
		 * @param newValueObject
		 *   The replacement element.
		 * @param canDestroy
		 *   Whether the receiver can be modified if it's mutable.
		 * @return
		 *   A tuple containing the elements that were present in the receiver,
		 *   except that the element at index has been replaced by
		 *   newValueObject.
		 */
		fun A_Tuple.tupleAtPuttingCanDestroy(
			index: Int,
			newValueObject: A_BasicObject,
			canDestroy: Boolean
		): A_Tuple = dispatch {
			o_TupleAtPuttingCanDestroy(it, index, newValueObject, canDestroy)
		}

		/**
		 * Answer the code point of the character at the given one-based index
		 * in this tuple.  The tuple doesn't have to be a string, but the
		 * requested element must be a character.
		 *
		 * @param index
		 *   The one-based subscript into this tuple.
		 * @return
		 *   The code point of the [A_Character] at the specified index.
		 */
		fun A_Tuple.tupleCodePointAt(index: Int): Int =
			dispatch { o_TupleCodePointAt(it, index) }

		/**
		 * Determine whether the specified elements of this tuple each conform
		 * to the specified [type][TypeDescriptor].
		 *
		 * @param startIndex
		 *   The first index to check.
		 * @param endIndex
		 *   The last index to check.
		 * @param type
		 *   The type to check the elements against.
		 * @return
		 *   Whether all the elements are of that type.
		 */
		fun A_Tuple.tupleElementsInRangeAreInstancesOf(
			startIndex: Int,
			endIndex: Int,
			type: A_Type
		): Boolean = dispatch {
			o_TupleElementsInRangeAreInstancesOf(
				it, startIndex, endIndex, type)
		}

		/**
		 * Answer the specified element of the tuple.  It must be an
		 * [integer][IntegerDescriptor] in the range `[-2^31..2^31)`, and is
		 * returned as a Java `int`.
		 *
		 * @param index
		 *   Which 1-based index to use to subscript the tuple.
		 * @return
		 *   The `int` form of the specified tuple element.
		 */
		fun A_Tuple.tupleIntAt(index: Int): Int =
			dispatch { o_TupleIntAt(it, index) }

		/**
		 * Answer the specified element of the tuple.  It must be a [Long] in
		 * the range [-2^63..2^63), and is returned as a Kotlin [Long].
		 *
		 * @param index
		 *   Which 1-based index to use to subscript the tuple.
		 * @return
		 *   The [Long] form of the specified tuple element.
		 */
		fun A_Tuple.tupleLongAt(index: Int): Long =
			dispatch { o_TupleLongAt(it, index) }

		/**
		 * Answer a tuple that has the receiver's elements but in reverse order.
		 *
		 * @return
		 *   The reversed tuple.
		 */
		fun A_Tuple.tupleReverse(): A_Tuple = dispatch { o_TupleReverse(it) }

		/**
		 * Answer the number of elements in this tuple.
		 *
		 * @return
		 *   The maximum valid 1-based index for this tuple.
		 */
		val A_Tuple.tupleSize: Int get() = dispatch { o_TupleSize(it) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The first component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component1(): AvailObject =
			dispatch { o_TupleAt(it, 1) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The second component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component2(): AvailObject =
			dispatch { o_TupleAt(it, 2) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The third component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component3(): AvailObject =
			dispatch { o_TupleAt(it, 3) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The fourth component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component4(): AvailObject =
			dispatch { o_TupleAt(it, 4) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The fifth component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component5(): AvailObject =
			dispatch { o_TupleAt(it, 5) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The sixth component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component6(): AvailObject =
			dispatch { o_TupleAt(it, 6) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The seventh component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component7(): AvailObject =
			dispatch { o_TupleAt(it, 7) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The eighth component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component8(): AvailObject =
			dispatch { o_TupleAt(it, 8) }

		/**
		 * As a convenience in Kotlin, allow deconstruction of short tuples.
		 *
		 * @return
		 *   The ninth component for a Kotlin deconstructor.
		 */
		operator fun A_Tuple.component9(): AvailObject =
			dispatch { o_TupleAt(it, 9) }

		/**
		 * Concatenate two `A_Tuple`s or [A_String]s, relying on type deduction
		 * to decide which will be produced based on the arguments.
		 *
		 * @param firstTuple
		 *   The first tuple or string to concatenate.
		 * @param secondTuple
		 *   The second tuple or string to concatenate.
		 * @param canDestroy
		 *   Whether either input tuple may be destroyed if it's also mutable.
		 * @param T
		 *   The kind of tuple to operate on (`A_Tuple` or a subclass}.
		 * @return
		 *   The concatenated tuple, with as strong a static type as can be
		 *   determined from the input types.
		 */
		fun <T : A_Tuple> concatenate(
			firstTuple: T,
			secondTuple: T,
			canDestroy: Boolean): T
		{
			return firstTuple.concatenateWith(secondTuple, canDestroy).cast()
		}
	}
}

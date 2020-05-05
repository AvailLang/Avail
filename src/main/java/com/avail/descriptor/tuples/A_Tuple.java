/*
 * A_Tuple.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.descriptor.tuples;

import com.avail.descriptor.A_Character;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.IndirectionDescriptor;
import com.avail.descriptor.numbers.IntegerDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeDescriptor;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;

import static com.avail.utility.Casts.cast;

/**
 * {@code A_Tuple} is an interface that specifies the tuple-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Tuple
extends A_BasicObject, Iterable<AvailObject>
{
	/**
	 * Create a tuple with the same elements as the receiver, but with the
	 * specified newElement appended.
	 *
	 * @param newElement
	 *        The element to append to the receiver to produce a new tuple.
	 * @param canDestroy
	 *        Whether the receiver may be destroyed if it's mutable.
	 * @return The tuple containing all the elements that the receiver had, plus
	 *         the newElement.
	 */
	A_Tuple appendCanDestroy (
		A_BasicObject newElement,
		boolean canDestroy);

	/**
	 * Construct a Java {@linkplain Set set} from the receiver, a {@linkplain
	 * TupleDescriptor tuple}.
	 *
	 * @return A set containing each element in the tuple.
	 */
	A_Set asSet ();

	/**
	 * Answer the approximate memory cost in bets per element of this tuple.
	 * This is used to decide the direction of {@linkplain IndirectionDescriptor
	 * indirections} after determining two objects are equal.
	 *
	 * @return The approximate cost in bits per element.
	 */
	int bitsPerEntry ();

	/**
	 * Extract the backing {@code byte[]} from this tuple.  Only applicable if
	 * the tuple's descriptor answers true to {@link #isByteArrayTuple()}.
	 *
	 * @return This tuple's byte array.  Don't modify it.
	 */
	byte[] byteArray ();

	/**
	 * The receiver must be a {@link ByteBufferTupleDescriptor byte buffer
	 * tuple}; answer its backing {@link ByteBuffer}.
	 *
	 * @return The receiver's {@link ByteBuffer}.
	 */
	ByteBuffer byteBuffer ();

	/**
	 * Answer the N<sup>th</sup> child of this {@linkplain TreeTupleDescriptor
	 * tree tuple}.
	 *
	 * @param childIndex Which child tuple to fetch.
	 * @return The specified child of the tree tuple node.
	 */
	A_Tuple childAt (int childIndex);

	/**
	 * Answer the number of children this {@linkplain TreeTupleDescriptor tree
	 * tuple} contains.
	 *
	 * @return The width of this tree tuple node.
	 */
	int childCount ();

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain TupleDescriptor tuple}. The size of the
	 * subrange of both objects is determined by the index range supplied for
	 * the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTuple
	 *        The tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithAnyTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aTuple,
		int startIndex2);

	/** Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@link ByteArrayTupleDescriptor byte array tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteArrayTuple
	 *        The {@link ByteArrayTupleDescriptor byte array tuple} used in the
	 *        comparison
	 * @param startIndex2
	 *        The inclusive lower bound of the tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithByteArrayTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aByteArrayTuple,
		int startIndex2);

	/**
	 * Test whether the subtuple of the receiver from startIndex1 to endIndex1
	 * equals the subtuple of the {@link ByteBufferTupleDescriptor byte buffer
	 * tuple} of the same length starting at startIndex2.
	 *
	 * @param startIndex1
	 *        The first index to examine from the receiver.
	 * @param endIndex1
	 *        The last index to examine from the receiver.
	 * @param aByteBufferTuple
	 *        The byte buffer tuple to which to compare elements.
	 * @param startIndex2
	 *        The first index into the byte buffer tuple at which comparison
	 *        should take place.
	 * @return Whether the two subtuples are equal.
	 */
	boolean compareFromToWithByteBufferTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aByteBufferTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteStringDescriptor byte string}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteString
	 *        The byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithByteStringStartingAt (
		int startIndex1,
		int endIndex1,
		A_String aByteString,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteTupleDescriptor byte tuple}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteTuple
	 *        The byte tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithByteTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aByteTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain IntegerIntervalTupleDescriptor integer
	 * interval tuple}. The size of the subrange of both objects is determined
	 * by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anIntegerIntervalTuple
	 *        The integer interval tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithIntegerIntervalTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple anIntegerIntervalTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain IntTupleDescriptor int tuple}. The size
	 * of the subrange of both objects is determined by the index range supplied
	 * for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anIntTuple
	 *        The int tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the int tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithIntTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple anIntTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain NybbleTupleDescriptor nybble tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aNybbleTuple
	 *        The nybble tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the nybble tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithNybbleTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aNybbleTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ObjectTupleDescriptor object tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anObjectTuple
	 *        The object tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the object tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithObjectTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple anObjectTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain RepeatedElementTupleDescriptor repeated
	 * element tuple}. The size of the subrange of both objects is determined
	 * by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aRepeatedElementTuple
	 *        The repeated element tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithRepeatedElementTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aRepeatedElementTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain SmallIntegerIntervalTupleDescriptor
	 * small integer interval tuple}. The size of the subrange of both objects
	 * is determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aSmallIntegerIntervalTuple
	 *        The small integer interval tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithSmallIntegerIntervalTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aSmallIntegerIntervalTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of another object. The size of the subrange of both objects is
	 * determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anotherObject
	 *        The other object used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the other object's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple anotherObject,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain TwoByteStringDescriptor two-byte
	 * string}. The size of the subrange of both objects is determined by the
	 * index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTwoByteString
	 *        The two-byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the two-byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithTwoByteStringStartingAt (
		int startIndex1,
		int endIndex1,
		A_String aTwoByteString,
		int startIndex2);

	/**
	 * Compute the hash of the specified subrange of this tuple.
	 *
	 * @param start The first index to contribute to the hash.
	 * @param end The last index to consider, inclusive.
	 * @return The hash of the specified tuple subrange.
	 */
	int computeHashFromTo (int start, int end);

	/**
	 * Given a tuple of tuples, concatenate all the inner tuples to construct
	 * one new tuple.  May destroy the original tuple of tuples if so indicated.
	 *
	 * @param canDestroy Whether the input may be destroyed or reused.
	 * @return The concatenation of this tuple's elements, all tuples.
	 */
	A_Tuple concatenateTuplesCanDestroy (boolean canDestroy);

	/**
	 * Concatenate the receiver and the argument otherTuple to form a new tuple.
	 * Assume that the two input tuples may be destroyed or recycled if they're
	 * mutable.
	 *
	 * @param otherTuple The tuple to append.
	 * @param canDestroy Whether the input tuples can be destroyed or reused.
	 * @return The concatenation of the two tuples.
	 */
	@ReferencedInGeneratedCode
	A_Tuple concatenateWith (A_Tuple otherTuple, boolean canDestroy);

	/**
	 * Concatenate two {@code A_Tuple}s or {@link A_String}s, relying on type
	 * deduction to decide which will be produced based on the arguments.
	 *
	 * @param firstTuple
	 *        The first tuple or string to concatenate.
	 * @param secondTuple
	 *        The second tuple or string to concatenate.
	 * @param canDestroy
	 *        Whether either input tuple may be destroyed if it's also mutable.
	 * @param <T>
	 *        The kind of tuple to operate on ({@code A_Tuple} or a subclass}.
	 * @return The concatenated tuple, with as strong a static type as can be
	 *         determined from the input types.
	 */
	static <T extends A_Tuple> T concatenate (
		final T firstTuple,
		final T secondTuple,
		final boolean canDestroy)
	{
		return cast(firstTuple.concatenateWith(secondTuple, canDestroy));
	}

	/**
	 * The {@link CheckedMethod} for {@link #concatenateWith(A_Tuple, boolean)}.
	 */
	CheckedMethod concatenateWithMethod = CheckedMethod.instanceMethod(
		A_Tuple.class,
		"concatenateWith",
		A_Tuple.class,
		A_Tuple.class,
		boolean.class);

	/**
	 * Make a mutable copy of the tuple but in a form that accepts any objects.
	 *
	 * @return The new mutable {@link ObjectTupleDescriptor object tuple}.
	 */
	A_Tuple copyAsMutableObjectTuple ();

	/**
	 * Make a mutable copy of the tuple but in a form that accepts ints.
	 *
	 * @return The new mutable {@link IntTupleDescriptor int tuple}.
	 */
	A_Tuple copyAsMutableIntTuple ();

	/**
	 * Make a tuple that only contains the given range of elements of the given
	 * tuple.  Subclasses have different strategies for how to accomplish this
	 * efficiently.
	 *
	 * @param start
	 *        The start of the range to extract.
	 * @param end
	 *        The end of the range to extract.
	 * @param canDestroy
	 *        Whether the original object may be destroyed if mutable.
	 * @return The subtuple.
	 */
	A_Tuple copyTupleFromToCanDestroy (
		int start,
		int end,
		boolean canDestroy);

	/**
	 * Extract the specified element from the tuple.  The element must be an
	 * integer in the range [0..15], and is returned as a Java {@code byte}.
	 *
	 * @param index The index into the tuple.
	 * @return The nybble as a Java byte.
	 */
	byte extractNybbleFromTupleAt (int index);

	/**
	 * Calculate the hash of the subtuple spanning the two indices.
	 *
	 * @param startIndex The first index of the subtuple.
	 * @param endIndex The last index of the subtuple.
	 * @return The hash of the subtuple.
	 */
	int hashFromTo (int startIndex, int endIndex);

	/**
	 * Given two objects that are known to be equal, is the first one in a
	 * better form (more compact, more efficient, older generation) than the
	 * second one?
	 *
	 * @param anotherObject
	 *        An object equal to this, but perhaps in a better or worse
	 *        representation.
	 * @return Whether the receiver has a representation that is superior (less
	 *         space, faster access) to the argument.
	 */
	boolean isBetterRepresentationThan (
		A_BasicObject anotherObject);

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject receiver} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @return An {@linkplain Iterator iterator}.
	 */
	@Override
	Iterator<AvailObject> iterator ();

	/**
	 * Returns a Java {@link Spliterator} over the elements, which are {@link
	 * AvailObject}s.  Note that this is an {@link Override} because {@code
	 * A_Tuple} extends {@link Iterable}.
	 *
	 * @return A {@link Spliterator} of {@link AvailObject}s.
	 */
	@Override
	Spliterator<AvailObject> spliterator();

	/**
	 * Returns a sequential {@code Stream} with this tuple as its source.
	 *
	 * @return A {@link Stream} of {@link AvailObject}s.
	 */
	Stream<AvailObject> stream();

	/**
	 * Returns a possibly parallel {@code Stream} with this tuple as its source.
	 * It is allowable for this method to return a sequential stream.
	 *
	 * @return A {@link Spliterator} of {@link AvailObject}s.
	 */
	Stream<AvailObject> parallelStream();

	/**
	 * The receiver is a {@linkplain ByteStringDescriptor byte string}; extract
	 * the {@link A_Character#codePoint() code point} of the {@link A_Character
	 * character} at the given index as an unsigned byte.
	 *
	 * @param index The index of the character to extract.
	 * @return The code point of the character at the given index, as a Java
	 *         {@code short} in the range [0..255].
	 */
	short rawByteForCharacterAt (int index);

	/**
	 * The receiver is a {@linkplain TwoByteStringDescriptor two-byte string};
	 * extract the {@link A_Character#codePoint() code point} of the {@link
	 * A_Character character} at the given index as an unsigned short.
	 *
	 * @param index The index of the character to extract.
	 * @return The code point of the character at the given index, as a Java
	 *         {@code int} in the range [0..65535].
	 */
	int rawShortForCharacterAt (int index);

	/**
	 * The receiver is a mutable {@linkplain TwoByteStringDescriptor two-byte
	 * string}; overwrite the {@link A_Character character} at the given index
	 * with the character having the given code point.
	 *
	 * @param index
	 *        The index of the character to overwrite.
	 * @param anInteger
	 *        The code point of the character to write at the given index, as a
	 *        Java {@code int} in the range [0..65535].
	 */
	void rawShortForCharacterAtPut (int index, int anInteger);

	/**
	 * Replace the first child of this {@linkplain TreeTupleDescriptor tree
	 * tuple}.  Make a copy to modify if the receiver is immutable.  Answer the
	 * modified original or copy.
	 *
	 * @param newFirst The new child tuple.
	 * @return The tree tuple with the first child replaced, potentially
	 *         destructively.
	 */
	A_Tuple replaceFirstChild (A_Tuple newFirst);

	/**
	 * Transfer the specified subrange of this tuple of bytes into the provided
	 * {@link ByteBuffer}.  There should be sufficient room to write the bytes.
	 *
	 * @param startIndex The subscript of the first byte to write.
	 * @param endIndex The subscript of the last byte to write.
	 * @param outputByteBuffer The {@code ByteBuffer} in which to write.
	 */
	void transferIntoByteBuffer (
		int startIndex,
		int endIndex,
		ByteBuffer outputByteBuffer);

	/**
	 * Return the height of this {@linkplain TreeTupleDescriptor tree tuple}.
	 * Flat tuples and subranges have height 0, and tree tuples have heights
	 * from 1 to 10.  All of a tree tuple's children have a height of one less
	 * than the parent tree tuple.
	 *
	 * @return The height of the tree tuple.
	 */
	int treeTupleLevel ();

	/**
	 * Answer the specified element of the tuple.
	 *
	 * @param index Which element should be extracted.
	 * @return The element of the tuple.
	 */
	@ReferencedInGeneratedCode
	AvailObject tupleAt (int index);

	/** The {@link CheckedMethod} for {@link #tupleAt(int)}. */
	CheckedMethod tupleAtMethod = CheckedMethod.instanceMethod(
		A_Tuple.class,
		"tupleAt",
		AvailObject.class,
		int.class);

	/**
	 * Answer a new tuple like the receiver but with a single element replaced
	 * at the specified index.  If the receiver is mutable and canDestroy is
	 * true, then the receiver may be modified or destroyed.
	 *
	 * @param index The index at which to replace an element.
	 * @param newValueObject The replacement element.
	 * @param canDestroy Whether the receiver can be modified if it's mutable.
	 * @return A tuple containing the elements that were present in the
	 *         receiver, except that the element at index has been replaced by
	 *         newValueObject.
	 */
	A_Tuple tupleAtPuttingCanDestroy (
		int index,
		A_BasicObject newValueObject,
		boolean canDestroy);

	/**
	 * Answer the code point of the character at the given one-based index in
	 * this tuple.  The tuple doesn't have to be a string, but the requested
	 * element must be a character.
	 *
	 * @param index
	 *        The one-based subscript into this tuple.
	 * @return The code point of the {@link A_Character} at the specified index.
	 */
	int tupleCodePointAt (
		int index);

	/**
	 * Determine whether the specified elements of this tuple each conform to
	 * the specified {@linkplain TypeDescriptor type}.
	 *
	 * @param startIndex The first index to check.
	 * @param endIndex The last index to check.
	 * @param type The type to check the elements against.
	 * @return Whether all the elements are of that type.
	 */
	boolean tupleElementsInRangeAreInstancesOf (
		int startIndex,
		int endIndex,
		A_Type type);

	/**
	 * Answer the specified element of the tuple.  It must be an {@linkplain
	 * IntegerDescriptor integer} in the range [-2^31..2^31), and is returned as
	 * a Java {@code int}.
	 *
	 * @param index Which 1-based index to use to subscript the tuple.
	 * @return The {@code int} form of the specified tuple element.
	 */
	int tupleIntAt (int index);

	/**
	 * Answer a tuple that has the receiver's elements but in reverse order.
	 *
	 * @return The reversed tuple.
	 */
	A_Tuple tupleReverse();

	/**
	 * Answer the number of elements in this tuple.
	 *
	 * @return The maximum valid 1-based index for this tuple.
	 */
	@ReferencedInGeneratedCode
	int tupleSize ();

	/** The {@link CheckedMethod} for {@link #tupleSize()}. */
	CheckedMethod tupleSizeMethod = CheckedMethod.instanceMethod(
		A_Tuple.class,
		"tupleSize",
		int.class);

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The first component for a Kotlin deconstructor.
	 */
	AvailObject component1 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The second component for a Kotlin deconstructor.
	 */
	AvailObject component2 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The third component for a Kotlin deconstructor.
	 */
	AvailObject component3 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The fourth component for a Kotlin deconstructor.
	 */
	AvailObject component4 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The fifth component for a Kotlin deconstructor.
	 */
	AvailObject component5 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The sixth component for a Kotlin deconstructor.
	 */
	AvailObject component6 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The seventh component for a Kotlin deconstructor.
	 */
	AvailObject component7 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The eighth component for a Kotlin deconstructor.
	 */
	AvailObject component8 ();

	/**
	 * As a convenience in Kotlin, allow deconstruction of short tuples.
	 *
	 * @return The ninth component for a Kotlin deconstructor.
	 */
	AvailObject component9 ();
}

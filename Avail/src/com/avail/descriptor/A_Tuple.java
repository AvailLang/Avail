/**
 * A_Tuple.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;

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

	/** TODO: [LAS] This signature differs from the others
	 * @param i
	 * @param tupleSize
	 * @param aByteArrayTuple
	 * @param j
	 * @return
	 */
	boolean compareFromToWithByteArrayTupleStartingAt (
		int i,
		int tupleSize,
		A_Tuple aByteArrayTuple,
		int j);

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
	 * Dispatch to the descriptor.
	 */
	A_Tuple copyAsMutableObjectTuple ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple copyTupleFromToCanDestroy (
		int start,
		int end,
		boolean canDestroy);

	/**
	 * Given two objects that are known to be equal, is the first one in a
	 * better form (more compact, more efficient, older generation) than the
	 * second one?
	 *
	 * @param anotherObject
	 * @return
	 */
	boolean isBetterRepresentationThan (
		A_BasicObject anotherObject);

	/**
	 * Dispatch to the descriptor.
	 */
	byte extractNybbleFromTupleAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	int hashFromTo (int startIndex, int endIndex);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject tupleAt (int index);

	/**
	 * @param index
	 * @param anObject
	 */
	void objectTupleAtPut (int index, A_BasicObject anObject);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple tupleAtPuttingCanDestroy (
		int index,
		A_BasicObject newValueObject,
		boolean canDestroy);

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
	 * Answer the reverse tuple of the tuple receiver.
	 * Dispatch to the descriptor.
	 * @return
	 */
	A_Tuple tupleReverse();

	/**
	 * Answer the number of elements in this tuple.
	 *
	 * @return The maximum valid 1-based index for this tuple.
	 */
	int tupleSize ();

	/**
	 * Construct a Java {@linkplain Set set} from the receiver, a {@linkplain
	 * TupleDescriptor tuple}.
	 *
	 * @return A set containing each element in the tuple.
	 */
	A_Set asSet ();

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
	 * @param newElement
	 * @param canDestroy
	 * @return
	 */
	A_Tuple appendCanDestroy (
		A_BasicObject newElement,
		boolean canDestroy);

	/**
	 * @return
	 */
	public ByteBuffer byteBuffer ();

	/**
	 * Dispatch to the descriptor.
	 */
	int bitsPerEntry ();

	/**
	 * Dispatch to the descriptor.
	 */
	void rawNybbleAtPut (int index, byte aNybble);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawByteAtPut (int index, short anInteger);

	/**
	 * Dispatch to the descriptor.
	 */
	short rawByteForCharacterAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	int rawShortForCharacterAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawShortForCharacterAtPut (int index, int anInteger);

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
	 * Answer the number of children this {@linkplain TreeTupleDescriptor tree
	 * tuple} contains.
	 *
	 * @return The width of this tree tuple node.
	 */
	int childCount ();

	/**
	 * Answer the N<sup>th</sup> child of this {@linkplain TreeTupleDescriptor
	 * tree tuple}.
	 *
	 * @param childIndex Which child tuple to fetch.
	 * @return The specified child of the tree tuple node.
	 */
	A_Tuple childAt (int childIndex);

	/**
	 * Concatenate the receiver and the argument otherTuple to form a new tuple.
	 * Assume that the two input tuples may be destroyed or recycled if they're
	 * mutable.
	 *
	 * @param otherTuple The tuple to append.
	 * @param canDestroy Whether the input tuples can be destroyed or reused.
	 * @return The concatenation of the two tuples.
	 */
	A_Tuple concatenateWith (A_Tuple otherTuple, boolean canDestroy);

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
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteBufferTuple
	 * @param startIndex2
	 * @return
	 */
	boolean compareFromToWithByteBufferTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aByteBufferTuple,
		int startIndex2);

	/**
	 * Extract the backing {@code byte[]} from this tuple.  Only applicable if
	 * the tuple's descriptor answers true to {@linkplain
	 * TupleDescriptor#o_IsByteArrayTuple(AvailObject)}.
	 *
	 * @return This tuple's byte array.  Don't modify it.
	 */
	byte[] byteArray ();

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
}

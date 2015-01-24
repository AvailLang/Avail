/**
 * TupleDescriptor.java
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

import static com.avail.descriptor.TupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.min;
import static java.util.Collections.min;
import static java.util.Collections.max;
import java.nio.ByteBuffer;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

/**
 * {@code TupleDescriptor} is an abstract descriptor class under which all tuple
 * representations are defined (not counting {@linkplain BottomTypeDescriptor
 * bottom} and {@linkplain IndirectionDescriptor transparent indirections}).  It
 * defines a {@link IntegerSlots#HASH_OR_ZERO HASH_OR_ZERO} integer slot which
 * must be defined in all subclasses.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class TupleDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A slot to hold the cached hash value of a tuple.  If zero, then the
		 * hash value must be computed upon request.  Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO
	}

	@Override
	final boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == HASH_OR_ZERO;
	}

	@Override @AvailMethod
	final void o_HashOrZero (final AvailObject object, final int value)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(HASH_OR_ZERO, value);
			}
		}
		else
		{
			object.setSlot(HASH_OR_ZERO, value);
		}
	}

	@Override @AvailMethod
	final int o_HashOrZero (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return object.slot(HASH_OR_ZERO);
			}
		}
		return object.slot(HASH_OR_ZERO);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		if (object.tupleSize() == 0)
		{
			aStream.append("<>");
			return;
		}
		if (object.isString())
		{
			aStream.append('"');
			for (int i = 1, limit = object.tupleSize(); i <= limit; i++)
			{
				final A_Character availChar = object.tupleAt(i);
				final int c = availChar.codePoint();
				if (c == '\"' || c == '\\')
				{
					aStream.appendCodePoint('\\');
					aStream.appendCodePoint(c);
				}
				else if (c == '\n')
				{
					aStream.append("\\n");
				}
				else if (c == '\r')
				{
					aStream.append("\\r");
				}
				else if (c == '\t')
				{
					aStream.append("\\t");
				}
				else if ((c >= 0 && c < 32) || c == 127)
				{
					aStream.append(String.format("\\(%x)", c));
				}
				else
				{
					aStream.appendCodePoint(c);
				}
			}
			aStream.appendCodePoint('"');
			return;
		}
		final List<String> strings = new ArrayList<>(object.tupleSize());
		int totalChars = 0;
		boolean anyBreaks = false;
		for (final A_BasicObject element : object)
		{
			final StringBuilder localBuilder = new StringBuilder();
			element.printOnAvoidingIndent(
				localBuilder,
				recursionList,
				indent + 1);
			totalChars += localBuilder.length();
			if (!anyBreaks)
			{
				anyBreaks = localBuilder.indexOf("\n") >= 0;
			}
			strings.add(localBuilder.toString());
		}
		aStream.append('<');
		final boolean breakElements = strings.size() > 1
				&& (anyBreaks || totalChars > 60);
		for (int i = 0; i < strings.size(); i++)
		{
			if (i > 0)
			{
				aStream.append(",");
				if (!breakElements)
				{
					aStream.append(" ");
				}
			}
			if (breakElements)
			{
				aStream.append("\n");
				for (int j = indent; j > 0; j--)
				{
					aStream.append("\t");
				}
			}
			aStream.append(strings.get(i));
		}
		aStream.append('>');
	}

	@Override @AvailMethod
	abstract boolean o_Equals (
		AvailObject object,
		A_BasicObject another);

	@Override @AvailMethod
	boolean o_EqualsAnyTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Compare this arbitrary Tuple and the given arbitrary tuple.
		if (object.sameAddressAs(aTuple))
		{
			return true;
		}
		// Compare sizes...
		final int size = object.tupleSize();
		if (size != aTuple.tupleSize())
		{
			return false;
		}
		if (o_Hash(object) != aTuple.hash())
		{
			return false;
		}
		for (int i = 1; i <= size; i++)
		{
			if (!o_TupleAt(object, i).equals(aTuple.tupleAt(i)))
			{
				return false;
			}
		}
		if (object.isBetterRepresentationThan(aTuple))
		{
			if (!aTuple.descriptor().isShared())
			{
				object.makeImmutable();
				aTuple.becomeIndirectionTo(object);
			}
		}
		else
		{
			if (!isShared())
			{
				aTuple.makeImmutable();
				object.becomeIndirectionTo(aTuple);
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_EqualsByteString (
		final AvailObject object,
		final A_String aByteString)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aByteString);
	}

	@Override @AvailMethod
	boolean o_EqualsByteTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override
	boolean o_EqualsByteBufferTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override
	boolean o_EqualsIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsReverseTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override
	boolean o_EqualsSmallIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override
	boolean o_EqualsRepeatedElementTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsObjectTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Default to generic comparison.
		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsTwoByteString (
		final AvailObject object,
		final A_String aTwoByteString)
	{
		// Default to generic tuple comparison.
		return o_EqualsAnyTuple(object, aTwoByteString);
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more compact, more efficient, older generation) than
		// the second one?
		final A_Tuple anotherTuple = (A_Tuple)anotherObject;
		return object.bitsPerEntry() < anotherTuple.bitsPerEntry();
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aTypeObject)
	{
		if (aTypeObject.isSupertypeOfPrimitiveTypeEnum(NONTYPE))
		{
			return true;
		}
		if (!aTypeObject.isTupleType())
		{
			return false;
		}
		// See if it's an acceptable size...
		final int tupleSize = object.tupleSize();
		if (!aTypeObject.sizeRange().rangeIncludesInt(tupleSize))
		{
			return false;
		}
		// The tuple's size is out of range.
		final A_Tuple typeTuple = aTypeObject.typeTuple();
		final int breakIndex = min(tupleSize, typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (!object.tupleAt(i).isInstanceOf(typeTuple.tupleAt(i)))
			{
				return false;
			}
		}
		if (breakIndex + 1 > tupleSize)
		{
			return true;
		}
		final A_Type defaultTypeObject = aTypeObject.defaultType();
		if (!defaultTypeObject.isSupertypeOfPrimitiveTypeEnum(ANY))
		{
			return object.tupleElementsInRangeAreInstancesOf(
				breakIndex + 1,
				tupleSize,
				defaultTypeObject);
		}
		return true;
	}

	/**
	 * The hash value is stored raw in the object's hashOrZero slot if it
	 * has been computed, otherwise that slot is zero. If a zero is
	 * detected, compute the hash and store it in hashOrZero. Note that the
	 * hash can (extremely rarely) be zero, in which case the hash has to be
	 * computed each time.
	 *
	 * @param object An object.
	 * @return The hash.
	 */
	private final int hash (final A_Tuple object)
	{
		int hash = object.hashOrZero();
		if (hash == 0)
		{
			hash = computeHashForObject(object);
			object.hashOrZero(hash);
		}
		return hash;
	}

	@Override @AvailMethod
	final int o_Hash (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return hash(object);
			}
		}
		return hash(object);
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		final A_Tuple tupleOfTypes = object.copyAsMutableObjectTuple();
		final int tupleSize = object.tupleSize();
		for (int i = 1; i <= tupleSize; i++)
		{
			tupleOfTypes.tupleAtPuttingCanDestroy(
				i,
				AbstractEnumerationTypeDescriptor.withInstance(
					object.tupleAt(i)),
				true);
		}
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			IntegerDescriptor.fromInt(object.tupleSize()).kind(),
			tupleOfTypes,
			BottomTypeDescriptor.bottom());
	}

	@Override @AvailMethod
	abstract boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2);

	@Override @AvailMethod
	boolean o_CompareFromToWithAnyTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aTuple,
		final int startIndex2)
	{
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (!object.tupleAt(index1).equals(aTuple.tupleAt(index2)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aByteString,
		final int startIndex2)
	{
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteArrayTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteBufferTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteBufferTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteBufferTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntegerIntervalTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			anIntegerIntervalTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aSmallIntegerIntervalTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aSmallIntegerIntervalTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithRepeatedElementTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aRepeatedElementTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aRepeatedElementTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithTwoByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aTwoByteString,
		final int startIndex2)
	{
		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	@Override @AvailMethod
	A_Tuple o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		// Take a tuple of tuples and answer one big tuple constructed by
		// concatenating the subtuples together.
		final int tupleSize = object.tupleSize();
		if (tupleSize == 0)
		{
			return TupleDescriptor.empty();
		}
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		A_Tuple accumulator = object.tupleAt(1);
		for (int i = 2; i <= tupleSize; i++)
		{
			accumulator = accumulator.concatenateWith(object.tupleAt(i), true);
		}
		return accumulator;
	}

	/**
	 * Subclasses should override to deal with short subranges and efficient
	 * copying techniques.  Here we pretty much just create a {@linkplain
	 * SubrangeTupleDescriptor subrange tuple}.
	 */
	@Override @AvailMethod
	A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		assert 1 <= start && start <= end + 1;
		final int tupleSize = object.tupleSize();
		assert 0 <= end && end <= tupleSize;
		final int size = end - start + 1;
		if (size == 0)
		{
			if (isMutable() && canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			return TupleDescriptor.empty();
		}
		if (size == tupleSize)
		{
			if (isMutable() && !canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		return SubrangeTupleDescriptor.createSubrange(object, start, size);
	}

	@Override @AvailMethod
	byte o_ExtractNybbleFromTupleAt (final AvailObject object, final int index)
	{
		// Get the element at the given index in the tuple object, and extract a
		// nybble from it. Fail if it's not a nybble. Obviously overridden for
		// speed in NybbleTupleDescriptor.
		final int nyb = object.tupleIntAt(index);
		if (!(nyb >= 0 && nyb <= 15))
		{
			error("nybble is out of range", object);
			return 0;
		}
		return (byte) nyb;
	}

	@Override @AvailMethod
	int o_HashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Compute object's hash value over the given range.
		if (startIndex == 1 && endIndex == object.tupleSize())
		{
			return object.hash();
		}
		return object.computeHashFromTo(startIndex, endIndex);
	}

	@Override @AvailMethod
	abstract AvailObject o_TupleAt (
		final AvailObject object,
		final int index);

	@Override @AvailMethod
	abstract A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract int o_TupleIntAt (final AvailObject object, final int index);

	@Override @AvailMethod
	A_Set o_AsSet (final AvailObject object)
	{
		A_Set result = SetDescriptor.empty();
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			result = result.setWithElementCanDestroy(object.tupleAt(i), true);
		}
		return result;
	}

	@Override @AvailMethod
	boolean o_IsTuple (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsString (final AvailObject object)
	{
		final int limit = object.tupleSize();
		for (int i = 1; i <= limit; i++)
		{
			if (!object.tupleAt(i).isCharacter())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
	{
		return ReverseTupleDescriptor.createReverseTuple(object);
	}

	@Override @AvailMethod
	abstract int o_TupleSize (final AvailObject object);

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		final int size = object.tupleSize();
		if (size == 0)
		{
			return SerializerOperation.NYBBLE_TUPLE;
		}
		boolean hasNonChars = false;
		boolean hasNonInts = false;
		int maxCodePoint = 0;
		int maxInteger = 0;
		int minInteger = 0;
		for (int i = 1; i <= size; i++)
		{
			final AvailObject element = object.tupleAt(i);
			if (element.isCharacter())
			{
				if (hasNonChars)
				{
					return SerializerOperation.GENERAL_TUPLE;
				}
				hasNonInts = true;
				maxCodePoint = Math.max(maxCodePoint, element.codePoint());
			}
			else
			{
				if (hasNonInts)
				{
					return SerializerOperation.GENERAL_TUPLE;
				}
				hasNonChars = true;
				if (element.isInt())
				{
					final int integer = element.extractInt();
					maxInteger = Math.max(maxInteger, integer);
					minInteger = Math.min(minInteger, integer);
				}
				else
				{
					return SerializerOperation.GENERAL_TUPLE;
				}
			}
		}
		assert !(hasNonChars && hasNonInts);
		if (hasNonChars)
		{
			assert !hasNonInts;
			if (minInteger >= 0 && maxInteger <= 15)
			{
				return SerializerOperation.NYBBLE_TUPLE;
			}
			if (minInteger >= 0 && maxInteger <= 255)
			{
				return SerializerOperation.BYTE_TUPLE;
			}
			return SerializerOperation.GENERAL_TUPLE;
		}
		assert hasNonInts;
		if (maxCodePoint <= 255)
		{
			return SerializerOperation.BYTE_STRING;
		}
		if (maxCodePoint <= 65535)
		{
			return SerializerOperation.SHORT_STRING;
		}
		return SerializerOperation.ARBITRARY_STRING;
	}


	/**
	 * Compute the object's hash value.
	 *
	 * @param object The object to hash.
	 * @return The hash value.
	 */
	int computeHashForObject (final A_Tuple object)
	{
		return object.computeHashFromTo(1, object.tupleSize());
	}


	/**
	 * Compute the hash value from the object's data. The result should be an
	 * {@code int}.  To keep the rehashing cost down for concatenated tuples, we
	 * use a non-commutative hash function. If the tuple has elements with hash
	 * values
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 *   <mrow>
	 *   <msub><mi>h</mi><mn>1</mn></msub>
	 *   <mi>&hellip;</mi>
	 *   <msub><mi>h</mi><mi>n</mi></msub>
	 * </mrow></math>,
	 * we use the formula
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 *   <mrow>
	 *     <mo>&InvisibleTimes;</mo>
	 *     <msub><mi>h</mi><mn>1</mn></msub>
	 *     <msup><mi>a</mi><mn>1</mn></msup>
	 *   </mrow>
	 *   <mo>+</mo>
	 *   <mrow>
	 *     <mo>&InvisibleTimes;</mo>
	 *     <msub><mi>h</mi><mn>2</mn></msub>
	 *     <msup><mi>a</mi><mn>2</mn></msup>
	 *   </mrow>
	 *   <mo>+</mo>
	 *   <mi>&hellip;</mi>
	 *   <mo>+</mo>
	 *   <mrow>
	 *     <mo>&InvisibleTimes;</mo>
	 *     <msub><mi>h</mi><mi>n</mi></msub>
	 *     <msup><mi>a</mi><mi>n</mi></msup>
	 *   </mrow>
	 * </mrow>/</math>.
	 * This can be rewritten as
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 *   <munderover>
	 *     <mo>&sum;</mo>
	 *     <mrow><mi>i</mi><mo>=</mo><mn>1</mn></mrow>
	 *     <mi>n</mi>
	 *   </munderover>
	 *   <mrow>
	 *     <msub><mi>h</mi><mi>i</mi></msub>
	 *     <mo>&InvisibleTimes;</mo>
	 *     <msup><mi>a</mi><mi>i</mi></msup>
	 *   </mrow>
	 * </mrow></math>
	 * ). The constant {@code a} is chosen as a primitive element of the group
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 *   <mfenced>
	 *     <msub>
	 *       <mo>&integers;</mo>
	 *       <msup><mn>2</mn><mn>32</mn></msup>
	 *     </msub>
	 *     <mo>&times;</mo>
	 *   </mfenced>
	 * </mrow></math>,
	 * specifically 1,664,525, as taken from <cite>Knuth, The Art of Computer
	 * Programming, Vol. 2, 2<sup>nd</sup> ed., page 102, row 26</cite>. See
	 * also pages 19, 20, theorems B and C. The period of this cycle is
	 * 2<sup>30</sup>.
	 *
	 * <p>To append an (n+1)<sup>st</sup> element to a tuple, one can compute
	 * the new hash by adding
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 *   <mrow>
	 *     <msub>
	 *       <mi>h</mi>
	 *       <mi><mrow><mi>n</mi><mo>&plus;</mo><mn>1</mn></mrow></mi>
	 *     </msub>
	 *     <mo>&InvisibleTimes;</mo>
	 *     <msup>
	 *       <mi>a</mi>
	 *       <mi><mrow><mi>n</mi><mo>&plus;</mo><mn>1</mn></mrow></mi>
	 *     </msup>
	 *   </mrow>
	 * </mrow></math>
	 * to the previous hash.  Similarly, concatenating two tuples of length x
	 * and y is a simple matter of multiplying the right tuple's hash by
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 *   <msup><mi>a</mi><mi>x</mi></msup>
	 * </mrow></math>
	 * and adding it to the left tuple's hash.
	 * </p>
	 *
	 * <p>
	 * The element hash values are exclusive-ored with
	 * {@linkplain #preToggle a randomly chosen constant} before being used, to
	 * help prevent similar nested tuples from producing equal hashes.
	 * </p>
	 */
	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = object.tupleAt(index).hash() ^ preToggle;
			hash = hash * multiplier + itemHash;
		}
		return hash * multiplier;
	}

	@Override @AvailMethod
	String o_AsNativeString (final AvailObject object)
	{
		final int size = object.tupleSize();
		final StringBuilder builder = new StringBuilder(size);
		for (int i = 1; i <= size; i++)
		{
			builder.appendCodePoint(object.tupleAt(i).codePoint());
		}
		return builder.toString();
	}

	/**
	 * Answer a mutable copy of object that holds arbitrary objects.
	 */
	@Override @AvailMethod
	A_Tuple o_CopyAsMutableObjectTuple (final AvailObject object)
	{
		final int size = object.tupleSize();
		final AvailObject result =
			ObjectTupleDescriptor.createUninitialized(size);
		result.hashOrZero(object.hashOrZero());
		for (int i = 1; i <= size; i++)
		{
			result.objectTupleAtPut(i, object.tupleAt(i));
		}
		return result;
	}

	@Override
	boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		for (int index = startIndex; index <= endIndex; index++)
		{
			if (!object.tupleAt(index).isInstanceOf(type))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A simple {@link Iterator} over a tuple's elements.
	 */
	private static final class TupleIterator implements Iterator<AvailObject>
	{
		/**
		 * The tuple over which to iterator.
		 */
		private final AvailObject tuple;

		/**
		 * The size of the tuple.
		 */
		private final int size;

		/**
		 * The index of the next {@linkplain AvailObject element}.
		 */
		int index = 1;

		/**
		 * Construct a new {@link TupleIterator} on the given {@linkplain
		 * TupleDescriptor tuple}.
		 *
		 * @param tuple
		 */
		@InnerAccess TupleIterator (final AvailObject tuple)
		{
			this.tuple = tuple;
			this.size = tuple.tupleSize();
		}

		@Override
		public boolean hasNext ()
		{
			return index <= size;
		}

		@Override
		public AvailObject next ()
		{
			if (index > size)
			{
				throw new NoSuchElementException();
			}

			return tuple.tupleAt(index++);
		}

		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public Iterator<AvailObject> o_Iterator (final AvailObject object)
	{
		object.makeImmutable();
		return new TupleIterator(object);
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		if (object.isString())
		{
			return object.asNativeString();
		}
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	@Override
	boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return object.isString();
	}

	/**
	 * Construct a new tuple of arbitrary {@linkplain AvailObject Avail objects}
	 * based on the given tuple, but with an additional element appended.  The
	 * elements may end up being shared between the original and the copy, so
	 * the client must ensure that either the elements are marked immutable, or
	 * one of the copies is not kept after the call.
	 */
	@Override @AvailMethod
	public A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		if (originalSize >= ObjectTupleDescriptor.maximumCopySize)
		{
			final A_Tuple singleton = TupleDescriptor.from(newElement);
			return object.concatenateWith(singleton, canDestroy);
		}
		final A_Tuple newTuple = ObjectTupleDescriptor.createUninitialized(
			originalSize + 1);
		for (int i = 1; i <= originalSize; i++)
		{
			newTuple.objectTupleAtPut(i, object.tupleAt(i));
		}
		newTuple.objectTupleAtPut(originalSize + 1, newElement);
		return newTuple;
	}

	@Override
	int o_TreeTupleLevel (final AvailObject object)
	{
		// TreeTupleDescriptor overrides this.
		return 0;
	}

	@Override
	abstract A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy);

	/**
	 * Transfer the specified range of bytes into the provided {@link
	 * ByteBuffer}.  The {@code ByteBuffer} should have enough room to store
	 * the required number of bytes.
	 */
	@Override @AvailMethod
	void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		for (int index = startIndex; index <= endIndex; index++)
		{
			outputByteBuffer.put((byte) object.tupleIntAt(index));
		}
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		if (object.isString())
		{
			writer.write(object.asNativeString());
		}
		else
		{
			writer.startArray();
			for (final AvailObject o : object)
			{
				o.writeTo(writer);
			}
			writer.endArray();
		}
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		if (object.isString())
		{
			writer.write(object.asNativeString());
		}
		else
		{
			writer.startArray();
			for (final AvailObject o : object)
			{
				o.writeSummaryTo(writer);
			}
			writer.endArray();
		}
	}

	/** The empty tuple. */
	private static final AvailObject emptyTuple;

	static
	{
		final A_Tuple tuple = NybbleTupleDescriptor.mutableObjectOfSize(0);
		tuple.hash();
		emptyTuple = tuple.makeShared();
	}

	/**
	 * Return the empty {@linkplain TupleDescriptor tuple}.  Other empty tuples
	 * can be created, but if you know the tuple is empty you can save time and
	 * space by returning this one.
	 *
	 * @return The tuple of size zero.
	 */
	public static AvailObject empty ()
	{
		return emptyTuple;
	}

	/**
	 * Create a tuple with the specified elements. The elements are not made
	 * immutable first, nor is the new tuple.
	 *
	 * @param elements
	 *        The array of AvailObjects from which to construct a tuple.
	 * @return The new mutable tuple.
	 */
	public static A_Tuple from (
		final A_BasicObject... elements)
	{
		if (elements.length == 0)
		{
			return empty();
		}
		A_Tuple tuple;
		final int size = elements.length;
		tuple = ObjectTupleDescriptor.createUninitialized(size);
		for (int i = 1; i <= size; i++)
		{
			tuple.objectTupleAtPut(i, elements[i - 1]);
		}
		return tuple;
	}

	/**
	 * Construct a new tuple of arbitrary {@linkplain AvailObject Avail objects}
	 * passed in a list.  The elements are not made immutable first, nor is the
	 * new tuple necessarily made immutable.
	 *
	 * @param list
	 *        The list of {@linkplain AvailObject Avail objects} from which
	 *        to construct a tuple.
	 * @return The corresponding tuple of objects.
	 */
	public static <E extends A_BasicObject> A_Tuple fromList (
		final List<E> list)
	{
		final int size = list.size();
		if (size == 0)
		{
			return empty();
		}
		final A_Tuple tuple = ObjectTupleDescriptor.createUninitialized(size);
		for (int i = 0; i < size; i++)
		{
			tuple.objectTupleAtPut(i + 1, list.get(i));
		}
		return tuple;
	}

	/**
	 * Construct a {@linkplain List list} from the specified {@linkplain
	 * TupleDescriptor tuple}. The elements are not made immutable.
	 *
	 * @param tuple
	 *        A tuple.
	 * @return The corresponding list of objects.
	 */
	@SuppressWarnings("unchecked")
	public static <X extends A_BasicObject> List<X> toList (
		final A_Tuple tuple)
	{
		final List<X> list =
			new ArrayList<>(tuple.tupleSize());
		for (final AvailObject element : tuple)
		{
			list.add((X) element);
		}
		return list;
	}

	/**
	 * Construct an {@linkplain AvailObject AvailObject[]} from the specified
	 * {@linkplain TupleDescriptor tuple}. The elements are not made immutable.
	 *
	 * @param tuple
	 *        A tuple.
	 * @return The corresponding Java array of AvailObjects.
	 */
	public static AvailObject[] toArray (final A_Tuple tuple)
	{
		final int size = tuple.tupleSize();
		final AvailObject[] array = new AvailObject[size];
		for (int i = 0; i < size; i++)
		{
			array[i] = tuple.tupleAt(i + 1);
		}
		return array;
	}

	/**
	 * Construct a new tuple of arbitrary {@linkplain AvailObject Avail objects}
	 * based on the given tuple, but with an occurrence of the specified element
	 * missing, if it was present at all.  The elements may end up being shared
	 * between the original and the copy, so the client must ensure that either
	 * the elements are marked immutable, or one of the copies is not kept after
	 * the call.  If the element is not found, then answer the original tuple.
	 *
	 * @param originalTuple
	 *        The original tuple of {@linkplain AvailObject Avail objects} on
	 *        which to base the new tuple.
	 * @param elementToExclude
	 *        The element that should should have an occurrence excluded from
	 *        the new tuple, if it was present.
	 * @return The new tuple.
	 */
	public static A_Tuple without (
		final A_Tuple originalTuple,
		final A_BasicObject elementToExclude)
	{
		final int originalSize = originalTuple.tupleSize();
		for (int seekIndex = 1; seekIndex <= originalSize; seekIndex++)
		{
			if (originalTuple.tupleAt(seekIndex).equals(elementToExclude))
			{
				final A_Tuple newTuple =
					ObjectTupleDescriptor.createUninitialized(originalSize - 1);
				for (int i = 1; i < seekIndex; i++)
				{
					newTuple.objectTupleAtPut(i, originalTuple.tupleAt(i));
				}
				for (int i = seekIndex + 1; i <= originalSize; i++)
				{
					newTuple.objectTupleAtPut(i - 1, originalTuple.tupleAt(i));
				}
				return newTuple;
			}
		}
		return originalTuple;
	}

	/**
	 * Construct a new tuple of integers. Use the most compact representation
	 * that can still represent each supplied {@link Integer}.
	 *
	 * @param list
	 *        The list of Java {@linkplain Integer}s to assemble in a tuple.
	 * @return A new mutable tuple of integers.
	 */
	public static A_Tuple fromIntegerList (final List<Integer> list)
	{
		if (list.size() == 0)
		{
			return empty();
		}
		final AvailObject tuple;
		final int minValue = min(list);
		if (minValue >= 0)
		{
			final int maxValue = max(list);
			if (maxValue <= 15)
			{
				tuple = NybbleTupleDescriptor.mutableObjectOfSize(list.size());
				for (int i = 1; i <= list.size(); i++)
				{
					tuple.rawNybbleAtPut(i, list.get(i - 1).byteValue());
				}
				return tuple;
			}
			if (maxValue <= 255)
			{
				tuple = ByteTupleDescriptor.mutableObjectOfSize(list.size());
				for (int i = 1; i <= list.size(); i++)
				{
					tuple.rawByteAtPut(i, list.get(i - 1).shortValue());
				}
				return tuple;
			}
		}
		tuple = ObjectTupleDescriptor.createUninitialized(list.size());
		for (int i = 1; i <= list.size(); i++)
		{
			tuple.objectTupleAtPut(
				i,
				IntegerDescriptor.fromInt(list.get(i - 1).intValue()));
		}
		return tuple;
	}

	/**
	 * Four tables, each containing powers of {@link #multiplier}.  The 0th
	 * table contains M^i for i=0..255, the 1st table contains M^(256*i)
	 * for i=0..255,... and the 3rd table contains M^((256^3)*i) for i=0..255.
	 */
	static final int[][] powersOfMultiplier = new int[4][256];

	static
	{
		int scaledMultiplier = multiplier;
		for (final int[] subtable : powersOfMultiplier)
		{
			int power = 1;
			for  (int i = 0; i < 256; i++)
			{
				subtable[i] = power;
				power *= scaledMultiplier;
			}
			scaledMultiplier = power;
		}
	}

	/**
	 * Compute {@link #multiplier} raised to the specified power, truncated to
	 * an int.
	 *
	 * @param anInteger
	 *        The exponent by which to raise the base {@link #multiplier}.
	 * @return {@link #multiplier} raised to the specified power.
	 */
	static int multiplierRaisedTo (final int anInteger)
	{
		return powersOfMultiplier[0][anInteger & 0xFF]
			* powersOfMultiplier[1][(anInteger >> 8) & 0xFF]
			* powersOfMultiplier[2][(anInteger >> 16) & 0xFF]
			* powersOfMultiplier[3][(anInteger >> 24) & 0xFF];
	}


	/**
	 * The constant by which each element's hash should be XORed prior to
	 * combining them.  This reduces the chance of systematic collisions due to
	 * using the same elements in different patterns of nested tuples.
	 */
	static final int preToggle = 0x71E570A6;

	/**
	 * Construct a new {@link TupleDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected TupleDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}
}

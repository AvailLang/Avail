/**
 * TupleDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.min;
import static java.util.Collections.min;
import static java.util.Collections.max;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

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
	public enum IntegerSlots implements IntegerSlotsEnum
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

	@Override @AvailMethod
	void o_HashOrZero (final AvailObject object, final int value)
	{
		object.setSlot(IntegerSlots.HASH_OR_ZERO, value);
	}

	@Override @AvailMethod
	int o_HashOrZero (final AvailObject object)
	{
		return object.slot(IntegerSlots.HASH_OR_ZERO);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == IntegerSlots.HASH_OR_ZERO;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
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
				final AvailObject availChar = object.tupleAt(i);
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
		final List<String> strings = new ArrayList<String>(object.tupleSize());
		int totalChars = 0;
		boolean anyBreaks = false;
		for (final AvailObject element : object)
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
	boolean o_EqualsAnyTuple (
		final AvailObject object,
		final AvailObject aTuple)
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
			aTuple.becomeIndirectionTo(object);
			object.makeImmutable();
		}
		else
		{
			object.becomeIndirectionTo(aTuple);
			// Now that there are at least two references to it...
			aTuple.makeImmutable();
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_EqualsByteString (
		final AvailObject object,
		final AvailObject aByteString)
	{
		// Default to generic tuple comparison.

		return o_EqualsAnyTuple(object, aByteString);
	}

	@Override @AvailMethod
	boolean o_EqualsByteTuple (
		final AvailObject object,
		final AvailObject aTuple)
	{
		// Default to generic tuple comparison.

		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final AvailObject aTuple)
	{
		// Default to generic comparison.

		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsObjectTuple (
		final AvailObject object,
		final AvailObject aTuple)
	{
		// Default to generic comparison.

		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override @AvailMethod
	boolean o_EqualsTwoByteString (
		final AvailObject object,
		final AvailObject aTwoByteString)
	{
		// Default to generic tuple comparison.

		return o_EqualsAnyTuple(object, aTwoByteString);
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject)
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more compact, more efficient, older generation) than
		// the second one?

		return object.bitsPerEntry() < anotherObject.bitsPerEntry();
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aTypeObject)
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
		final AvailObject typeTuple = aTypeObject.typeTuple();
		final int breakIndex = min(tupleSize, typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (!object.tupleAt(i).isInstanceOf(aTypeObject.typeAtIndex(i)))
			{
				return false;
			}
		}
		final AvailObject defaultTypeObject = aTypeObject.defaultType();
		if (!defaultTypeObject.isSupertypeOfPrimitiveTypeEnum(ANY))
		{
			for (int i = breakIndex + 1; i <= tupleSize; i++)
			{
				if (!object.tupleAt(i).isInstanceOf(defaultTypeObject))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// The hash value is stored raw in the object's hashOrZero slot if it
		// has been computed, otherwise that slot is zero. If a zero is
		// detected, compute the hash and store it in hashOrZero. Note that the
		// hash can (extremely rarely) be zero, in which case the hash has to be
		// computed each time.

		int hash = object.hashOrZero();
		if (hash == 0)
		{
			hash = computeHashForObject(object);
			object.hashOrZero(hash);
		}
		return hash;
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		final AvailObject tupleOfTypes = object.copyAsMutableObjectTuple();
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
		final AvailObject anotherObject,
		final int startIndex2);

	@Override @AvailMethod
	boolean o_CompareFromToWithAnyTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2)
	{
		int index2 = startIndex2;
		for (int index1 = startIndex1; index1 <= endIndex1; index1++)
		{
			if (!object.tupleAt(index1).equals(aTuple.tupleAt(index2)))
			{
				return false;
			}
			index2++;
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
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
		final AvailObject aByteTuple,
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
	boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
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
		final AvailObject anObjectTuple,
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
		final AvailObject aTwoByteString,
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
	AvailObject o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		// Take a tuple of tuples and answer one big tuple constructed by
		// concatenating the subtuples together. Optimized so that the resulting
		// splice tuple's zones are not themselves splice tuples.

		int zones = 0;
		int newSize = 0;
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			final AvailObject sub = object.tupleAt(i);
			final int subZones = sub.tupleSize() == 0
				? 0
				: sub.traversed().isSplice()
					? sub.numberOfZones()
					: 1;
			// Empty zones are not allowed in splice tuples.
			zones += subZones;
			newSize += sub.tupleSize();
		}
		if (newSize == 0)
		{
			return TupleDescriptor.empty();
		}
		// Now we know how many zones will be in the final tuple. We must
		// allocate room for it. In the Java translation, the garbage collector
		// will never increase the number of zones, so there will always be
		// room in the allocated chunk (maybe more than enough) for all the
		// zones.

		final AvailObject result =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				zones,
				zones * 2,
				SpliceTupleDescriptor.mutable());
		int majorIndex = 0;
		int zone = 1;
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			final AvailObject sub = object.tupleAt(i).traversed();
			if (sub.isSplice())
			{
				assert sub.tupleSize() > 0;
				for (
					int originalZone = 1, end2 = sub.numberOfZones();
					originalZone <= end2;
					originalZone++)
				{
					majorIndex += sub.sizeOfZone(originalZone);
					result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
						zone,
						sub.subtupleForZone(originalZone),
						sub.startSubtupleIndexInZone(originalZone),
						majorIndex);
					if (canDestroy && sub.descriptor().isMutable())
					{
						sub.setSubtupleForZoneTo(
							originalZone,
							NullDescriptor.nullObject());
					}
					zone++;
				}
			}
			else
			{
				if (sub.tupleSize() != 0)
				{
					majorIndex += sub.tupleSize();
					result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
						zone,
						sub,
						1,
						majorIndex);
					zone++;
				}
				if (canDestroy && isMutable())
				{
					object.tupleAtPut(i, NullDescriptor.nullObject());
				}
			}
		}
		assert zone == zones + 1 : "Wrong number of zones";
		assert majorIndex == newSize : "Wrong resulting tuple size";
		result.hashOrZero(result.computeHashFromTo(1, majorIndex));
		if (canDestroy && isMutable)
		{
			object.assertObjectUnreachableIfMutable();
		}
		result.verify();
		return result;
	}

	@Override @AvailMethod
	AvailObject o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		// Make a tuple that only contains the given range of elements of the
		// given tuple.  Overridden in ObjectTupleDescriptor so that if
		// isMutable and canDestroy are true then the parts of the tuple outside
		// the subrange will have their reference counts decremented (i.e.,
		// destroyed if mutable) and those tuple slots will be set to the null
		// object.

		assert 1 <= start && start <= end + 1;
		assert 0 <= end && end <= object.tupleSize();
		if (start - 1 == end)
		{
			if (isMutable && canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			return TupleDescriptor.empty();
		}
		if (isMutable && canDestroy && (start == 1 || end - start < 20))
		{
			if (start != 1)
			{
				for (int i = 1; i <= end - start + 1; i++)
				{
					object.tupleAtPut(i, object.tupleAt(start + i - 1));
				}
			}
			object.truncateTo(end - start + 1);
			return object;
		}
		final AvailObject result =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				1,
				2,
				SpliceTupleDescriptor.mutable());
		result.hashOrZero(object.computeHashFromTo(start, end));
		result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
			1,
			object,
			start,
			end - start + 1);
		result.verify();
		return result;
	}

	@Override @AvailMethod
	byte o_ExtractNybbleFromTupleAt (
		final AvailObject object,
		final int index)
	{
		// Get the element at the given index in the tuple object, and extract a
		// nybble from it.  Fail if it's not a nybble. Obviously overridden for
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
	abstract AvailObject o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy);

	@Override @AvailMethod
	abstract int o_TupleIntAt (final AvailObject object, final int index);

	@Override @AvailMethod
	AvailObject o_AsSet (final AvailObject object)
	{
		AvailObject result = SetDescriptor.empty();
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
	boolean o_IsByteTuple (final AvailObject object)
	{
		for (int i = object.tupleSize(); i >= 1; i--)
		{
			if (!object.tupleAt(i).isUnsignedByte())
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override @AvailMethod
	boolean o_IsString (final AvailObject object)
	{
		for (int i = object.tupleSize(); i >= 1; i--)
		{
			if (!object.tupleAt(i).isCharacter())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	abstract int o_TupleSize (final AvailObject object);

	@Override @AvailMethod
	boolean o_IsSplice (final AvailObject object)
	{
		return false;
	}

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
	int computeHashForObject (final AvailObject object)
	{
		return object.computeHashFromTo(1, object.tupleSize());
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// Compute the hash value from the object's data. The result should be
		// a Smalltalk Integer between 16r00000000 and 16rFFFFFFFF inclusive.
		// To keep the rehashing cost down for concatenated tuples, we use a
		// non-commutative hash function. If the tuple has elements with hash
		// values h[1] through h[n], we use the formula...
		// H=h[1]a^1 + h[2]a^2 + h[3]a^3... + h[n]a^n
		// This can be rewritten as sum(i=1..n)(a^i * h[i]). The constant 'a' is
		// chosen as a primitive element of (Z[2^32],*), specifically 1664525,
		// as taken from Knuth, The Art of Computer Programming, Vol. 2, 2nd
		// ed., page 102, row 26. See also pages 19, 20, theorems B and C. The
		// period of this cycle is 2^30. The element hash values are xored with
		// a random constant (16r9EE570A6) before being used, to help prevent
		// similar nested tuples from producing equal hashes.

		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = object.tupleAt(index).hash() ^ PreToggle;
			hash = hash * multiplier + itemHash;
		}
		return hash * multiplier;
	}

	@Override @AvailMethod
	String o_AsNativeString (
		final AvailObject object)
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
	AvailObject o_CopyAsMutableObjectTuple (
		final AvailObject object)
	{
		final int size = object.tupleSize();
		final AvailObject result = ObjectTupleDescriptor.mutable().create(size);
		result.hashOrZero(object.hashOrZero());
		for (int i = 1; i <= size; i++)
		{
			result.tupleAtPut(i, object.tupleAt(i));
		}
		return result;
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	public Iterator<AvailObject> o_Iterator (final AvailObject object)
	{
		object.makeImmutable();
		final int size = object.tupleSize();
		return new Iterator<AvailObject>()
		{
			/**
			 * The index of the next {@linkplain AvailObject element}.
			 */
			int index = 1;

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

				return object.tupleAt(index++);
			}

			@Override
			public void remove ()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	Object o_MarshalToJava (
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
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return object.isString();
	}

	/**
	 * The empty tuple.
	 */
	static AvailObject EmptyTuple;

	/**
	 * Create my cached empty tuple and various well known strings.
	 */
	static void createWellKnownObjects ()
	{
		EmptyTuple = NybbleTupleDescriptor.mutableObjectOfSize(0);
		EmptyTuple.hashOrZero(0);
		EmptyTuple.makeImmutable();
}

	/**
	 * Clear my cached empty tuple and various well known strings.
	 */
	static void clearWellKnownObjects ()
	{
		EmptyTuple = null;
	}

	/**
	 * Create a tuple with the specified elements.  The elements are not made
	 * immutable first, nor is the new tuple.
	 *
	 * @param elements
	 *            The array of AvailObjects from which to construct a tuple.
	 * @return
	 *            The new mutable tuple.
	 */
	public static AvailObject from (
		final AvailObject ... elements)
	{
		AvailObject tuple;
		final int size = elements.length;
		tuple = ObjectTupleDescriptor.mutable().create(size);
		for (int i = 1; i <= size; i++)
		{
			tuple.tupleAtPut(i, elements[i - 1]);
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
	public static AvailObject fromList (
		final List<AvailObject> list)
	{
		final int size = list.size();
		if (size == 0)
		{
			return TupleDescriptor.empty();
		}
		final AvailObject tuple = ObjectTupleDescriptor.mutable().create(size);
		for (int i = 0; i < size; i++)
		{
			tuple.tupleAtPut(i + 1, list.get(i));
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
	public static List<AvailObject> toList (final AvailObject tuple)
	{
		final List<AvailObject> list = new ArrayList<AvailObject>(
			tuple.tupleSize());
		for (final AvailObject element : tuple)
		{
			list.add(element);
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
	public static AvailObject[] toArray (final AvailObject tuple)
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
	 * based on the given tuple, but with an additional element appended.  The
	 * elements may end up being shared between the original and the copy, so
	 * the client must ensure that either the elements are marked immutable, or
	 * one of the copies is not kept after the call.
	 *
	 * @param newElement
	 *            The new element that should be at the end of the new tuple.
	 * @return
	 *            The new mutable tuple of objects including all elements of the
	 *            passed tuple plus the new element.
	 */
	@Override @AvailMethod
	public AvailObject o_AppendCanDestroy (
		final AvailObject object,
		final AvailObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		final AvailObject newTuple = ObjectTupleDescriptor.mutable().create(
			originalSize + 1);
		for (int i = 1; i <= originalSize; i++)
		{
			newTuple.tupleAtPut(i, object.tupleAt(i));
		}
		newTuple.tupleAtPut(originalSize + 1, newElement);
		return newTuple;
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
	 *            The original tuple of {@linkplain AvailObject Avail objects}
	 *            on which to base the new tuple.
	 * @param elementToExclude
	 *            The element that should should have an occurrence excluded
	 *            from the new tuple, if it was present.
	 * @return
	 *            The new tuple.
	 */
	public static AvailObject without (
		final AvailObject originalTuple,
		final AvailObject elementToExclude)
	{
		final int originalSize = originalTuple.tupleSize();
		for (int seekIndex = 1; seekIndex <= originalSize; seekIndex++)
		{
			if (originalTuple.tupleAt(seekIndex).equals(elementToExclude))
			{
				final AvailObject newTuple =
					ObjectTupleDescriptor.mutable().create(originalSize - 1);
				for (int i = 1; i < seekIndex; i++)
				{
					newTuple.tupleAtPut(i, originalTuple.tupleAt(i));
				}
				for (int i = seekIndex + 1; i <= originalSize; i++)
				{
					newTuple.tupleAtPut(i - 1, originalTuple.tupleAt(i));
				}
				return newTuple;
			}
		}
		return originalTuple;
	}

	/**
	 * Construct a new tuple of integers.  Use the most compact representation
	 * that can still represent each supplied {@link Integer}.
	 *
	 * @param list
	 *            The list of Java {@linkplain Integer}s to assemble in a tuple.
	 * @return
	 *            A new mutable tuple of integers.
	 */
	public static AvailObject fromIntegerList (
		final List<Integer> list)
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
		tuple = ObjectTupleDescriptor.mutable().create(list.size());
		for (int i = 1; i <= list.size(); i++)
		{
			tuple.tupleAtPut(
				i,
				IntegerDescriptor.fromInt(list.get(i - 1).intValue()));
		}
		return tuple;
	}

	/**
	 * Compute {@link #multiplier} raised to the specified power, truncated to
	 * an int.
	 *
	 * @param anInteger
	 *            The exponent by which to raise the base {@link #multiplier}.
	 * @return
	 *            {@link #multiplier} raised to the specified power.
	 */
	static int multiplierRaisedTo (final int anInteger)
	{
		int result = 1;
		int power = multiplier;
		int residue = anInteger;
		while (residue != 0)
		{
			if ((residue & 1) != 0)
			{
				result *= power;
			}
			power *= power;
			residue >>>= 1;
		}
		return result;
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
		return EmptyTuple;
	}

	/**
	 * The constant by which each element's hash should be XORed prior to
	 * combining them.  This reduces the chance of systematic collisions due to
	 * using the same elements in different patterns of nested tuples.
	 */
	static final int PreToggle = 0x71E570A6;

	/**
	 * Construct a new {@link TupleDescriptor}.
	 *
	 * @param isMutable
	 *            Does the {@linkplain Descriptor descriptor} represent a
	 *            mutable object?
	 */
	protected TupleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}

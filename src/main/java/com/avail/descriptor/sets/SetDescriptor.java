/*
 * SetDescriptor.java
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

package com.avail.descriptor.sets;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.A_Character;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.Descriptor;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.representation.AvailObjectFieldHelper;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeTag;
import com.avail.exceptions.AvailErrorCode;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.IteratorNotNull;
import com.avail.utility.json.JSONWriter;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;

import static com.avail.descriptor.numbers.IntegerDescriptor.fromInt;
import static com.avail.descriptor.sets.LinearSetBinDescriptor.*;
import static com.avail.descriptor.sets.SetDescriptor.ObjectSlots.ROOT_BIN;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.types.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.types.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.types.TypeDescriptor.Types.CHARACTER;
import static com.avail.descriptor.types.TypeDescriptor.Types.NONTYPE;
import static java.lang.String.format;

/**
 * An Avail {@linkplain SetDescriptor set} refers to the root of a Bagwell Ideal
 * Hash Tree. If the set is empty, the root is {@linkplain NilDescriptor
 * nil}, which may not be a member of a set. If the set has one element, the
 * root is the element itself. If the set has two or more elements then a
 * {@linkplain SetBinDescriptor bin} must be used.  There are two types of bin,
 * the {@linkplain LinearSetBinDescriptor linear bin} and the {@linkplain
 * HashedSetBinDescriptor hashed bin}. The linear bin is used below a threshold
 * size, after which a hashed bin is substituted. The linear bin simply
 * contains an arbitrarily ordered list of elements, which are examined
 * exhaustively when testing set membership. The hashed bin uses up to five
 * bits of an element's {@link AvailObject#hash() hash value} to partition
 * the set. The depth of the bin in the hash tree determines which hash bits
 * are used.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SetDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The topmost bin of this set.  If it's {@link NilDescriptor nil},
		 * the set is empty.  If it's a {@link SetBinDescriptor set bin} then
		 * the bin contains the elements.  Otherwise the set contains one
		 * element, the object in this field.
		 */
		ROOT_BIN
	}

	/**
	 * Extract the root {@linkplain SetBinDescriptor bin} from the {@linkplain
	 * SetDescriptor set}.
	 *
	 * @param object The set from which to extract the root bin.
	 * @return The set's bin.
	 */
	private static AvailObject rootBin (final AvailObject object)
	{
		return object.slot(ROOT_BIN);
	}

	/**
	 * Replace the {@code SetDescriptor set}'s root {@linkplain
	 * SetBinDescriptor bin}. The replacement may be the {@link
	 * NilDescriptor#nil nil} to indicate an empty map.
	 *
	 * @param set
	 *        The set (must not be an indirection).
	 * @param bin
	 *        The root bin for the set, or nil.
	 */
	private static void setRootBin (
		final AvailObject set,
		final A_BasicObject bin)
	{
		set.setSlot(ROOT_BIN, bin);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		if (object.setSize() == 0)
		{
			aStream.append('∅');
		}
		else if (object.setElementsAreAllInstancesOfKind(CHARACTER.o()))
		{
			aStream.append("¢[");
			final SortedSet<Integer> codePointsSet = new TreeSet<>();
			for (final A_Character character : object)
			{
				codePointsSet.add(character.codePoint());
			}
			final Iterator<Integer> iterator = codePointsSet.iterator();
			int runStart = iterator.next();
			do
			{
				int runEnd = runStart;
				int next = -1;
				while (iterator.hasNext())
				{
					next = iterator.next();
					if (next != runEnd + 1)
					{
						break;
					}
					runEnd++;
					next = -1;
				}
				writeRangeElement(aStream, runStart);
				if (runEnd != runStart)
				{
					// Skip the dash if the start and end are consecutive.
					if (runEnd != runStart + 1)
					{
						aStream.appendCodePoint('-');
					}
					writeRangeElement(aStream, runEnd);
				}
				runStart = next;
			}
			while (runStart != -1);
			aStream.append("]");
		}
		else
		{
			final A_Tuple tuple = object.asTuple();
			aStream.append('{');
			boolean first = true;
			for (final AvailObject element : tuple)
			{
				if (!first)
				{
					aStream.append(", ");
				}
				element.printOnAvoidingIndent(
					aStream, recursionMap, indent + 1);
				first = false;
			}
			aStream.append('}');
		}
	}

	/**
	 * Write a code point that's either the start or end of a range, or a single
	 * value.
	 *
	 * @param builder Where to write the possibly encoded code point.
	 * @param codePoint The code point ({@code int}) to write.
	 */
	private static void writeRangeElement (
		final StringBuilder builder,
		final int codePoint)
	{
		int escaped = -1;
		switch (codePoint)
		{
			case ' ': // Show Unicode space (U+0020) as itself.
				builder.appendCodePoint(' '); return;
			case '-': // Special - used to show ranges.
			case '[': // Special - start of character set.
			case ']': // Special - end of character set.
				builder.append(format("\\(%x)", codePoint)); return;
			case '\n': escaped = 'n'; break;
			case '\r': escaped = 'r'; break;
			case '\t': escaped = 't'; break;
			case '\\': escaped = '\\'; break;
			case '"': escaped = '"'; break;
		}
		if (escaped != -1)
		{
			builder.appendCodePoint('\\');
			builder.appendCodePoint(escaped);
			return;
		}
		switch (Character.getType(codePoint))
		{
			case Character.COMBINING_SPACING_MARK:
			case Character.CONTROL:
			case Character.ENCLOSING_MARK:
			case Character.FORMAT:
			case Character.NON_SPACING_MARK:
			case Character.PARAGRAPH_SEPARATOR:
			case Character.PRIVATE_USE:
			case Character.SPACE_SEPARATOR:
			case Character.SURROGATE:
			case Character.UNASSIGNED:
			{
				builder.append(format("\\(%X)", codePoint));
				return;
			}
			default:
			{
				builder.appendCodePoint(codePoint);
			}
		}
	}

	@Override
	protected String o_NameForDebugger (final AvailObject object)
	{
		return super.o_NameForDebugger(object) + ": setSize="
			+ object.setSize();
	}

	/**
	 * Synthetic slots to display.
	 */
	enum FakeSetSlots implements ObjectSlotsEnumJava
	{
		/**
		 * A fake slot to present in the debugging view for each of the elements
		 * of this set.
		 */
		ELEMENT_;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Use the {@linkplain SetIterator set iterator} to build the list of values
	 * (in arbitrary order).  Hide the bin structure.
	 */
	@Override
	protected AvailObjectFieldHelper[] o_DescribeForDebugger (
		final AvailObject object)
	{
		final AvailObjectFieldHelper[] fields =
			new AvailObjectFieldHelper[object.setSize()];
		int counter = 0;
		for (final AvailObject element : object)
		{
			fields[counter] = new AvailObjectFieldHelper(
				object, FakeSetSlots.ELEMENT_, counter + 1, element);
			counter++;
		}
		return fields;
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsSet(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsSet (final AvailObject object, final A_Set aSet)
	{
		// Check if either the sets or the bins are the same objects.
		if (object.sameAddressAs(aSet)
			|| rootBin(object).sameAddressAs(rootBin((AvailObject) aSet)))
		{
			return true;
		}
		// Eliminate the trivial case of different sizes or hashes.
		if (object.setSize() != aSet.setSize()
			|| object.hash() != aSet.hash())
		{
			return false;
		}
		// TODO: [MvG] Unfortunately, this also has time at least proportional
		// to the set sizes, even if the sets are virtually identical and share
		// structural subcomponents.  We can do better, ostensibly by navigating
		// the tries together, checking for structural sharing.  Perhaps also
		// coalescing bins that happen to be equivalent, but that's harder to
		// check safely.
		if (!rootBin(object).isBinSubsetOf(aSet))
		{
			return false;
		}
		if (!isShared())
		{
			aSet.makeImmutable();
			object.becomeIndirectionTo(aSet);
		}
		else if (!aSet.descriptor().isShared())
		{
			object.makeImmutable();
			aSet.becomeIndirectionTo(object);
		}
		else
		{
			// They're both shared.  Substitute one of the bins for the other to
			// speed up subsequent equality checks.
			object.writeBackSlot(ROOT_BIN, 1, rootBin((AvailObject) aSet));
		}
		return true;
	}

	@Override @AvailMethod
	protected boolean o_HasElement (
		final AvailObject object,
		final A_BasicObject elementObject)
	{
		return rootBin(object).binHasElementWithHash(
			elementObject, elementObject.hash());
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		// A set's hash is a simple function of its rootBin's setBinHash, which
		// is always the sum of its elements' hashes.
		return rootBin(object).setBinHash() ^ 0xCD9EFC6;
	}

	@Override @AvailMethod
	protected boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aTypeObject)
	{
		if (aTypeObject.isSupertypeOfPrimitiveTypeEnum(NONTYPE))
		{
			return true;
		}
		if (!aTypeObject.isSetType())
		{
			return false;
		}
		// See if it's an acceptable size...
		if (!aTypeObject.sizeRange().rangeIncludesInt(object.setSize()))
		{
			return false;
		}
		final A_Type expectedContentType = aTypeObject.contentType();
		if (expectedContentType.isEnumeration())
		{
			// Check the complete membership.
			for (final AvailObject member : object)
			{
				if (!expectedContentType.enumerationIncludesInstance(member))
				{
					return false;
				}
			}
			return true;
		}
		return rootBin(object).binElementsAreAllInstancesOfKind(
			expectedContentType);
	}

	@Override @AvailMethod
	protected boolean o_IsSet (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected boolean o_IsSubsetOf (final AvailObject object, final A_Set another)
	{
		return object.setSize() <= another.setSize()
			&& rootBin(object).isBinSubsetOf(another);
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		final int size = object.setSize();
		final AvailObject sizeRange = instanceType(fromInt(size));
		return setTypeForSizesContentType(sizeRange, enumerationWith(object));
	}

	/**
	 * Answer whether all my elements are instances of the specified kind.
	 */
	@Override @AvailMethod
	protected boolean o_SetElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind)
	{
		return rootBin(object).binElementsAreAllInstancesOfKind(kind);
	}

	@Override @AvailMethod
	protected A_Set o_SetIntersectionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		// Compute the intersection of two sets. May destroy one of them if
		// it's mutable and canDestroy is true.
		final A_Set smaller;
		final A_Set larger;
		if (object.setSize() <= otherSet.setSize())
		{
			smaller = object;
			larger = otherSet.traversed();
		}
		else
		{
			larger = object;
			smaller = otherSet.traversed();
		}
		boolean intersected = false;
		A_Set result = smaller.makeImmutable();
		for (final AvailObject element : smaller)
		{
			if (!larger.hasElement(element))
			{
				if (!intersected)
				{
					assert result == smaller;
					result.makeImmutable();
					intersected = true;
				}
				result = result.setWithoutElementCanDestroy(element, true);
			}
		}
		return result;
	}

	@Override
	protected boolean o_SetIntersects (
		final AvailObject object,
		final A_Set otherSet)
	{
		final A_Set smaller;
		final A_Set larger;
		if (object.setSize() <= otherSet.setSize())
		{
			smaller = object;
			larger = otherSet.traversed();
		}
		else
		{
			larger = object;
			smaller = otherSet.traversed();
		}
		for (final AvailObject element : smaller)
		{
			if (larger.hasElement(element))
			{
				return true;
			}
		}
		return false;
	}

	@Override @AvailMethod
	protected A_Set o_SetMinusCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		// Compute the asymmetric difference of two sets (a \ b).  May destroy
		// one of them if it's mutable and canDestroy is true.
		boolean intersected = false;
		A_Set result = object;
		for (final AvailObject element : object)
		{
			if (otherSet.hasElement(element))
			{
				if (!intersected)
				{
					result.makeImmutable();
					intersected = true;
				}
				result = result.setWithoutElementCanDestroy(element, true);
			}
		}
		return result;
	}

	@Override @AvailMethod
	protected A_Set o_SetUnionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		// Compute the union of two sets. May destroy one of them if it's
		// mutable and canDestroy is true.
		final A_Set smaller;
		final A_Set larger;
		if (object.setSize() <= otherSet.setSize())
		{
			smaller = object;
			larger = otherSet.traversed();
		}
		else
		{
			larger = object;
			smaller = otherSet.traversed();
		}
		if (!canDestroy && larger.descriptor().isMutable())
		{
			larger.makeImmutable();
		}
		if (smaller.setSize() == 0)
		{
			return larger;
		}
		A_Set result = larger;
		for (final A_BasicObject element : smaller)
		{
			result = result.setWithElementCanDestroy(
				element.makeImmutable(),
				canDestroy);
		}
		return result;
	}

	@Override @AvailMethod
	protected A_Set o_SetWithElementCanDestroy (
		final AvailObject object,
		final A_BasicObject newElementObject,
		final boolean canDestroy)
	{
		// Ensure newElementObject is in the set, adding it if necessary. May
		// destroy the set if it's mutable and canDestroy is true.
		final int elementHash = newElementObject.hash();
		final AvailObject root = rootBin(object);
		final int oldSize = root.setBinSize();
		final A_BasicObject newRootBin =
			root.setBinAddingElementHashLevelCanDestroy(
				newElementObject,
				elementHash,
				(byte) 0,
				canDestroy && isMutable());
		if (newRootBin.setBinSize() == oldSize)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final AvailObject result;
		if (canDestroy && isMutable())
		{
			result = object;
		}
		else
		{
			result = mutable().create();
		}
		setRootBin(result, newRootBin);
		return result;
	}

	@Override @AvailMethod
	protected A_Set o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObjectToExclude,
		final boolean canDestroy)
	{
		// Ensure elementObjectToExclude is not in the set, removing it if
		// necessary. May destroy the set if it's mutable and canDestroy is
		// true.
		final AvailObject root = rootBin(object);
		final int oldSize = root.setBinSize();
		final AvailObject newRootBin = root.binRemoveElementHashLevelCanDestroy(
			elementObjectToExclude,
			elementObjectToExclude.hash(),
			(byte) 0,
			(canDestroy && isMutable()));
		if (newRootBin.setBinSize() == oldSize)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final AvailObject result;
		if (canDestroy && isMutable())
		{
			result = object;
		}
		else
		{
			result = mutable().create();
		}
		setRootBin(result, newRootBin);
		return result;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	/**
	 * A {@code SetIterator} is returned when a {@link SetDescriptor set} is
	 * asked for its {@link AvailObject#iterator()}.  Among other uses, this is
	 * useful when combined with Java's "foreach" control structure.
	 */
	public abstract static class SetIterator
		implements IteratorNotNull<AvailObject>
	{
		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}
	}

	@Override @AvailMethod
	protected SetIterator o_Iterator (final AvailObject object)
	{
		return rootBin(object).setBinIterator();
	}

	@Override @AvailMethod
	protected A_Tuple o_AsTuple (final AvailObject object)
	{
		final int size = object.setSize();
		if (size == 0)
		{
			return emptyTuple();
		}
		final IteratorNotNull<AvailObject> iterator = object.iterator();
		return generateObjectTupleFrom(
			object.setSize(), index -> iterator.next().makeImmutable());
	}

	@Override @AvailMethod
	protected int o_SetSize (final AvailObject object)
	{
		// Answer how many elements are in the set. Delegate to the rootBin.
		return rootBin(object).setBinSize();
	}

	@Override @AvailMethod @ThreadSafe
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.SET;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("set");
		writer.write("elements");
		writer.startArray();
		for (final AvailObject o : object)
		{
			o.writeTo(writer);
		}
		writer.endArray();
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("set");
		writer.write("elements");
		writer.startArray();
		for (final AvailObject o : object)
		{
			o.writeSummaryTo(writer);
		}
		writer.endArray();
		writer.endObject();
	}

	/**
	 * Construct a new {@code SetDescriptor set} from the specified
	 * {@linkplain Collection collection} of {@linkplain AvailObject objects}.
	 * Neither the elements nor the resultant set are made immutable.
	 *
	 * @param collection A collection.
	 * @return A new mutable set containing the elements of the collection.
	 */
	public static A_Set setFromCollection (
		final Collection<? extends A_BasicObject> collection)
	{
		final Iterator<? extends A_BasicObject> iterator =
			collection.iterator();
		return generateSetFrom(collection.size(), i -> iterator.next());
	}

	/**
	 * Construct a Java {@link Set} from the specified Avail {@link A_Set}.  The
	 * elements are not made immutable.
	 *
	 * @param set
	 *        An Avail set.
	 * @return The corresponding Java {@link Set} of {@link AvailObject}s.
	 */
	public static Set<AvailObject> toSet (
		final A_Set set)
	{
		final Set<AvailObject> nativeSet = new HashSet<>(set.setSize());
		for (final AvailObject element : set)
		{
			nativeSet.add(element);
		}
		return nativeSet;
	}

	/**
	 * Create an Avail set with the specified elements. The elements are not
	 * made immutable first, nor is the new set.
	 *
	 * @param elements
	 *        The array of Avail values from which to construct a set.
	 * @return The new mutable set.
	 */
	public static A_Set set (
		final A_BasicObject... elements)
	{
		return generateSetFrom(elements.length, i -> elements[i - 1]);
	}

	/**
	 * Create an Avail set with the numeric values of the specified {@link
	 * AvailErrorCode}s.  The numeric codes (Avail integers) are not made
	 * immutable first, nor is the new set.
	 *
	 * @param errorCodeElements
	 *        The array of AvailErrorCodes from which to construct a set.
	 * @return The new mutable set.
	 */
	public static A_Set set (
		final AvailErrorCode... errorCodeElements)
	{
		return generateSetFrom(errorCodeElements, AvailErrorCode::numericCode);
	}

	/**
	 * Construct a new {@code SetDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SetDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.SET_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link SetDescriptor}. */
	private static final SetDescriptor mutable =
		new SetDescriptor(Mutability.MUTABLE);

	@Override
	public SetDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link SetDescriptor}. */
	private static final SetDescriptor immutable =
		new SetDescriptor(Mutability.IMMUTABLE);

	@Override
	public SetDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link SetDescriptor}. */
	private static final SetDescriptor shared =
		new SetDescriptor(Mutability.SHARED);

	@Override
	public SetDescriptor shared ()
	{
		return shared;
	}

	/** The empty set. */
	private static final A_Set emptySet;

	static
	{
		final AvailObject set = mutable.create();
		setRootBin(set, emptyLinearSetBin((byte) 0));
		set.hash();
		emptySet = set.makeShared();
	}

	/**
	 * Answer the empty set.
	 *
	 * @return The empty set.
	 */
	@ReferencedInGeneratedCode
	public static A_Set emptySet ()
	{
		return emptySet;
	}

	/**
	 * The {@link CheckedMethod} for {@link #emptySet()}.
	 */
	public static final CheckedMethod emptySetMethod =
		CheckedMethod.staticMethod(
			SetDescriptor.class,
			"emptySet",
			A_Set.class);

	/**
	 * Create an Avail set with exactly one element.  The element is not made
	 * immutable first, nor is the new set.
	 *
	 * @param element
	 *        The sole element of the set.
	 * @return The new mutable set.
	 */
	public static A_Set singletonSet (
		final A_BasicObject element)
	{
		final AvailObject set = mutable.create();
		setRootBin(set, element);
		set.hash();
		return set;
	}

	/**
	 * Create an Avail set with exactly two elements.  The elements are not made
	 * immutable first, nor is the new set.
	 *
	 * @param element1
	 *        The first element of the set.
	 * @param element2
	 *        The second element of the set.
	 * @return The new mutable set.
	 */
	public static A_Set twoElementSet (
		final A_BasicObject element1,
		final A_BasicObject element2)
	{
		assert !element1.equals(element2);
		final AvailObject set = mutable.create();
		final AvailObject bin =
			createLinearSetBinPair((byte) 0, element1, element2);
		setRootBin(set, bin);
		set.hash();
		return set;
	}

	/**
	 * Create an {@link A_Set}, then run the generator the specified number of
	 * times to produce elements to add.  Deduplicate the elements.  Answer the
	 * resulting set.
	 *
	 * @param size The number of values to extract from the generator.
	 * @param generator A generator to provide {@link AvailObject}s to store.
	 * @return The new set.
	 */
	public static A_Set generateSetFrom (
		final int size,
		final IntFunction<? extends A_BasicObject> generator)
	{
		switch (size)
		{
			case 0: return emptySet();
			case 1: return singletonSet(generator.apply(1));
			case 2:
			{
				final A_BasicObject element1 = generator.apply(1);
				final A_BasicObject element2 = generator.apply(2);
				if (element1.equals(element2))
				{
					return singletonSet(element1);
				}
				else
				{
					return twoElementSet(element1, element2);
				}
			}
			default:
			{
				final AvailObject bin =
					generateSetBinFrom((byte)0, size, generator);
				final AvailObject set = mutable.create();
				setRootBin(set, bin);
				return set;
			}
		}
	}

	/**
	 * Create an {@link A_Set}, then run the iterator the specified number of
	 * times to produce elements to add.  Deduplicate the elements.  Answer the
	 * resulting set.
	 *
	 * @param size The number of values to extract from the iterator.
	 * @param iterator An iterator to provide {@link AvailObject}s to store.
	 * @return The new set.
	 */
	public static A_Set generateSetFrom (
		final int size,
		final Iterator<? extends A_BasicObject> iterator)
	{
		return generateSetFrom(size, i -> iterator.next());
	}

	/**
	 * Create an {@link A_Set}, then run the iterator the specified number of
	 * times to produce elements to add.  Deduplicate the elements.  Answer the
	 * resulting set.
	 *
	 * @param size
	 *        The number of values to extract from the iterator.
	 * @param iterator
	 *        An iterator to provide {@link AvailObject}s to store.
	 * @param transformer
	 *        A {@link Function} to transform iterator elements.
	 * @return The new set.
	 * @param <A>
	 *        The type of value produced by the iterator and consumed by the
	 *        transformer.
	 */
	public static <A> A_Set generateSetFrom (
		final int size,
		final Iterator<? extends A> iterator,
		final Function<? super A, ? extends A_BasicObject> transformer)
	{
		return generateSetFrom(size, i -> transformer.apply(iterator.next()));
	}

	/**
	 * Create an {@link A_Set}, then iterate over the collection, invoking the
	 * transformer to produce elements to add.  Deduplicate the elements.
	 * Answer the resulting set.
	 *
	 * @param collection
	 *        A collection containing values to be transformed.
	 * @param transformer
	 *        A {@link Function} to transform collection elements to
	 *        {@link A_BasicObject}s.
	 * @return The new set.
	 * @param <A>
	 *        The type of value found in the collection and consumed by the
	 *        transformer.
	 */
	public static <A> A_Set generateSetFrom (
		final Collection<? extends A> collection,
		final Function<? super A, ? extends A_BasicObject> transformer)
	{
		return generateSetFrom(
			collection.size(), collection.iterator(), transformer);
	}

	/**
	 * Create an {@link A_Set}, then iterate over the array, invoking the
	 * transformer to produce elements to add.  Deduplicate the elements.
	 * Answer the resulting set.
	 *
	 * @param array
	 *        An array containing values to be transformed.
	 * @param transformer
	 *        A {@link Function} to transform array elements to
	 *        {@link A_BasicObject}s.
	 * @return The new set.
	 * @param <A>
	 *        The type of value found in the array and consumed by the
	 *        transformer.
	 */
	public static <A> A_Set generateSetFrom (
		final A[] array,
		final Function<? super A, ? extends A_BasicObject> transformer)
	{
		return generateSetFrom(
			array.length, i -> transformer.apply(array[i - 1]));
	}

	/**
	 * Create an {@link A_Set}, then iterate over the {@link A_Tuple}, invoking
	 * the transformer to produce elements to add.  Deduplicate the elements.
	 * Answer the resulting set.
	 *
	 * @param tuple
	 *        A tuple containing values to be transformed.
	 * @param transformer
	 *        A {@link Function} to transform tuple elements to
	 *        {@link A_BasicObject}s.
	 * @return The new set.
	 */
	public static A_Set generateSetFrom (
		final A_Tuple tuple,
		final Function<? super AvailObject, ? extends A_BasicObject>
			transformer)
	{
		return generateSetFrom(
			tuple.tupleSize(), tuple.iterator(), transformer);
	}
}

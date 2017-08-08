/**
 * ObjectDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ObjectDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ObjectDescriptor.ObjectSlots.*;
import java.util.*;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Strings;
import com.avail.utility.json.JSONWriter;

/**
 * Avail {@linkplain ObjectTypeDescriptor user-defined object types} are novel.
 * They consist of a {@linkplain MapDescriptor map} of keys (field name
 * {@linkplain AtomDescriptor atoms}) and their associated field {@linkplain
 * TypeDescriptor types}. Similarly, user-defined objects consist of a map from
 * field names to field values. An object instance conforms to an object type
 * if and only the instance's field keys are a superset of the type's field
 * keys, and for each field key in common the field value is an instance of the
 * field type.
 *
 * <p>
 * That suggests a simple strategy for representing user-defined objects: Wrap
 * a map. That's what we've done here.  It's not the only strategy, since there
 * are plenty of ways of accomplishing the same semantics. But it's good enough
 * for now.
 * </p>
 *
 * <p>Once we start implementing receiver-type-specific code splitting we'll
 * need to introduce a multiple-dispatch mechanism to deal with multimethods.
 * At that point we'll probably introduce a new representation where objects
 * contain a map from field names to slot numbers, plus a tuple holding those
 * slots (or just a variable number of slots in the object itself). Then two
 * objects with the same layout can use the same type-specific optimized code to
 * access the object's fields. Conversely, objects with different field layouts
 * would be considered incompatible for the purpose of sharing optimized code,
 * even if the objects themselves were equal. Technically, this would be
 * receiver-layout-specific optimization, but since there isn't a single
 * receiver it would have to depend on the combined layouts of any user-defined
 * objects for which the optimized code needs fast access to state variables.
 * </p>
 *
 * @see ObjectTypeDescriptor
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class ObjectDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the {@link #HASH_OR_ZERO}.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A bit field to hold the cached hash value of an object.  If zero,
		 * then the hash value must be computed upon request.  Note that in the
		 * very rare case that the hash value actually equals zero, the hash
		 * value has to be computed every time it is requested.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain ObjectTypeDescriptor kind} of the {@linkplain
		 * ObjectDescriptor object}.
		 */
		KIND,

		/**
		 * The values associated with keys for this object.  The assignment of
		 * object fields to these slots is determined by the descriptor's {@link
		 * ObjectDescriptor#variant}.
		 */
		FIELD_VALUES_;
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == HASH_AND_MORE
			|| e == KIND;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsObject(object);
	}

	@Override @AvailMethod
	boolean o_EqualsObject (
		final AvailObject object,
		final AvailObject anObject)
	{
		if (object.sameAddressAs(anObject))
		{
			return true;
		}
		final ObjectDescriptor otherDescriptor =
			(ObjectDescriptor) anObject.descriptor;
		if (variant != otherDescriptor.variant)
		{
			return false;
		}
		// If one of the hashes is already computed, compute the other if
		// necessary, then compare the hashes to eliminate the vast majority of
		// the unequal cases.
		int myHash = object.slot(HASH_OR_ZERO);
		int otherHash = anObject.slot(HASH_OR_ZERO);
		if (myHash != 0 || otherHash != 0)
		{
			if (myHash == 0)
			{
				myHash = object.hash();
			}
			if (otherHash == 0)
			{
				otherHash = anObject.hash();
			}
			if (myHash != otherHash)
			{
				return false;
			}
		}
		// Hashes are equal.  Compare fields, which must be in corresponding
		// positions because we share the same variant.
		for (
			int i = 1, limit = object.variableObjectSlotsCount();
			i <= limit;
			i++)
		{
			if (!object.slot(FIELD_VALUES_, i).equals(
				anObject.slot(FIELD_VALUES_, i)))
			{
				return false;
			}
		}
		if (!isShared() && object.slot(KIND).equalsNil())
		{
			object.becomeIndirectionTo(anObject);
		}
		else if (!otherDescriptor.isShared())
		{
			anObject.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	AvailObject o_FieldAt (final AvailObject object, final A_Atom field)
	{
		// Fails with NullPointerException if key is not found.
		final int slotIndex = variant.fieldToSlotIndex.get(field);
		if (slotIndex == 0)
		{
			return (AvailObject) field;
		}
		return object.slot(FIELD_VALUES_, slotIndex);
	}

	@Override @AvailMethod
	A_BasicObject o_FieldAtPuttingCanDestroy (
		final AvailObject object,
		final A_Atom field,
		final A_BasicObject value,
		final boolean canDestroy)
	{
		if (!canDestroy && isMutable())
		{
			object.makeImmutable();
		}
		final Map<A_Atom, Integer> fieldToSlotIndex = variant.fieldToSlotIndex;
		final Integer slotIndex = fieldToSlotIndex.get(field);
		if (slotIndex != null)
		{
			if (slotIndex == 0)
			{
				assert value.equals(field);
				return object;
			}
			// Replace an existing real field.
			final AvailObject result =  canDestroy && isMutable()
				? object
				: newLike(variant.mutableObjectDescriptor, object, 0, 0);
			result.setSlot(FIELD_VALUES_, slotIndex, value);
			result.setSlot(KIND, NilDescriptor.nil());
			result.setSlot(HASH_OR_ZERO, 0);
			return result;
		}
		// Make room for another slot and find/create the variant.
		final A_Set newFieldsSet =
			variant.allFields.setWithElementCanDestroy(field, false);
		final ObjectLayoutVariant newVariant =
			ObjectLayoutVariant.variantForFields(newFieldsSet);
		final Map<A_Atom, Integer> newVariantSlotMap =
			newVariant.fieldToSlotIndex;
		final AvailObject result =
			newVariant.mutableObjectDescriptor.create(newVariant.realSlotCount);
		for (final Map.Entry<A_Atom, Integer> oldEntry
			: fieldToSlotIndex.entrySet())
		{
			result.setSlot(
				FIELD_VALUES_,
				newVariantSlotMap.get(oldEntry.getKey()),
				object.slot(FIELD_VALUES_, oldEntry.getValue()));
		}
		final int newVariantSlotIndex = newVariantSlotMap.get(field);
		if (newVariantSlotIndex != 0)
		{
			result.setSlot(FIELD_VALUES_, newVariantSlotIndex, value);
		}
		result.setSlot(KIND, NilDescriptor.nil());
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod
	A_Map o_FieldMap (final AvailObject object)
	{
		// Warning: May be much slower than it was before ObjectLayoutVariant.
		A_Map fieldMap = MapDescriptor.empty();
		for (final Map.Entry<A_Atom, Integer> entry
			: variant.fieldToSlotIndex.entrySet())
		{
			final A_Atom field = entry.getKey();
			final int slotIndex = entry.getValue();
			fieldMap = fieldMap.mapAtPuttingCanDestroy(
				field,
				slotIndex == 0
					? field
					: object.slot(FIELD_VALUES_, slotIndex),
				true);
		}
		return fieldMap;
	}

	@Override @AvailMethod
	A_Tuple o_FieldTuple (final AvailObject object)
	{
		final Iterator<Map.Entry<A_Atom, Integer>> fieldIterator =
			variant.fieldToSlotIndex.entrySet().iterator();
		final A_Tuple resultTuple = ObjectTupleDescriptor.generateFrom(
			variant.fieldToSlotIndex.size(),
			() ->
			{
				final Map.Entry<A_Atom, Integer> entry =
					fieldIterator.next();
				final A_Atom field = entry.getKey();
				final int slotIndex = entry.getValue();
				return TupleDescriptor.from(
					field,
					slotIndex == 0
						? field
						: object.slot(FIELD_VALUES_, slotIndex));
			});
		assert !fieldIterator.hasNext();
		return resultTuple;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			// Don't lock if we're shared.  Multiple simultaneous computations
			// of *the same* value are benign races.
			hash = variant.variantId;
			for (
				int i = 1, limit = object.variableObjectSlotsCount();
				i <= limit;
				i++)
			{
				hash *= multiplier;
				hash ^= object.slot(FIELD_VALUES_, i).hash();
			}
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aTypeObject)
	{
		return aTypeObject.isSupertypeOfPrimitiveTypeEnum(NONTYPE)
			|| aTypeObject.hasObjectInstance(object);
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		AvailObject kind = object.slot(KIND);
		if (kind.equalsNil())
		{
			object.makeImmutable();
			kind = variant.mutableObjectTypeDescriptor.createFromObject(object);
			if (isShared())
			{
				// Don't lock, since multiple threads would compute equal values
				// anyhow.  Make the object shared since it's being written to
				// a mutable slot of a shared object.
				kind = kind.traversed().makeShared();
			}
			object.setSlot(KIND, kind);
		}
		return kind;
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.OBJECT;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("object");
		writer.write("map");
		writer.startObject();
		for (final Map.Entry<A_Atom, Integer> entry
			: variant.fieldToSlotIndex.entrySet())
		{
			final A_Atom field = entry.getKey();
			final int slotIndex = entry.getValue();
			final A_BasicObject value = slotIndex == 0
				? field
				: object.slot(FIELD_VALUES_, slotIndex);
			field.atomName().writeTo(writer);
			value.writeTo(writer);
		}
		writer.endObject();
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("object");
		writer.write("map");
		writer.startObject();
		for (final Map.Entry<A_Atom, Integer> entry
			: variant.fieldToSlotIndex.entrySet())
		{
			final A_Atom field = entry.getKey();
			final int slotIndex = entry.getValue();
			final A_BasicObject value = slotIndex == 0
				? field
				: object.slot(FIELD_VALUES_, slotIndex);
			field.atomName().writeTo(writer);
			value.writeSummaryTo(writer);
		}
		writer.endObject();
		writer.endObject();
	}

	/**
	 * Extract the field value at the specified slot index.
	 *
	 * @param object An object.
	 * @param slotIndex The non-zero slot index.
	 * @return The value of the field at the specified slot index.
	 */
	public static AvailObject getField (
		final AvailObject object,
		final int slotIndex)
	{
		return object.slot(FIELD_VALUES_, slotIndex);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final A_Tuple pair =
			ObjectTypeDescriptor.namesAndBaseTypesForType(object.kind());
		final A_Set names = pair.tupleAt(1);
		final A_Set baseTypes = pair.tupleAt(2);
		builder.append("a/an ");
		final List<String> sortedNames = new ArrayList<>(names.setSize());
		for (final A_String name : names)
		{
			sortedNames.add(name.asNativeString());
		}
		Collections.sort(sortedNames);
		boolean first = true;
		for (final String name : sortedNames)
		{
			if (!first)
			{
				builder.append(" ∩ ");
			}
			else
			{
				first = false;
			}
			builder.append(name);
		}
		if (first)
		{
			builder.append("object");
		}
		final A_Atom explicitSubclassingKey = EXPLICIT_SUBCLASSING_KEY.atom;
		A_Set ignoreKeys = SetDescriptor.empty();
		for (final A_Type baseType : baseTypes)
		{
			final A_Map fieldTypes = baseType.fieldTypeMap();
			for (final Entry entry : fieldTypes.mapIterable())
			{
				if (!entry.key().getAtomProperty(explicitSubclassingKey)
					.equalsNil())
				{
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(
						entry.key(), true);
				}
			}
		}
		first = true;
		for (final Entry entry : object.fieldMap().mapIterable())
		{
			if (!ignoreKeys.hasElement(entry.key()))
			{
				if (first)
				{
					builder.append(" with:");
					first = false;
				}
				else
				{
					builder.append(",");
				}
				Strings.newlineTab(builder, indent);
				builder.append(entry.key().atomName().asNativeString());
				builder.append(" = ");
				entry.value().printOnAvoidingIndent(
					builder, recursionMap, indent + 1);
			}
		}
	}

	/**
	 * Construct an {@linkplain ObjectDescriptor object} with attribute
	 * {@linkplain AtomDescriptor keys} and values taken from the provided
	 * {@linkplain MapDescriptor map}.
	 *
	 * @param map A map from keys to their corresponding values.
	 * @return The new object.
	 */
	public static AvailObject objectFromMap (final A_Map map)
	{
		final ObjectLayoutVariant variant =
			ObjectLayoutVariant.variantForFields(map.keysAsSet());
		final ObjectDescriptor mutableDescriptor =
			variant.mutableObjectDescriptor;
		final Map<A_Atom, Integer> slotMap = variant.fieldToSlotIndex;
		final AvailObject result =
			mutableDescriptor.create(variant.realSlotCount);
		for (final Entry entry : map.mapIterable())
		{
			final int slotIndex = slotMap.get(entry.key());
			if (slotIndex > 0)
			{
				result.setSlot(FIELD_VALUES_, slotIndex, entry.value());
			}
		}
		result.setSlot(KIND, NilDescriptor.nil());
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	/**
	 * Construct an object from the specified {@linkplain TupleDescriptor
	 * tuple} of field assignments.
	 *
	 * @param tuple
	 *        A tuple of 2-tuples whose first element is an {@linkplain
	 *        AtomDescriptor atom} and whose second element is an arbitrary
	 *        value.
	 * @return The new object.
	 */
	public static AvailObject objectFromTuple (final A_Tuple tuple)
	{
		A_Map map = MapDescriptor.empty();
		for (final A_Tuple fieldAssignment : tuple)
		{
			final A_Atom fieldAtom = fieldAssignment.tupleAt(1);
			final A_BasicObject fieldValue = fieldAssignment.tupleAt(2);
			map = map.mapAtPuttingCanDestroy(fieldAtom, fieldValue, true);
		}
		return objectFromMap(map);
	}

	/** This descriptor's {@link ObjectLayoutVariant}. */
	public final ObjectLayoutVariant variant;

	/**
	 * Construct a new {@link ObjectDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	ObjectDescriptor (
		final Mutability mutability,
		final ObjectLayoutVariant variant)
	{
		super(
			mutability,
			TypeTag.OBJECT_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
		this.variant = variant;
	}

	@Deprecated @Override
	ObjectDescriptor mutable ()
	{
		return variant.mutableObjectDescriptor;
	}

	@Deprecated @Override
	ObjectDescriptor immutable ()
	{
		return variant.immutableObjectDescriptor;
	}

	@Deprecated @Override
	ObjectDescriptor shared ()
	{
		return variant.sharedObjectDescriptor;
	}
}

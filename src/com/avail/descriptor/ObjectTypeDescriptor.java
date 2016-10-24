/**
 * ObjectTypeDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ObjectTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ObjectTypeDescriptor.ObjectSlots.*;
import java.util.*;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Generator;
import com.avail.utility.Strings;
import com.avail.utility.json.JSONWriter;

/**
 * {@code ObjectTypeDescriptor} represents an Avail object type. An object type
 * associates {@linkplain AtomDescriptor fields} with {@linkplain TypeDescriptor
 * types}. An object type's instances have at least the same fields and field
 * values that are instances of the appropriate types.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ObjectTypeDescriptor
extends TypeDescriptor
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
		 * A bit field to hold the cached hash value of an object type.  If
		 * zero, the hash value must be computed upon request.  Note that in the
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
		 * The types associated with keys for this object.  The assignment of
		 * object fields to these slots is determined by the descriptor's {@link
		 * ObjectTypeDescriptor#variant}.
		 */
		FIELD_TYPES_
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == HASH_AND_MORE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final A_Map myFieldTypeMap = object.fieldTypeMap();
		final A_Tuple pair = namesAndBaseTypesForType(object);
		final A_Set names = pair.tupleAt(1);
		final A_Set baseTypes = pair.tupleAt(2);
		boolean first = true;
		for (final A_String name : names)
		{
			if (!first)
			{
				builder.append(" ∩ ");
			}
			else
			{
				first = false;
			}
			builder.append(name.asNativeString());
		}
		if (first)
		{
			builder.append("object");
		}
		A_Set ignoreKeys = SetDescriptor.empty();
		for (final A_Type baseType : baseTypes)
		{
			final A_Map fieldTypes = baseType.fieldTypeMap();
			for (final MapDescriptor.Entry entry : fieldTypes.mapIterable())
			{
				final A_Atom atom = entry.key();
				final A_Type type = entry.value();
				if (type.isEnumeration()
					&& type.instanceCount().equalsInt(1)
					&& type.instance().equals(atom))
				{
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(
						atom, true);
				}
				else if (myFieldTypeMap.mapAt(atom).equals(type))
				{
					// Also eliminate keys whose field type is no stronger than
					// it is in the named object type(s).
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(
						atom, true);
				}
			}
		}
		first = true;
		for (final MapDescriptor.Entry entry
			: object.fieldTypeMap().mapIterable())
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
				builder.append(" : ");
				entry.value().printOnAvoidingIndent(
					builder,
					recursionMap,
					indent + 1);
			}
		}
	}

	@Override
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsObjectType(object);
	}

	@Override
	boolean o_EqualsObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		if (object.sameAddressAs(anObjectType))
		{
			return true;
		}
		final ObjectTypeDescriptor otherDescriptor =
			(ObjectTypeDescriptor) anObjectType.descriptor;
		if (variant != otherDescriptor.variant)
		{
			return false;
		}
		// If one of the hashes is already computed, compute the other if
		// necessary, then compare the hashes to eliminate the vast majority of
		// the unequal cases.
		int myHash = object.slot(HASH_OR_ZERO);
		int otherHash = anObjectType.slot(HASH_OR_ZERO);
		if (myHash != 0 || otherHash != 0)
		{
			if (myHash == 0)
			{
				myHash = object.hash();
			}
			if (otherHash == 0)
			{
				otherHash = anObjectType.hash();
			}
			if (myHash != otherHash)
			{
				return false;
			}
		}
		// Hashes are equal.  Compare field types, which must be in
		// corresponding positions because we share the same variant.
		for (
			int i = 1, limit = object.variableObjectSlotsCount();
			i <= limit;
			i++)
		{
			if (!object.slot(FIELD_TYPES_, i).equals(
				anObjectType.slot(FIELD_TYPES_, i)))
			{
				return false;
			}
		}
		if (!isShared())
		{
			object.becomeIndirectionTo(anObjectType);
		}
		else if (!otherDescriptor.isShared())
		{
			anObjectType.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	A_Map o_FieldTypeMap (final AvailObject object)
	{
		// Warning: May be much slower than it was before ObjectLayoutVariant.
		A_Map fieldTypeMap = MapDescriptor.empty();
		for (Map.Entry<A_Atom, Integer> entry
			: variant.fieldToSlotIndex.entrySet())
		{
			final A_Atom field = entry.getKey();
			final int slotIndex = entry.getValue();
			fieldTypeMap = fieldTypeMap.mapAtPuttingCanDestroy(
				field,
				slotIndex == 0
					? InstanceTypeDescriptor.on(field)
					: object.slot(FIELD_TYPES_, slotIndex),
				true);
		}
		return fieldTypeMap;
	}

	@Override @AvailMethod
	A_Tuple o_FieldTypeTuple (final AvailObject object)
	{
		final Iterator<Map.Entry<A_Atom, Integer>> fieldIterator =
			variant.fieldToSlotIndex.entrySet().iterator();
		final A_Tuple resultTuple = ObjectTupleDescriptor.generateFrom(
			variant.fieldToSlotIndex.size(),
			new Generator<A_BasicObject>()
			{
				@Override
				public A_BasicObject value ()
				{
					final Map.Entry<A_Atom, Integer> entry =
						fieldIterator.next();
					final A_Atom field = entry.getKey();
					final int slotIndex = entry.getValue();
					return TupleDescriptor.from(
						field,
						slotIndex == 0
							? InstanceTypeDescriptor.on(field)
							: object.slot(FIELD_TYPES_, slotIndex));
				}
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
			hash = variant.randomInt ^ 0xE3561F16;
			for (
				int i = 1, limit = object.variableObjectSlotsCount();
				i <= limit;
				i++)
			{
				hash *= multiplier;
				hash -= object.slot(FIELD_TYPES_, i).hash();
			}
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		final ObjectDescriptor instanceDescriptor =
			(ObjectDescriptor) potentialInstance.descriptor;
		final ObjectLayoutVariant instanceVariant =
			instanceDescriptor.variant;
		if (instanceVariant == variant)
		{
			// The instance and I share a variant, so blast through the fields
			// in lock-step doing instance checks.
			for (
				int i = 1, limit = variant.realSlotCount;
				i <= limit;
				i++)
			{
				final AvailObject instanceFieldValue =
					ObjectDescriptor.getField(potentialInstance, i);
				final A_Type fieldType = object.slot(FIELD_TYPES_, i);
				if (!instanceFieldValue.isInstanceOf(fieldType))
				{
					return false;
				}
			}
			return true;
		}
		// The variants disagree.  For each field type in this object type,
		// check that there is a corresponding field value in the object, and
		// that its type conforms.  For field types that are only for explicit
		// subclassing, just make sure the same field is present in the object.
		final Map<A_Atom, Integer> instanceVariantSlotMap =
			instanceVariant.fieldToSlotIndex;
		for (final Map.Entry<A_Atom, Integer> entry
			: variant.fieldToSlotIndex.entrySet())
		{
			final A_Atom field = entry.getKey();
			final int slotIndex = entry.getValue();
			if (slotIndex == 0)
			{
				if (!instanceVariantSlotMap.containsKey(field))
				{
					return false;
				}
			}
			else
			{
				final Integer instanceSlotIndex =
					instanceVariantSlotMap.get(field);
				if (instanceSlotIndex == null)
				{
					// The instance didn't have a field that the type requires.
					return false;
				}
				// The object and object type should agree about whether the
				// field is for explicit subclassing.
				assert instanceSlotIndex != 0;
				final A_BasicObject fieldValue = ObjectDescriptor.getField(
					potentialInstance, instanceSlotIndex);
				A_Type fieldType = object.slot(FIELD_TYPES_, slotIndex);
				if (!fieldValue.isInstanceOf(fieldType))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.isSupertypeOfObjectType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		if (object.sameAddressAs(anObjectType))
		{
			return true;
		}
		final ObjectTypeDescriptor subtypeDescriptor =
			(ObjectTypeDescriptor) anObjectType.descriptor;
		final ObjectLayoutVariant subtypeVariant =
			subtypeDescriptor.variant;
		if (subtypeVariant == variant)
		{
			// The potential subtype and I share a variant, so blast through the
			// fields in lock-step doing subtype checks.
			for (
				int i = 1, limit = variant.realSlotCount;
				i <= limit;
				i++)
			{
				final AvailObject subtypeFieldType =
					anObjectType.slot(FIELD_TYPES_, i);
				final AvailObject myFieldType = object.slot(FIELD_TYPES_, i);
				if (!subtypeFieldType.isSubtypeOf(myFieldType))
				{
					return false;
				}
			}
			return true;
		}
		// The variants disagree.  Do some quick field count checks first.  Note
		// that since variants are canonized by the set of fields, we can safely
		// assume that the subtype has *strictly* more fields than the
		// supertype... but also note that the number of real slots can still
		// be equal while satisfying the not-same-variant but is-subtype.
		if (subtypeVariant.realSlotCount < variant.realSlotCount
			|| subtypeVariant.fieldToSlotIndex.size() <=
				variant.fieldToSlotIndex.size())
		{
			return false;
		}
		// For each of my fields, check that the field is present in the
		// potential subtype, and that its type is a subtype of my field's type.
		final Map<A_Atom, Integer> subtypeVariantSlotMap =
			subtypeVariant.fieldToSlotIndex;
		for (final Map.Entry<A_Atom, Integer> entry
			: variant.fieldToSlotIndex.entrySet())
		{
			final A_Atom field = entry.getKey();
			final int supertypeSlotIndex = entry.getValue();
			if (supertypeSlotIndex == 0)
			{
				if (!subtypeVariantSlotMap.containsKey(field))
				{
					return false;
				}
			}
			else
			{
				final Integer subtypeSlotIndex =
					subtypeVariantSlotMap.get(field);
				if (subtypeSlotIndex == null)
				{
					// The potential subtype didn't have a necessary field.
					return false;
				}
				// The types should agree about whether the field is for
				// explicit subclassing.
				assert subtypeSlotIndex != 0;
				final A_Type subtypeFieldType = anObjectType.slot(
					FIELD_TYPES_, subtypeSlotIndex);
				final A_Type supertypeFieldType = object.slot(
					FIELD_TYPES_, supertypeSlotIndex);
				if (!subtypeFieldType.isSubtypeOf(supertypeFieldType))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfObjectType(object);
	}

	/**
	 * Answer the most general type that is still at least as specific as these.
	 * Here we're finding the nearest common descendant of two object types.
	 */
	@Override @AvailMethod
	A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		final ObjectTypeDescriptor otherDescriptor =
			(ObjectTypeDescriptor) anObjectType.descriptor();
		final ObjectLayoutVariant otherVariant =
			otherDescriptor.variant;
		if (otherVariant == variant)
		{
			// Field slot indices agree, so blast through the slots in order.
			final AvailObject intersection =
				variant.mutableObjectTypeDescriptor.create(
					variant.realSlotCount);
			for (int i = 1, limit = variant.realSlotCount; i <= limit; i++)
			{
				final A_Type fieldIntersaction =
					object.slot(FIELD_TYPES_, i).typeIntersection(
						anObjectType.slot(FIELD_TYPES_, i));
				if (fieldIntersaction.isBottom())
				{
					// Abandon the partially built object type.
					return BottomTypeDescriptor.bottom();
				}
				intersection.setSlot(FIELD_TYPES_, i, fieldIntersaction);
			}
			intersection.setSlot(HASH_OR_ZERO, 0);
			return intersection;
		}
		// The variants disagree, so do it the hard(er) way.
		final A_Set mergedFields = variant.allFields.setUnionCanDestroy(
			otherVariant.allFields, false);
		final ObjectLayoutVariant resultVariant =
			ObjectLayoutVariant.variantForFields(mergedFields);
		final Map<A_Atom, Integer> mySlotMap = variant.fieldToSlotIndex;
		final Map<A_Atom, Integer> otherSlotMap = otherVariant.fieldToSlotIndex;
		final Map<A_Atom, Integer> resultSlotMap =
			resultVariant.fieldToSlotIndex;
		final AvailObject result =
			resultVariant.mutableObjectTypeDescriptor.create(
				resultVariant.realSlotCount);
		for (final Map.Entry<A_Atom, Integer> resultEntry
			: resultSlotMap.entrySet())
		{
			final int resultSlotIndex = resultEntry.getValue();
			if (resultSlotIndex > 0)
			{
				final A_Atom field = resultEntry.getKey();
				final Integer mySlotIndex = mySlotMap.get(field);
				final Integer otherSlotIndex = otherSlotMap.get(field);
				final A_Type fieldType;
				if (mySlotIndex == null)
				{
					fieldType = anObjectType.slot(FIELD_TYPES_, otherSlotIndex);
				}
				else if (otherSlotIndex == null)
				{
					fieldType = object.slot(FIELD_TYPES_, mySlotIndex);
				}
				else
				{
					fieldType = object.slot(FIELD_TYPES_, mySlotIndex)
						.typeIntersection(
							anObjectType.slot(FIELD_TYPES_, otherSlotIndex));
					if (fieldType.isBottom())
					{
						return BottomTypeDescriptor.bottom();
					}
				}
				result.setSlot(FIELD_TYPES_, resultEntry.getValue(), fieldType);
			}
		}
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfObjectType(object);
	}

	/**
	 * Answer the most specific type that is still at least as general as these.
	 * Here we're finding the nearest common ancestor of two eager object types.
	 */
	@Override @AvailMethod
	A_Type o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		final ObjectTypeDescriptor otherDescriptor =
			(ObjectTypeDescriptor) anObjectType.descriptor();
		final ObjectLayoutVariant otherVariant =
			otherDescriptor.variant;
		if (otherVariant == variant)
		{
			// Field slot indices agree, so blast through the slots in order.
			final AvailObject union =
				variant.mutableObjectTypeDescriptor.create(
					variant.realSlotCount);
			for (int i = 1, limit = variant.realSlotCount; i <= limit; i++)
			{
				final A_Type fieldIntersaction =
					object.slot(FIELD_TYPES_, i).typeUnion(
						anObjectType.slot(FIELD_TYPES_, i));
				union.setSlot(FIELD_TYPES_, i, fieldIntersaction);
			}
			union.setSlot(HASH_OR_ZERO, 0);
			return union;
		}
		// The variants disagree, so do it the hard(er) way.
		final A_Set narrowedFields =
			variant.allFields.setIntersectionCanDestroy(
				otherVariant.allFields, false);
		final ObjectLayoutVariant resultVariant =
			ObjectLayoutVariant.variantForFields(narrowedFields);
		final Map<A_Atom, Integer> mySlotMap = variant.fieldToSlotIndex;
		final Map<A_Atom, Integer> otherSlotMap = otherVariant.fieldToSlotIndex;
		final Map<A_Atom, Integer> resultSlotMap =
			resultVariant.fieldToSlotIndex;
		final AvailObject result =
			resultVariant.mutableObjectTypeDescriptor.create(
				resultVariant.realSlotCount);
		for (final Map.Entry<A_Atom, Integer> resultEntry
			: resultSlotMap.entrySet())
		{
			final int resultSlotIndex = resultEntry.getValue();
			if (resultSlotIndex > 0)
			{
				final A_Atom field = resultEntry.getKey();
				final int mySlotIndex = mySlotMap.get(field);
				final int otherSlotIndex = otherSlotMap.get(field);
				final A_Type fieldType = object.slot(FIELD_TYPES_, mySlotIndex)
					.typeUnion(anObjectType.slot(FIELD_TYPES_, otherSlotIndex));
				result.setSlot(FIELD_TYPES_, resultEntry.getValue(), fieldType);
			}
		}
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.OBJECT_TYPE;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// There isn't an immutable descriptor; make it shared.
			return object.makeShared();
		}
		return object;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("object type");
		for (final MapDescriptor.Entry entry :
			object.fieldTypeMap().mapIterable())
		{
			entry.key().atomName().writeTo(writer);
			entry.value().writeTo(writer);
		}
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("object type");
		for (final MapDescriptor.Entry entry :
			object.fieldTypeMap().mapIterable())
		{
			entry.key().atomName().writeTo(writer);
			entry.value().writeSummaryTo(writer);
		}
		writer.endObject();
	}

	/**
	 * Create an {@linkplain ObjectTypeDescriptor object type} using the given
	 * {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
	 * atoms} to {@linkplain TypeDescriptor types}.
	 *
	 * @param map The map from atoms to types.
	 * @return The new {@linkplain ObjectTypeDescriptor object type}.
	 */
	public static AvailObject objectTypeFromMap (final A_Map map)
	{
		final ObjectLayoutVariant variant =
			ObjectLayoutVariant.variantForFields(map.keysAsSet());
		final ObjectTypeDescriptor mutableDescriptor =
			variant.mutableObjectTypeDescriptor;
		final Map<A_Atom, Integer> slotMap = variant.fieldToSlotIndex;
		final AvailObject result =
			mutableDescriptor.create(variant.realSlotCount);
		for (MapDescriptor.Entry entry : map.mapIterable())
		{
			final int slotIndex = slotMap.get(entry.key());
			if (slotIndex > 0)
			{
				result.setSlot(FIELD_TYPES_, slotIndex, entry.value());
			}
		}
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	/**
	 * Create an {@linkplain ObjectTypeDescriptor object type} from the
	 * specified {@linkplain TupleDescriptor tuple}.
	 *
	 * @param tuple
	 *        A tuple whose elements are 2-tuples whose first element is an
	 *        {@linkplain AtomDescriptor atom} and whose second element is a
	 *        {@linkplain TypeDescriptor type}.
	 * @return The new object type.
	 */
	public static AvailObject objectTypeFromTuple (final A_Tuple tuple)
	{
		A_Map map = MapDescriptor.empty();
		for (final A_Tuple fieldTypeAssignment : tuple)
		{
			final A_Atom fieldAtom = fieldTypeAssignment.tupleAt(1);
			final A_BasicObject fieldType = fieldTypeAssignment.tupleAt(2);
			map = map.mapAtPuttingCanDestroy(fieldAtom, fieldType, true);
		}
		return objectTypeFromMap(map);
	}

	/**
	 * Given an {@link ObjectDescriptor object} whose variant is this mutable
	 * object type descriptor's variant, create an object type whose fields are
	 * populated with instance types based on the object's fields.
	 *
	 * @param object An object.
	 * @return An object type.
	 */
	AvailObject createFromObject (AvailObject object)
	{
		final AvailObject result = create(variant.realSlotCount);
		for (int i = 1, limit = variant.realSlotCount; i <= limit; i++)
		{
			final A_BasicObject fieldValue =
				ObjectDescriptor.getField(object, i);
			final A_Type fieldType =
				AbstractEnumerationTypeDescriptor.withInstance(fieldValue);
			result.setSlot(FIELD_TYPES_, i, fieldType);
		}
		result.setSlot(HASH_OR_ZERO, 0);
		return result;
	}

	/**
	 * Assign a name to the specified {@linkplain ObjectTypeDescriptor
	 * user-defined object type}.  If the only field key {@linkplain
	 * AtomDescriptor atoms} in the object type are {@linkplain
	 * AtomDescriptor#o_IsAtomSpecial(AvailObject) special atoms}, then the
	 * name will not be recorded (unless allowSpecialAtomsToHoldName is true,
	 * which is really only for naming special object types like {@link
	 * #exceptionType}).  Note that it is technically <em>legal</em> for there
	 * to be multiple names for a particular object type, although this is of
	 * questionable value.
	 *
	 * @param anObjectType
	 *        A {@linkplain ObjectTypeDescriptor user-defined object type}.
	 * @param aString
	 *        A name.
	 * @param allowSpecialAtomsToHoldName
	 *        Whether to allow the object type name to be attached to a special
	 *        atom.
	 */
	public static void setNameForType (
		final A_Type anObjectType,
		final A_String aString,
		final boolean allowSpecialAtomsToHoldName)
	{
		assert aString.isString();
		final A_Atom propertyKey = AtomDescriptor.objectTypeNamePropertyKey();
		synchronized (propertyKey)
		{
			int leastNames = Integer.MAX_VALUE;
			A_Atom keyAtomWithLeastNames = null;
			A_Map keyAtomNamesMap = null;
			for (final MapDescriptor.Entry entry
				: anObjectType.fieldTypeMap().mapIterable())
			{
				final A_Atom atom = entry.key();
				if (allowSpecialAtomsToHoldName || !atom.isAtomSpecial())
				{
					final A_Map namesMap = atom.getAtomProperty(propertyKey);
					if (namesMap.equalsNil())
					{
						keyAtomWithLeastNames = atom;
						keyAtomNamesMap = MapDescriptor.empty();
						leastNames = 0;
						break;
					}
					final int mapSize = namesMap.mapSize();
					if (mapSize < leastNames)
					{
						keyAtomWithLeastNames = atom;
						keyAtomNamesMap = namesMap;
						leastNames = mapSize;
					}
				}
			}
			if (keyAtomWithLeastNames != null)
			{
				assert keyAtomNamesMap != null;
				A_Set namesSet = keyAtomNamesMap.hasKey(anObjectType)
					? keyAtomNamesMap.mapAt(anObjectType)
					: SetDescriptor.empty();
				namesSet = namesSet.setWithElementCanDestroy(aString, false);
				keyAtomNamesMap = keyAtomNamesMap.mapAtPuttingCanDestroy(
					anObjectType, namesSet, true);
				keyAtomWithLeastNames.setAtomProperty(
					propertyKey, keyAtomNamesMap);
			}
		}
	}
	/**
	 * Remove a type name from the specified {@linkplain ObjectTypeDescriptor
	 * user-defined object type}.  If the object type does not currently have
	 * the specified type name, or if this name has already been removed, do
	 * nothing.
	 *
	 * @param aString
	 *        A name to disassociate from the type.
	 * @param anObjectType
	 *        A {@linkplain ObjectTypeDescriptor user-defined object type}.
	 */
	public static void removeNameFromType (
		final A_String aString,
		final A_Type anObjectType)
	{
		assert aString.isString();
		final A_Atom propertyKey = AtomDescriptor.objectTypeNamePropertyKey();
		synchronized (propertyKey)
		{
			for (final MapDescriptor.Entry entry
				: anObjectType.fieldTypeMap().mapIterable())
			{
				final A_Atom atom = entry.key();
				if (!atom.isAtomSpecial())
				{
					A_Map namesMap = atom.getAtomProperty(propertyKey);
					if (!namesMap.equalsNil() && namesMap.hasKey(anObjectType))
					{
						// In theory the user can give this type multiple names,
						// so only remove the one that we've been told to.
						A_Set namesSet = namesMap.mapAt(anObjectType);
						namesSet = namesSet.setWithoutElementCanDestroy(
							aString, false);
						if (namesSet.setSize() == 0)
						{
							namesMap = namesMap.mapWithoutKeyCanDestroy(
								anObjectType, false);
						}
						else
						{
							namesMap = namesMap.mapAtPuttingCanDestroy(
								anObjectType, namesSet, false);
						}
						atom.setAtomProperty(propertyKey, namesMap);
					}
				}
			}
		}
	}

	/**
	 * Answer information about the user-assigned name of the specified
	 * {@linkplain ObjectTypeDescriptor user-defined object type}.
	 *
	 * @param anObjectType A {@linkplain ObjectTypeDescriptor user-defined
	 *                     object type}.
	 * @return A tuple with two elements:  (1) A set of names of the {@linkplain
	 *         ObjectTypeDescriptor user-defined object type}, excluding names
	 *         for which a strictly more specific named type is known, and (2)
	 *         A set of object types corresponding to those names.
	 */
	public static A_Tuple namesAndBaseTypesForType (
		final A_Type anObjectType)
	{
		final A_Atom propertyKey = AtomDescriptor.objectTypeNamePropertyKey();
		A_Map applicableTypesAndNames = MapDescriptor.empty();
		synchronized (propertyKey)
		{
			for (final MapDescriptor.Entry entry
				: anObjectType.fieldTypeMap().mapIterable())
			{
				final A_Map map = entry.key().getAtomProperty(propertyKey);
				if (!map.equalsNil())
				{
					for (final MapDescriptor.Entry innerEntry :
						map.mapIterable())
					{
						final A_Type namedType = innerEntry.key();
						if (anObjectType.isSubtypeOf(namedType))
						{
							A_Set nameSet = innerEntry.value();
							if (applicableTypesAndNames.hasKey(namedType))
							{
								nameSet = nameSet.setUnionCanDestroy(
									applicableTypesAndNames.mapAt(namedType),
									true);
							}
							applicableTypesAndNames =
								applicableTypesAndNames.mapAtPuttingCanDestroy(
									namedType, nameSet, true);
						}
					}
				}
			}
			applicableTypesAndNames.makeImmutable();
		}
		A_Map filtered = applicableTypesAndNames;
		for (final MapDescriptor.Entry childEntry
			: applicableTypesAndNames.mapIterable())
		{
			final A_Type childType = childEntry.key();
			for (final MapDescriptor.Entry parentEntry
				: applicableTypesAndNames.mapIterable())
			{
				final A_Type parentType = parentEntry.key();
				if (!childType.equals(parentType)
					&& childType.isSubtypeOf(parentType))
				{
					filtered = filtered.mapWithoutKeyCanDestroy(
						parentType, true);
				}
			}
		}
		A_Set names = SetDescriptor.empty();
		A_Set baseTypes = SetDescriptor.empty();
		for (final MapDescriptor.Entry entry : filtered.mapIterable())
		{
			names = names.setUnionCanDestroy(entry.value(), true);
			baseTypes = baseTypes.setWithElementCanDestroy(entry.key(), true);
		}
		return TupleDescriptor.from(names, baseTypes);
	}

	/**
	 * Answer the user-assigned names of the specified {@linkplain
	 * ObjectTypeDescriptor user-defined object type}.
	 *
	 * @param anObjectType A {@linkplain ObjectTypeDescriptor user-defined
	 *                     object type}.
	 * @return A {@linkplain SetDescriptor set} containing the names of the
	 *         {@linkplain ObjectTypeDescriptor user-defined object type},
	 *         excluding names for which a strictly more specific named type is
	 *         known.
	 */
	public static A_Set namesForType (final A_Type anObjectType)
	{
		return namesAndBaseTypesForType(anObjectType).tupleAt(1);
	}

	/**
	 * Answer the set of named base types for the specified {@linkplain
	 * ObjectTypeDescriptor user-defined object type}.
	 *
	 * @param anObjectType A {@linkplain ObjectTypeDescriptor user-defined
	 *                     object type}.
	 * @return A {@linkplain SetDescriptor set} containing the named ancestors
	 *         of the specified {@linkplain ObjectTypeDescriptor user-defined
	 *         object type}, excluding named types for which a strictly more
	 *         specific named type is known.
	 */
	public static A_BasicObject namedBaseTypesForType (
		final A_Type anObjectType)
	{
		return namesAndBaseTypesForType(anObjectType).tupleAt(2);
	}

	/** This descriptor's {@link ObjectLayoutVariant}. */
	public final ObjectLayoutVariant variant;

	/**
	 * Construct a new {@link ObjectDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	ObjectTypeDescriptor (
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
	ObjectTypeDescriptor mutable ()
	{
		return variant.mutableObjectTypeDescriptor;
	}

	@Deprecated @Override
	ObjectTypeDescriptor immutable ()
	{
		return variant.immutableObjectTypeDescriptor;
	}

	@Deprecated @Override
	ObjectTypeDescriptor shared ()
	{
		return variant.sharedObjectTypeDescriptor;
	}

	/**
	 * The most general {@linkplain ObjectTypeDescriptor object type}.
	 */
	private static final A_Type mostGeneralType =
		objectTypeFromMap(MapDescriptor.empty()).makeShared();

	/**
	 * Answer the top (i.e., most general) {@linkplain ObjectTypeDescriptor
	 * object type}.
	 *
	 * @return The object type that makes no constraints on its fields.
	 */
	public static A_Type mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype of all object types.
	 */
	private static final A_Type meta =
		InstanceMetaDescriptor.on(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all object types.  This is just an {@linkplain
	 * InstanceTypeDescriptor instance type} on the {@linkplain
	 * #mostGeneralType() most general type}.
	 *
	 * @return The type of the most general object type.
	 */
	public static A_Type meta ()
	{
		return meta;
	}

	/**
	 * The {@linkplain AtomDescriptor atom} that identifies the {@linkplain
	 * #exceptionType exception type}.
	 */
	private static final A_Atom exceptionAtom =
		AtomDescriptor.createSpecialAtom("explicit-exception");

	static
	{
		exceptionAtom.setAtomProperty(
			AtomDescriptor.explicitSubclassingKey(),
			AtomDescriptor.trueObject());
	}

	/**
	 * Answer the {@linkplain AtomDescriptor atom} that identifies the
	 * {@linkplain #exceptionType() exception type}.
	 *
	 * @return The special exception atom.
	 */
	public static A_Atom exceptionAtom ()
	{
		return exceptionAtom;
	}

	/**
	 * The {@linkplain AtomDescriptor atom} that identifies the {@linkplain
	 * AtomDescriptor stack dump field} of an {@link #exceptionType() exception
	 * type}.
	 */
	private static final A_Atom stackDumpAtom =
		AtomDescriptor.createSpecialAtom("stack dump");

	/**
	 * Answer the {@linkplain AtomDescriptor atom} that identifies the
	 * {@linkplain AtomDescriptor stack dump field} of an {@link
	 * #exceptionType() exception type}.
	 *
	 * @return The special stack dump atom.
	 */
	public static A_Atom stackDumpAtom ()
	{
		return stackDumpAtom;
	}

	/**
	 * The most general exception type.
	 */
	private static final A_Type exceptionType;

	static
	{
		final A_Type type = objectTypeFromTuple(
			TupleDescriptor.from(
				TupleDescriptor.from(
					exceptionAtom,
					InstanceTypeDescriptor.on(exceptionAtom))));
		setNameForType(type, StringDescriptor.from("exception"), true);
		exceptionType = type.makeShared();
	}

	/**
	 * Answer the most general exception type. This is just an {@linkplain
	 * ObjectTypeDescriptor object type} that contains a well-known {@linkplain
	 * #exceptionAtom() atom}.
	 *
	 * @return The most general exception type.
	 */
	public static A_Type exceptionType ()
	{
		return exceptionType;
	}

	/**
	 * The type of the most general exception type.
	 */
	private static final A_Type exceptionMeta =
		InstanceMetaDescriptor.on(exceptionType);

	/**
	 * Answer the most general exception type's type.
	 *
	 * @return The most general exception meta.
	 */
	public static A_Type exceptionMeta ()
	{
		return exceptionMeta;
	}
}

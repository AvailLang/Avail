/**
 * ObjectTypeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.ObjectTypeDescriptor.ObjectSlots.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

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
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain MapTypeDescriptor map} from {@linkplain
		 * AtomDescriptor field names} to their declared {@linkplain
		 * TypeDescriptor types}.
		 */
		FIELD_TYPE_MAP
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
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
			builder.append("Unnamed object type");
		}
		A_Set ignoreKeys = SetDescriptor.empty();
		for (final AvailObject baseType : baseTypes)
		{
			final A_BasicObject fieldTypes = baseType.slot(FIELD_TYPE_MAP);
			for (final MapDescriptor.Entry entry : fieldTypes.mapIterable())
			{
				if (InstanceTypeDescriptor.on(entry.key()).equals(entry.value()))
				{
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(
						entry.key(),
						true);
				}
			}
		}
		first = true;
		for (final MapDescriptor.Entry entry
			: object.slot(FIELD_TYPE_MAP).mapIterable())
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
				builder.append('\n');
				for (int tab = 0; tab < indent; tab++)
				{
					builder.append('\t');
				}
				builder.append(entry.key().name().asNativeString());
				builder.append(" : ");
				entry.value().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		}
	}

	@Override @AvailMethod
	A_Map o_FieldTypeMap (final AvailObject object)
	{
		return object.slot(FIELD_TYPE_MAP);
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
		return object.slot(FIELD_TYPE_MAP).equals(
			anObjectType.slot(FIELD_TYPE_MAP));
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.fieldTypeMap().hash() * 11 ^ 0xE3561F16;
	}

	@Override @AvailMethod
	A_Tuple o_FieldTypeTuple (final AvailObject object)
	{
		final A_Map map = object.slot(FIELD_TYPE_MAP);
		final List<A_Tuple> fieldAssignments = new ArrayList<A_Tuple>(
			map.mapSize());
		for (final MapDescriptor.Entry entry : map.mapIterable())
		{
			fieldAssignments.add(TupleDescriptor.from(entry.key(), entry.value()));
		}
		return TupleDescriptor.fromList(fieldAssignments);
	}

	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		final A_Map typeMap = object.slot(FIELD_TYPE_MAP);
		final A_Map instMap = potentialInstance.fieldMap();
		if (instMap.mapSize() < typeMap.mapSize())
		{
			return false;
		}
		for (final MapDescriptor.Entry entry : typeMap.mapIterable())
		{
			final AvailObject fieldKey = entry.key();
			final AvailObject fieldType = entry.value();
			if (!instMap.hasKey(fieldKey))
			{
				return false;
			}
			if (!instMap.mapAt(fieldKey).isInstanceOf(fieldType))
			{
				return false;
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
		final A_BasicObject anObjectType)
	{
		final A_Map m1 = object.slot(FIELD_TYPE_MAP);
		final A_Map m2 = anObjectType.fieldTypeMap();
		if (m1.mapSize() > m2.mapSize())
		{
			return false;
		}
		for (final MapDescriptor.Entry entry : m1.mapIterable())
		{
			final AvailObject fieldKey = entry.key();
			final AvailObject fieldType = entry.value();
			if (!m2.hasKey(fieldKey))
			{
				return false;
			}
			if (!m2.mapAt(fieldKey).isSubtypeOf(fieldType))
			{
				return false;
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
	 * Here we're finding the nearest common descendant of two eager object
	 * types.
	 */
	@Override @AvailMethod
	A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final A_Type anObjectType)
	{
		final A_Map map1 = object.slot(FIELD_TYPE_MAP);
		final A_Map map2 = anObjectType.slot(FIELD_TYPE_MAP);
		A_Map resultMap = MapDescriptor.empty();
		for (final MapDescriptor.Entry entry : map1.mapIterable())
		{
			final A_Atom key = entry.key();
			A_Type type = entry.value();
			if (map2.hasKey(key))
			{
				type = type.typeIntersection(map2.mapAt(key));
				if (type.equals(BottomTypeDescriptor.bottom()))
				{
					return BottomTypeDescriptor.bottom();
				}
			}
			resultMap = resultMap.mapAtPuttingCanDestroy(
				key,
				type,
				true);
		}
		for (final MapDescriptor.Entry entry : map2.mapIterable())
		{
			final AvailObject key = entry.key();
			final AvailObject type = entry.value();
			if (!map1.hasKey(key))
			{
				resultMap = resultMap.mapAtPuttingCanDestroy(
					key,
					type,
					true);
			}
		}
		return objectTypeFromMap(resultMap);
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
		final A_Type anObjectType)
	{
		final A_Map map1 = object.slot(FIELD_TYPE_MAP);
		final A_Map map2 = anObjectType.slot(FIELD_TYPE_MAP);
		A_Map resultMap = MapDescriptor.empty();
		for (final MapDescriptor.Entry entry : map1.mapIterable())
		{
			final AvailObject key = entry.key();
			if (map2.hasKey(key))
			{
				final A_Type valueType = entry.value();
				resultMap = resultMap.mapAtPuttingCanDestroy(
					key,
					valueType.typeUnion(map2.mapAt(key)),
					true);
			}
		}
		return objectTypeFromMap(resultMap);
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
		final AvailObject result = mutable.create();
		result.setSlot(FIELD_TYPE_MAP, map);
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
		for (final A_Tuple fieldDefinition : tuple)
		{
			final AvailObject fieldAtom = fieldDefinition.tupleAt(1);
			final AvailObject type = fieldDefinition.tupleAt(2);
			map = map.mapAtPuttingCanDestroy(fieldAtom, type, true);
		}
		final AvailObject result = mutable.create();
		result.setSlot(FIELD_TYPE_MAP, map);
		return result;
	}

	/**
	 * Assign a name to the specified {@linkplain ObjectTypeDescriptor
	 * user-defined object type}.
	 *
	 * @param anObjectType
	 *        A {@linkplain ObjectTypeDescriptor user-defined object type}.
	 * @param aString A name.
	 */
	public static void setNameForType (
		final A_Type anObjectType,
		final A_String aString)
	{
		assert aString.isString();
		final A_Atom propertyKey = AtomDescriptor.objectTypeNamePropertyKey();
		int leastNames = Integer.MAX_VALUE;
		A_Atom keyAtomWithLeastNames = null;
		A_Map keyAtomNamesMap = null;
		for (final MapDescriptor.Entry entry
			: anObjectType.fieldTypeMap().mapIterable())
		{
			final A_Atom atom = entry.key();
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
		if (keyAtomWithLeastNames != null)
		{
			assert keyAtomNamesMap != null;
			keyAtomNamesMap = keyAtomNamesMap.mapAtPuttingCanDestroy(
				anObjectType,
				aString,
				true);
			keyAtomWithLeastNames.setAtomProperty(propertyKey, keyAtomNamesMap);
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
		for (final MapDescriptor.Entry entry
			: anObjectType.fieldTypeMap().mapIterable())
		{
			final A_BasicObject map = entry.key().getAtomProperty(propertyKey);
			if (!map.equalsNil())
			{
				for (final MapDescriptor.Entry innerEntry : map.mapIterable())
				{
					if (anObjectType.isSubtypeOf(innerEntry.key()))
					{
						applicableTypesAndNames =
							applicableTypesAndNames.mapAtPuttingCanDestroy(
								innerEntry.key(),
								innerEntry.value(),
								true);
					}
				}
			}
		}
		applicableTypesAndNames.makeImmutable();
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
						parentType,
						true);
				}
			}
		}
		A_Set names = SetDescriptor.empty();
		A_Set baseTypes = SetDescriptor.empty();
		for (final MapDescriptor.Entry entry : filtered.mapIterable())
		{
			names = names.setWithElementCanDestroy(entry.value(), true);
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
	/**
	 * Construct a new {@link ObjectTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ObjectTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link ObjectTypeDescriptor}. */
	private static final ObjectTypeDescriptor mutable =
		new ObjectTypeDescriptor(Mutability.MUTABLE);

	@Override
	ObjectTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ObjectTypeDescriptor}. */
	private static final ObjectTypeDescriptor shared =
		new ObjectTypeDescriptor(Mutability.SHARED);

	@Override
	ObjectTypeDescriptor immutable ()
	{
		// There isn't an immutable descriptor, only a shared one.
		return shared;
	}

	@Override
	ObjectTypeDescriptor shared ()
	{
		return shared;
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
		AtomDescriptor.create(
			StringDescriptor.from("explicit-exception"),
			NilDescriptor.nil());

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
		AtomDescriptor.createSpecialAtom(StringDescriptor.from("stack dump"));

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
		setNameForType(type, StringDescriptor.from("exception"));
		exceptionType = type.makeShared();
	}

	/**
	 * Answer the most general exception type. This is just an {@linkplain
	 * InstanceTypeDescriptor instance type} on an {@linkplain
	 * ObjectTypeDescriptor object type} that contains a well-known {@linkplain
	 * #exceptionAtom() atom}.
	 *
	 * @return The most general exception type.
	 */
	public static A_Type exceptionType ()
	{
		return exceptionType;
	}
}

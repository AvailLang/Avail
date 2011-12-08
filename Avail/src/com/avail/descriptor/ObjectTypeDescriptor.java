/**
 * ObjectTypeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static java.lang.Math.min;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;

/**
 * TODO: Document this type!
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ObjectTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
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
		final List<AvailObject> recursionList,
		final int indent)
	{
		final AvailObject pair = namesAndBaseTypesForType(object);
		final AvailObject names = pair.tupleAt(1);
		final AvailObject baseTypes = pair.tupleAt(2);
		boolean first = true;
		for (final AvailObject name : names)
		{
			if (!first)
			{
				builder.append("+");
			}
			else
			{
				builder.append("Type(");
				first = false;
			}
			builder.append(name.asNativeString());
		}
		if (first)
		{
			builder.append("Unnamed object type");
		}
		else
		{
			builder.append(")");
		}
		AvailObject ignoreKeys = SetDescriptor.empty();
		for (final AvailObject baseType : baseTypes)
		{
			final AvailObject fieldTypes = baseType.fieldTypeMap();
			for (final MapDescriptor.Entry entry : fieldTypes.mapIterable())
			{
				if (InstanceTypeDescriptor.on(entry.key).equals(entry.value))
				{
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(
						entry.key,
						true);
				}
			}
		}
		first = true;
		for (final MapDescriptor.Entry entry
			: object.fieldTypeMap().mapIterable())
		{
			if (!ignoreKeys.hasElement(entry.key))
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
				builder.append(entry.key.name().asNativeString());
				builder.append(" : ");
				entry.value.printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		}
	}

	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		final AvailObject typeMap = object.fieldTypeMap();
		final AvailObject instMap = potentialInstance.fieldMap();
		if (instMap.mapSize() < typeMap.mapSize())
		{
			return false;
		}
		for (final MapDescriptor.Entry entry : typeMap.mapIterable())
		{
			final AvailObject fieldKey = entry.key;
			final AvailObject fieldType = entry.value;
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
	@NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FIELD_TYPE_MAP);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.fieldTypeMap().hash() * 11 ^ 0xE3561F16;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return TYPE.o();
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfObjectType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		final AvailObject m1 = object.fieldTypeMap();
		final AvailObject m2 = anObjectType.fieldTypeMap();
		if (m1.mapSize() > m2.mapSize())
		{
			return false;
		}
		for (final MapDescriptor.Entry entry : m1.mapIterable())
		{
			final AvailObject fieldKey = entry.key;
			final AvailObject fieldType = entry.value;
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
	@NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
	@NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		final AvailObject map1 = object.fieldTypeMap();
		final AvailObject map2 = anObjectType.fieldTypeMap();
		AvailObject resultMap = MapDescriptor.newWithCapacity(
			map1.capacity() + map2.capacity());
		for (final MapDescriptor.Entry entry : map1.mapIterable())
		{
			final AvailObject key = entry.key;
			AvailObject type = entry.value;
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
			final AvailObject key = entry.key;
			final AvailObject type = entry.value;
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
	@NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
	@NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		final AvailObject map1 = object.fieldTypeMap();
		final AvailObject map2 = anObjectType.fieldTypeMap();
		AvailObject resultMap = MapDescriptor.newWithCapacity(
			min(map1.capacity(), map2.capacity()));
		for (final MapDescriptor.Entry entry : map1.mapIterable())
		{
			final AvailObject key = entry.key;
			if (map2.hasKey(key))
			{
				final AvailObject valueType = entry.value;
				resultMap = resultMap.mapAtPuttingCanDestroy(
					key,
					valueType.typeUnion(map2.mapAt(key)),
					true);
			}
		}
		return objectTypeFromMap(resultMap);
	}

	/**
	 * Create an {@linkplain ObjectTypeDescriptor object type} using the given
	 * {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
	 * atoms}' {@linkplain InstanceTypeDescriptor instance types} to {@linkplain
	 * TypeDescriptor types}.
	 *
	 * @param map
	 *        The {@linkplain MapDescriptor map} from {@linkplain
	 *        AtomDescriptor key} {@linkplain InstanceTypeDescriptor types} to
	 *        {@linkplain TypeDescriptor types}.
	 * @return The new {@linkplain ObjectTypeDescriptor object type}.
	 */
	public static AvailObject objectTypeFromMap (
		final @NotNull AvailObject map)
	{
		final AvailObject result = mutable().create();
		result.objectSlotPut(ObjectSlots.FIELD_TYPE_MAP, map);
		return result;
	}

	/**
	 * Assign a name to the specified {@linkplain ObjectTypeDescriptor
	 * user-defined object type}.
	 *
	 * @param anObjectType A {@linkplain ObjectTypeDescriptor user-defined
	 *                     object type}.
	 * @param aString A name.
	 */
	public static void setNameForType (
		final @NotNull AvailObject anObjectType,
		final @NotNull AvailObject aString)
	{
		assert aString.isString();
		final AvailObject propertyKey =
			AtomDescriptor.objectTypeNamePropertyKey();
		for (final MapDescriptor.Entry entry
			: anObjectType.fieldTypeMap().mapIterable())
		{
			final AvailObject atom = entry.key;
			AvailObject namesMap = atom.getAtomProperty(propertyKey);
			if (namesMap.equalsNull())
			{
				namesMap = MapDescriptor.empty();
			}
			namesMap = namesMap.mapAtPuttingCanDestroy(
				anObjectType,
				aString,
				true);
			atom.setAtomProperty(propertyKey, namesMap);
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
	public static AvailObject namesAndBaseTypesForType (
		final @NotNull AvailObject anObjectType)
	{
		final AvailObject propertyKey =
			AtomDescriptor.objectTypeNamePropertyKey();
		AvailObject applicableTypesAndNames = MapDescriptor.empty();
		for (final MapDescriptor.Entry entry
			: anObjectType.fieldTypeMap().mapIterable())
		{
			final AvailObject map = entry.key.getAtomProperty(propertyKey);
			if (!map.equalsNull())
			{
				for (final MapDescriptor.Entry innerEntry : map.mapIterable())
				{
					if (anObjectType.isSubtypeOf(innerEntry.key))
					{
						applicableTypesAndNames =
							applicableTypesAndNames.mapAtPuttingCanDestroy(
								innerEntry.key,
								innerEntry.value,
								true);
					}
				}
			}
		}
		applicableTypesAndNames.makeImmutable();
		AvailObject filtered = applicableTypesAndNames;
		for (final MapDescriptor.Entry childEntry
			: applicableTypesAndNames.mapIterable())
		{
			final AvailObject childType = childEntry.key;
			for (final MapDescriptor.Entry parentEntry
				: applicableTypesAndNames.mapIterable())
			{
				final AvailObject parentType = parentEntry.key;
				if (!childType.equals(parentType)
					&& childType.isSubtypeOf(parentType))
				{
					filtered = filtered.mapWithoutKeyCanDestroy(
						parentType,
						true);
				}
			}
		}
		AvailObject names = SetDescriptor.empty();
		AvailObject baseTypes = SetDescriptor.empty();
		for (final MapDescriptor.Entry entry : filtered.mapIterable())
		{
			names = names.setWithElementCanDestroy(entry.value, true);
			baseTypes = baseTypes.setWithElementCanDestroy(entry.key, true);
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
	public static AvailObject namesForType (
		final @NotNull AvailObject anObjectType)
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
	public static AvailObject namedBaseTypesForType (
		final @NotNull AvailObject anObjectType)
	{
		return namesAndBaseTypesForType(anObjectType).tupleAt(2);
	}

	/**
	 * The most general {@linkplain ObjectTypeDescriptor object type}.
	 */
	private static AvailObject MostGeneralType;

	/**
	 * The metatype of all object types.
	 */
	private static AvailObject Meta;

	/**
	 * Create the top (i.e., most general) {@linkplain ObjectTypeDescriptor
	 * object type}.
	 */
	static void createWellKnownObjects ()
	{
		MostGeneralType = objectTypeFromMap(MapDescriptor.empty());
		MostGeneralType.makeImmutable();
		Meta = InstanceTypeDescriptor.on(MostGeneralType);
		Meta.makeImmutable();
	}

	/**
	 * Clear any static references to publicly accessible objects.
	 */
	static void clearWellKnownObjects ()
	{
		MostGeneralType = null;
		Meta = null;
	}

	/**
	 * Answer the top (i.e., most general) {@linkplain ObjectTypeDescriptor
	 * object type}.
	 *
	 * @return The object type that makes no constraints on its fields.
	 */
	public static AvailObject mostGeneralType ()
	{
		return MostGeneralType;
	}

	/**
	 * Answer the metatype for all object types.  This is just an {@linkplain
	 * InstanceTypeDescriptor instance type} on the {@linkplain
	 * #mostGeneralType() most general type}.
	 *
	 * @return The type of the most general object type.
	 */
	public static AvailObject meta ()
	{
		return Meta;
	}


	/**
	 * Construct a new {@link ObjectTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ObjectTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link ObjectTypeDescriptor}. */
	private final static @NotNull ObjectTypeDescriptor mutable =
		new ObjectTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ObjectTypeDescriptor}.
	 *
	 * @return The mutable {@link ObjectTypeDescriptor}.
	 */
	public static @NotNull ObjectTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ObjectTypeDescriptor}. */
	private final static @NotNull ObjectTypeDescriptor immutable =
		new ObjectTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ObjectTypeDescriptor}.
	 *
	 * @return The immutable {@link ObjectTypeDescriptor}.
	 */
	public static @NotNull ObjectTypeDescriptor immutable ()
	{
		return immutable;
	}
}

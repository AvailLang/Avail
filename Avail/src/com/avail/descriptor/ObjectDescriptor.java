/**
 * ObjectDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ObjectDescriptor.ObjectSlots.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

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
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A map from attribute keys to their corresponding values. Attribute
		 * keys are {@linkplain AtomDescriptor atoms}, and the values can be
		 * anything. An object's type is derived from this map and the types of
		 * the attribute values, so it's not quite right to say that the values
		 * can be anything.
		 */
		FIELD_MAP,

		/**
		 * The {@linkplain ObjectTypeDescriptor kind} of the {@linkplain
		 * ObjectDescriptor object}.
		 */
		KIND
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == KIND;
	}

	@Override @AvailMethod
	A_Map o_FieldMap (final AvailObject object)
	{
		return object.slot(FIELD_MAP);
	}

	@Override @AvailMethod
	A_Tuple o_FieldTuple (final AvailObject object)
	{
		final A_Map map = object.slot(FIELD_MAP);
		final List<A_Tuple> fieldAssignments = new ArrayList<A_Tuple>(
			map.mapSize());
		for (final MapDescriptor.Entry entry : map.mapIterable())
		{
			fieldAssignments.add(TupleDescriptor.from(entry.key(), entry.value()));
		}
		return TupleDescriptor.fromList(fieldAssignments);
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
		return object.slot(FIELD_MAP).equals(anObject.slot(FIELD_MAP));
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
		return aTypeObject.hasObjectInstance(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// Answer the object's hash value.
		return computeHashFromFieldMapHash(object.slot(FIELD_MAP).hash());
	}

	/**
	 * Lazily compute and install the kind of the specfied {@linkplain
	 * ObjectDescriptor object}.
	 *
	 * @param object An object.
	 * @return A type.
	 */
	private AvailObject kind (final AvailObject object)
	{
		AvailObject kind = object.slot(KIND);
		if (kind.equalsNil())
		{
			object.makeImmutable();
			final A_Map valueMap = object.slot(FIELD_MAP);
			A_Map typeMap = MapDescriptor.empty();
			for (final MapDescriptor.Entry entry : valueMap.mapIterable())
			{
				typeMap = typeMap.mapAtPuttingCanDestroy(
					entry.key(),
					AbstractEnumerationTypeDescriptor.withInstance(entry.value()),
					true);
			}
			kind = ObjectTypeDescriptor.objectTypeFromMap(typeMap);
			if (isShared())
			{
				kind = kind.traversed().makeShared();
			}
			object.setSlot(KIND, kind);
		}
		return kind;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return kind(object);
			}
		}
		return kind(object);
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.OBJECT;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final A_BasicObject object)
	{
		return false;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		final A_Tuple pair =
			ObjectTypeDescriptor.namesAndBaseTypesForType(object.kind());
		final A_Set names = pair.tupleAt(1);
		final A_Set baseTypes = pair.tupleAt(2);
		boolean first = true;
		builder.append("Instance of (");
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
			builder.append("unnamed object type)");
		}
		else
		{
			builder.append(")");
		}
		A_Set ignoreKeys = SetDescriptor.empty();
		for (final A_Type baseType : baseTypes)
		{
			final A_Map fieldTypes = baseType.fieldTypeMap();
			for (final MapDescriptor.Entry entry : fieldTypes.mapIterable())
			{
				if (InstanceTypeDescriptor.on(entry.key()).equals(
					entry.value()))
				{
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(
						entry.key(),
						true);
				}
			}
		}
		first = true;
		for (final MapDescriptor.Entry entry
			: object.slot(FIELD_MAP).mapIterable())
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
				builder.append(" = ");
				entry.value().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
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
		final AvailObject result = mutable.create();
		result.setSlot(FIELD_MAP, map);
		result.setSlot(KIND, NilDescriptor.nil());
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

	/**
	 * Compute the hash of a user-defined object that would be {@linkplain
	 * #objectFromMap(A_Map) constructed} from a map with the given hash
	 * value.
	 *
	 * @param fieldMapHash The hash of some map.
	 * @return The hash of the user-defined object that would be constructed
	 *         from a map whose hash was provided.
	 */
	private static int computeHashFromFieldMapHash (final int fieldMapHash)
	{
		return fieldMapHash + 0x1099BE88 ^ 0x38547ADE;
	}

	/**
	 * Construct a new {@link ObjectDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ObjectDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link ObjectDescriptor}. */
	private static final ObjectDescriptor mutable =
		new ObjectDescriptor(Mutability.MUTABLE);

	@Override
	ObjectDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ObjectDescriptor}. */
	private static final ObjectDescriptor immutable =
		new ObjectDescriptor(Mutability.IMMUTABLE);

	@Override
	ObjectDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ObjectDescriptor}. */
	private static final ObjectDescriptor shared =
		new ObjectDescriptor(Mutability.SHARED);

	@Override
	ObjectDescriptor shared ()
	{
		return shared;
	}
}

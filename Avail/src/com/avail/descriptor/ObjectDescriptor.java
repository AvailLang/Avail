/**
 * ObjectDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;

/**
 * Avail {@linkplain ObjectTypeDescriptor user-defined object types} are novel.
 * They consist of a {@linkplain MapDescriptor map} of keys (field name
 * {@linkplain AtomDescriptor atoms}) and their associated field {@linkplain
 * TypeDescriptor types}.  Similarly, user-defined objects consist of a map from
 * field names to field values.  An object instance conforms to an object type
 * if and only the instance's field keys are a superset of the type's field
 * keys, and for each field key in common the field value is an instance of the
 * field type.
 *
 * <p>
 * That suggests a simple strategy for representing user-defined objects:  Wrap
 * a map.  That's what we've done here.  It's not the only strategy, since there
 * are plenty of ways of accomplishing the same semantics.  But it's good enough
 * for now.
 * </p>
 *
 * <p>Once we start implementing receiver-type-specific code splitting we'll
 * need to introduce a multiple-dispatch mechanism to deal with multimethods.
 * At that point we'll probably introduce a new representation where objects
 * contain a map from field names to slot numbers, plus a tuple holding those
 * slots (or just a variable number of slots in the object itself).  Then two
 * objects with the same layout can use the same type-specific optimized code to
 * access the object's fields.  Conversely, objects with different field layouts
 * would be considered incompatible for the purpose of sharing optimized code,
 * even if the objects themselves were equal.  Technically, this would be
 * receiver-layout-specific optimization, but since there isn't a single
 * receiver it would have to depend on the combined layouts of any user-defined
 * objects for which the optimized code needs fast access to state variables.
 * </p>
 *
 * @see ObjectTypeDescriptor
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ObjectDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A map from attribute keys to their corresponding values.  Attribute
		 * keys are {@linkplain AtomDescriptor atoms}, and the values can be
		 * anything.  An object's type is derived from this map and the types of
		 * the attribute values, so it's not quite right to say that the values
		 * can be anything.
		 */
		FIELD_MAP
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		final AvailObject pair =
			ObjectTypeDescriptor.namesAndBaseTypesForType(object.kind());
		final AvailObject names = pair.tupleAt(1);
		final AvailObject baseTypes = pair.tupleAt(2);
		boolean first = true;
		builder.append("Instance of (");
		for (final AvailObject name : names)
		{
			if (!first)
			{
				builder.append("+");
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
		AvailObject ignoreKeys = SetDescriptor.empty();
		for (final AvailObject baseType : baseTypes)
		{
			final AvailObject fieldTypes = baseType.fieldTypeMap();
			for (final MapDescriptor.Entry entry : fieldTypes.mapIterable())
			{
				if (entry.key.equals(entry.value))
				{
					ignoreKeys = ignoreKeys.setWithElementCanDestroy(
						entry.key,
						true);
				}
			}
		}
		first = true;
		for (final MapDescriptor.Entry entry
			: object.fieldMap().mapIterable())
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
				builder.append(" = ");
				entry.value.printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		}
	}

	@Override @AvailMethod
	@NotNull AvailObject o_FieldMap (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.FIELD_MAP);
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsObject(object);
	}

	@Override @AvailMethod
	boolean o_EqualsObject (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObject)
	{
		if (object.sameAddressAs(anObject))
		{
			return true;
		}
		return object.fieldMap().equals(anObject.fieldMap());
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTypeObject)
	{
		if (aTypeObject.equals(TOP.o()))
		{
			return true;
		}
		if (aTypeObject.equals(ANY.o()))
		{
			return true;
		}
		return aTypeObject.hasObjectInstance(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		// Answer the object's hash value.

		return computeHashFromFieldMapHash(object.fieldMap().hash());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		object.makeImmutable();
		final AvailObject valueMap = object.fieldMap();
		AvailObject typeMap = MapDescriptor.newWithCapacity(
			valueMap.capacity());
		for (final MapDescriptor.Entry entry : valueMap.mapIterable())
		{
			typeMap = typeMap.mapAtPuttingCanDestroy(
				entry.key,
				InstanceTypeDescriptor.on(entry.value),
				true);
		}
		return ObjectTypeDescriptor.objectTypeFromMap(typeMap);
	}

	/**
	 * Construct a user-defined object with attribute keys and values taken from
	 * the provided map.
	 *
	 * @param map
	 *            A map from {@link AtomDescriptor atom} keys to their
	 *            corresponding values.
	 * @return
	 *            A user-defined object with the specified fields.
	 */
	public static AvailObject objectFromMap (final AvailObject map)
	{
		final AvailObject result = mutable().create();
		result.setSlot(ObjectSlots.FIELD_MAP, map);
		return result;
	}

	/**
	 * Compute the hash of a user-defined object that would be {@linkplain
	 * #objectFromMap(AvailObject) constructed} from a map with the given hash
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
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ObjectDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ObjectDescriptor}.
	 */
	private final static ObjectDescriptor mutable = new ObjectDescriptor(true);

	/**
	 * Answer the mutable {@link ObjectDescriptor}.
	 *
	 * @return The mutable {@link ObjectDescriptor}.
	 */
	public static ObjectDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ObjectDescriptor}.
	 */
	private final static ObjectDescriptor immutable = new ObjectDescriptor(false);

	/**
	 * Answer the immutable {@link ObjectDescriptor}.
	 *
	 * @return The immutable {@link ObjectDescriptor}.
	 */
	public static ObjectDescriptor immutable ()
	{
		return immutable;
	}
}

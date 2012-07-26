/**
 * AtomDescriptor.java
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

import static com.avail.descriptor.AtomDescriptor.IntegerSlots.*;
import static com.avail.descriptor.AtomDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.serialization.*;

/**
 * An {@code atom} is an object that has identity by fiat, i.e., it is
 * distinguished from all other objects by the fact of its creation event and
 * the history of what happens to its references.  Not all objects in Avail have
 * that property (hence the acronym Advanced Value And Identity Language),
 * unlike most object-oriented programming languages.
 *
 * <p>
 * When an atom is created, a string is supplied to act as the atom's name.
 * This name does not have to be unique among atoms, and is simply used to
 * describe the atom textually.
 * </p>
 *
 * <p>
 * Atoms fill the role of enumerations commonly found in other languages.
 * They're not the only things that can fill that role, but they're a simple way
 * to do so.  In particular, {@linkplain AbstractEnumerationTypeDescriptor
 * enumerations} and multiply polymorphic method dispatch provide a phenomenally
 * powerful technique when combined with atoms.  A collection of atoms, say
 * named {@code red}, {@code green}, and {@code blue}, are added to a
 * {@linkplain SetDescriptor set} from which an enumeration is then constructed.
 * Such a type has exactly three instances: the three atoms.  Unlike the vast
 * majority of languages that support enumerations, Avail allows one to define
 * another enumeration containing the same three values plus {@code yellow},
 * {@code cyan}, and {@code magenta}.  {@code red} is a member of both
 * enumerations, for example.
 * </p>
 *
 * <p>
 * Booleans are implemented with exactly this technique, with an atom
 * representing <code>true</code> and another representing <code>false</code>.
 * The boolean type itself is merely an enumeration of these two values.  The
 * only thing special about booleans is that they are referenced by the Avail
 * virtual machine.  In fact, this very class, {@code AtomDescriptor}, contains
 * these references in {@link #trueObject} and {@link #falseObject}.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AtomDescriptor
extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this {@linkplain AtomDescriptor atom}.  It is a
		 * random number (not 0), computed on demand.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A string (non-uniquely) roughly identifying this atom.  It need not
		 * be unique among atoms.
		 */
		NAME,

		/**
		 * The {@linkplain ModuleDescriptor module} that was active when this
		 * atom was issued.  This information is crucial to {@linkplain
		 * Serializer serialization}.
		 */
		ISSUING_MODULE
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == HASH_OR_ZERO;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final String nativeName = object.name().asNativeString();
		// Some atoms print nicer than others.
		if (AvailRuntime.isSpecialAtom(object))
		{
			aStream.append(nativeName);
			return;
		}
		// Default printing: Print the name of the atom, encased in double
		// quotes if it contains any nonalphanumeric characters, followed by a
		// parenthetical aside describing what module originally issued it.
		aStream.append('$');
		if (nativeName.matches("\\w+"))
		{
			aStream.append(nativeName);
		}
		else
		{
			aStream.append('"');
			aStream.append(nativeName);
			aStream.append('"');
		}
		final AvailObject issuer = object.slot(ISSUING_MODULE);
		if (!issuer.equalsNull())
		{
			aStream.append(" (from ");
			final String issuerName = issuer.name().asNativeString();
			aStream.append(
				issuerName.substring(issuerName.lastIndexOf('/') + 1));
			aStream.append(')');
		}
	}

	/**
	 * Create a new atom with the given name.  The name is not globally unique,
	 * but serves to help to visually distinguish atoms.
	 *
	 * @param name
	 *            A string used to help identify the new atom.
	 * @param issuingModule
	 *            Which {@linkplain ModuleDescriptor module} was active when the
	 *            atom was created.
	 * @return
	 *            The new atom, not equal to any object in use before this
	 *            method was invoked.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject name,
		final @NotNull AvailObject issuingModule)
	{
		final AvailObject instance = mutable().create();
		instance.setSlot(NAME, name);
		instance.setSlot(HASH_OR_ZERO, 0);
		instance.setSlot(ISSUING_MODULE, issuingModule);
		instance.makeImmutable();
		return instance;
	}

	/**
	 * The atom representing the Avail concept "true".
	 */
	private static AvailObject trueObject;

	/**
	 * The atom representing the Avail concept "false".
	 */
	private static AvailObject falseObject;

	/**
	 * The atom used as a property key under which to store information about
	 * object type names.
	 */
	private static AvailObject objectTypeNamePropertyKey;


	/**
	 * Answer the atom representing the Avail concept "true".
	 *
	 * @return Avail's <code>true</code> boolean object.
	 */
	public static AvailObject trueObject ()
	{
		return trueObject;
	}

	/**
	 * Answer the atom representing the Avail concept "false".
	 *
	 * @return Avail's <code>false</code> boolean object.
	 */
	public static AvailObject falseObject ()
	{
		return falseObject;
	}

	/**
	 * Answer the atom used as a property key to name {@linkplain
	 * ObjectTypeDescriptor object types}.  This property occurs within each
	 * atom which occurs as a field type key of the object type.  The value is a
	 * map from object type to name.  The naming information is set up via
	 * {@link ObjectTypeDescriptor#setNameForType(AvailObject, AvailObject)}.
	 *
	 * @return An atom that's special because it's known by the virtual machine.
	 */
	public static AvailObject objectTypeNamePropertyKey ()
	{
		return objectTypeNamePropertyKey;
	}


	/**
	 * Create the true and false singletons.
	 */
	static void createWellKnownObjects ()
	{
		trueObject = create(
			StringDescriptor.from("true"),
			NullDescriptor.nullObject());
		falseObject = create(
			StringDescriptor.from("false"),
			NullDescriptor.nullObject());
		objectTypeNamePropertyKey = create(
			StringDescriptor.from("object names"),
			NullDescriptor.nullObject());
	}

	/**
	 * Release the true and false singletons.
	 */
	static void clearWellKnownObjects ()
	{
		trueObject = null;
		falseObject = null;
		objectTypeNamePropertyKey = null;
	}

	/**
	 * Convert a Java <code>boolean</code> into an Avail boolean.  There are
	 * exactly two Avail booleans, which are just ordinary atoms ({@link
	 * #trueObject} and {@link #falseObject}) which are known by the Avail
	 * virtual machine.
	 *
	 * @param aBoolean A Java <code>boolean</code>
	 * @return An Avail boolean.
	 */
	public static AvailObject objectFromBoolean (final boolean aBoolean)
	{
		return aBoolean ? trueObject : falseObject;
	}

	@Override @AvailMethod
	void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(NAME, value);
	}


	@Override @AvailMethod
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_IssuingModule (
		final @NotNull AvailObject object)
	{
		return object.slot(ISSUING_MODULE);
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			do
			{
				hash = AvailRuntime.nextHash();
			}
			while (hash == 0);
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return ATOM.o();
	}

	@Override @AvailMethod
	boolean o_ExtractBoolean (
		final @NotNull AvailObject object)
	{
		if (object.equals(trueObject))
		{
			return true;
		}
		assert object.equals(falseObject);
		return false;
	}

	@Override @AvailMethod
	boolean o_IsAtom (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfPrimitiveTypeWithOrdinal(ATOM.ordinal());
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Convert myself to an equivalent {@linkplain AtomWithPropertiesDescriptor
	 * atom with properties}, then add the property to it.
	 * </p>
	 */
	@Override @AvailMethod
	void o_SetAtomProperty (
		final @NotNull AvailObject object,
		final AvailObject key,
		final AvailObject value)
	{
		final AvailObject substituteAtom =
			AtomWithPropertiesDescriptor.createWithNameAndModuleAndHash(
				object.slot(NAME),
				object.slot(ISSUING_MODULE),
				object.slot(HASH_OR_ZERO));
		object.becomeIndirectionTo(substituteAtom);
		substituteAtom.setAtomProperty(key, value);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This atom has no properties, so always answer {@linkplain
	 * NullDescriptor#nullObject() the null object}.
	 * </p>
	 */
	@Override @AvailMethod
	AvailObject o_GetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key)
	{
		return NullDescriptor.nullObject();
	}

	@Override
	@AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.ATOM;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		if (object.equals(trueObject))
		{
			return Boolean.TRUE;
		}
		if (object.equals(falseObject))
		{
			return Boolean.FALSE;
		}
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	/**
	 * Construct a new {@link AtomDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AtomDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link AtomDescriptor}.
	 */
	private static final AtomDescriptor mutable =
		new AtomDescriptor(true);

	/**
	 * Answer the mutable {@link AtomDescriptor}.
	 *
	 * @return The mutable {@link AtomDescriptor}.
	 */
	public static AtomDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link AtomDescriptor}.
	 */
	private static final AtomDescriptor immutable =
		new AtomDescriptor(false);

	/**
	 * Answer the immutable {@link AtomDescriptor}.
	 *
	 * @return The immutable {@link AtomDescriptor}.
	 */
	public static AtomDescriptor immutable ()
	{
		return immutable;
	}
}

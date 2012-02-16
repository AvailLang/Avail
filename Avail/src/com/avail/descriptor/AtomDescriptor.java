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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
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
 * these references in {@link #TrueObject} and {@link #FalseObject}.
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
		return e == IntegerSlots.HASH_OR_ZERO;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		// Some atoms print nicer than others.
		if (object.equals(TrueObject))
		{
			aStream.append("true");
			return;
		}
		if (object.equals(FalseObject))
		{
			aStream.append("false");
			return;
		}
		// Default printing.
		aStream.append('$');
		final String nativeName = object.name().asNativeString();
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
		aStream.append('[');
		aStream.append(object.hash());
		aStream.append(']');
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
		instance.setSlot(ObjectSlots.NAME, name);
		instance.setSlot(IntegerSlots.HASH_OR_ZERO, 0);
		instance.setSlot(ObjectSlots.ISSUING_MODULE, issuingModule);
		instance.makeImmutable();
		return instance;
	}

	/**
	 * The atom representing the Avail concept "true".
	 */
	private static AvailObject TrueObject;

	/**
	 * The atom representing the Avail concept "false".
	 */
	private static AvailObject FalseObject;

	/**
	 * The atom used as a property key under which to store information about
	 * object type names.
	 */
	private static AvailObject ObjectTypeNamePropertyKey;


	/**
	 * Answer the atom representing the Avail concept "true".
	 *
	 * @return Avail's <code>true</code> boolean object.
	 */
	public static AvailObject trueObject ()
	{
		return TrueObject;
	}

	/**
	 * Answer the atom representing the Avail concept "false".
	 *
	 * @return Avail's <code>false</code> boolean object.
	 */
	public static AvailObject falseObject ()
	{
		return FalseObject;
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
		return ObjectTypeNamePropertyKey;
	}


	/**
	 * Create the true and false singletons.
	 */
	static void createWellKnownObjects ()
	{
		TrueObject = create(
			StringDescriptor.from("true"),
			NullDescriptor.nullObject());
		FalseObject = create(
			StringDescriptor.from("false"),
			NullDescriptor.nullObject());
		ObjectTypeNamePropertyKey = create(
			StringDescriptor.from("objectNames"),
			NullDescriptor.nullObject());
	}

	/**
	 * Release the true and false singletons.
	 */
	static void clearWellKnownObjects ()
	{
		TrueObject = null;
		FalseObject = null;
		ObjectTypeNamePropertyKey = null;
	}

	/**
	 * Convert a Java <code>boolean</code> into an Avail boolean.  There are
	 * exactly two Avail booleans, which are just ordinary atoms ({@link
	 * #TrueObject} and {@link #FalseObject}) which are known by the Avail
	 * virtual machine.
	 *
	 * @param aBoolean A Java <code>boolean</code>
	 * @return An Avail boolean.
	 */
	public static AvailObject objectFromBoolean (final boolean aBoolean)
	{
		return aBoolean ? TrueObject : FalseObject;
	}

	/**
	 * A random generator used for creating hash values as needed.
	 */
	private static Random hashGenerator = new Random();


	@Override @AvailMethod
	void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.NAME, value);
	}


	@Override @AvailMethod
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.NAME);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_IssuingModule (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.ISSUING_MODULE);
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
		int hash = object.slot(IntegerSlots.HASH_OR_ZERO);
		if (hash == 0)
		{
			do
			{
				hash = hashGenerator.nextInt();
			}
			while (hash == 0);
			object.setSlot(IntegerSlots.HASH_OR_ZERO, hash);
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
		if (object.equals(TrueObject))
		{
			return true;
		}
		assert object.equals(FalseObject);
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
		return aType.equals(TOP.o())
			|| aType.equals(ANY.o())
			|| aType.equals(ATOM.o());
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
				object.slot(ObjectSlots.NAME),
				object.slot(ObjectSlots.ISSUING_MODULE),
				object.slot(IntegerSlots.HASH_OR_ZERO));
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
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.ATOM;
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

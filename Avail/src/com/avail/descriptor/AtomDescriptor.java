/**
 * AtomDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import java.io.RandomAccessFile;
import java.nio.channels.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.AvailRuntime.FileHandle;
import com.avail.annotations.*;
import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.compiler.MessageSplitter;
import com.avail.exceptions.MalformedMessageException;
import com.avail.serialization.*;
import com.avail.utility.json.JSONWriter;

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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see AtomWithPropertiesDescriptor
 * @see AtomWithPropertiesSharedDescriptor
 */
public class AtomDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this {@linkplain AtomDescriptor atom}.  It is a
		 * random number (not 0), computed on demand.
		 */
		@HideFieldInDebugger
		HASH
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == HASH;
	}

	@Override
	public final void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		final String nativeName = object.atomName().asNativeString();
		// Some atoms print nicer than others.
		if (object.isAtomSpecial())
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
		final A_Module issuer = object.slot(ISSUING_MODULE);
		if (!issuer.equalsNil())
		{
			aStream.append(" (from ");
			final String issuerName = issuer.moduleName().asNativeString();
			aStream.append(
				issuerName.substring(issuerName.lastIndexOf('/') + 1));
			aStream.append(')');
		}
	}

	@Override @AvailMethod
	A_String o_AtomName (final AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	A_Module o_IssuingModule (final AvailObject object)
	{
		return object.slot(ISSUING_MODULE);
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		int hash = object.slot(HASH);
		if (hash == 0)
		{
			do
			{
				hash = AvailRuntime.nextHash();
			}
			while (hash == 0);
			object.setSlot(HASH, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	final A_Type o_Kind (final AvailObject object)
	{
		return ATOM.o();
	}

	@Override @AvailMethod
	final boolean o_ExtractBoolean (final AvailObject object)
	{
		if (object.equals(trueObject()))
		{
			return true;
		}
		assert object.equals(falseObject());
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsAtom (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	final boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.isSupertypeOfPrimitiveTypeEnum(ATOM);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Before becoming shared, convert the object to an equivalent {@linkplain
	 * AtomWithPropertiesDescriptor atom with properties}, otherwise the object
	 * won't be able to support property definitions.
	 * </p>
	 */
	@Override
	AvailObject o_MakeShared (final AvailObject object)
	{
		// Special atoms, which are already shared, should not transform.
		if (!isShared())
		{
			final AvailObject substituteAtom =
				AtomWithPropertiesDescriptor.createWithNameAndModuleAndHash(
					object.slot(NAME),
					object.slot(ISSUING_MODULE),
					object.slot(HASH));
			object.becomeIndirectionTo(substituteAtom);
			object.makeShared();
			return substituteAtom;
		}
		return object;
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
		final AvailObject object,
		final A_Atom key,
		final A_BasicObject value)
	{
		assert !isShared();
		final AvailObject substituteAtom =
			AtomWithPropertiesDescriptor.createWithNameAndModuleAndHash(
				object.slot(NAME),
				object.slot(ISSUING_MODULE),
				object.slot(HASH));
		object.becomeIndirectionTo(substituteAtom);
		substituteAtom.setAtomProperty(key, value);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This atom has no properties, so always answer {@linkplain
	 * NilDescriptor#nil() nil}.
	 * </p>
	 */
	@Override @AvailMethod
	AvailObject o_GetAtomProperty (
		final AvailObject object,
		final A_Atom key)
	{
		return NilDescriptor.nil();
	}

	@Override
	@AvailMethod @ThreadSafe
	final SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.ATOM;
	}

	@Override
	boolean o_IsBoolean (final AvailObject object)
	{
		return object.isInstanceOf(EnumerationTypeDescriptor.booleanObject());
	}

	@Override
	boolean o_IsAtomSpecial (final AvailObject object)
	{
		// See AtomWithPropertiesSharedDescriptor.
		return false;
	}

	@Override
	final @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		if (object.equals(trueObject()))
		{
			return Boolean.TRUE;
		}
		if (object.equals(falseObject()))
		{
			return Boolean.FALSE;
		}
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	@Override
	A_Bundle o_BundleOrCreate (final AvailObject object)
		throws MalformedMessageException
	{
		A_Bundle bundle = object.getAtomProperty(messageBundleKey);
		if (bundle.equalsNil())
		{
			final A_String name = object.slot(NAME);
			final int numArgs;
			final MessageSplitter splitter = new MessageSplitter(name);
			numArgs = splitter.numberOfArguments();
			final A_Method method = MethodDescriptor.newMethod(numArgs);
			bundle = MessageBundleDescriptor.newBundle(object, method, splitter);
			object.setAtomProperty(messageBundleKey, bundle);
		}
		return bundle;
	}

	@Override
	A_Bundle o_BundleOrNil (final AvailObject object)
	{
		return object.getAtomProperty(messageBundleKey);
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("atom");
		writer.write("atom name");
		object.slot(NAME).writeTo(writer);
		writer.write("issuing module");
		object.slot(ISSUING_MODULE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Construct a new {@link AtomDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private AtomDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * Construct a new {@link AtomDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected AtomDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/** The mutable {@link AtomDescriptor}. */
	private static final AtomDescriptor mutable =
		new AtomDescriptor(Mutability.MUTABLE);

	@Override
	AtomDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link AtomDescriptor}. */
	private static final AtomDescriptor immutable =
		new AtomDescriptor(Mutability.IMMUTABLE);

	@Override
	AtomDescriptor immutable ()
	{
		return immutable;
	}

	@Deprecated
	@Override
	final AtomDescriptor shared ()
	{
		throw unsupportedOperationException();
	}

	/**
	 * Create a new atom with the given name. The name is not globally unique,
	 * but serves to help to visually distinguish atoms.
	 *
	 * @param name
	 *        A string used to help identify the new atom.
	 * @param issuingModule
	 *        Which {@linkplain ModuleDescriptor module} was active when the
	 *        atom was created.
	 * @return
	 *        The new atom, not equal to any object in use before this method
	 *        was invoked.
	 */
	public static AvailObject create (
		final A_String name,
		final A_Module issuingModule)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(NAME, name);
		instance.setSlot(HASH, 0);
		instance.setSlot(ISSUING_MODULE, issuingModule);
		return instance.makeImmutable();
	}

	/**
	 * Create a new special atom with the given name. The name is not globally
	 * unique, but serves to help to visually distinguish atoms. A special atom
	 * should not have properties added to it after initialization.
	 *
	 * @param name
	 *        A string used to help identify the new atom.
	 * @return
	 *        The new atom, not equal to any object in use before this method
	 *        was invoked.
	 */
	public static A_Atom createSpecialAtom (
		final String name)
	{
		AvailObject atom = mutable.create();
		atom.setSlot(NAME, StringDescriptor.from(name).makeShared());
		atom.setSlot(HASH, 0);
		atom.setSlot(ISSUING_MODULE, NilDescriptor.nil());
		atom = atom.makeShared();
		atom.descriptor = AtomWithPropertiesSharedDescriptor.sharedAndSpecial;
		return atom;
	}

	/**
	 * The atom representing the Avail concept "true".
	 */
	private static final A_Atom trueObject = createSpecialAtom("true");

	/**
	 * The atom representing the Avail concept "false".
	 */
	private static final A_Atom falseObject = createSpecialAtom("false");

	/**
	 * Convert a Java <code>boolean</code> into an Avail boolean.  There are
	 * exactly two Avail booleans, which are just ordinary atoms ({@link
	 * #trueObject} and {@link #falseObject}) which are known by the Avail
	 * virtual machine.
	 *
	 * @param aBoolean A Java <code>boolean</code>
	 * @return An Avail boolean.
	 */
	public static A_Atom objectFromBoolean (final boolean aBoolean)
	{
		return aBoolean ? trueObject : falseObject;
	}

	/**
	 * The atom used as a property key under which to store information about
	 * object type names.
	 */
	private static final A_Atom objectTypeNamePropertyKey =
		createSpecialAtom("object names");

	/**
	 * The atom used as a key in a {@link ParserState}'s {@linkplain
	 * ParserState#clientDataMap} to store the current map of declarations that
	 * are in scope.
	 */
	private static final A_Atom compilerScopeMapKey =
		createSpecialAtom("Compilation scope");

	/**
	 * The atom used as a key in a {@linkplain FiberDescriptor fiber}'s global
	 * map to extract the current {@link ParserState}'s {@linkplain
	 * ParserState#clientDataMap}.
	 */
	private static final A_Atom clientDataGlobalKey =
		createSpecialAtom("Compiler client data");

	/**
	 * The atom used as a property key under which to store an {@link
	 * FileHandle}.
	 */
	private static final A_Atom fileKey =
		createSpecialAtom("file key");

	/**
	 * The atom used as a property key under which to store an {@link
	 * AsynchronousServerSocketChannel asynchronous server socket channel}.
	 */
	private static final A_Atom serverSocketKey =
		createSpecialAtom("server socket key");

	/**
	 * The atom used as a property key under which to store an {@link
	 * AsynchronousSocketChannel asynchronous socket channel}.
	 */
	private static final A_Atom socketKey =
		createSpecialAtom("socket key");

	/**
	 * The property key that indicates that a {@linkplain FiberDescriptor fiber}
	 * global is inheritable.
	 */
	private static final A_Atom heritableKey =
		createSpecialAtom("heritability");

	/**
	 * The property key from which to extract an atom's {@linkplain
	 * MessageBundleDescriptor message bundle}, if any.
	 */
	private static final A_Atom messageBundleKey =
		createSpecialAtom("message bundle");

	/**
	 * Answer the atom representing the Avail concept "true".
	 *
	 * @return Avail's <code>true</code> boolean object.
	 */
	public static A_Atom trueObject ()
	{
		return trueObject;
	}

	/**
	 * Answer the atom representing the Avail concept "false".
	 *
	 * @return Avail's <code>false</code> boolean object.
	 */
	public static A_Atom falseObject ()
	{
		return falseObject;
	}

	/**
	 * Answer the atom used as a property key to name {@linkplain
	 * ObjectTypeDescriptor object types}.  This property occurs within each
	 * atom which occurs as a field type key of the object type.  The value is a
	 * map from object type to the set of names of that exact type (typically
	 * just one).  The naming information is set up via {@link
	 * ObjectTypeDescriptor#setNameForType(A_Type, A_String, boolean)}, and
	 * removed by {@link ObjectTypeDescriptor#removeNameFromType(A_String,
	 * A_Type)}.
	 *
	 * @return An atom that's special because it's known by the virtual machine.
	 */
	public static A_Atom objectTypeNamePropertyKey ()
	{
		return objectTypeNamePropertyKey;
	}

	/**
	 * Answer the atom used to identify the entry in a {@linkplain
	 * ParserState}'s {@linkplain ParserState#clientDataMap client data map}
	 * which holds the current map of declarations that are currently in scope.
	 *
	 * @return An atom to use as a key in a ParserState's client data map.
	 */
	public static A_Atom compilerScopeMapKey ()
	{
		return compilerScopeMapKey;
	}

	/**
	 * Answer the atom used to locate the compiler's {@linkplain
	 * ParserState#clientDataMap client data map} within the current fiber's
	 * {@linkplain FiberDescriptor.ObjectSlots#FIBER_GLOBALS global map}.
	 * Among other things, the client data map contains another map under the
	 * key {@link #compilerScopeMapKey()} which holds the current declarations
	 * that are in scope.
	 *
	 * @return An atom to use as a key in a fiber's global map to extract the
	 *         current ParserState's client data map.
	 */
	public static A_Atom clientDataGlobalKey ()
	{
		return clientDataGlobalKey;
	}

	/**
	 * Answer the atom used as a property key under which to store a {@link
	 * RandomAccessFile}.
	 *
	 * @return An atom that's special because it's known by the virtual machine.
	 */
	public static A_Atom fileKey ()
	{
		return fileKey;
	}

	/**
	 * Answer the atom used as a property key under which to store an {@link
	 * AsynchronousServerSocketChannel asynchronous server socket channel}.
	 *
	 * @return An atom that's special because it's known by the virtual machine.
	 */
	public static A_Atom serverSocketKey ()
	{
		return serverSocketKey;
	}

	/**
	 * Answer the atom used as a property key under which to store an {@link
	 * AsynchronousSocketChannel asynchronous socket channel}.
	 *
	 * @return An atom that's special because it's known by the virtual machine.
	 */
	public static A_Atom socketKey ()
	{
		return socketKey;
	}

	/**
	 * Answer the property key that indicates that a {@linkplain FiberDescriptor
	 * fiber} global is inheritable.
	 *
	 * @return An atom that's special because it's known by the virtual machine.
	 */
	public static A_Atom heritableKey ()
	{
		return heritableKey;
	}

	/**
	 * Answer the property key under which this atom's {@linkplain
	 * MessageBundleDescriptor message bundle}, if any, is stored.
	 *
	 * @return An atom held by the VM, used to extract an atom's message bundle.
	 */
	public static A_Atom messageBundleKey ()
	{
		return messageBundleKey;
	}
}

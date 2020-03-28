/*
 * AtomDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
package com.avail.descriptor.atoms

import com.avail.AvailRuntimeSupport
import com.avail.annotations.AvailMethod
import com.avail.annotations.ThreadSafe
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.*
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.Companion.sharedAndSpecial
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.Companion.sharedAndSpecialForFalse
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor.Companion.sharedAndSpecialForTrue
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.representation.*
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.EnumerationTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.exceptions.MalformedMessageException
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*
import java.util.regex.Pattern

/**
 * An `atom` is an object that has identity by fiat, i.e., it is
 * distinguished from all other objects by the fact of its creation event and
 * the history of what happens to its references.  Not all objects in Avail have
 * that property (hence the acronym Advanced Value And Identity Language),
 * unlike most object-oriented programming languages.
 *
 *
 *
 * When an atom is created, a string is supplied to act as the atom's name.
 * This name does not have to be unique among atoms, and is simply used to
 * describe the atom textually.
 *
 *
 *
 *
 * Atoms fill the role of enumerations commonly found in other languages.
 * They're not the only things that can fill that role, but they're a simple way
 * to do so.  In particular, [ enumerations][AbstractEnumerationTypeDescriptor] and multiply polymorphic method dispatch provide a phenomenally
 * powerful technique when combined with atoms.  A collection of atoms, say
 * named `red`, `green`, and `blue`, are added to a
 * [set][SetDescriptor] from which an enumeration is then constructed.
 * Such a type has exactly three instances: the three atoms.  Unlike the vast
 * majority of languages that support enumerations, Avail allows one to define
 * another enumeration containing the same three values plus `yellow`,
 * `cyan`, and `magenta`.  `red` is a member of both
 * enumerations, for example.
 *
 *
 *
 *
 * Booleans are implemented with exactly this technique, with an atom
 * representing `true` and another representing `false`.
 * The boolean type itself is merely an enumeration of these two values.  The
 * only thing special about booleans is that they are referenced by the Avail
 * virtual machine.  In fact, this very class, `AtomDescriptor`, contains
 * these references in [.trueObject] and [.falseObject].
 *
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see AtomWithPropertiesDescriptor
 *
 * @see AtomWithPropertiesSharedDescriptor
 */
open class AtomDescriptor
	/**
	 * Construct a new `AtomDescriptor`.
	 *
	 * @param mutability
	 * The [mutability][Mutability] of the new descriptor.
	 * @param typeTag
	 * The [TypeTag] to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 * The Java [Class] which is a subclass of [            ] and defines this object's object slots
	 * layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 * The Java [Class] which is a subclass of [            ] and defines this object's object slots
	 * layout, or null if there are no integer slots.
	 */
	protected constructor(
		mutability: Mutability,
		typeTag: TypeTag,
		objectSlotsEnumClass: Class<out ObjectSlotsEnum>,
		integerSlotsEnumClass: Class<out IntegerSlotsEnum>
	) : Descriptor(
		mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [.HASH_OR_ZERO], but the upper
		 * 32 can be used by other [BitField]s in subclasses.
		 */
		HASH_AND_MORE;

		companion object {
			/**
			 * A slot to hold the hash value, or zero if it has not been computed.
			 * The hash of an atom is a random number, computed once.
			 */
			val HASH_OR_ZERO = AbstractDescriptor.bitField(HASH_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * A string (non-uniquely) roughly identifying this atom.  It need not
		 * be unique among atoms.
		 */
		NAME,

		/**
		 * The [module][ModuleDescriptor] that was active when this
		 * atom was issued.  This information is crucial to [ ].
		 */
		ISSUING_MODULE
	}

	override fun allowsImmutableToMutableReferenceInField(e: AbstractSlotsEnum): Boolean {
		return e === IntegerSlots.HASH_AND_MORE
	}

	/** A [Pattern] of one or more word characters.  */
	private val wordPattern = Pattern.compile("\\w+")
	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		aStream: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int) {
		val nativeName = self.atomName().asNativeString()
		// Some atoms print nicer than others.
		if (self.isAtomSpecial) {
			aStream.append(nativeName)
			return
		}
		// Default printing: Print the name of the atom, encased in double
		// quotes if it contains any nonalphanumeric characters, followed by a
		// parenthetical aside describing what module originally issued it.
		aStream.append('$')
		if (wordPattern.matcher(nativeName).matches()) {
			aStream.append(nativeName)
		} else {
			aStream.append('"')
			aStream.append(nativeName)
			aStream.append('"')
		}
		val issuer: A_Module = self.slot(ObjectSlots.ISSUING_MODULE)
		if (!issuer.equalsNil()) {
			aStream.append(" (from ")
			val issuerName = issuer.moduleName().asNativeString()
			aStream.append(
				issuerName.substring(issuerName.lastIndexOf('/') + 1))
			aStream.append(')')
		}
	}

	@AvailMethod
	override fun o_AtomName(self: AvailObject): A_String {
		return self.slot(ObjectSlots.NAME)
	}

	@AvailMethod
	override fun o_IssuingModule(self: AvailObject): A_Module {
		return self.slot(ObjectSlots.ISSUING_MODULE)
	}

	@AvailMethod
	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean {
		return another.traversed().sameAddressAs(self)
	}

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int {
		var hash = self.slot(IntegerSlots.HASH_OR_ZERO)
		if (hash == 0) {
			do {
				hash = AvailRuntimeSupport.nextHash()
			} while (hash == 0)
			self.setSlot(IntegerSlots.HASH_OR_ZERO, hash)
		}
		return hash
	}

	@AvailMethod
	override fun o_Kind(self: AvailObject): A_Type {
		return TypeDescriptor.Types.ATOM.o()
	}

	@AvailMethod
	override fun o_ExtractBoolean(self: AvailObject): Boolean {
		if (self.equals(trueObject())) {
			return true
		}
		assert(self.equals(falseObject()))
		return false
	}

	@AvailMethod
	override fun o_IsAtom(self: AvailObject): Boolean {
		return true
	}

	@AvailMethod
	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type): Boolean {
		return aType.isSupertypeOfPrimitiveTypeEnum(TypeDescriptor.Types.ATOM)
	}

	/**
	 * {@inheritDoc}
	 *
	 *
	 *
	 * Before becoming shared, convert the object to an equivalent [ ], otherwise the object
	 * won't be able to support property definitions.
	 *
	 */
	override fun o_MakeShared(self: AvailObject): AvailObject {
		// Special atoms, which are already shared, should not transform.
		if (!isShared) {
			val substituteAtom: AvailObject = AtomWithPropertiesDescriptor.Companion.createWithNameAndModuleAndHash(
				self.slot(ObjectSlots.NAME),
				self.slot(ObjectSlots.ISSUING_MODULE),
				self.slot(IntegerSlots.HASH_OR_ZERO))
			self.becomeIndirectionTo(substituteAtom)
			self.makeShared()
			return substituteAtom
		}
		return self
	}

	/**
	 * {@inheritDoc}
	 *
	 *
	 *
	 * Convert myself to an equivalent [ atom with properties][AtomWithPropertiesDescriptor], then add the property to it.
	 *
	 */
	@AvailMethod
	override fun o_SetAtomProperty(
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject) {
		assert(!isShared)
		val substituteAtom: AvailObject = AtomWithPropertiesDescriptor.Companion.createWithNameAndModuleAndHash(
			self.slot(ObjectSlots.NAME),
			self.slot(ObjectSlots.ISSUING_MODULE),
			self.slot(IntegerSlots.HASH_OR_ZERO))
		self.becomeIndirectionTo(substituteAtom)
		substituteAtom.setAtomProperty(key, value)
	}

	/**
	 * {@inheritDoc}
	 *
	 *
	 *
	 * This atom has no properties, so always answer [ ][NilDescriptor.nil].
	 *
	 */
	@AvailMethod
	override fun o_GetAtomProperty(
		self: AvailObject,
		key: A_Atom): AvailObject {
		return NilDescriptor.nil
	}

	@AvailMethod
	@ThreadSafe
	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation {
		if (self.isAtomSpecial) {
			return SerializerOperation.SPECIAL_ATOM
		}
		if (!self.getAtomProperty(SpecialAtom.HERITABLE_KEY.atom).equalsNil()) {
			return SerializerOperation.HERITABLE_ATOM
		}
		return if (!self.getAtomProperty(SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom).equalsNil()) {
			SerializerOperation.EXPLICIT_SUBCLASS_ATOM
		} else SerializerOperation.ATOM
	}

	override fun o_IsBoolean(self: AvailObject): Boolean {
		return self.isInstanceOf(EnumerationTypeDescriptor.booleanType())
	}

	override fun o_IsAtomSpecial(self: AvailObject): Boolean {
		// See AtomWithPropertiesSharedDescriptor.
		return false
	}

	override fun o_MarshalToJava(
		self: AvailObject,
		ignoredClassHint: Class<*>?
	): Any? = when {
		self.equals(trueObject()) -> java.lang.Boolean.TRUE
		self.equals(falseObject()) -> java.lang.Boolean.FALSE
		else -> super.o_MarshalToJava(self, ignoredClassHint)
	}

	@Throws(MalformedMessageException::class)
	override fun o_BundleOrCreate(self: AvailObject): A_Bundle {
		var bundle: A_Bundle = self.getAtomProperty(SpecialAtom.MESSAGE_BUNDLE_KEY.atom)
		if (bundle.equalsNil()) {
			val name: A_String = self.slot(ObjectSlots.NAME)
			val splitter = MessageSplitter(name)
			val method: A_Method = MethodDescriptor.newMethod(splitter.numberOfArguments)
			bundle = MessageBundleDescriptor.newBundle(self, method, splitter)
			self.setAtomProperty(SpecialAtom.MESSAGE_BUNDLE_KEY.atom, bundle)
		}
		return bundle
	}

	override fun o_BundleOrNil(self: AvailObject): A_Bundle {
		return self.getAtomProperty(SpecialAtom.MESSAGE_BUNDLE_KEY.atom)
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("atom")
		writer.write("atom name")
		self.slot(ObjectSlots.NAME).writeTo(writer)
		if (!self.slot(ObjectSlots.ISSUING_MODULE).equalsNil()) {
			writer.write("issuing module")
			self.slot(ObjectSlots.ISSUING_MODULE).writeSummaryTo(writer)
		}
		writer.endObject()
	}

	override fun mutable(): AtomDescriptor {
		return mutable
	}

	override fun immutable(): AtomDescriptor {
		return immutable
	}

	@Deprecated("")
	override fun shared(): AtomDescriptor {
		throw unsupportedOperationException()
	}

	/**
	 * `SpecialAtom` enumerates [atoms][A_Atom] that are known
	 * to the virtual machine.
	 */
	enum class SpecialAtom {
		/**
		 * The atom representing the Avail concept "true".
		 */
		TRUE(createSpecialBooleanAtom("true", true)),

		/**
		 * The atom representing the Avail concept "false".
		 */
		FALSE(createSpecialBooleanAtom("false", false)),

		/**
		 * The atom used as a property key to name [ ].  This property occurs within each
		 * atom which occurs as a field type key of the object type.  The value
		 * is a map from object type to the set of names of that exact type
		 * (typically just one).  The naming information is set up via [ ][ObjectTypeDescriptor.setNameForType], and
		 * removed by [ObjectTypeDescriptor.removeNameFromType].
		 */
		OBJECT_TYPE_NAME_PROPERTY_KEY("object names"),

		/**
		 * The atom used as a key in a [ParserState]'s
		 * [ParserState.getClientDataMap] to store the current map of
		 * declarations that are in scope.
		 */
		COMPILER_SCOPE_MAP_KEY("Compilation scope"),

		/**
		 * The atom used as a key in a [ParserState]'s
		 * [ParserState.getClientDataMap] to store a tuple of maps to
		 * restore as the blocks that are being parsed are completed.
		 */
		COMPILER_SCOPE_STACK_KEY("Compilation scope stack"),

		/**
		 * The atom used as a key in a [ParserState]'s [ ][ParserState.getClientDataMap] to accumulate the tuple of tokens that
		 * have been parsed so far for the current method/macro site.
		 */
		ALL_TOKENS_KEY("All tokens"),

		/**
		 * The atom used as a key in a [ParserState]'s [ ][ParserState.getClientDataMap] to accumulate the tuple of tokens that
		 * have been parsed so far for the current method/macro site and are
		 * mentioned by name in the method name.
		 */
		STATIC_TOKENS_KEY("Static tokens"),

		/**
		 * The atom used to identify the entry in a [ParserState]'s
		 * [client data map][ParserState.getClientDataMap] containing the
		 * bundle of the macro send for which the current fiber is computing a
		 * replacement phrase.
		 */
		MACRO_BUNDLE_KEY("Macro bundle"),

		/**
		 * The atom used as a key in a [fiber][FiberDescriptor]'s
		 * global map to extract the current [ParserState]'s [ ][ParserState.getClientDataMap].
		 */
		CLIENT_DATA_GLOBAL_KEY("Compiler client data"),

		/**
		 * The atom used as a property key under which to store an [ ].
		 */
		FILE_KEY("file key"),

		/**
		 * The atom used as a property key under which to store an [ ].
		 */
		SERVER_SOCKET_KEY("server socket key"),

		/**
		 * The atom used as a property key under which to store an [ ].
		 */
		SOCKET_KEY("socket key"),

		/**
		 * The property key that indicates that a [ fiber][FiberDescriptor] global is inheritable.
		 */
		HERITABLE_KEY("heritability"),

		/**
		 * The property key from which to extract an atom's [ ], if any.
		 */
		MESSAGE_BUNDLE_KEY("message bundle"),

		/**
		 * The property key whose presence indicates an atom is for explicit
		 * subclassing of object types.
		 */
		EXPLICIT_SUBCLASSING_KEY("explicit subclassing");

		/** The special atom.  */
		@JvmField
		val atom: A_Atom

		/**
		 * Create a `SpecialAtom` with the given name.
		 *
		 * @param name The name of the atom to be created.
		 */
		constructor(name: String) {
			atom = createSpecialAtom(name)
		}

		/**
		 * Create a `SpecialAtom` to hold the given already constructed
		 * [A_Atom].
		 *
		 * @param atom
		 * The actual [A_Atom] to be held by this `SpecialAtom`.
		 */
		constructor(atom: A_Atom) {
			this.atom = atom
		}
	}

	companion object {
		/** The mutable [AtomDescriptor].  */
		private val mutable = AtomDescriptor(
			Mutability.MUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The immutable [AtomDescriptor].  */
		private val immutable = AtomDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/**
		 * Create a new atom with the given name. The name is not globally unique,
		 * but serves to help to visually distinguish atoms.
		 *
		 * @param name
		 * A string used to help identify the new atom.
		 * @param issuingModule
		 * Which [module][ModuleDescriptor] was active when the
		 * atom was created.
		 * @return
		 * The new atom, not equal to any object in use before this method
		 * was invoked.
		 */
		@JvmStatic
		fun createAtom(
			name: A_String?,
			issuingModule: A_Module?): AvailObject {
			val instance = mutable.create()
			instance.setSlot(ObjectSlots.NAME, name!!)
			instance.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			instance.setSlot(ObjectSlots.ISSUING_MODULE, issuingModule!!)
			return instance.makeImmutable()
		}

		/**
		 * Create a new special atom with the given name. The name is not globally
		 * unique, but serves to help to visually distinguish atoms. A special atom
		 * should not have properties added to it after initialization.
		 *
		 * @param name
		 * A string used to help identify the new atom.
		 * @return
		 * The new atom, not equal to any object in use before this method
		 * was invoked.
		 */
		@JvmStatic
		fun createSpecialAtom(
			name: String
		): A_Atom {
			var atom = mutable.create()
			atom.setSlot(ObjectSlots.NAME, stringFrom(name).makeShared())
			atom.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			atom.setSlot(ObjectSlots.ISSUING_MODULE, NilDescriptor.nil)
			atom = atom.makeShared()
			atom.setDescriptor(sharedAndSpecial)
			return atom
		}

		/**
		 * Create one of the two boolean atoms, using the given name and boolean
		 * value.  A special atom should not have properties added to it after
		 * initialization.
		 *
		 * @param name
		 * A string used to help identify the new boolean atom.
		 * @param booleanValue
		 * The boolean for which to build a corresponding special atom.
		 * @return
		 * The new atom, not equal to any object in use before this method
		 * was invoked.
		 */
		fun createSpecialBooleanAtom(
			name: String,
			booleanValue: Boolean): A_Atom {
			var atom = mutable.create()
			atom.setSlot(ObjectSlots.NAME, stringFrom(name).makeShared())
			atom.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			atom.setSlot(ObjectSlots.ISSUING_MODULE, NilDescriptor.nil)
			atom = atom.makeShared()
			atom.setDescriptor(when {
				booleanValue -> sharedAndSpecialForTrue
				else -> sharedAndSpecialForFalse
			})
			return atom
		}

		/**
		 * Convert a Java `boolean` into an Avail boolean.  There are
		 * exactly two Avail booleans, which are just ordinary atoms ([ ][.trueObject] and [.falseObject]) which are known by the Avail
		 * virtual machine.
		 *
		 * @param aBoolean A Java `boolean`
		 * @return An Avail boolean.
		 */
		@JvmStatic
		fun objectFromBoolean(aBoolean: Boolean): A_Atom {
			return if (aBoolean) SpecialAtom.TRUE.atom else SpecialAtom.FALSE.atom
		}

		/**
		 * Answer the atom representing the Avail concept "true".
		 *
		 * @return Avail's `true` boolean object.
		 */
		@JvmStatic
		fun trueObject(): A_Atom {
			return SpecialAtom.TRUE.atom
		}

		/**
		 * Answer the atom representing the Avail concept "false".
		 *
		 * @return Avail's `false` boolean object.
		 */
		@JvmStatic
		fun falseObject(): A_Atom {
			return SpecialAtom.FALSE.atom
		}
	}
}
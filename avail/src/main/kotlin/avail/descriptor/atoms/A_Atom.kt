/*
 * A_Atom.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.atoms

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.exceptions.MalformedMessageException
import avail.persistence.cache.record.NameInModule

/**
 * `A_Atom` is an interface that specifies the atom-specific operations that an
 * [AvailObject] must implement.  It's a sub-interface of [A_BasicObject], the
 * interface that defines the behavior that all [AvailObject]s are required to
 * support.
 *
 * @author [Mark van Gulik](mark@availlang.org)
 */
interface A_Atom : A_BasicObject {

	companion object {
		/**
		 * Answer the descriptive string that was supplied when this atom was
		 * created.  The string didn't have to be unique within the
		 * [issuingModule], but certain operations might only work if it happens
		 * to be.
		 *
		 * @return
		 *   The string within this [atom][AtomDescriptor].
		 */
		val A_Atom.atomName: A_String get() = dispatch { o_AtomName(it) }

		/**
		 * Answer the [module][A_Module] within which this atom was created.
		 *
		 * @return
		 *   The issuing module.
		 */
		val A_Atom.issuingModule: A_Module
			get() = dispatch { o_IssuingModule(it) }

		/**
		 * Extract a Java `boolean` from this atom.  The atom must be either
		 * the [trueObject] or the [falseObject].
		 *
		 * @return
		 *   `true` if it's the [trueObject], `false` if it's the [falseObject],
		 *   and otherwise fail.
		 */
		val A_Atom.extractBoolean: Boolean
			get() = dispatch { o_ExtractBoolean(it) }

		fun A_Atom.setAtomBundle(bundle: A_Bundle) =
			dispatch { o_SetAtomBundle(it, bundle) }

		/**
		 * Set the specified property of this atom to the specified value.
		 * Normal atoms have properties that can be set and read in this way,
		 * but specifically not *enumerated* by Avail code.  You can see
		 * anything that you know how to look for, but everything else is
		 * thereby encapsulated.
		 *
		 * @param key
		 *   The property key to affect, an [atom][AtomDescriptor].
		 * @param value
		 *   The value to associate with that property key within the receiver.
		 *   If it's [nil], remove the property.
		 */
		fun A_Atom.setAtomProperty(key: A_Atom, value: A_BasicObject) =
			dispatch { o_SetAtomProperty(it, key, value) }

		/**
		 * Look up a property of this atom.  Normal atoms have properties that
		 * can be set and read in this way, but specifically not *enumerated* by
		 * Avail code.  You can see anything that you know how to look for, but
		 * everything else is thereby encapsulated.
		 *
		 * @param key
		 *   The property key to look up, an [atom][AtomDescriptor].
		 * @return
		 *   The value associated with that property key within the receiver, or
		 *   nil if there is no such property in this atom.
		 */
		fun A_Atom.getAtomProperty(key: A_Atom): AvailObject =
			dispatch { o_GetAtomProperty(it, key) }

		/**
		 * Answer the [message&#32;bundle][MessageBundleDescriptor] associated
		 * with this atom.  If the atom does not yet have a message bundle
		 * associated with it, create one for that purpose and install it.  The
		 * creation of the bundle is atomic, ensuring multiple fibers attempting
		 * to create the atom's bundle will agree about which bundle was created
		 * and installed in the atom.
		 *
		 * @return
		 *   The atom's message bundle.
		 * @throws MalformedMessageException
		 *   If anything is wrong with the message name.
		 */
		@Throws(MalformedMessageException::class)
		fun A_Atom.bundleOrCreate(): A_Bundle =
			dispatch { o_BundleOrCreate(it) }

		/**
		 * Answer the [message&#32;bundle][MessageBundleDescriptor] associated
		 * with this atom.  If the atom does not yet have a message bundle
		 * associated with it, answer [nil].
		 *
		 * @return
		 *   The atom's message bundle or nil.
		 */
		val A_Atom.bundleOrNil: A_Bundle
			get() = dispatch { o_BundleOrNil(it) }

		/**
		 * Answer whether this atom is specially known to the Avail virtual
		 * machine.
		 *
		 * @return
		 *   Whether this atom is special to the VM.
		 */
		val A_Atom.isAtomSpecial: Boolean
			get() = dispatch { o_IsAtomSpecial(it) }

		val A_Atom.asNameInModule: NameInModule
			get() = NameInModule(
				issuingModule.moduleNameNative,
				atomName.asNativeString())
	}
}

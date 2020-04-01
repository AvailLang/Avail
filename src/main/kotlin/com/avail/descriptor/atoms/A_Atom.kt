/*
 * A_Atom.java
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.A_Module
import com.avail.descriptor.AvailObject
import com.avail.descriptor.NilDescriptor
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.exceptions.MalformedMessageException

/**
 * `A_Atom` is an interface that specifies the atom-specific operations
 * that an [AvailObject] must implement.  It's a sub-interface of [ ], the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Atom : A_BasicObject {
	/**
	 * Answer the descriptive string that was supplied when this atom was
	 * created.  The string didn't have to be unique within the [ ][.issuingModule], but certain operations might only work if it happens
	 * to be.
	 *
	 * @return The string within this [atom][AtomDescriptor].
	 */
	fun atomName(): A_String

	/**
	 * Answer the [module][ModuleDescriptor] within which this
	 * [atom][AtomDescriptor] was created.
	 *
	 * @return The issuing module.
	 */
	fun issuingModule(): A_Module

	/**
	 * Extract a Java `boolean` from this atom.  The atom must be either
	 * the object [AtomDescriptor.trueObject] or the object
	 * [AtomDescriptor.falseObject].
	 *
	 * @return `true` if it's the trueObject(), `false` if it's the
	 * falseObject(), and otherwise fail.
	 */
	fun extractBoolean(): Boolean

	/**
	 * Set the specified property of this atom to the specified value.  Normal
	 * atoms have properties that can be set and read in this way, but
	 * specifically not *enumerated* by Avail code.  You can see anything
	 * that you know how to look for, but everything else is thereby
	 * encapsulated.
	 *
	 * @param key
	 * The property key to affect, an [            atom][AtomDescriptor].
	 * @param value
	 * The value to associate with that property key within the
	 * receiver.
	 */
	fun setAtomProperty(key: A_Atom, value: A_BasicObject)

	/**
	 * Look up a property of this atom.  Normal atoms have properties that can
	 * be set and read in this way, but specifically not *enumerated* by
	 * Avail code.  You can see anything that you know how to look for, but
	 * everything else is thereby encapsulated.
	 *
	 * @param key
	 * The property key to look up, an [            atom][AtomDescriptor].
	 * @return
	 * The value associated with that property key within the
	 * receiver.
	 */
	fun getAtomProperty(key: A_Atom): AvailObject

	/**
	 * Answer the [message bundle][MessageBundleDescriptor] associated
	 * with this atom.  If the atom does not yet have a message bundle
	 * associated with it, create one for that purpose and install it.
	 *
	 * @return The atom's message bundle.
	 * @throws MalformedMessageException
	 * If anything is wrong with the message name.
	 */
	@Throws(MalformedMessageException::class)
	fun bundleOrCreate(): A_Bundle

	/**
	 * Answer the [message bundle][MessageBundleDescriptor] associated
	 * with this atom.  If the atom does not yet have a message bundle
	 * associated with it, answer [nil][NilDescriptor].
	 *
	 * @return The atom's message bundle or nil.
	 */
	fun bundleOrNil(): A_Bundle

	/**
	 * Answer whether this atom is specially known to the Avail virtual machine.
	 *
	 * @return Whether this atom is special to the VM.
	 */
	val isAtomSpecial: Boolean
}
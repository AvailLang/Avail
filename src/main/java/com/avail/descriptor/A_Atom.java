/*
 * A_Atom.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.exceptions.MalformedMessageException;

/**
 * {@code A_Atom} is an interface that specifies the atom-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Atom
extends A_BasicObject
{
	/**
	 * Answer the descriptive string that was supplied when this atom was
	 * created.  The string didn't have to be unique within the {@link
	 * #issuingModule()}, but certain operations might only work if it happens
	 * to be.
	 *
	 * @return The string within this {@linkplain AtomDescriptor atom}.
	 */
	A_String atomName ();

	/**
	 * Answer the {@linkplain ModuleDescriptor module} within which this
	 * {@linkplain AtomDescriptor atom} was created.
	 *
	 * @return The issuing module.
	 */
	A_Module issuingModule ();

	/**
	 * Extract a Java {@code boolean} from this atom.  The atom must be either
	 * the object {@linkplain AtomDescriptor#trueObject()} or the object
	 * {@linkplain AtomDescriptor#falseObject()}.
	 *
	 * @return {@code true} if it's the trueObject(), {@code false} if it's the
	 *         falseObject(), and otherwise fail.
	 */
	boolean extractBoolean ();

	/**
	 * Set the specified property of this atom to the specified value.  Normal
	 * atoms have properties that can be set and read in this way, but
	 * specifically not <em>enumerated</em> by Avail code.  You can see anything
	 * that you know how to look for, but everything else is thereby
	 * encapsulated.
	 *
	 * @param key
	 *            The property key to affect, an {@linkplain AtomDescriptor
	 *            atom}.
	 * @param value
	 *            The value to associate with that property key within the
	 *            receiver.
	 */
	void setAtomProperty (A_Atom key, A_BasicObject value);

	/**
	 * Look up a property of this atom.  Normal atoms have properties that can
	 * be set and read in this way, but specifically not <em>enumerated</em> by
	 * Avail code.  You can see anything that you know how to look for, but
	 * everything else is thereby encapsulated.
	 *
	 * @param key
	 *            The property key to look up, an {@linkplain AtomDescriptor
	 *            atom}.
	 * @return
	 *            The value associated with that property key within the
	 *            receiver.
	 */
	AvailObject getAtomProperty (A_Atom key);

	/**
	 * Answer the {@linkplain MessageBundleDescriptor message bundle} associated
	 * with this atom.  If the atom does not yet have a message bundle
	 * associated with it, create one for that purpose and install it.
	 *
	 * @return The atom's message bundle.
	 * @throws MalformedMessageException
	 *         If anything is wrong with the message name.
	 */
	A_Bundle bundleOrCreate () throws MalformedMessageException;

	/**
	 * Answer the {@linkplain MessageBundleDescriptor message bundle} associated
	 * with this atom.  If the atom does not yet have a message bundle
	 * associated with it, answer {@linkplain NilDescriptor nil}.
	 *
	 * @return The atom's message bundle or nil.
	 */
	A_Bundle bundleOrNil ();

	/**
	 * Answer whether this atom is specially known to the Avail virtual machine.
	 *
	 * @return Whether this atom is special to the VM.
	 */
	boolean isAtomSpecial ();
}

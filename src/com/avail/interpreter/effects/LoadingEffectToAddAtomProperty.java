/**
 * LoadingEffectToAddAtomProperty.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.effects;

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToAddAtomProperty} summarizes the addition of a
 * property to an atom.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToAddAtomProperty extends LoadingEffect
{
	/** The atom to add a property to. */
	final A_Atom atom;

	/** The property key, another atom, that is being added. */
	final A_Atom propertyKey;

	/** The property value being added under the property key to the atom. */
	final A_BasicObject propertyValue;

	/**
	 * Construct a new {@link LoadingEffectToAddAtomProperty}.
	 *
	 * @param atom The atom to add a property to.
	 * @param propertyKey The property key that is being added.
	 * @param propertyValue The value to add under that key.
	 */
	public LoadingEffectToAddAtomProperty (
		final A_Atom atom,
		final A_Atom propertyKey,
		final A_BasicObject propertyValue)
	{
		assert !atom.isAtomSpecial();
		assert !propertyKey.isAtomSpecial();
		this.atom = atom;
		this.propertyKey = propertyKey;
		this.propertyValue = propertyValue;
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		// Push the atom that should have a property added.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(atom));
		// Push the property key.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(propertyKey));
		// Push the property value.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(propertyValue));
		// Call the property adding method.
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.ATOM_PROPERTY.bundle),
			writer.addLiteral(Types.TOP.o()));
	}
}

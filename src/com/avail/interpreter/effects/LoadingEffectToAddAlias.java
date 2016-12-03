/**
 * LoadingEffectToAddAlias.java
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
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_String;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToAddAlias} summarizes the addition of an alias {@link
 * A_Bundle bundle} to a {@link A_Method method}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToAddAlias extends LoadingEffect
{
	/** The name of an atom to define aliased to oldAtom. */
	final A_String newString;

	/** The existing atom that the new atom will alias. */
	final A_Atom oldAtom;

	/**
	 * Construct a new {@link LoadingEffectToAddAlias}.
	 *
	 * @param newString The name of the atom to create.
	 * @param oldAtom The existing atom to alias.
	 */
	public LoadingEffectToAddAlias (
		final A_String newString,
		final A_Atom oldAtom)
	{
		this.newString = newString;
		this.oldAtom = oldAtom;
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		// Push the (string) name of the atom to create.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(newString));
		// Push the existing atom that the new atom will alias.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(oldAtom));
		// Call the method that aliases an atom.
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.ALIAS.bundle),
			writer.addLiteral(Types.TOP.o()));
	}
}

/**
 * LoadingEffectToAddGrammaticalRestriction.java
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

import com.avail.descriptor.*;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToAddGrammaticalRestriction} summarizes the addition of
 * a {@link A_GrammaticalRestriction grammatical restriction} syntactic rule.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToAddGrammaticalRestriction extends LoadingEffect
{
	/**
	 * The {@link A_Atom atoms} naming {@link A_Bundle message bundles} whose
	 * invocations should have their child phrases restricted grammatically.
	 */
	final A_Set parentAtoms;

	/**
	 * A tuple of sets of atoms.  Each tuple position corresponds with one
	 * argument slot position (not necessarily a top-level argument) of the
	 * parentAtoms' message bundles.  The elements of the set are the atoms that
	 * name message bundles that must not be the message of a {@link
	 * SendNodeDescriptor send phrase} occupying that argument position.
	 */
	final A_Tuple childAtomSets;

	/**
	 * Construct a new {@link LoadingEffectToAddGrammaticalRestriction}.
	 *
	 * @param parentAtoms
	 *        The atoms whose sends are to have their arguments restricted.
	 * @param childAtomSets
	 *        The restrictions to be applied to each argument position.
	 */
	public LoadingEffectToAddGrammaticalRestriction (
		final A_Set parentAtoms,
		final A_Tuple childAtomSets)
	{
		this.parentAtoms = parentAtoms;
		this.childAtomSets = childAtomSets;
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		// Push the set of parent atoms.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(parentAtoms));
		// Push the tuple of sets of atoms to forbid as sends in arguments.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(childAtomSets));
		// Call the method to add the grammatical restrictions.
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.GRAMMATICAL_RESTRICTION.bundle),
			writer.addLiteral(Types.TOP.o()));
	}
}

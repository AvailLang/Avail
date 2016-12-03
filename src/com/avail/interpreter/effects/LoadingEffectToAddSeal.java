/**
 * LoadingEffectToAddSeal.java
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
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToAddSeal} summarizes the addition of a seal to a
 * {@link A_Method method}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToAddSeal extends LoadingEffect
{
	/** The name of the method having a seal added. */
	final A_Atom methodName;

	/** The tuple of argument types at which to place the seal. */
	final A_Tuple seal;

	/**
	 * Construct a new {@link LoadingEffectToAddSeal}.
	 *
	 * @param methodName The name of the method having a seal added.
	 * @param seal The tuple of argument types at which to place a seal.
	 */
	public LoadingEffectToAddSeal (final A_Atom methodName, final A_Tuple seal)
	{
		this.methodName = methodName;
		this.seal = seal;
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		// Push the (atom) name of the method to seal.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(methodName));
		// Push the tuple of types.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(seal));
		// Call the sealing method.
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.SEAL.bundle),
			writer.addLiteral(Types.TOP.o()));
	}
}

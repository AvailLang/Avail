/**
 * LoadingEffectToAddPrefixFunction.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.MethodDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToAddPrefixFunction} summarizes the addition of one
 * {@link A_Method#prefixFunctions() prefix function} to a {@link A_Method
 * method}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToAddPrefixFunction extends LoadingEffect
{
	/** The method having a prefix function added by this effect. */
	final A_Method method;

	/** The index of the prefix function being added. */
	final int prefixFunctionIndex;

	/** The prefix function being added. */
	final A_Function prefixFunction;

	/**
	 * Construct a new {@link LoadingEffectToAddPrefixFunction}.
	 *
	 * @param method The method to which to add a prefix function.
	 * @param prefixFunctionIndex The index at which to add it.
	 * @param prefixFunction The prefix function to add.
	 */
	public LoadingEffectToAddPrefixFunction (
		final A_Method method,
		final int prefixFunctionIndex,
		final A_Function prefixFunction)
	{
		this.method = method;
		this.prefixFunctionIndex = prefixFunctionIndex;
		this.prefixFunction = prefixFunction;
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		// Push the bundle's atom.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(method.chooseBundle().message()));
		// Push the prefix function index.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(IntegerDescriptor.fromInt(prefixFunctionIndex)));
		// Push the prefix function.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(prefixFunction));
		// Call the method to add the prefix function.
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(
				MethodDescriptor.vmDeclarePrefixFunctionAtom().bundleOrNil()),
			writer.addLiteral(Types.TOP.o()));
	}
}

/**
 * LoadingEffectToRunPrimitive.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToRunPrimitive} summarizes the execution of some
 * primitive, with literal arguments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToRunPrimitive extends LoadingEffect
{
	/** The primitive to run. */
	final A_Bundle primitiveBundle;

	/** The array of arguments to pass to the primitive. */
	final A_BasicObject[] arguments;

	/**
	 * Construct a new {@code LoadingEffectToRunPrimitive}.
	 *
	 * @param primitiveBundle The primitive {@link A_Bundle} to invoke.
	 * @param arguments The argument values for the primitive.
	 */
	public LoadingEffectToRunPrimitive (
		final A_Bundle primitiveBundle,
		final A_BasicObject... arguments)
	{
		assert primitiveBundle.bundleMethod().numArgs() == arguments.length;
		this.primitiveBundle = primitiveBundle;
		this.arguments = arguments.clone();
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		// Push each argument.
		for (final A_BasicObject argument : arguments)
		{
			writer.write(
				0,
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(argument));
		}
		// Call the primitive, leaving the return value on the stack.
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(primitiveBundle),
			writer.addLiteral(Types.TOP.o()));
	}
}

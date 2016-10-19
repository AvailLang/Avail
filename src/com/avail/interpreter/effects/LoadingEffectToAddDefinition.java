/**
 * LoadingEffectToAddDefinition.java
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
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.DefinitionDescriptor;
import com.avail.descriptor.MethodDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToAddDefinition} summarizes the addition of one
 * {@link DefinitionDescriptor definition} to a {@link A_Method method}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToAddDefinition extends LoadingEffect
{
	/** The definition being added by this effect. */
	final A_Definition definition;

	/**
	 * Construct a new {@link LoadingEffectToAddDefinition}.
	 *
	 * @param definition The definition being added.
	 */
	public LoadingEffectToAddDefinition (final A_Definition definition)
	{
		this.definition = definition;
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		final A_Atom atom =
			definition.definitionMethod().chooseBundle().message();
		if (definition.isAbstractDefinition())
		{
			// Push the bundle's atom.
			writer.write(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(atom));
			// Push the function type.
			writer.write(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(definition.bodySignature()));
			// Call the abstract definition loading method.
			writer.write(
				L1Operation.L1_doCall,
				writer.addLiteral(
					MethodDescriptor.vmAbstractDefinerAtom().bundleOrNil()),
				writer.addLiteral(Types.TOP.o()));
			return;
		}
		if (definition.isForwardDefinition())
		{
			// Push the bundle's atom.
			writer.write(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(atom));
			// Push the function type.
			writer.write(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(definition.bodySignature()));
			// Call the forward definition loading method.
			writer.write(
				L1Operation.L1_doCall,
				writer.addLiteral(
					MethodDescriptor.vmForwardDefinerAtom().bundleOrNil()),
				writer.addLiteral(Types.TOP.o()));
			return;
		}
		if (definition.isMacroDefinition())
		{
			// NOTE: The prefix functions are dealt with as separate effects.
			// Push the bundle's atom.
			writer.write(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(atom));
			// Push the macro body function.
			writer.write(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(definition.bodyBlock()));
			// Call the macro definition method.
			writer.write(
				L1Operation.L1_doCall,
				writer.addLiteral(
					MethodDescriptor.vmJustMacroDefinerAtom().bundleOrNil()),
				writer.addLiteral(Types.TOP.o()));
			return;
		}
		assert definition.isMethodDefinition();
		// Push the bundle's atom.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(atom));
		// Push the body function.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(definition.bodyBlock()));
		// Call the definition loading method.
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(
				MethodDescriptor.vmMethodDefinerAtom().bundleOrNil()),
			writer.addLiteral(Types.TOP.o()));
	}
}

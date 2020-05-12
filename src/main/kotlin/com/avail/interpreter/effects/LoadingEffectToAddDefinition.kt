/*
 * LoadingEffectToAddDefinition.kt
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

package com.avail.interpreter.effects

import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.*
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.interpreter.levelOne.L1InstructionWriter
import com.avail.interpreter.levelOne.L1Operation

/**
 * A `LoadingEffectToAddDefinition` summarizes the addition of one
 * [definition][DefinitionDescriptor] to a [method][A_Method].
 *
 * @property definition
 *   The definition being added by this effect.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `LoadingEffectToAddDefinition`.
 *
 * @param definition
 *   The definition being added.
 */
internal class LoadingEffectToAddDefinition constructor(
	internal val bundle: A_Bundle,
	internal val definition: A_Definition
) : LoadingEffect() {
	override fun writeEffectTo(writer: L1InstructionWriter)
	{
		val atom = bundle.message()
		with(writer) {
			when {
				definition.isAbstractDefinition() -> {
					// Push the bundle's atom.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(atom))
					// Push the function type.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(definition.bodySignature()))
					// Call the abstract definition loading method.
					write(
						0,
						L1Operation.L1_doCall,
						addLiteral(ABSTRACT_DEFINER.bundle),
						addLiteral(Types.TOP.o()))
				}
				definition.isForwardDefinition() -> {
					// Push the bundle's atom.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(atom))
					// Push the function type.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(definition.bodySignature()))
					// Call the forward definition loading method.
					write(
						0,
						L1Operation.L1_doCall,
						addLiteral(FORWARD_DEFINER.bundle),
						addLiteral(Types.TOP.o()))
				}
				definition.isMacroDefinition() -> {
					// NOTE: The prefix functions are dealt with as separate effects.
					// Push the bundle's atom.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(atom))
					// Push the tuple of macro prefix functions.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(definition.prefixFunctions()))
					// Push the macro body function.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(definition.bodyBlock()))
					// Call the macro definition method.
					write(
						0,
						L1Operation.L1_doCall,
						addLiteral(MACRO_DEFINER.bundle),
						addLiteral(Types.TOP.o()))
				}
				else -> {
					assert(definition.isMethodDefinition())
					// Push the bundle's atom.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(atom))
					// Push the body function.
					write(
						0,
						L1Operation.L1_doPushLiteral,
						addLiteral(definition.bodyBlock()))
					// Call the definition loading method.
					write(
						0,
						L1Operation.L1_doCall,
						addLiteral(METHOD_DEFINER.bundle),
						addLiteral(Types.TOP.o()))
				}
			}
		}
	}
}

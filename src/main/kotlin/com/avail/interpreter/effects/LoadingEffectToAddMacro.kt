/*
 * LoadingEffectToAddMacro.kt
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
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.MACRO_DEFINER
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.interpreter.levelOne.L1InstructionWriter
import com.avail.interpreter.levelOne.L1Operation

/**
 * A `LoadingEffectToAddMacro` summarizes the addition of one [macro][A_Macro]
 * to a [method&#32;bundle][A_Bundle].
 *
 * @property bundle
 *   The [A_Bundle] for which a macro is being added by this effect.
 * @property macro
 *   The [A_Macro] being added by this effect.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `LoadingEffectToAddDefinition`.
 *
 * @param bundle
 *   The [A_Bundle] for which a macro is being added by this effect.
 * @param macro
 *   The [A_Macro] being added by this effect.
 */
internal class LoadingEffectToAddMacro constructor(
	internal val bundle: A_Bundle,
	internal val macro: A_Macro
) : LoadingEffect() {
	override fun writeEffectTo(writer: L1InstructionWriter)
	{
		val atom = bundle.message
		with(writer) {
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
				addLiteral(macro.prefixFunctions()))
			// Push the macro body function.
			write(
				0,
				L1Operation.L1_doPushLiteral,
				addLiteral(macro.bodyBlock()))
			// Call the macro definition method.
			write(
				0,
				L1Operation.L1_doCall,
				addLiteral(MACRO_DEFINER.bundle),
				addLiteral(Types.TOP.o))
		}
	}
}

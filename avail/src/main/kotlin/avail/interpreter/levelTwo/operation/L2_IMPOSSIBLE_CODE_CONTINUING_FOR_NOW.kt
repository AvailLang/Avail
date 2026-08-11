/*
 * L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operation

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.bottomRestriction
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator

/**
 * An [L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW] is similar to an
 * [L2_IMPOSSIBLE_CODE], but it doesn't alter control flow.  It gets rewritten
 * as an [L2_IMPOSSIBLE_CODE] during graph regeneration, and it must not be
 * present in the final graph.  Its purpose is to indicate that a condition
 * that should never occur has been detected, but in a situation that can't
 * simply produce an [L2_IMPOSSIBLE_CODE] directly, specifically while inserting
 * code retroactive during phi generation, since we don't want to deal with the
 * consequences of discovering a contradiction at that time, since we can't
 * readily change the target block's incoming edge list.
 *
 * When the instruction is added, it transforms the current manifest so that
 * *every* constraint is bottom-typed (i.e., impossible).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW
constructor(
): L2Instruction()
{
	override val isCold get() = true

	// We definitely don't want to quietly drop this kind of instruction.  We
	// also need it to be emitted right away, never postponed.
	override val hasSideEffect get() = true

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		// For safety, make everything in the manifest be impossible.
		manifest.synonymsArray().forEach { synonym ->
			manifest.setRestriction(
				synonym.pickSemanticValue(),
				bottomRestriction)
		}
	}

	override fun transformedByRegenerator(regenerator: L2Regenerator): L2Instruction
	{
		return L2_IMPOSSIBLE_CODE()
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		+L2_IMPOSSIBLE_CODE()
	}

	override fun JVMTranslator.translateToJVM()
	{
		error(
			"L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW instructions should have " +
				"been eliminated")
	}
}

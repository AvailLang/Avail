/*
 * L2_JUMP_IF_COMPARE_INT.kt
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

import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.new.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Jump to the target if int1 compares to int2 in the way requested by the
 * [numericComparator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * @property numericComparator
 *   The [NumericComparator] on which this [L2_JUMP_IF_COMPARE_INT] is based.
 */
class L2_JUMP_IF_COMPARE_INT(
	private val numericComparator: NumericComparator,
	var int1: L2ReadIntOperand,
	var int2: L2ReadIntOperand,
	@On(SUCCESS) var ifTrue: L2PcOperand,
	@On(FAILURE) var ifFalse: L2PcOperand
): L2NewConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)

		val restriction1 = int1.restriction().intersection(
			manifest.restrictionFor(int1.semanticValue()))
		val restriction2 = int2.restriction().intersection(
			manifest.restrictionFor(int2.semanticValue()))

		// Restrict both values along both branches.
		val (rest1, rest2, rest3, rest4) =
			numericComparator.computeRestrictions(
				restriction1.forBoxed(), restriction2.forBoxed()
			).map(TypeRestriction::forUnboxedInt)
		ifTrue.manifest().setRestriction(
			int1.semanticValue(),
			restriction1.intersection(rest1))
		ifTrue.manifest().setRestriction(
			int2.semanticValue(),
			restriction2.intersection(rest2))
		ifFalse.manifest().setRestriction(
			int1.semanticValue(),
			restriction1.intersection(rest3))
		ifFalse.manifest().setRestriction(
			int2.semanticValue(),
			restriction2.intersection(rest4))
	}

	override fun appendToWithWarnings(
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(int1.registerString())
		builder.append(" ")
		builder.append(numericComparator.comparatorName)
		builder.append(" ")
		builder.append(int2.registerString())
		renderOperandsExcludingFields(builder, ::int1, ::int2)
	}

	override fun toString(): String
	{
		return super.toString() + "(" + numericComparator.comparatorName + ")"
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator
	)
	{
		// Use the basic generator to check if the branch can be elided.
		regenerator.compareAndBranchInt(
			numericComparator, int1, int2, ifTrue, ifFalse)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (int1 op int2) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, int1.register())
		translator.load(method, int2.register())
		emitBranch(
			translator,
			method,
			this,
			numericComparator.opcode,
			ifTrue,
			ifFalse)
	}
}

/*
 * L2_JUMP_IF_OBJECTS_EQUAL.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2IsUnboxedIntCondition.Companion.unboxedIntCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Branch based on whether the two values are equal to each other.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_JUMP_IF_OBJECTS_EQUAL(
	var first: L2ReadBoxedOperand,
	var second: L2ReadBoxedOperand,
	@On(SUCCESS) var ifEqual: L2PcOperand,
	@On(FAILURE) var ifNotEqual: L2PcOperand
): L2ConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		// Merge the source and destination only along the ifEqual branch.
		ifEqual.manifest().mergeExistingSemanticValues(
			first.semanticValue(), second.semanticValue())
	}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(first.registerString())
		builder.append(" = ")
		builder.append(second.registerString())
		renderOperandsExcludingFields(builder, ::first, ::second)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (first.equals(second)) goto ifEqual;
		// :: else goto notEqual;
		translator.load(method, first.register())
		translator.load(method, second.register())
		A_BasicObject.equalsMethod.generateCall(method)
		emitBranch(
			translator, method, this, Opcodes.IFNE, ifEqual, ifNotEqual)
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		val manifest = regenerator.currentManifest
		val restriction1 = manifest.restrictionFor(first.semanticValue())
		val restriction2 = manifest.restrictionFor(second.semanticValue())
		if (restriction1.intersection(restriction2).isImpossible)
		{
			// The restrictions are disjoint, so the comparison is always false.
			// Jump unconditionally to the false case.
			regenerator.jumpTo(ifNotEqual.targetBlock())
			return
		}
		restriction1.constantOrNull?.let { c1 ->
			restriction2.constantOrNull?.let { c2 ->
				if (c1.equals(c2))
				{
					// The restrictions say the values are the same constant, so
					// it's always true.  Jump unconditionally to the true case.
					regenerator.jumpTo(ifEqual.targetBlock())
					return
				}
			}
		}
		if (!first.restriction().containedByType(i32)
			|| !second.restriction().containedByType(i32))
		{
			return super.emitTransformedInstruction(regenerator)
		}
		// The values are definitely ints, even if they're not necessarily both
		// (or either) in int registers.
		val unreachable = L2BasicBlock("should not reach")
		val int1Reg = regenerator.readInt(
			L2SemanticUnboxedInt(first.semanticValue()), unreachable)
		val int2Reg = regenerator.readInt(
			L2SemanticUnboxedInt(second.semanticValue()), unreachable)
		// Note that we *must not* reuse the manifests in the translated edges
		// ifTrue and ifFalse, since they might not include information about
		// registers freshly generated for int1Reg and int2Reg, which might have
		// had to be constructed from boxed forms.  In particular, there was a
		// case where a boxed value was unboxed (unconditionally), but removed
		// as dead code in the same pass that translated a downstream occurrence
		// of L2_JUMP_IF_OBJECT_EQUAL, which could be translated to an
		// L2_JUMP_IF_COMPARE_INT by the compareAndBranchInt() below.
		regenerator.compareAndBranchInt(
			NumericComparator.Equal,
			int1Reg,
			int2Reg,
			L2PcOperand(ifEqual.targetBlock(), ifEqual.isBackward),
			L2PcOperand(ifNotEqual.targetBlock(), ifNotEqual.isBackward))
		assert(!unreachable.currentlyReachable())
	}

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		val conditions = mutableListOf<L2SplitCondition?>()
		if (first.restriction().intersectsType(i32)
			&& second.restriction().intersectsType(i32))
		{
			conditions.add(
				unboxedIntCondition(
					listOf(first.register(), second.register())))
		}
		return conditions
	}
}

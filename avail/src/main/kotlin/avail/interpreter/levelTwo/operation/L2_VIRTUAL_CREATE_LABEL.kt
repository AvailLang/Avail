/*
 * L2_VIRTUAL_CREATE_LABEL.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must reain the above copyright notice, this
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

import avail.descriptor.functions.LabelDescriptor
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.HideInAllVisualizations
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2ControlFlowGraph.ZoneType
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator

/**
 * This is a placeholder instruction, which is replaced if still live after data
 * flow optimizations by:
 *
 * - ensuring the caller is reified.  If we're not already in a reification
 * [L2ControlFlowGraph.Zone], generate the following, with a dynamic
 * [L2_JUMP_IF_ALREADY_REIFIED] check to skip past it:
 *   1.  [L2_REIFY],
 *   1.  [L2_ENTER_L2_CHUNK] (start of reification area),
 *   1.  [L2_SAVE_ALL_AND_PC_TO_INT], falling through to
 *   1.  [L2_GET_CURRENT_CONTINUATION],
 *   1.  [L2_GET_CURRENT_FUNCTION],
 *   1.  [L2_CREATE_CONTINUATION],
 *   1.  [L2_RETURN_FROM_REIFICATION_HANDLER], then outside the reification
 *       area,
 *   1.  [L2_ENTER_L2_CHUNK].
 *
 * - [L2_GET_CURRENT_CONTINUATION], if the reified caller isn't already
 *   visible,
 * - [L2_CREATE_LABEL], which builds the [label][LabelDescriptor] from the
 *   function and the reified caller.
 *
 * A label records nothing else, so no registers or level two entry point are
 * saved for it.  Restarting it invokes the function afresh.
 *
 * @property code
 *   The [A_RawFunction] that is known statically to be referenced by the
 *   [function].
 * @property function
 *   The [A_Function] whose invocation the label names.
 * @property outputLabel
 *   Where to write the new label.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_VIRTUAL_CREATE_LABEL
constructor(
	@HideInAllVisualizations
	var code: L2ConstantOperand,
	@HideInAllVisualizations
	var function: L2ReadBoxedOperand,
	var outputLabel: L2WriteBoxedOperand
): L2Instruction()
{
	override val isPlaceholder get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" ").append(outputLabel)
		append("\n\tfunction = ").append(
			when (val f = function.register().constant)
			{
				null -> function.toString()
				else -> f.code().methodName
			})
	}

	override fun L2Regenerator.generateReplacement(
		originalInstruction: L2Instruction)
	{
		// Make sure this instruction didn't migrate into (and get lodged in) a
		// multi-way dispatch zone.
		if (currentBlock().zone == null)
		{
			// Force the caller to be reified.  Use a dummy continuation
			// that only captures L2 state, since it can't become invalid or
			// be seen by L1 code before it resumes.
			val zone = ZoneType.BEGIN_REIFICATION_FOR_LABEL.createZone(
				"Reify caller for label")
			val alreadyReifiedEdgeSplit =
				createBasicBlock("already reified (edge split)")
			val startReification =
				createBasicBlock("start reification")
			val onReification =
				createBasicBlock("on reification", zone)
			val reificationOfframp =
				createBasicBlock("reification off-ramp", zone)
			val afterReification =
				createBasicBlock("after reification")
			val callerIsReified =
				createBasicBlock("caller is reified")
			val unreachable =
				createBasicBlock("unreachable")
			+L2_JUMP_IF_ALREADY_REIFIED(
				edgeTo(alreadyReifiedEdgeSplit),
				edgeTo(startReification))

			startBlock(alreadyReifiedEdgeSplit)
			jumpTo(callerIsReified)

			startBlock(startReification)
			+L2_REIFY(
				L2ConstantOperand(
					stringFrom("Reification for label creation in L2: "
						+ topFrame.codeName.replace('\n', ' '))),
				edgeTo(onReification))

			startBlock(onReification)
			+L2_ENTER_L2_CHUNK(
				L2IntImmediateOperand(DefaultEntryPoint.TRANSIENT.offset),
				L2CommentOperand("Transient, cannot be invalid."))
			val tempOffset = intWriteTemp(
				"offset to continue dummy", restrictionForType(i32))
			val tempRegisterDump = boxedWriteTemp(
				"dump for dummy continuation",
				restrictionForType(Types.OTHER_NONTYPE()))
			// Since this is a dummy continuation being constructed, it can't
			// become immutable or shared, so we don't have to worry about
			// capturing any elided variable values.
			+L2_SAVE_ALL_AND_PC_TO_INT(
				ifFallThrough = edgeTo(reificationOfframp),
				reference = edgeTo(afterReification),
				referenceOffset = tempOffset,
				registerDump = tempRegisterDump,
				finalSavedBoxedRegisters =
					L2ReadBoxedVectorOperand(emptyList()))

			startBlock(reificationOfframp)
			val tempCaller = boxedWrite(
				setOf(topFrame.reifiedCaller()),
				restrictionForType(Types.CONTINUATION()))
			val readCurrentFunction = currentFunction(
				topFrame,
				function.restriction.constantOrNull,
				function.restriction.type)
			val dummyContinuation = boxedWriteTemp(
				"dummy continuation",
				restrictionForType(Types.CONTINUATION()))
			+L2_GET_CURRENT_CONTINUATION(tempCaller)
			val dummyCreated = createBasicBlock(
				"return dummy continuation", zone)
			+L2_CREATE_CONTINUATION(
				function = readCurrentFunction,
				code = code,
				caller = readBoxed(tempCaller),
				levelOnePc = L2IntImmediateOperand(Int.MAX_VALUE),
				levelOneStackp = L2IntImmediateOperand(Int.MAX_VALUE),
				slotValues = L2ReadBoxedVectorOperand(emptyList()),
				destination = dummyContinuation,
				labelAddress = readIntNoFail(tempOffset.pickSemanticValue()),
				registerDump = readBoxed(tempRegisterDump),
				ifFallThrough = edgeTo(dummyCreated),
				freeze = L2PcVectorOperand(emptyList()),
				comment = L2CommentOperand("Dummy reification continuation."))
			startBlock(dummyCreated)
			+L2_RETURN_FROM_REIFICATION_HANDLER(readBoxed(dummyContinuation))

			startBlock(unreachable)
			+L2_UNREACHABLE_CODE()

			startBlock(afterReification)
			+L2_ENTER_L2_CHUNK(
				L2IntImmediateOperand(DefaultEntryPoint.TRANSIENT.offset),
				L2CommentOperand("Transient, cannot be invalid."))
			jumpTo(callerIsReified)

			startBlock(callerIsReified)
		}
		// Caller has been reified, or is known to already be reified.
		val caller = topFrame.reifiedCaller()
		if (!currentManifest.hasSemanticValue(caller))
		{
			+L2_GET_CURRENT_CONTINUATION(
				boxedWrite(
					setOf(caller),
					restrictionForType(Types.CONTINUATION())))
		}
		+L2_CREATE_LABEL(function, readBoxed(caller), outputLabel)
	}

	override fun JVMTranslator.translateToJVM()
	{
		throw UnsupportedOperationException(
			"${javaClass.simpleName} should " +
				"have been replaced during optimization")
	}
}

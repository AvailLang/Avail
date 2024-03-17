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

import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk.ChunkEntryPoint
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2ControlFlowGraph.ZoneType
import avail.optimizer.L2Generator
import avail.optimizer.L2Generator.Companion.backEdgeTo
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface.SpecialBlock.AFTER_OPTIONAL_PRIMITIVE
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.performance.Statistic
import avail.performance.StatisticReport.REIFICATIONS
import org.objectweb.asm.MethodVisitor

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
 *   1.  [L2_SET_CONTINUATION],
 *   1.  [L2_RETURN_FROM_REIFICATION_HANDLER], then outside the reification
 *       area,
 *   1.  [L2_ENTER_L2_CHUNK].
 *
 * - [L2_SAVE_ALL_AND_PC_TO_INT], falling through to
 * - [L2_GET_CURRENT_FUNCTION] (or [L2_MOVE_CONSTANT]), if the current
 *     function isn't already visible,
 * - [L2_GET_CURRENT_CONTINUATION]
 * - [L2_CREATE_CONTINUATION].
 * - The target of the [L2_SAVE_ALL_AND_PC_TO_INT] is in another block, which
 *   contains an unconditional [L2_JUMP_BACK] to the
 *   [SpecialBlock.AFTER_OPTIONAL_PRIMITIVE].
 *
 * The second [L2_SAVE_ALL_AND_PC_TO_INT] captures the offset of the
 * [L2Generator]'s [L2_ENTER_L2_CHUNK] entry point at
 * [SpecialBlock.AFTER_OPTIONAL_PRIMITIVE].  The register dump is fed to the
 * [L2_CREATE_CONTINUATION], so that when the continuation is restarted as a
 * label, it will restore these values into registers.
 *
 * The [L2_CREATE_CONTINUATION]'s level one pc is set to zero to indicate this
 * is a label.  The stack is empty, and only the arguments, function, and caller
 * are recorded – for level one.  For level two, it also captures the register
 * dump and the level two offset of the entry point above.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_VIRTUAL_CREATE_LABEL : L2Operation(
	WRITE_BOXED.named("output label"),
	READ_BOXED.named("immutable function"),
	READ_BOXED_VECTOR.named("arguments"),
	INT_IMMEDIATE.named("frame size"))
{
	override val isPlaceholder get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val outputLabel = instruction.operand<L2WriteBoxedOperand>(0)
		val function = instruction.operand<L2ReadBoxedOperand>(1)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(2)
		val frameSize = instruction.operand<L2IntImmediateOperand>(3)
		renderPreamble(instruction, builder)
		builder.append(" ").append(outputLabel)
		builder.append("\n\tfunction = ").append(function)
		builder.append("\n\targuments = ").append(arguments)
		builder.append("\n\tframeSize = ").append(frameSize)
	}

	override fun generateReplacement(
		instruction: L2Instruction,
		regenerator: L2Regenerator
	): Unit = regenerator.run {
		assert(this@L2_VIRTUAL_CREATE_LABEL == instruction.operation)
		val labelOutput = transformOperand(
			instruction.operand<L2WriteBoxedOperand>(0))
		val function = transformOperand(
			instruction.operand<L2ReadBoxedOperand>(1))
		val arguments = transformOperand(
			instruction.operand<L2ReadBoxedVectorOperand>(2))
		val frameSize = instruction.operand<L2IntImmediateOperand>(3)
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
			addInstruction(
				L2_JUMP_IF_ALREADY_REIFIED,
				edgeTo(alreadyReifiedEdgeSplit),
				edgeTo(startReification))

			startBlock(alreadyReifiedEdgeSplit)
			jumpTo(callerIsReified)

			startBlock(startReification)
			addInstruction(
				L2_REIFY,
				L2IntImmediateOperand(1),
				L2IntImmediateOperand(0),
				L2ArbitraryConstantOperand(
					Statistic(
						REIFICATIONS,
						"Reification for label creation in L2: "
							+ topFrame.codeName.replace('\n', ' '))),
				edgeTo(onReification))

			startBlock(onReification)
			addInstruction(
				L2_ENTER_L2_CHUNK,
				L2IntImmediateOperand(
					ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
				L2CommentOperand("Transient, cannot be invalid."))
			val tempOffset = intWriteTemp(
				intRestrictionForType(i32))
			val tempRegisterDump = boxedWriteTemp(
				boxedRestrictionForType(Types.ANY.o))
			addInstruction(
				L2_SAVE_ALL_AND_PC_TO_INT,
				edgeTo(afterReification),
				tempOffset,
				tempRegisterDump,
				edgeTo(reificationOfframp))

			startBlock(reificationOfframp)
			val tempCaller = boxedWrite(
				setOf(topFrame.reifiedCaller()),
				boxedRestrictionForType(mostGeneralContinuationType))
			val tempFunction = boxedWrite(
				setOf(topFrame.function()),
				boxedRestrictionForType(mostGeneralFunctionType()))
			val dummyContinuation = boxedWriteTemp(
				boxedRestrictionForType(mostGeneralContinuationType))
			addInstruction(
				L2_GET_CURRENT_CONTINUATION,
				tempCaller)
			addInstruction(
				L2_GET_CURRENT_FUNCTION,
				tempFunction)
			addInstruction(
				L2_CREATE_CONTINUATION,
				readBoxed(tempFunction),
				readBoxed(tempCaller),
				L2IntImmediateOperand(Int.MAX_VALUE),
				L2IntImmediateOperand(Int.MAX_VALUE),
				L2ReadBoxedVectorOperand(emptyList()),
				dummyContinuation,
				readInt(tempOffset.onlySemanticValue(), unreachable),
				readBoxed(tempRegisterDump),
				L2CommentOperand("Dummy reification continuation."))
			addInstruction(
				L2_SET_CONTINUATION,
				readBoxed(dummyContinuation))
			addInstruction(
				L2_RETURN_FROM_REIFICATION_HANDLER)

			startBlock(unreachable)
			addInstruction(L2_UNREACHABLE_CODE)

			startBlock(afterReification)
			addInstruction(
				L2_ENTER_L2_CHUNK,
				L2IntImmediateOperand(
					ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
				L2CommentOperand("Transient, cannot be invalid."))
			jumpTo(callerIsReified)

			startBlock(callerIsReified)
		}
		// Caller has been reified, or is known to already be reified.
		val tempCallerWrite = boxedWriteTemp(
			boxedRestrictionForType(mostGeneralContinuationType))
		addInstruction(L2_GET_CURRENT_CONTINUATION, tempCallerWrite)

		val fallThrough = createBasicBlock(
			"Fall-through for label creation",
			currentBlock().zone)
		val writeOffset = intWriteTemp(intRestrictionForType(i32))
		val writeRegisterDump =
			boxedWriteTemp(boxedRestrictionForType(Types.ANY.o))
		addInstruction(
			L2_SAVE_ALL_AND_PC_TO_INT,
			backEdgeTo(specialBlocks[AFTER_OPTIONAL_PRIMITIVE]!!),
			writeOffset,
			writeRegisterDump,
			edgeTo(fallThrough))

		// Force there to be nothing considered live in the edge leading to the
		// label's entry point.
		val saveInstruction = currentBlock().instructions().last()
		val referenceEdge: L2PcOperand =
			L2_SAVE_ALL_AND_PC_TO_INT.referenceOf(saveInstruction)
		referenceEdge.forcedClampedEntities = mutableSetOf()

		startBlock(fallThrough)
		val frameSizeInt = frameSize.value
		val slots = arguments.elements.toMutableList()
		val nilRead = boxedConstant(nil)
		repeat(frameSizeInt - slots.size) { slots.add(nilRead) }
		addInstruction(
			L2_CREATE_CONTINUATION,
			function,
			readBoxed(tempCallerWrite),
			L2IntImmediateOperand(0),  // indicates a label.
			L2IntImmediateOperand(frameSizeInt + 1),  // empty stack
			L2ReadBoxedVectorOperand(slots),  // each immutable
			labelOutput,
			readInt(
				writeOffset.onlySemanticValue(),
				unreachablePcOperand().targetBlock()),
			readBoxed(writeRegisterDump),
			L2CommentOperand("Create label."))
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		throw UnsupportedOperationException(
			"${javaClass.simpleName} should " +
				"have been replaced during optimization")
	}
}

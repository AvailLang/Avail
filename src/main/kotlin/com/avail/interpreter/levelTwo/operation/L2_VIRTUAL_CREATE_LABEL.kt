/*
 * L2_VIRTUAL_CREATE_LABEL.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.types.ContinuationTypeDescriptor
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2CommentOperand
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory
import com.avail.optimizer.L2ControlFlowGraph
import com.avail.optimizer.L2ControlFlowGraph.ZoneType
import com.avail.optimizer.L2Generator
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.utility.PrefixSharingList
import org.objectweb.asm.MethodVisitor
import java.util.*

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
 *   1.  [L2_RETURN_FROM_REIFICATION_HANDLER], then outside the reification area,
 *   1.  [L2_ENTER_L2_CHUNK].
 *
 * - [L2_SAVE_ALL_AND_PC_TO_INT], falling through to
 * - [L2_GET_CURRENT_FUNCTION] (or [L2_MOVE_CONSTANT]), if the current
 *     function isn't already visible,
 * - [L2_MAKE_IMMUTABLE] for the current function,
 * - [L2_GET_CURRENT_CONTINUATION]
 * - [L2_MAKE_IMMUTABLE] for the current caller,
 * - [L2_CREATE_CONTINUATION].
 * - The target of the [L2_SAVE_ALL_AND_PC_TO_INT] is in another block, which
 *  contains an unconditional [L2_JUMP_BACK] to the
 * [L2Generator.afterOptionalInitialPrimitiveBlock].
 *
 * The second [L2_SAVE_ALL_AND_PC_TO_INT] captures the offset of the
 * [L2Generator]'s [L2_ENTER_L2_CHUNK] entry point at
 * [L2Generator.afterOptionalInitialPrimitiveBlock].  The register dump is
 * fed to the [L2_CREATE_CONTINUATION], so that when the continuation is
 * restarted as a label, it will restore these values into registers.
 *
 * The [L2_CREATE_CONTINUATION]'s level one pc is set to zero to
 * indicate this is a label.  The stack is empty, and only the arguments,
 * function, and caller are recorded – for level one.  For level two, it also
 * captures the register dump and the level two offset of the entry point above.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_VIRTUAL_CREATE_LABEL : L2Operation(
	L2OperandType.WRITE_BOXED.named("output label"),
	L2OperandType.READ_BOXED.named("immutable function"),
	L2OperandType.READ_BOXED_VECTOR.named("arguments"),
	L2OperandType.INT_IMMEDIATE.named("frame size"))
{
	override val isPlaceholder: Boolean
		get() = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val outputLabel =
			instruction.operand<L2WriteBoxedOperand>(0)
		val function =
			instruction.operand<L2ReadBoxedOperand>(1)
		val arguments =
			instruction.operand<L2ReadBoxedVectorOperand>(2)
		val frameSize =
			instruction.operand<L2IntImmediateOperand>(3)
		renderPreamble(instruction, builder)
		builder.append(" ").append(outputLabel)
		builder.append("\n\tfunction = ").append(function)
		builder.append("\n\targuments = ").append(arguments)
		builder.append("\n\tframeSize = ").append(frameSize)
	}

	override fun generateReplacement(
		instruction: L2Instruction,
		generator: L2Generator)
	{
		assert(this == instruction.operation())
		val outputLabel =
			instruction.operand<L2WriteBoxedOperand>(0)
		val function =
			instruction.operand<L2ReadBoxedOperand>(1)
		val arguments =
			instruction.operand<L2ReadBoxedVectorOperand>(2)
		val frameSize =
			instruction.operand<L2IntImmediateOperand>(3)
		val reifiedCaller: L2ReadBoxedOperand
		reifiedCaller = if (generator.currentBlock().zone !== null)
		{
			// We're in a reification zone, so our caller has already been
			// reified.
			val tempWrite =
				generator.boxedWriteTemp(TypeRestriction.restrictionForType(
					ContinuationTypeDescriptor.mostGeneralContinuationType(),
					RestrictionFlagEncoding.BOXED))
			generator.addInstruction(
				L2_GET_CURRENT_CONTINUATION, tempWrite)
			generator.readBoxed(tempWrite)
		}
		else
		{
			// Force the caller to be reified.  Use a dummy continuation that
			// only captures L2 state, since it can't become invalid or be seen
			// by L1 code before it resumes.
			val zone =
				ZoneType.BEGIN_REIFICATION_FOR_LABEL.createZone(
					"Reify caller for label")
			val alreadyReifiedEdgeSplit =
				generator.createBasicBlock("already reified (edge split)")
			val startReification =
				generator.createBasicBlock("start reification")
			val onReification =
				generator.createBasicBlock("on reification", zone)
			val reificationOfframp =
				generator.createBasicBlock("reification off-ramp", zone)
			val afterReification =
				generator.createBasicBlock("after reification")
			val callerIsReified =
				generator.createBasicBlock("caller is reified")
			generator.addInstruction(
				L2_JUMP_IF_ALREADY_REIFIED,
				L2Generator.edgeTo(alreadyReifiedEdgeSplit),
				L2Generator.edgeTo(startReification))
			generator.startBlock(alreadyReifiedEdgeSplit)
			generator.jumpTo(callerIsReified)
			generator.startBlock(startReification)
			generator.addInstruction(
				L2_REIFY,
				L2IntImmediateOperand(1),
				L2IntImmediateOperand(0),
				L2IntImmediateOperand(
					StatisticCategory.PUSH_LABEL_IN_L2.ordinal),
				L2Generator.edgeTo(onReification))
			generator.startBlock(onReification)
			generator.addInstruction(
				L2_ENTER_L2_CHUNK,
				L2IntImmediateOperand(
					ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
				L2CommentOperand(
					"Transient, cannot be invalid."))
			val tempOffset = generator.intWriteTemp(
				TypeRestriction.restrictionForType(
					IntegerRangeTypeDescriptor.int32,
					RestrictionFlagEncoding.UNBOXED_INT))
			val tempRegisterDump =
				generator.boxedWriteTemp(TypeRestriction.restrictionForType(
					TypeDescriptor.Types.ANY.o, RestrictionFlagEncoding.BOXED))
			generator.addInstruction(
				L2_SAVE_ALL_AND_PC_TO_INT,
				L2Generator.edgeTo(afterReification),
				tempOffset,
				tempRegisterDump,
				L2Generator.edgeTo(reificationOfframp))
			generator.startBlock(reificationOfframp)
			val tempCaller = generator.boxedWrite(
				generator.topFrame.reifiedCaller(),
				TypeRestriction.restrictionForType(
					ContinuationTypeDescriptor.mostGeneralContinuationType(),
					RestrictionFlagEncoding.BOXED))
			val tempFunction = generator.boxedWrite(
				generator.topFrame.function(),
				TypeRestriction.restrictionForType(
					FunctionTypeDescriptor.mostGeneralFunctionType(),
					RestrictionFlagEncoding.BOXED))
			val dummyContinuation =
				generator.boxedWriteTemp(TypeRestriction.restrictionForType(
					ContinuationTypeDescriptor.mostGeneralContinuationType(),
					RestrictionFlagEncoding.BOXED))
			generator.addInstruction(
				L2_GET_CURRENT_CONTINUATION,
				tempCaller)
			generator.addInstruction(
				L2_GET_CURRENT_FUNCTION,
				tempFunction)
			generator.addInstruction(
				L2_CREATE_CONTINUATION,
				generator.readBoxed(tempFunction),
				generator.readBoxed(tempCaller),
				L2IntImmediateOperand(Int.MAX_VALUE),
				L2IntImmediateOperand(Int.MAX_VALUE),
				L2ReadBoxedVectorOperand(emptyList()),
				dummyContinuation,
				generator.readInt(
					tempOffset.onlySemanticValue(),
					generator.unreachablePcOperand().targetBlock()),
				generator.readBoxed(tempRegisterDump),
				L2CommentOperand("Dummy reification continuation."))
			generator.addInstruction(
				L2_SET_CONTINUATION,
				generator.readBoxed(dummyContinuation))
			generator.addInstruction(
				L2_RETURN_FROM_REIFICATION_HANDLER)
			generator.startBlock(afterReification)
			generator.addInstruction(
				L2_ENTER_L2_CHUNK,
				L2IntImmediateOperand(
					ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
				L2CommentOperand(
					"Transient, cannot be invalid."))
			generator.jumpTo(callerIsReified)
			generator.startBlock(callerIsReified)
			// Caller has been reified, or checked to be already reified.
			val tempWrite =
				generator.boxedWriteTemp(TypeRestriction.restrictionForType(
					ContinuationTypeDescriptor.mostGeneralContinuationType(),
					RestrictionFlagEncoding.BOXED))
			generator.addInstruction(
				L2_GET_CURRENT_CONTINUATION,
				tempWrite)
			generator.readBoxed(tempWrite)
		}
		val fallThrough = generator.createBasicBlock(
			"Fall-through for label creation",
			generator.currentBlock().zone)
		val writeOffset = generator.intWriteTemp(
			TypeRestriction.restrictionForType(
				IntegerRangeTypeDescriptor.int32,
				RestrictionFlagEncoding.UNBOXED_INT))
		val writeRegisterDump =
			generator.boxedWriteTemp(TypeRestriction.restrictionForType(
				TypeDescriptor.Types.ANY.o, RestrictionFlagEncoding.BOXED))
		generator.addInstruction(
			L2_SAVE_ALL_AND_PC_TO_INT,
			L2Generator.backEdgeTo(generator.afterOptionalInitialPrimitiveBlock),
			writeOffset,
			writeRegisterDump,
			L2Generator.edgeTo(fallThrough))

		// Force there to be nothing considered live in the edge leading to the
		// label's entry point.
		val saveInstruction =
			PrefixSharingList.last(generator.currentBlock().instructions())
		val referenceEdge: L2PcOperand =
			L2_SAVE_ALL_AND_PC_TO_INT.referenceOf(saveInstruction)
		referenceEdge.forcedClampedEntities = mutableSetOf()
		generator.startBlock(fallThrough)
		val frameSizeInt = frameSize.value
		val slots: MutableList<L2ReadBoxedOperand> =
			ArrayList(arguments.elements())
		val nilRead =
			generator.boxedConstant(NilDescriptor.nil)
		while (slots.size < frameSizeInt)
		{
			slots.add(nilRead)
		}
		generator.addInstruction(
			L2_CREATE_CONTINUATION,
			generator.makeImmutable(function),
			generator.makeImmutable(reifiedCaller),
			L2IntImmediateOperand(0),  // indicates a label.
			L2IntImmediateOperand(frameSizeInt + 1),  // empty stack
			L2ReadBoxedVectorOperand(slots),  // each immutable
			outputLabel,
			generator.readInt(
				writeOffset.onlySemanticValue(),
				generator.unreachablePcOperand().targetBlock()),
			generator.readBoxed(writeRegisterDump),
			L2CommentOperand("Create label."))
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		throw UnsupportedOperationException(
			"${this@L2_VIRTUAL_CREATE_LABEL.javaClass.simpleName} should " +
				"have been replaced during optimization")
	}
}

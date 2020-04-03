/*
 * L2_VIRTUAL_REIFY.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2CommentOperand;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operation.L2_REIFY.StatisticCategory;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2ControlFlowGraph.Zone;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.types.ContinuationTypeDescriptor.mostGeneralContinuationType;
import static com.avail.descriptor.types.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static com.avail.optimizer.L2ControlFlowGraph.ZoneType.BEGIN_REIFICATION_FOR_LABEL;
import static com.avail.optimizer.L2Generator.edgeTo;
import static java.util.Collections.emptyList;

/**
 * This is a placeholder instruction, which is replaced if still live after data
 * flow optimizations by:
 *
 * <ol>
 *    <li>{@link L2_REIFY},</li>
 *    <li>{@link L2_ENTER_L2_CHUNK} (start of reification area),</li>
 *    <li>{@link L2_SAVE_ALL_AND_PC_TO_INT}, falling through to</li>
 *    <li>{@link L2_GET_CURRENT_CONTINUATION},</li>
 *    <li>{@link L2_CREATE_CONTINUATION},</li>
 *    <li>{@link L2_SET_CONTINUATION},</li>
 *    <li>{@link L2_RETURN_FROM_REIFICATION_HANDLER}, then outside the
 *        reification area,</li>
 *    <li>{@link L2_ENTER_L2_CHUNK}.</li>
 *    <li>{@link L2_GET_CURRENT_CONTINUATION}, which gets the reified
 *        caller.</li>
 * </ol>
 *
 * <p>The {@link L2_CREATE_CONTINUATION}'s level one pc is set to an arbitrary
 * value, since it isn't used or ever exposed to level one code.</p>
 *
 * <p>This instruction is allowed to commute past any other instructions except
 * {@link L2_SAVE_ALL_AND_PC_TO_INT}.  Since these usually end up in off-ramps,
 * where reification is already happening, we're often able to postpone the
 * reification until we're already in an off-ramp, which is expected to be a low
 * frequency path in the {@link L2ControlFlowGraph}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@WritesHiddenVariable({
	// potentially clobbered
//	CURRENT_ARGUMENTS.class,
	// potentially clobbered
	LATEST_RETURN_VALUE.class,
	// potentially clobbered
//	STACK_REIFIER.class
})
public final class L2_VIRTUAL_REIFY
extends L2Operation
{
	/**
	 * Construct an {@code L2_VIRTUAL_REIFY}.
	 */
	private L2_VIRTUAL_REIFY ()
	{
		super(
			COMMENT.is("usage comment"),
			WRITE_BOXED.is("reified caller"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_VIRTUAL_REIFY instance =
		new L2_VIRTUAL_REIFY();

	@Override
	public boolean isPlaceholder ()
	{
		return true;
	}

	/**
	 * Extract the reified caller {@link L2WriteBoxedOperand} from this virtual
	 * reification instruction.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to examine, which must have this class
	 *        as its operation.
	 * @return The {@link L2WriteBoxedOperand} into which the calling
	 *         continuation will be written.
	 */
	public static L2WriteBoxedOperand callerWriteOperandOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		return instruction.operand(1);
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		// final L2CommentOperand usageComment = instruction.operand(0);
		final L2WriteBoxedOperand reifiedCaller = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(" ");
		builder.append(reifiedCaller);
	}

	@Override
	public void generateReplacement (
		final L2Instruction instruction, final L2Generator generator)
	{
		final L2WriteBoxedOperand originalRegisterWrite =
			callerWriteOperandOf(instruction);
		if (generator.currentBlock().zone != null)
		{
			// The instruction is already within a reification zone, so the
			// caller is necessarily already reified.  Just get it.
			generator.addInstruction(
				L2_GET_CURRENT_CONTINUATION.instance,
				originalRegisterWrite);
			return;
		}

		// Force reification of a dummy continuation that captures all live
		// register state.  When reification of the whole stack has completed,
		// resume it, which reenters this chunk at an L2_ENTER_L2_CHUNK which
		// explodes the register dump back into the registers.  Then fetch the
		// reified caller and continue running right after where the original
		// placeholder instruction was.

		final Zone zone =
			BEGIN_REIFICATION_FOR_LABEL.createZone("Reify caller");
		final L2BasicBlock alreadyReifiedEdgeSplit = generator.createBasicBlock(
			"already reified (edge split)");
		final L2BasicBlock startReification = generator.createBasicBlock(
			"start reification");
		final L2BasicBlock onReification = generator.createBasicBlock(
			"on reification", zone);
		final L2BasicBlock reificationOfframp = generator.createBasicBlock(
			"reification off-ramp", zone);
		final L2BasicBlock afterReification = generator.createBasicBlock(
			"after reification");
		final L2BasicBlock callerIsReified = generator.createBasicBlock(
			"caller is reified");

		generator.addInstruction(
			L2_JUMP_IF_ALREADY_REIFIED.instance,
			edgeTo(alreadyReifiedEdgeSplit),
			edgeTo(startReification));

		generator.startBlock(alreadyReifiedEdgeSplit);
		generator.addInstruction(
			L2_JUMP.instance,
			edgeTo(callerIsReified));

		generator.startBlock(startReification);
		generator.addInstruction(
			L2_REIFY.instance,
			new L2IntImmediateOperand(1),
			new L2IntImmediateOperand(0),
			new L2IntImmediateOperand(
				StatisticCategory.PUSH_LABEL_IN_L2.ordinal()),
			edgeTo(onReification));

		generator.startBlock(onReification);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient, cannot be invalid."));
		final L2WriteIntOperand tempOffset = generator.intWriteTemp(
			restrictionForType(int32(), UNBOXED_INT));
		final L2WriteBoxedOperand tempRegisterDump = generator.boxedWriteTemp(
			restrictionForType(ANY.o(), BOXED));
		generator.addInstruction(
			L2_SAVE_ALL_AND_PC_TO_INT.instance,
			edgeTo(afterReification),
			tempOffset,
			tempRegisterDump,
			edgeTo(reificationOfframp));

		generator.startBlock(reificationOfframp);
		final L2WriteBoxedOperand tempCaller = generator.boxedWrite(
			generator.topFrame.reifiedCaller(),
			restrictionForType(mostGeneralContinuationType(), BOXED));
		final L2WriteBoxedOperand tempFunction = generator.boxedWrite(
			generator.topFrame.function(),
			restrictionForType(mostGeneralFunctionType(), BOXED));
		final L2WriteBoxedOperand dummyContinuation = generator.boxedWriteTemp(
			restrictionForType(mostGeneralContinuationType(), BOXED));
		generator.addInstruction(
			L2_GET_CURRENT_CONTINUATION.instance,
			tempCaller);
		generator.addInstruction(
			L2_GET_CURRENT_FUNCTION.instance,
			tempFunction);
		generator.addInstruction(
			L2_CREATE_CONTINUATION.instance,
			generator.readBoxed(tempFunction),
			generator.readBoxed(tempCaller),
			new L2IntImmediateOperand(Integer.MAX_VALUE),
			new L2IntImmediateOperand(Integer.MAX_VALUE),
			new L2ReadBoxedVectorOperand(emptyList()),
			dummyContinuation,
			generator.readInt(
				tempOffset.onlySemanticValue(),
				generator.unreachablePcOperand().targetBlock()),
			generator.readBoxed(tempRegisterDump),
			new L2CommentOperand("Dummy reification continuation."));
		generator.addInstruction(
			L2_SET_CONTINUATION.instance,
			generator.readBoxed(dummyContinuation));
		generator.addInstruction(L2_RETURN_FROM_REIFICATION_HANDLER.instance);

		generator.startBlock(afterReification);
		generator.addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2IntImmediateOperand(
				ChunkEntryPoint.TRANSIENT.offsetInDefaultChunk),
			new L2CommentOperand(
				"Transient, cannot be invalid."));
		generator.addInstruction(
			L2_JUMP.instance,
			edgeTo(callerIsReified));

		generator.startBlock(callerIsReified);
		generator.addInstruction(
			L2_GET_CURRENT_CONTINUATION.instance,
			originalRegisterWrite);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		throw new RuntimeException(
			getClass().getSimpleName()
				+ " should have been replaced during optimization");
	}
}

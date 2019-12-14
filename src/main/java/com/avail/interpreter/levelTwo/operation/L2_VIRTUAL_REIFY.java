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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;

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
 * where reification is already happening, TODO We have to be careful.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_VIRTUAL_REIFY
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_CONTINUATION}.
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
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
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
		// Replace it with real reification code.
		System.out.println("NOT YET IMPLEMENTED");


//
//		// Force reification of the current continuation and all callers, then
//		// resume that continuation right away, which also makes it available.
//		// Create a new continuation like it, with only the caller, function,
//		// and arguments present, and having an empty stack and an L1 pc of 0.
//		// Then push that new continuation on the virtual stack.
//		final int oldStackp = stackp;
//		// The initially constructed continuation is always immediately resumed,
//		// so this should never be observed.
//		stackp = Integer.MAX_VALUE;
//
//		getCurrentContinuationAs();
//
//
//***TODO Move to optimization phase.
//
//
//		final L2BasicBlock onReification = generator.createBasicBlock(
//			"on reification",
//			BEGIN_REIFICATION_FOR_LABEL.createZone(
//				"Reify caller for pushing label"));
//		addInstruction(
//			L2_REIFY.instance,
//			new L2IntImmediateOperand(1),
//			new L2IntImmediateOperand(0),
//			new L2IntImmediateOperand(
//				StatisticCategory.PUSH_LABEL_IN_L2.ordinal()),
//			edgeTo(onReification));
//
//		generator.startBlock(onReification);
//		generator.addInstruction(
//			L2_ENTER_L2_CHUNK.instance,
//			new L2IntImmediateOperand(TRANSIENT.offsetInDefaultChunk),
//			new L2CommentOperand(
//				"Transient, before label creation - cannot be invalid."));
//		reify(null, UNREACHABLE);
//		// We just continued the reified continuation, having exploded the
//		// continuation into slot registers.  Create a label based on it, but
//		// capturing only the arguments (with pc=0, stack=empty).
//		generator.addInstruction(L2_JUMP.instance, edgeTo(callerIsReified));
//
//		generator.startBlock(callerIsReified);
//		assert code.primitive() == null;
//		final int numArgs = code.numArgs();
//		final List<L2ReadBoxedOperand> slotsForLabel =
//			new ArrayList<>(numSlots);
//		for (int i = 1; i <= numArgs; i++)
//		{
//			slotsForLabel.add(generator.makeImmutable(readSlot(i)));
//		}
//		final L2ReadBoxedOperand nilTemp = generator.boxedConstant(nil);
//		for (int i = numArgs + 1; i <= numSlots; i++)
//		{
//			slotsForLabel.add(nilTemp);  // already immutable
//		}
//		// Now create the actual label continuation and push it.
//		stackp = oldStackp - 1;
//		final A_Type continuationType =
//			continuationTypeForFunctionType(code.functionType());
//		final L2SemanticValue label = topFrame().label();
//		final L2WriteBoxedOperand destinationRegister =
//			generator.boxedWrite(label, restriction(continuationType, null));
//
//		// Now generate the reification instructions, ensuring that when
//		// returning into the resulting continuation it will enter a block where
//		// the slot registers are the new ones we just created.
//		final L2WriteIntOperand writeOffset = generator.intWriteTemp(
//			restrictionForType(int32(), UNBOXED_INT));
//		final L2WriteBoxedOperand writeRegisterDump = generator.boxedWriteTemp(
//			restrictionForType(ANY.o(), BOXED));
//
//		final L2BasicBlock fallThrough =
//			generator.createBasicBlock("Caller is reified");
//		addInstruction(
//			L2_SAVE_ALL_AND_PC_TO_INT.instance,
//			edgeTo(fallThrough),
//			backEdgeTo(generator.afterOptionalInitialPrimitiveBlock),
//			writeOffset,
//			writeRegisterDump);
//
//		generator.startBlock(fallThrough);
//		addInstruction(
//			L2_CREATE_CONTINUATION.instance,
//			generator.makeImmutable(getCurrentFunction()),
//			generator.makeImmutable(getCurrentContinuation()),  // the caller
//			new L2IntImmediateOperand(0),  // indicates a label.
//			new L2IntImmediateOperand(numSlots + 1),  // empty stack
//			new L2ReadBoxedVectorOperand(slotsForLabel),  // each immutable
//			destinationRegister,
//			generator.readInt(
//				writeOffset.semanticValue(),
//				generator.unreachablePcOperand().targetBlock()),
//			generator.readBoxed(writeRegisterDump),
//			new L2CommentOperand("Create a label continuation."));
//		// Continue, with the label having been pushed.
//		forceSlotRegister(
//			stackp, instructionDecoder.pc(), currentManifest().read(label));

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

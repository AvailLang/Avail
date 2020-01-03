/*
 * L2_VIRTUAL_CREATE_LABEL.java
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
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2ControlFlowGraph.Zone;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static com.avail.optimizer.L2Generator.backEdgeTo;
import static com.avail.optimizer.L2Generator.edgeTo;
import static com.avail.utility.PrefixSharingList.last;
import static java.util.Collections.emptySet;

/**
 * This is a placeholder instruction, which is replaced if still live after data
 * flow optimizations by:
 *
 * <ol>
 *    <li>{@link L2_SAVE_ALL_AND_PC_TO_INT}, falling through to</li>
 *    <li>{@link L2_GET_CURRENT_FUNCTION} if the current function isn't already
 *        visible,</li>
 *    <li>{@link L2_MAKE_IMMUTABLE} for the current function,</li>
 *    <li>{@link L2_MAKE_IMMUTABLE} for the current caller, which must already
 *        be in a visible register,</li>
 *    <li>{@link L2_CREATE_CONTINUATION}.</li>
 *    <li>The target of the {@link L2_SAVE_ALL_AND_PC_TO_INT} is in another
 *        block, which contains an unconditional {@link L2_JUMP_BACK} to the
 *        {@link L2Generator#afterOptionalInitialPrimitiveBlock}.</li>
 * </ol>
 *
 * <p>The {@link L2_SAVE_ALL_AND_PC_TO_INT} captures the offset of the
 * {@link L2Generator}'s {@link L2_ENTER_L2_CHUNK} entry point at
 * {@link L2Generator#afterOptionalInitialPrimitiveBlock}.  The register dump is
 * fed to the {@link L2_CREATE_CONTINUATION}, so that when the continuation is
 * restarted as a label, it will restore these values into registers.</p>
 *
 * <p>The {@link L2_CREATE_CONTINUATION}'s level one pc is set to zero to
 * indicate this is a label.  The stack is empty, and only the arguments,
 * function, and caller are recorded – for level one.  For level two, it also
 * captures the register dump and the level two offset of the entry point above.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_VIRTUAL_CREATE_LABEL
extends L2Operation
{
	/**
	 * Construct an {@code L2_VIRTUAL_CREATE_LABEL}, which will be converted
	 * into other instructions after it has had an opportunity to be moved out
	 * of the high frequency paths.
	 */
	private L2_VIRTUAL_CREATE_LABEL ()
	{
		super(
			WRITE_BOXED.is("output label"),
			READ_BOXED.is("reified caller"),
			READ_BOXED.is("immutable function"),
			READ_BOXED_VECTOR.is("arguments"),
			INT_IMMEDIATE.is("frame size"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_VIRTUAL_CREATE_LABEL instance =
		new L2_VIRTUAL_CREATE_LABEL();

	@Override
	public boolean isPlaceholder ()
	{
		return true;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2WriteBoxedOperand outputLabel = instruction.operand(0);
		final L2ReadBoxedOperand reifiedCaller = instruction.operand(1);
		final L2ReadBoxedOperand function = instruction.operand(2);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(3);
		final L2IntImmediateOperand frameSize = instruction.operand(4);

		renderPreamble(instruction, builder);
		builder.append(" ").append(outputLabel);
		builder.append("\n\tcaller = ").append(reifiedCaller);
		builder.append("\n\tfunction = ").append(function);
		builder.append("\n\targuments = ").append(arguments);
		builder.append("\n\tframeSize = ").append(frameSize);
	}

	/**
	 * Since an {@code L2_VIRTUAL_REIFY} and its transformed form are each
	 * idempotent, it's legal to introduce a computational redundancy whereby
	 * the value might be computed multiple times along some paths, even if the
	 * original {@link L2ControlFlowGraph} didn't have that redundancy.
	 *
	 * <p>In particular, for an {@code L2_VIRTUAL_REIFY}, it's not worth
	 * avoiding redundant computation, since a common situation is that all of
	 * these instructions end up migrating into reification {@link Zone}s, which
	 * are off the high-frequency track, and therefore not particularly relevant
	 * for performance.  It's better to replicate these instructions downward in
	 * the hope of moving them all off the high-speed track, even if some of the
	 * low-frequency tracks end up reifying multiple times, and even if there's
	 * a chance some of the reifications end up along a high frequency path.
	 * It's still worth stopping whenever the value is always-live-in.</p>
	 *
	 * <p>We can also "look ahead" in the graph to see if any of the subsequent
	 * uses are actually outside reification zones, and avoid introducing extra
	 * redundancy in that case.  We approximate that by looking at all uses of
	 * this instruction's output register, and if they're all inside reification
	 * zones we allow the replication.</p>
	 *
	 * @param instruction
	 *        The {@link L2Instruction} using this operation.
	 * @return Whether to replicate this instruction into multiple successor
	 *         blocks, even if some successors have multiple incoming edges
	 *         (and which might not need the value).
	 */
	@Override
	public boolean shouldReplicateIdempotently (final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final L2WriteBoxedOperand outputLabel = instruction.operand(0);
//		final L2ReadBoxedOperand reifiedCaller = instruction.operand(1);
//		final L2ReadBoxedOperand function = instruction.operand(2);
//		final L2ReadBoxedVectorOperand arguments = instruction.operand(3);
//		final L2IntImmediateOperand frameSize = instruction.operand(4);

		final Set<L2ReadOperand<?>> uses = outputLabel.register().uses();
		return uses.stream()
			.allMatch(use -> use.instruction().basicBlock().zone != null);
	}

	@Override
	public void generateReplacement (
		final L2Instruction instruction, final L2Generator generator)
	{
		assert this == instruction.operation();
		final L2WriteBoxedOperand outputLabel = instruction.operand(0);
		final L2ReadBoxedOperand reifiedCaller = instruction.operand(1);
		final L2ReadBoxedOperand function = instruction.operand(2);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(3);
		final L2IntImmediateOperand frameSize = instruction.operand(4);

		final L2BasicBlock fallThrough = generator.createBasicBlock(
			"Fall-through for label creation",
			generator.currentBlock().zone);
		final L2WriteIntOperand writeOffset = generator.intWriteTemp(
			restrictionForType(int32(), UNBOXED_INT));
		final L2WriteBoxedOperand writeRegisterDump = generator.boxedWriteTemp(
			restrictionForType(ANY.o(), BOXED));
		generator.addInstruction(
			L2_SAVE_ALL_AND_PC_TO_INT.instance,
			backEdgeTo(generator.afterOptionalInitialPrimitiveBlock),
			writeOffset,
			writeRegisterDump,
			edgeTo(fallThrough));

		// Force there to be nothing considered live in the edge leading to the
		// label's entry point.
		final L2Instruction saveInstruction =
			last(generator.currentBlock().instructions());
		final L2PcOperand referenceEdge =
			L2_SAVE_ALL_AND_PC_TO_INT.referenceOf(saveInstruction);
		referenceEdge.forcedClampedEntities = emptySet();

		generator.startBlock(fallThrough);
		final int frameSizeInt = frameSize.value;
		final List<L2ReadBoxedOperand> slots =
			new ArrayList<>(arguments.elements());
		final L2ReadBoxedOperand nilRead = generator.boxedConstant(nil);
		while (slots.size() < frameSizeInt)
		{
			slots.add(nilRead);
		}
		generator.addInstruction(
			L2_CREATE_CONTINUATION.instance,
			generator.makeImmutable(function),
			generator.makeImmutable(reifiedCaller),
			new L2IntImmediateOperand(0),  // indicates a label.
			new L2IntImmediateOperand(frameSizeInt + 1),  // empty stack
			new L2ReadBoxedVectorOperand(slots),  // each immutable
			outputLabel,
			generator.readInt(
				writeOffset.onlySemanticValue(),
				generator.unreachablePcOperand().targetBlock()),
			generator.readBoxed(writeRegisterDump),
			new L2CommentOperand("Create label."));
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

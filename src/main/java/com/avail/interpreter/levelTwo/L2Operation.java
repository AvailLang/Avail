/*
 * L2Operation.java
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
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
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

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.variables.A_Variable;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.L2ControlFlowOperation;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE;
import com.avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT;
import com.avail.interpreter.levelTwo.operation.L2_VIRTUAL_REIFY;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph.Zone;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.reoptimizer.L2Inliner;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;
import static java.util.Collections.emptyList;

/**
 * The instruction set for the {@linkplain Interpreter level two Avail
 * interpreter}.  Avail programs can only see as far down as the level one
 * nybblecode representation.  Level two translations are invisibly created as
 * necessary to boost performance of frequently executed code.  Technically
 * level two is an optional part of an Avail implementation, but modern hardware
 * has enough memory that this should really always be present.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2Operation
{
	/**
	 * A brief hierarchy of classes for sensibly parameterizing the
	 * {@link ReadsHiddenVariable} and {@link WritesHiddenVariable} annotations
	 * on an {@code L2Operation}s.  We'd use an {@code enum} here, but they
	 * don't play at all nicely with annotations in Java.
	 */
	@SuppressWarnings("AbstractClassWithoutAbstractMethods")
	public abstract static class HiddenVariable
	{
		/** How the current continuation field is affected. */
		@HiddenVariableShift(0)
		public static class CURRENT_CONTINUATION extends HiddenVariable { }

		/** How the current function field is affected. */
		@HiddenVariableShift(1)
		public static class CURRENT_FUNCTION extends HiddenVariable { }

		/** How the current arguments of this frame are affected. */
		@HiddenVariableShift(2)
		public static class CURRENT_ARGUMENTS extends HiddenVariable { }

		/** How the latest return value field is affected. */
		@HiddenVariableShift(3)
		public static class LATEST_RETURN_VALUE extends HiddenVariable { }

		/** How the current stack reifier field is affected. */
		@HiddenVariableShift(4)
		public static class STACK_REIFIER extends HiddenVariable { }

		/**
		 * How any other global variables are affected.  This includes things
		 * like the global exception reporter, the stringification function,
		 * observerless setup, etc.
		 *
		 * <p>{@link Primitive}s are annotated with the
		 * {@link Flag#ReadsFromHiddenGlobalState} and
		 * {@link Flag#WritesToHiddenGlobalState} flags in their constructors to
		 * indicate that {@code GLOBAL_STATE} is affected.</p>
		 */
		@HiddenVariableShift(5)
		public static class GLOBAL_STATE extends HiddenVariable { }
	}

	/**
	 * The bitwise-or of the masks of {@link HiddenVariable}s that are read by
	 * {@link L2Instruction}s using this operation.  Note that all reads are
	 * considered to happen before all writes.
	 */
	public final int readsHiddenVariablesMask;

	/**
	 * The bitwise-or of the masks of {@link HiddenVariable}s that are
	 * overwritten by {@link L2Instruction}s using this operation.  Note that
	 * all reads are considered to happen before all writes.
	 */
	public final int writesHiddenVariablesMask;

	/**
	 * Is the enclosing {@link L2Instruction} an entry point into its {@link
	 * L2Chunk}?
	 *
	 * @param instruction
	 *        The enclosing {@code L2Instruction}.
	 * @return {@code true} if this {@code L2Operation} is an entry point,
	 *         {@code false} otherwise.
	 */
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return false;
	}

	/**
	 * The {@linkplain L2NamedOperandType named operand types} that this
	 * {@linkplain L2Operation operation} expects.
	 */
	protected final L2NamedOperandType[] namedOperandTypes;

	/**
	 * Answer the {@linkplain L2NamedOperandType named operand types} that this
	 * {@code L2Operation operation} expects.
	 *
	 * @return The named operand types that this operation expects.
	 */
	public L2NamedOperandType[] operandTypes ()
	{
		return stripNull(namedOperandTypes);
	}

	/**
	 * The name of this level two operation.  This is initialized to be the
	 * {@linkplain Class#getSimpleName() simple name} of the {@link Class}.
	 */
	private final @Nullable String name;

	/**
	 * Answer the name of this {@code L2Operation}.
	 *
	 * @return The operation name, suitable for symbolic debugging of level two
	 *         generated code.
	 */
	public String name ()
	{
		return stripNull(name);
	}

	/**
	 * A {@link Statistic} that records the number of nanoseconds spent while
	 * executing {@link L2Instruction}s that use this operation.
	 */
	public final Statistic jvmTranslationTime;

	/**
	 * Protect the constructor so the subclasses can maintain a fly-weight
	 * pattern (or arguably a singleton).
	 *
	 * @param theNamedOperandTypes
	 *        The {@link L2NamedOperandType}s that describe the layout of
	 *        operands for my instructions.
	 */
	protected L2Operation (final L2NamedOperandType... theNamedOperandTypes)
	{
		final String className = this.getClass().getSimpleName();
		name = className;
		jvmTranslationTime = new Statistic(
			className, StatisticReport.L2_TO_JVM_TRANSLATION_TIME);
		namedOperandTypes = theNamedOperandTypes.clone();

		assert this instanceof L2ControlFlowOperation
			|| this instanceof L2_SAVE_ALL_AND_PC_TO_INT
			|| Arrays.stream(namedOperandTypes).noneMatch(
				x -> x.operandType() == L2OperandType.PC);

		final @Nullable ReadsHiddenVariable readsAnnotation =
			getClass().getAnnotation(ReadsHiddenVariable.class);
		int readMask = 0;
		if (readsAnnotation != null)
		{
			for (final Class<? extends HiddenVariable> hiddenVariableSubclass :
				readsAnnotation.value())
			{
				final HiddenVariableShift shiftAnnotation =
					hiddenVariableSubclass.getAnnotation(
						HiddenVariableShift.class);
				readMask |= 1 << shiftAnnotation.value();
			}
		}
		this.readsHiddenVariablesMask = readMask;

		final @Nullable WritesHiddenVariable writesAnnotation =
			getClass().getAnnotation(WritesHiddenVariable.class);
		int writeMask = 0;
		if (writesAnnotation != null)
		{
			for (final Class<? extends HiddenVariable> hiddenVariableSubclass :
				writesAnnotation.value())
			{
				final HiddenVariableShift shiftAnnotation =
					hiddenVariableSubclass.getAnnotation(
						HiddenVariableShift.class);
				writeMask |= 1 << shiftAnnotation.value();
			}
		}
		this.writesHiddenVariablesMask = writeMask;
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  If the operation {@link
	 * #altersControlFlow()}, expect a {@link RegisterSet} per outgoing edge,
	 * in the same order.  Otherwise expect one {@link RegisterSet}.
	 *
	 * @param instruction
	 *        The L2Instruction containing this L2Operation.
	 * @param registerSets
	 *        A list of RegisterSets to update with information that this
	 *        operation provides.
	 * @param generator
	 *        The L2Generator for which to advance the type analysis.
	 */
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		assert !(this instanceof L2ControlFlowOperation);
		throw new UnsupportedOperationException(
			"Multi-target propagateTypes is only applicable to an "
				+ "L2ControlFlowOperation");
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  The instruction must not have
	 * multiple possible successor instructions.
	 *
	 * @param instruction
	 *        The L2Instruction containing this L2Operation.
	 * @param registerSet
	 *        A {@link RegisterSet} to supply with information.
	 * @param generator
	 *            The L2Generator for which to advance the type analysis.
	 * @see #propagateTypes(L2Instruction, List, L2Generator)
	 */
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		// We're phasing this out, so don't make this an abstract method.
		throw new UnsupportedOperationException(
			"Single-target propagateTypes should be overridden.");
	}

	/**
	 * Answer whether an instruction using this operation should be emitted
	 * during final code generation. For example, a move between registers with
	 * the same finalIndex can be left out during code generation, although it
	 * can't actually be removed before then.
	 *
	 * @param instruction The instruction containing this operation.
	 * @return A {@code boolean} indicating if this operation should be emitted.
	 */
	public boolean shouldEmit (final L2Instruction instruction)
	{
		return true;
	}

	/**
	 * Answer whether this {@code L2Operation} changes the state of the
	 * interpreter in any way other than by writing to its destination
	 * registers. Most operations are computational and don't have side effects.
	 *
	 * @return Whether this operation has any side effect.
	 */
	public boolean hasSideEffect ()
	{
		return false;
	}

	/**
	 * Answer whether the given {@link L2Instruction} (whose operation must be
	 * the receiver) changes the state of the interpreter in any way other than
	 * by writing to its destination registers. Most operations are
	 * computational and don't have side effects.
	 *
	 * <p>
	 * Most enum instances can override {@link #hasSideEffect()} if
	 * {@code false} isn't good enough, but some might need to know details of
	 * the actual {@link L2Instruction} – in which case they should override
	 * this method instead.
	 * </p>
	 *
	 * @param instruction
	 *            The {@code L2Instruction} for which a side effect test is
	 *            being performed.
	 * @return Whether that L2Instruction has any side effect.
	 */
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return hasSideEffect();
	}

	/**
	 * Answer whether the {@link L2Instruction}, which has this
	 * {@code L2Operation} as its operation, is sufficient cause to consider
	 * an {@link L2Chunk} containing it to not be considered a simple leaf.
	 *
	 * <p>A simple leaf must not invoke other chunks, loop, or originate
	 * reification for any reason other than an interrupt.  Simple leaves may
	 * have their interrupt check elided for performance.</p>
	 *
	 * @param instruction
	 *        The instruction to check, which has this as its operation.
	 * @return Whether to disqualify an {@link L2Chunk} containing the given
	 *         instruction from being treated as a simple leaf.
	 */
	public boolean makesChunkNotSimpleLeaf (final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return false;
	}

	/**
	 * Answer whether execution of this instruction can divert the flow of
	 * control from the next instruction.  An L2Operation either always falls
	 * through or always alters control.
	 *
	 * @return Whether this operation alters the flow of control.
	 */
	public boolean altersControlFlow ()
	{
		return false;
	}

	/**
	 * Answer true if this instruction leads to multiple targets, *multiple* of
	 * which can be reached.  This is not the same as a branch, in which only
	 * one will be reached for any circumstance of reaching this instruction.
	 * In particular, an {@link L2_SAVE_ALL_AND_PC_TO_INT} instruction jumps to
	 * its fall-through label, but after reification has saved the live register
	 * state, it gets restored again and winds up traversing the other edge.
	 *
	 * <p>This is an important distinction, in that this type of instruction
	 * should act as a barrier against redundancy elimination.  Otherwise an
	 * object with identity (i.e., a variable) created in the first branch won't
	 * be the same as the one produced in the second branch.</p>
	 *
	 * <p>Also, we must treat as always-live-in to this instruction any values
	 * that are used in <em>either</em> branch, since they'll both be taken.</p>
	 *
	 * @return Whether multiple branches may be taken following the circumstance
	 *         of arriving at this instruction.
	 */
	public boolean goesMultipleWays ()
	{
		return false;
	}

	/**
	 * Answer whether execution of this instruction causes a {@linkplain
	 * A_Variable variable} to be read.
	 *
	 * @return Whether the instruction causes a variable to be read.
	 */
	public boolean isVariableGet ()
	{
		return false;
	}

	/**
	 * Answer whether execution of this instruction causes a {@linkplain
	 * A_Variable variable} to be written.
	 *
	 * @return Whether the instruction causes a variable to be written.
	 */
	public boolean isVariableSet ()
	{
		return false;
	}

	/**
	 * Answer whether this operation is a move between (compatible) registers.
	 *
	 * @return {@code true} if this operation simply moves data between two
	 *         registers of the same {@link RegisterKind}, otherwise {@code
	 *         false}.
	 */
	public boolean isMove ()
	{
		return false;
	}

	/**
	 * Answer whether this operation is a phi-function.  This is a convenient
	 * fiction that allows control flow to merge while in SSA form.
	 *
	 * @return {@code true} if this is a phi operation, {@code false} otherwise.
	 */
	public boolean isPhi ()
	{
		return false;
	}

	/**
	 * Answer whether this operation is a placeholder, and should be replaced
	 * using {@link L2Generator#replaceInstructionByGenerating(L2Instruction)}.
	 * Placeholder instructions (like {@link L2_VIRTUAL_REIFY}) are free to be
	 * moved through much of the control flow graph, even though the subgraphs
	 * they eventually get replaced by would be too complex to move.  The
	 * mobility of placeholder instructions is essential to postponing
	 * stack reification and label creation into off-ramps (reification
	 * {@link Zone}s) as much as possible.
	 *
	 * @return Whether the {@link L2Instruction} using this operation is a
	 *         placeholder, subject to later substitution.
	 */
	public boolean isPlaceholder ()
	{
		return false;
	}

	/**
	 * Answer whether this operation causes unconditional control flow jump to
	 * another {@link L2BasicBlock}.
	 *
	 * @return {@code true} iff this is an unconditional jump.
	 */
	public boolean isUnconditionalJump ()
	{
		return false;
	}

	/**
	 * This is the operation for the given instruction, which was just added to
	 * its basic block.  Do any post-processing appropriate for having added
	 * the instruction.  Its operands have already had their instruction fields
	 * set to the given instruction.
	 *
	 * <p>Automatically handle {@link L2WriteOperand}s that list a
	 * {@link Purpose} in their corresponding {@link L2NamedOperandType},
	 * ensuring the write is only considered to happen along the edge
	 * ({@link L2PcOperand}) having the same purpose.  Subclasses may want to do
	 * additional postprocessing.</p>
	 *
	 * @param instruction
	 *        The {@link L2Instruction} that was just added.
	 * @param manifest
	 *        The {@link L2ValueManifest} that is active at this instruction.
	 */
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		final List<Integer> edgeIndexOrder = new ArrayList<>();
		final L2Operand[] operands = instruction.operands();
		for (int i = 0; i < operands.length; i++)
		{
			final L2NamedOperandType namedOperandType = namedOperandTypes[i];
			final @Nullable Purpose purpose = namedOperandType.purpose();
			final L2Operand operand = operands[i];
			if (purpose == null)
			{
				// Process all operands without a purpose first.
				operand.instructionWasAdded(manifest);
			}
			else
			{
				if (operand instanceof L2PcOperand)
				{
					edgeIndexOrder.add(i);
				}
			}
		}
		// Create separate copies of the manifest for each outgoing edge.
		for (final int operandIndex : edgeIndexOrder)
		{
			final L2PcOperand edge = cast(operands[operandIndex]);
			final Purpose purpose =
				stripNull(namedOperandTypes[operandIndex].purpose());
			final L2ValueManifest manifestCopy = new L2ValueManifest(manifest);
			for (int i = 0; i < operands.length; i++)
			{
				final L2NamedOperandType namedOperandType =
					namedOperandTypes[i];
				if (namedOperandType.purpose() == purpose
					&& !(operands[i] instanceof L2PcOperand))
				{
					operands[i].instructionWasAdded(manifestCopy);
				}
			}
			edge.instructionWasAdded(manifestCopy);
		}
	}

	/**
	 * This is the operation for the given instruction, which was just inserted
	 * into its basic block as part of an optimization pass.  Do any
	 * post-processing appropriate for having inserted the instruction.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} that was just inserted.
	 */
	public void instructionWasInserted (
		final L2Instruction instruction)
	{
		assert !isEntryPoint(instruction)
			|| instruction.basicBlock().instructions().get(0) == instruction
			: "Entry point instruction must be at start of a block";

		instruction.operandsDo(
			operand -> operand.instructionWasInserted(instruction));
	}

	/**
	 * Write the given instruction's equivalent effect through the given {@link
	 * L2Inliner}.  The given {@link L2Instruction}'s {@linkplain
	 * L2Instruction#operation() operation} must be the current receiver.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} for which to write an equivalent effect
	 *        to the inliner.
	 * @param transformedOperands
	 *        The operands of the instruction, already transformed for the
	 *        inliner.
	 * @param retranslator
	 *        The {@link L2Inliner} through which to write the
	 *        instruction's equivalent effect.
	 */
	public void emitTransformedInstruction (
		final L2Instruction instruction,
		final L2Operand[] transformedOperands,
		final L2Inliner retranslator)
	{
		assert instruction.operation() == this;
		retranslator.emitInstruction(this, transformedOperands);
	}

	/**
	 * Write an alternative to this instruction into the given {@link List} of
	 * instructions.  The state at the start of this instruction has been
	 * provided, but should not be modified.  Answer whether a semantic change
	 * has taken place that might require another pass of flow analysis.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} containing this operation.
	 * @param registerSet
	 *        A {@link RegisterSet} holding type and value information about all
	 *        live registers upon arriving at this instruction.  This method
	 *        should modify the registerSet in-place to indicate the effect on
	 *        register types and values that this instruction will have.
	 * @param generator
	 *        Where to write the replacement instruction.
	 * @return Whether the regenerated instructions are different enough to
	 *         warrant another pass of flow analysis.
	 */
	@Deprecated
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		// By default just produce the same instruction.
		assert instruction.operation() == this;
		generator.addInstruction(instruction);
		return false;
	}

	/**
	 * Emit code to extract the specified outer value from the function produced
	 * by this instruction.  The new code is appended to the provided list of
	 * instructions, which may be at a code generation position unrelated to the
	 * receiver.  The extracted outer variable will be written to the provided
	 * target register.
	 *
	 * @param instruction
	 *        The instruction that produced the function.  Its {@linkplain
	 *        L2Instruction#operation() operation} is the receiver.
	 * @param functionRegister
	 *        The register holding the function at the code generation point.
	 * @param outerIndex
	 *        The one-based outer index to extract from the function.
	 * @param outerType
	 *        The type of value that must be in that outer.
	 * @param generator
	 *        The {@link L2Generator} into which to write the new code.
	 * @return The {@link L2ReadBoxedOperand} holding the outer value.
	 */
	public L2ReadBoxedOperand extractFunctionOuter (
		final L2Instruction instruction,
		final L2ReadBoxedOperand functionRegister,
		final int outerIndex,
		final A_Type outerType,
		final L2Generator generator)
	{
		assert instruction.operation() == this;
		final L2WriteBoxedOperand writer =
			generator.boxedWriteTemp(
				restrictionForType(outerType, BOXED));
		generator.addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2IntImmediateOperand(outerIndex),
			functionRegister,
			writer);
		return generator.readBoxed(writer);
	}

	/**
	 * Extract the constant {@link A_RawFunction} that's enclosed by the
	 * function produced or passed along by this instruction.
	 *
	 * @param instruction
	 *        The instruction to examine.
	 * @return The constant {@link A_RawFunction} extracted from the
	 *         instruction, or {@code null} if unknown.
	 */
	public @Nullable A_RawFunction getConstantCodeFrom (
		final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return null;
	}

	/**
	 * If this instruction is an attempt to execute a primitive, answer the
	 * register into which the primitive's result will be written if successful.
	 * Otherwise answer {@code null}.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} for which the receiver is the {@code
	 *        L2Operation}.
	 * @return The register into which the primitive attempted by this
	 *         instruction will write its result, or null if the instruction
	 *         isn't an attempt to run a primitive.
	 */
	@SuppressWarnings("unused")
	public @Nullable L2WriteBoxedOperand primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return null;
	}

	/**
	 * Extract the operands which are {@link L2PcOperand}s.  These are what lead
	 * to other {@link L2BasicBlock}s.  They also carry an edge-specific array
	 * of slots, and edge-specific {@link TypeRestriction}s for registers.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to examine.
	 * @return The {@link List} of target {@link L2PcOperand}s that are operands
	 *         of the given instruction.  These may be reachable directly via a
	 *         control flow change, or reachable only from some other mechanism
	 *         like continuation reification and later resumption of a
	 *         continuation.
	 */
	public List<L2PcOperand> targetEdges (final L2Instruction instruction)
	{
		return emptyList();
	}

	@Override
	public String toString ()
	{
		// Skip the L2_ prefix, as it is redundant in context.
		return name().substring(3);
	}

	/**
	 * Produce a sensible preamble for the textual rendition of the specified
	 * {@link L2Instruction} that includes the {@linkplain
	 * L2Instruction#offset() offset} and {@linkplain #toString() name} of the
	 * {@code L2Operation}.
	 *
	 * @param instruction
	 *        The {@code L2Instruction}.
	 * @param builder
	 *        The {@code StringBuilder} to which the preamble should be written.
	 */
	protected final void renderPreamble (
		final L2Instruction instruction,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final int offset = instruction.offset();
		if (offset != -1)
		{
			builder.append(instruction.offset());
			builder.append(". ");
		}
		builder.append(this);
	}

	/**
	 * Generically render all {@linkplain L2Operand operands} of the specified
	 * {@link L2Instruction} starting at the specified index.
	 *
	 * @param instruction
	 *        The {@code L2Instruction}.
	 * @param start
	 *        The start index.
	 * @param desiredTypes
	 *        The {@link L2OperandType}s of {@link L2Operand}s to be included in
	 *        generic renditions. Customized renditions may not honor these
	 *        types.
	 * @param builder
	 *        The {@link StringBuilder} to which the rendition should be
	 *        written.
	 */
	protected final void renderOperandsStartingAt (
		final L2Instruction instruction,
		final int start,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		final L2Operand[] operands = instruction.operands();
		final L2NamedOperandType[] types = operandTypes();
		for (int i = start, limit = operands.length; i < limit; i++)
		{
			final L2NamedOperandType type = types[i];
			if (desiredTypes.contains(type.operandType()))
			{
				final L2Operand operand = instruction.operand(i);
				builder.append("\n\t");
				assert operand.operandType() == type.operandType();
				builder.append(type.name());
				builder.append(" = ");
				builder.append(increaseIndentation(operand.toString(), 1));
			}
		}
	}

	/**
	 * Produce a sensible textual rendition of the specified {@link
	 * L2Instruction}.
	 *
	 * @param instruction
	 *        The {@code L2Instruction}.
	 * @param desiredTypes
	 *        The {@link L2OperandType}s of {@link L2Operand}s to be included in
	 *        generic renditions. Customized renditions may not honor these
	 *        types.
	 * @param builder
	 *        The {@link StringBuilder} to which the rendition should be
	 *        written.
	 * @param warningStyleChange
	 *        A mechanism to turn on and off a warning style, which the caller
	 *        may listen to, to track regions of the builder to highlight in its
	 *        own warning style.  This must be invoked in (true, false) pairs.
	 */
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		renderPreamble(instruction, builder);
		final L2NamedOperandType[] types = operandTypes();
		final L2Operand[] operands = instruction.operands();
		for (int i = 0, limit = operands.length; i < limit; i++)
		{
			final L2NamedOperandType type = types[i];
			if (desiredTypes.contains(type.operandType()))
			{
				final L2Operand operand = instruction.operand(i);
				builder.append("\n\t");
				assert operand.operandType() == type.operandType();
				builder.append(type.name());
				builder.append(" = ");
				operand.appendWithWarningsTo(builder, 1, warningStyleChange);
			}
		}
	}

	/**
	 * Translate the specified {@link L2Instruction} into corresponding JVM
	 * instructions.
	 *
	 * @param translator
	 *        The {@link JVMTranslator} responsible for the translation.
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction} to translate.
	 */
	public abstract void translateToJVM (
		JVMTranslator translator,
		MethodVisitor method,
		L2Instruction instruction);

	/**
	 * Generate code to replace this {@link L2Instruction}.  The instruction has
	 * already been removed.  Leave the generator in a state that ensures any
	 * {@link L2Register}s that would have been written by the old instruction
	 * are instead written by the new code.
	 *
	 * <p>Leave the code generation at the point where subsequent instructions
	 * of the rebuilt block will be re-emitted, whether that's in the same block
	 * or not.</p>
	 *
	 * @param instruction
	 *        The {@link L2Instruction} being replaced.
	 * @param generator
	 *        An {@link L2Generator} that has been configured for writing
	 *        arbitrary replacement code for this instruction.
	 */
	public void generateReplacement (
		final L2Instruction instruction,
		final L2Generator generator)
	{
		throw new RuntimeException(
			"A " + instruction.operation()
				+ " cannot be transformed by regeneration");
	}

	/**
	 * The given instruction has been declared dead code (the receiver is that
	 * instruction's operation).  If there's an alternative form of that
	 * instruction that should replace it, provide it.
	 *
	 * <p>Note that the old instruction will be removed and the new one added,
	 * so now's a good time to switch {@link L2PcOperand}s that may need to be
	 * moved between the instructions.</p>
	 *
	 * @param instruction
	 *        The instruction about to be removed or replaced.
	 * @return Either null or a replacement {@code L2Instruction} for the given
	 *         dead one.
	 */
	public @Nullable L2Instruction optionalReplacementForDeadInstruction (
		final L2Instruction instruction)
	{
		return null;
	}
}

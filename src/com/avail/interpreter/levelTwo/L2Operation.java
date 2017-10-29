/**
 * L2Operation.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Variable;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.operand.L2ImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.ReifyStackThrowable;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static com.avail.utility.Nulls.stripNull;
import static java.util.stream.Collectors.toList;

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
	 * The {@linkplain L2NamedOperandType named operand types} that this
	 * {@linkplain L2Operation operation} expects.
	 */
	protected @Nullable L2NamedOperandType[] namedOperandTypes;

	/**
	 * The array of operand indices which have type {@link L2PcOperand}.
	 */
	private int[] labelOperandIndices = {};

	/**
	 * Whether this operation can do something other than fall through to the
	 * next instruction of its {@link L2BasicBlock}.  This includes branching or
	 * returning.
	 */
	boolean altersControlFlow;

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
	 * The ordinal of this {@code L2Operation}, made available via the {@link
	 * #ordinal()} message.  The assignment of ordinals to operations depends on
	 * the order in which the operations are first encontered in the code
	 * generator (or elsewhere), so don't rely on specific numeric values.
	 */
	private final int ordinal;

	/**
	 * The ordinal of this {@code L2Operation}.  Note that the assignment of
	 * ordinals to operations depends on the order in which the operations are
	 * first encountered in the code generator, so don't rely on numeric values.
	 *
	 * <p>
	 * Among other things, this is intended as a disincentive to discourage the
	 * exposition of anything related to level two in media that outlive or
	 * otherwise exceed the scope of an invocation of the Avail virtual machine
	 * (i.e., files, communication channels, etc).  If such a mechanism becomes
	 * truly desirable in the future (despite it potentially acting as a
	 * hindrance against advancement of the level two instruction set and
	 * optimization engine), one could choose to initialize the classes in some
	 * particular order, thus defining the canon of level two operations.
	 * </p>
	 *
	 * <p>
	 * One more warning for good measure:  The level two instructions produced
	 * by the optimizer take into account (and are themselves taken into account
	 * by) the specific sets of definitions of relevant methods.  For
	 * example, the function [1+2;] may be tentatively folded to produce the
	 * constant 3, irrespective of the current environment's definition of a
	 * "_+_" operation.  Within a virtual machine, such level two code will be
	 * made contingent upon the "_+_" method's set of definitions, being
	 * invalidated automatically if a definition is added or removed.  Any
	 * attempt to simply plug such level two code into another environment is
	 * surely fraught with disaster, or at least great peril.
	 * </p>
	 *
	 * @return The operation's ordinal, used in {@link L2Chunk level two
	 *         wordcodes}.
	 */
	public int ordinal ()
	{
		return ordinal;
	}

	/**
	 * The {@linkplain L2Operation operations} that have been encountered thus
	 * far, organized as an array indexed by the operations' {@linkplain
	 * #ordinal ordinals}. The array might be padded on the right with nulls.
	 */
	static final L2Operation[] values = new L2Operation[200];

	/** Synchronization for the values array. */
	static final ReentrantLock valuesLock = new ReentrantLock();

	/**
	 * Answer an array of {@code L2Operation operations} which have been
	 * encountered thus far, indexed by {@link #ordinal}.  It may be padded with
	 * nulls.
	 *
	 * <p>
	 * The array may be replaced when new operations are encountered, so do not
	 * cache it elsewhere.
	 * </p>
	 *
	 * @return The known operations.
	 */
	public static L2Operation[] values ()
	{
		return values;
	}

	/**
	 * How many distinct kinds of operations have been encountered so far.
	 */
	private static int numValues = 0;

	/**
	 * A {@link Statistic} that records the number of nanoseconds spent while
	 * executing {@link L2Instruction}s that use this operation.
	 */
	public final Statistic statisticInNanoseconds;

	/**
	 * Protect the constructor so the subclasses can maintain a fly-weight
	 * pattern (or arguably a singleton).
	 */
	protected L2Operation ()
	{
		final String className = this.getClass().getSimpleName();
		name = className;
		statisticInNanoseconds = new Statistic(
			className, StatisticReport.L2_OPERATIONS);
		valuesLock.lock();
		try
		{
			ordinal = numValues;
			values[ordinal] = this;
			//noinspection AssignmentToStaticFieldFromInstanceMethod
			numValues++;
		}
		finally
		{
			valuesLock.unlock();
		}
	}

	/**
	 * Initialize a fresh {@code L2Operation}.
	 *
	 * @param theNamedOperandTypes
	 *        The named operand types that this operation expects.
	 * @return The receiver.
	 */
	public L2Operation init (final L2NamedOperandType... theNamedOperandTypes)
	{
		// Static class initialization causes this to happen, and L2Operation
		// subclasses may be first encountered by separate threads. Therefore we
		// must synchronize on the valuesLock.
		valuesLock.lock();
		try
		{
			assert namedOperandTypes == null;
			namedOperandTypes = theNamedOperandTypes;

			// The number of targets won't be large, so don't worry about the
			// quadratic cost.  An N-way dispatch would be a different story.
			int[] indices = {};
			for (int index = 0; index < namedOperandTypes.length; index++)
			{
				final L2NamedOperandType namedOperandType =
					namedOperandTypes[index];
				if (namedOperandType.operandType() == L2OperandType.PC)
				{
					indices = Arrays.copyOf(indices, indices.length + 1);
					indices[indices.length - 1] = index;
				}
			}
			labelOperandIndices = indices;
			altersControlFlow = indices.length > 0 || !reachesNextInstruction();
		}
		finally
		{
			valuesLock.unlock();
		}
		return this;
	}

	/**
	 * Execute this {@code L2Operation} within an {@link Interpreter}.  The
	 * {@linkplain L2Operand operands} are provided in the {@link L2Instruction}
	 * that is also passed.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} of which this is the {@code
	 *        L2Operation}.
	 * @param interpreter
	 *        The {@link Interpreter} on behalf of which to perform this
	 *        operation.
	 * @throws ReifyStackThrowable
	 *         If the instruction triggers stack reification.
	 */
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	throws ReifyStackThrowable
	{
		// Subclasses should implement either this or actionFor().
		// Eventually step() will be entirely deprecated.
		throw new UnsupportedOperationException();
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  The first {@link RegisterSet} is for
	 * the fall-through situation (if the {@link
	 * L2Operation#reachesNextInstruction()}), and represents the state in the
	 * case that the instruction runs normally and advances to the next one
	 * sequentially.  The remainder correspond to the {@link
	 * L2Instruction#targetEdges()} ()}.
	 *
	 * @param instruction
	 *            The L2Instruction containing this L2Operation.
	 * @param registerSets
	 *            A list of RegisterSets to update with information that this
	 *            operation provides.
	 * @param translator
	 *            The L2Translator for which to advance the type analysis.
	 */
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		assert false : "Please override multi-target L2Operation";
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  The instruction must not have
	 * multiple possible successor instructions.
	 *
	 * @param instruction
	 *            The L2Instruction containing this L2Operation.
	 * @param registerSet
	 *            A RegisterSet to supply with information about
	 *            A list of RegisterSets to update with information that this
	 *            operation provides.
	 * @param translator
	 *            The L2Translator for which to advance the type analysis.
	 * @see #propagateTypes(L2Instruction, List, L2Translator)
	 */
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		assert false : "Please override single-target L2Operation";
	}

	/**
	 * Answer whether an instruction using this operation should be emitted
	 * during final code generation. For example, a move between registers with
	 * the same finalIndex can be left out during code generation, although it
	 * can't actually be removed before then.
	 *
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
		assert instruction.operation == this;
		return hasSideEffect();
	}

	/**
	 * Answer whether execution of this instruction can lead to the next
	 * instruction in the sequence being reached.  Most instructions are of this
	 * form, but some might not be (return, unconditional branches, continuation
	 * resumption, etc).
	 *
	 * @return Whether the next instruction is potentially reachable from here.
	 */
	public boolean reachesNextInstruction ()
	{
		return true;
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
	 * @param translator
	 *        The list of instructions to augment.
	 * @return Whether the regenerated instructions are different enough to
	 *         warrant another pass of flow analysis.
	 */
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1Translator translator)
	{
		// By default just produce the same instruction.
		assert instruction.operation == this;
		translator.addInstruction(instruction);
		return false;
	}

	/**
	 * Emit code to extract the specified outer variable from the function
	 * produced by this instruction.  The new code is appended to the provided
	 * list of instructions, which may be at a code generation position
	 * unrelated to the receiver.  The extracted outer variable will be written
	 * to the provided target register.
	 *
	 * @param instruction
	 *        The instruction that produced the function.  Its {@linkplain
	 *        L2Instruction#operation operation} is the receiver.
	 * @param functionRegister
	 *        The register holding the function after this instruction runs.
	 * @param outerIndex
	 *        The one-based outer index to extract from the function.
	 * @param targetRegisterWrite
	 *        The {@link L2WritePointerOperand} into which the new code should
	 *        cause the captured outer variable or value to be written.
	 * @param translator
	 *        The {@link L1Translator} into which to write the new code.
	 * @return A boolean indicating whether an instruction substitution took
	 *         place which may warrant another pass of optimization.
	 */
	public boolean extractFunctionOuterRegister (
		final L2Instruction instruction,
		final L2ReadPointerOperand functionRegister,
		final int outerIndex,
		final L2WritePointerOperand targetRegisterWrite,
		final L1Translator translator)
	{
		assert instruction.operation == this;
		translator.addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionRegister,
			targetRegisterWrite);
		return false;
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
	public @Nullable L2WritePointerOperand primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation == this;
		return null;
	}

	/**
	 * Create an action (@link {@link Continuation1NotNull<Interpreter>}) for
	 * the instruction (whose operation is the receiver).
	 *
	 * @param instruction
	 *        The instruction for which to create an action.
	 * @return A {@link Continuation1NotNullThrowsReification} to run to effect
	 *         this {@code L2Operation} on an {@link Interpreter}.
	 */
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		// For now, most operations can just fall back on their step method.
		// Eventually we'll deprecate step, making actionFor() abstract.  And
		// further down the road, we'll generate JVM (or perhaps native) code
		// from each instruction instead, getting rid of both step() and
		// actionFor().
		return interpreter -> step(instruction, interpreter);
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
		return Arrays.stream(labelOperandIndices)
			.mapToObj(instruction::pcAt)
			.collect(toList());
	}

	/**
	 * Produce a suitable name to describe this instruction.
	 *
	 * @param instruction The instruction to name.
	 * @return The name of the instruction, suitable for debugging.
	 */
	public String debugNameIn (final L2Instruction instruction)
	{
		assert instruction.operation == this;
		return name();
	}
}

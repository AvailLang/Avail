/**
 * L2Translator.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * <p>
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 * <p>
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

package com.avail.optimizer;

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_ChunkDependable;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.SpecialReturnConstant;
import static com.avail.interpreter.levelTwo.register.FixedRegister.all;
import static com.avail.interpreter.levelTwo.register.FixedRegister
	.fixedRegisterCount;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;
import static java.lang.Math.max;
import static java.lang.String.format;

/**
 * The {@code L2Translator} converts a level one {@linkplain FunctionDescriptor
 * function} into a {@linkplain L2Chunk level two chunk}.  It optimizes as it
 * does so, folding and inlining method invocations whenever possible.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2Translator
{
	/**
	 * {@code DebugFlag}s control which kinds of {@linkplain L2Translator
	 * translation} events should be {@linkplain #logger logged}.
	 */
	@InnerAccess
	enum DebugFlag
	{
		/** Code generation. */
		GENERATION(false),

		/** Code optimization. */
		OPTIMIZATION(false),

		/** Data flow analysis. */
		DATA_FLOW(false),

		/** Dead instruction removal. */
		DEAD_INSTRUCTION_REMOVAL(false);

		/** The {@linkplain Logger logger}. */
		@InnerAccess
		final Logger logger;

		/**
		 * Should translation events of this kind be logged?
		 */
		private boolean shouldLog;

		/**
		 * Should translation events of this kind be logged?
		 *
		 * @return {@code true} if translation events of this kind should be
		 * logged, {@code false} otherwise.
		 */
		boolean shouldLog ()
		{
			return shouldLog;
		}

		/**
		 * Indicate whether translation events of the kind represented by the
		 * {@linkplain DebugFlag receiver} should be logged.
		 *
		 * @param flag
		 *        {@code true} if these events should be logged, {@code false}
		 *        otherwise.
		 */
		void shouldLog (final boolean flag)
		{
			shouldLog = flag;
		}

		/**
		 * Log the specified message if debugging is enabled for translation
		 * events of this kind.
		 *
		 * @param level
		 *        The {@linkplain Level severity level}.
		 * @param format
		 *        The format string.
		 * @param args
		 *        The format arguments.
		 */
		void log (final Level level, final String format, final Object... args)
		{
			if (shouldLog && logger.isLoggable(level))
			{
				logger.log(level, format, args);
			}
		}

		/**
		 * Log the specified message if debugging is enabled for translation
		 * events of this kind.
		 *
		 * @param level
		 *        The {@linkplain Level severity level}.
		 * @param exception
		 *        The {@linkplain Throwable exception} that motivated this log
		 *        entry.
		 * @param format
		 *        The format string.
		 * @param args
		 *        The format arguments.
		 */
		void log (
			final Level level,
			final Throwable exception,
			final String format,
			final Object... args)
		{
			if (shouldLog && logger.isLoggable(level))
			{
				logger.log(level, format(format, args), exception);
			}
		}

		/**
		 * Log the specified message if debugging is enabled for translation
		 * events of this kind.
		 *
		 * @param level
		 *        The {@linkplain Level severity level}.
		 * @param continuation
		 *        How to log the message. The argument is a {@link
		 *        Continuation2} that accepts the log message and a causal
		 *        {@linkplain Throwable exception} (or {@code null} if none).
		 */
		void log (
			final Level level,
			final Continuation1NotNull<Continuation2<String, Throwable>>
				continuation)
		{
			if (shouldLog && logger.isLoggable(level))
			{
				continuation.value(
					(message, exception) ->
						logger.log(level, message, exception));
			}
		}

		/**
		 * Construct a new {@code DebugFlag}.
		 *
		 * @param shouldLog
		 *        Should translation events of this kind be logged?
		 */
		DebugFlag (final boolean shouldLog)
		{
			this.logger = Logger.getLogger(
				L2Translator.class.getName() + "." + name());
			this.shouldLog = shouldLog;
		}
	}

	/**
	 * Don't inline dispatch logic if there are more than this many possible
	 * implementations at a call site.
	 */
	static final int maxPolymorphismToInlineDispatch = 5;

	/**
	 * Use a series of instance equality checks if we're doing type testing for
	 * method dispatch code and the type is a non-meta enumeration with at most
	 * this number of instances.  Otherwise do a type test.
	 */
	static final int maxExpandedEqualityChecks = 3;

	/**
	 * An indication of the possible degrees of optimization effort.  These are
	 * arranged approximately monotonically increasing in terms of both cost to
	 * generate and expected performance improvement.
	 */
	public enum OptimizationLevel
	{
		/**
		 * Unoptimized code, interpreted via level one machinery.  Technically
		 * the current implementation only executes level two code, but the
		 * default level two chunk relies on a level two instruction that simply
		 * fetches each nybblecode and interprets it.
		 */
		UNOPTIMIZED,

		/**
		 * The initial translation into level two instructions customized to a
		 * particular raw function.  This at least should avoid the cost of
		 * fetching nybblecodes.  It also avoids looking up monomorphic methods
		 * at execution time, and can inline or even fold calls to suitable
		 * primitives.  The inlined calls to infallible primitives are simpler
		 * than the calls to fallible ones or non-primitives or polymorphic
		 * methods.  Inlined primitive attempts avoid having to reify the
		 * calling continuation in the case that they're successful, but have to
		 * reify if the primitive fails.
		 */
		FIRST_TRANSLATION,

		/**
		 * Unimplemented.  The idea is that at this level some inlining of
		 * non-primitives will take place, emphasizing inlining of function
		 * application.  Invocations of methods that take a literal function
		 * should tend very strongly to get inlined, as the potential to
		 * turn things like continuation-based conditionals and loops into mere
		 * jumps is expected to be highly profitable.
		 */
		@Deprecated
		CHASED_BLOCKS,

		/**
		 * At some point the CPU cost of interpreting the level two code will
		 * exceed the cost of generating corresponding Java bytecodes.
		 */
		@Deprecated
		NATIVE;

		/** An array of all {@link OptimizationLevel} enumeration values. */
		private static final OptimizationLevel[] all = values();

		/**
		 * Answer an array of all {@code OptimizationLevel} enumeration values.
		 *
		 * @return An array of all {@code OptimizationLevel} enum values.  Do
		 * not modify the array.
		 */
		public static OptimizationLevel[] all ()
		{
			return all;
		}
	}

	/**
	 * The current {@link CompiledCodeDescriptor compiled code} being optimized.
	 */
	@InnerAccess
	final @Nullable A_RawFunction codeOrNull;

	/**
	 * The number of arguments expected by the code being optimized.
	 */
	@InnerAccess int numArgs;

	/**
	 * The number of locals created by the code being optimized.
	 */
	@InnerAccess int numLocals;

	/**
	 * The number of stack slots reserved for use by the code being optimized.
	 */
	@InnerAccess int numSlots;

	/**
	 * The amount of {@linkplain OptimizationLevel effort} to apply to the
	 * current optimization attempt.
	 */
	@InnerAccess
	final OptimizationLevel optimizationLevel;

	/**
	 * The {@link Interpreter} that tripped the translation request.
	 */
	@InnerAccess final @Nullable Interpreter interpreter;

	/**
	 * Answer the current {@link Interpreter}.  Fail if there isn't one.
	 *
	 * @return The interpreter that's triggering translation.
	 */
	@InnerAccess Interpreter interpreter ()
	{
		return stripNull(interpreter);
	}

	/**
	 * Answer the {@linkplain AvailRuntime runtime} associated with the current
	 * {@linkplain Interpreter interpreter}.
	 *
	 * @return The runtime.
	 */
	@InnerAccess AvailRuntime runtime ()
	{
		return interpreter().runtime();
	}

	/**
	 * An {@link AtomicLong} used to quickly generate unique 63-bit non-negative
	 * integers which serve to distinguish registers generated by the receiver.
	 */
	private final AtomicLong uniqueCounter = new AtomicLong();

	/**
	 * Answer the next value from the unique counter.  This is only used to
	 * distinguish registers for visual debugging.
	 *
	 * @return A long.
	 */
	long nextUnique ()
	{
		return uniqueCounter.getAndIncrement();
	}

	/**
	 * All {@link A_ChunkDependable contingent values} for which changes should
	 * cause the current {@linkplain L2Chunk level two chunk} to be
	 * invalidated.
	 */
	@InnerAccess
	A_Set contingentValues = emptySet();

	/**
	 * The architectural registers, representing the fixed registers followed by
	 * each object slot of the current continuation.  During initial translation
	 * of L1 to L2, these registers are used as though they are purely
	 * architectural (even though they're not precolored).  Subsequent
	 * conversion to static single-assignment form splits non-contiguous uses of
	 * these registers into distinct registers, assisting later optimizations.
	 */
	final List<L2ObjectRegister> architecturalRegisters;

	/**
	 * Answer the {@link L2Register#finalIndex() final index} of the register
	 * holding the first argument to this compiled code (or where the first
	 * argument would be if there were any).
	 */
	public static final int firstArgumentRegisterIndex = fixedRegisterCount();

	/**
	 * An {@link EnumMap} from each {@link FixedRegister} to its manifestation
	 * as an architectural {@link L2ObjectRegister}.
	 */
	@InnerAccess final EnumMap<FixedRegister, L2ObjectRegister>
		fixedRegisterMap;

	/**
	 * Answer the specified fixed register.
	 *
	 * @param registerEnum
	 *        The {@link FixedRegister} identifying the register.
	 * @return The {@link L2ObjectRegister} named by the registerEnum.
	 */
	public L2ObjectRegister fixed (final FixedRegister registerEnum)
	{
		return architecturalRegisters.get(registerEnum.ordinal());
	}

	/**
	 * Answer the register holding the specified continuation slot.  The slots
	 * are the arguments, then the locals, then the stack entries.  The first
	 * argument occurs just after the {@link FixedRegister}s.
	 *
	 * @param slotNumber
	 *        The index into the continuation's slots.
	 * @return A register representing that continuation slot.
	 */
	public L2ObjectRegister continuationSlot (final int slotNumber)
	{
		return architecturalRegisters.get(
			firstArgumentRegisterIndex - 1 + slotNumber);
	}

	/**
	 * Answer the register holding the specified argument/local number (the
	 * 1st argument is the 3rd architectural register).
	 *
	 * @param argumentNumber
	 *        The argument number for which the "architectural" register is
	 *        being requested.  If this is greater than the number of arguments,
	 *        then answer the register representing the local variable at that
	 *        position minus the number of registers.
	 * @return A register that represents the specified argument or local.
	 */
	public L2ObjectRegister argumentOrLocal (final int argumentNumber)
	{
		final A_RawFunction theCode = codeOrFail();
		assert argumentNumber <= theCode.numArgs() + theCode.numLocals();
		return continuationSlot(argumentNumber);
	}

	/**
	 * Answer the register representing the slot of the stack associated with
	 * the given index.
	 *
	 * @param stackIndex
	 *        A stack position, for example stackp.
	 * @return A {@linkplain L2ObjectRegister register} representing the stack
	 * at the given position.
	 */
	public L2ObjectRegister stackRegister (final int stackIndex)
	{
		assert 1 <= stackIndex && stackIndex <= codeOrFail().maxStackDepth();
		return continuationSlot(numArgs + numLocals + stackIndex);
	}

	/**
	 * Allocate a fresh {@linkplain L2IntegerRegister integer register} that
	 * nobody else has used yet.
	 *
	 * @return The new register.
	 */
	public L2IntegerRegister newIntegerRegister ()
	{
		return new L2IntegerRegister(nextUnique());
	}

	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.  Do not attempt
	 * to propagate type or constant information.
	 *
	 * @param operation
	 *        The operation to invoke.
	 * @param operands
	 *        The operands of the instruction.
	 */
	@InnerAccess void justAddInstruction (
		final L2Operation operation,
		final L2Operand... operands)
	{
		assert instructions.size() == instructionRegisterSets.size();
		final L2Instruction instruction =
			new L2Instruction(operation, operands);
		// During optimization the offset is just the index into the list of
		// instructions.
		instruction.setOffset(instructions.size());
		instructions.add(instruction);
		instructionRegisterSets.add(null);
	}

	/**
	 * Return the {@linkplain CompiledCodeDescriptor compiled Level One code}
	 * being translated.
	 *
	 * @return The code being translated.
	 */
	@InnerAccess @Nullable A_RawFunction codeOrNull ()
	{
		return codeOrNull;
	}

	/**
	 * Return the {@linkplain CompiledCodeDescriptor compiled Level One code}
	 * being translated.
	 *
	 * @return The code being translated.
	 */
	public final A_RawFunction codeOrFail ()
	{
		final A_RawFunction c = codeOrNull;
		if (c == null)
		{
			throw new RuntimeException("L2Translator code was null");
		}
		return c;
	}

	/**
	 * Attempt to inline an invocation of this method definition.  If it can be
	 * (and was) inlined, return the primitive function; otherwise return null.
	 *
	 * @param function
	 *        The {@linkplain FunctionDescriptor function} to be inlined or
	 *        invoked.
	 * @param args
	 *        A {@link List} of {@linkplain L2ObjectRegister registers}
	 *        holding the actual constant values used to look up the method
	 *        definition for the call.
	 * @param registerSet
	 *        A {@link RegisterSet} indicating the current state of the
	 *        registers at this invocation point.
	 * @return The provided method definition's primitive {@linkplain
	 *         FunctionDescriptor function}, or {@code null} otherwise.
	 */
	@InnerAccess
	static @Nullable A_Function primitiveFunctionToInline (
		final A_Function function,
		final List<L2ObjectRegister> args,
		final RegisterSet registerSet)
	{
		final int primitiveNumber = function.code().primitiveNumber();
		if (primitiveNumber == 0)
		{
			return null;
		}
		final Primitive primitive =
			Primitive.byPrimitiveNumberOrFail(primitiveNumber);
		if (primitive.hasFlag(SpecialReturnConstant)
			|| primitive.hasFlag(CanInline))
		{
			return function;
		}
		return null;
	}

	/**
	 * Optimize the stream of instructions.
	 */
	private void optimize ()
	{
		final List<L2Instruction> originals = new ArrayList<>(instructions);
		//noinspection StatementWithEmptyBody
		while (removeDeadInstructions())
		{
			// Do it again.
		}
		DebugFlag.OPTIMIZATION.log(
			Level.FINEST,
			log ->
			{
				@SuppressWarnings("resource")
				final Formatter formatter = new Formatter();
				formatter.format("%nOPTIMIZED: %s%n", codeOrFail());
				final Set<L2Instruction> kept = new HashSet<>(instructions);
				for (final L2Instruction instruction : originals)
				{
					formatter.format(
						"%n%s\t%s",
						kept.contains(instruction)
							? instruction.operation.shouldEmit()
								? "+"
								: "-"
							: "",
						instruction);
				}
				formatter.format("%n");
				log.value(formatter.toString(), null);
			});
	}

	/**
	 * Compute the program state at each instruction. This information includes,
	 * for each register, its constant value (if any) and type information,
	 * other registers that currently hold equivalent values, and the set of
	 * instructions that may have directly produced the current register value.
	 */
	private void computeDataFlow ()
	{
		instructionRegisterSets.clear();
		instructionRegisterSets.add(new RegisterSet(fixedRegisterMap));
		for (int i = 1, end = instructions.size(); i < end; i++)
		{
			instructionRegisterSets.add(null);
		}
		final int instructionsCount = instructions.size();
		final BitSet instructionsToVisit = new BitSet(instructionsCount);
		instructionsToVisit.set(0);
		for (
			int instructionIndex = 0;
			instructionIndex < instructionsCount;
			instructionIndex++)
		{
			if (!instructionsToVisit.get(instructionIndex))
			{
				continue;
			}
			final L2Instruction instruction =
				instructions.get(instructionIndex);
			DebugFlag.DATA_FLOW.log(
				Level.FINEST,
				"Trace #%d (%s):%n",
				instructionIndex,
				instruction);
			final RegisterSet regs =
				instructionRegisterSets.get(instructionIndex);
			final List<L2PcOperand> successors =
				new ArrayList<>(instruction.targetEdges());
			if (instruction.operation.reachesNextInstruction())
			{
				successors.add(0, instructions.get(instructionIndex + 1));
			}
			final int successorsSize = successors.size();
			// The list allTargets now holds every target instruction, starting
			// with the instruction following this one if this one
			// reachesNextInstruction().
			final List<RegisterSet> targetRegisterSets =
				new ArrayList<>(successorsSize);
			for (int i = 0; i < successorsSize; i++)
			{
				targetRegisterSets.add(new RegisterSet(regs));
			}
			instruction.propagateTypes(targetRegisterSets, L2Translator.this);

			for (int i = 0; i < successorsSize; i++)
			{
				final L2Instruction successor = successors.get(i);
				final RegisterSet targetRegisterSet = targetRegisterSets.get(i);
				final int targetInstructionNumber = successor.offset();
				DebugFlag.DATA_FLOW.log(
					Level.FINEST,
					log ->
					{
						final StringBuilder builder = new StringBuilder(100);
						targetRegisterSet.debugOn(builder);
						log.value(
							format(
								"\t->#%d:%s%n",
								targetInstructionNumber,
								increaseIndentation(builder.toString(), 1)),
							null);
					});
				final RegisterSet existing =
					instructionRegisterSets.get(targetInstructionNumber);
				final boolean followIt;
				if (existing == null)
				{
					instructionRegisterSets.set(
						targetInstructionNumber, targetRegisterSet);
					followIt = true;
				}
				else
				{
					followIt = existing.mergeFrom(targetRegisterSet);
				}
				if (followIt)
				{
					assert successor.offset() > instructionIndex;
					instructionsToVisit.set(successor.offset());
				}
			}
		}
	}

	/**
	 * Remove any unnecessary instructions.  Answer true if any were removed.
	 *
	 * <p>Compute a sequence of {@link RegisterSet}s that parallels the
	 * instructions.  Each RegisterSet knows the instruction that it is
	 * derived from, and information about all registers <em>prior</em> to the
	 * instruction's execution.  The information about each register includes
	 * the current constant value (if any), its type, the previous registers
	 * that contributed a value through a move (in chronological order) and have
	 * not since been overwritten, and the set of instructions that may have
	 * provided the current value.  There may be more than one such generating
	 * instruction due to merging of instruction flows via jumps.</p>
	 *
	 * <p>To compute this, the {@link L2_ENTER_L2_CHUNK} instruction is seeded
	 * with information about the {@linkplain FixedRegister fixed registers},
	 * arguments, and primitive failure value if applicable.  We then visit each
	 * instruction, computing a successor ProgramState due to running that
	 * instruction, then supply it to each successor instruction to broaden its
	 * existing state.  We note whether it actually changes the state.  After
	 * reaching the last instruction we check if any state changes happened, and
	 * if so we iterate again over all the instructions.  Eventually it
	 * converges, assuming loop inlining has not yet been implemented.</p>
	 *
	 * <p>In the case of loops it might not naturally converge, but after a few
	 * passes we can intentionally weaken the RegisterStates that keep changing.
	 * For example, a loop index might start off having type [1..1], then [1..2]
	 * due to the branch back, then [1..3], but eventually we'll assume it's not
	 * converging and broaden it all the way to (-∞..∞).  Or better yet, [1..∞),
	 * but that's a tougher thing to prove, so we have to be able to broaden it
	 * again in a subsequent pass.  Other type families have suitable
	 * fixed-point approximations of their own, and worst case we can always
	 * broaden them to {@code any} or ⊤ after a few more passes.</p>
	 *
	 * <p>So at this point, we know at the start of each instruction what values
	 * and types the registers have, what other registers hold the same values,
	 * and which instructions might have supplied those values.  Then we can
	 * mark all instructions that might provide values that will be used, as
	 * well as any instructions that have side-effects, then throw away any
	 * instructions that didn't get marked.  At the same time we can substitute
	 * "older" registers for newer ones.  Re-running this algorithm might then
	 * be able to discard unnecessary moves.</p>
	 *
	 * <p>In addition, due to type and value propagation there may be branches
	 * that become no longer reachable (e.g., primitives that can't fail for the
	 * subrange of values now known to occur).  Suitable simpler instructions
	 * can be substituted in their place (e.g., infallible primitive calls or
	 * even a move of a folded constant), making chunks of code unreachable.
	 * Code after unreachable labels is easily eliminated.</p>
	 *
	 * @return Whether any dead instructions were removed or changed.
	 */
	private boolean removeDeadInstructions ()
	{
		DebugFlag.DEAD_INSTRUCTION_REMOVAL.log(
			Level.FINEST,
			"Begin removing dead instructions");
		computeDataFlow();
		final Set<L2BasicBlock> reachableBlocks = findReachableBlocks();
		final Set<L2Instruction> neededInstructions =
			findInstructionsThatProduceNeededValues(reachableBlocks);

		// We now have a complete list of which instructions should be kept.
		DebugFlag.DEAD_INSTRUCTION_REMOVAL.log(
			Level.FINEST,
			log ->
			{
				@SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
				final Formatter formatter = new Formatter();
				formatter.format("Keep/drop instruction list:%n");
				for (final L2BasicBlock block : reachableBlocks)
				{
					formatter.format("%n\t%s:", block.name());
					for (final L2Instruction instruction : block.instructions())
					{
						formatter.format(
							"\t%s: %s%n",
							neededInstructions.contains(instruction)
								? "+"
								: "-",
							increaseIndentation(instruction.toString(), 2));
					}
					log.value(formatter.toString(), null);
					log.value("Propagation of needed instructions:", null);
				}
				log.value(formatter.toString(), null);
			});
		boolean anyChanges = false;
		for (final L2BasicBlock block : reachableBlocks)
		{
			anyChanges |= block.instructions().retainAll(neededInstructions);
		}

		final L1NaiveTranslator newNaiveTranslator =
			new L1NaiveTranslator(this);
		//TODO MvG - Finish converting this to the new SSA representation, or
		// maybe move it all to L1NaiveTranslator.
		anyChanges |= regenerateInstructions(
			oldInstructions, newNaiveTranslator);
		for (int i = 0, end = instructions.size(); i < end; i++)
		{
			instructions.get(i).setOffset(i);
			instructionRegisterSets.add(null);
		}
		assert instructions.size() == instructionRegisterSets.size();
		return anyChanges;
	}

	/**
	 * Find the set of instructions that are actually reachable recursively from
	 * the first instruction.
	 *
	 * @return The {@link Set} of reachable {@link L2Instruction}s.
	 */
	private Set<L2BasicBlock> findReachableBlocks ()
	{
		final Deque<L2BasicBlock> blocksToVisit = new ArrayDeque<>();
		blocksToVisit.add(instructions.get(0).basicBlock);
		final Set<L2BasicBlock> reachableBlocks = new HashSet<>();
		while (!blocksToVisit.isEmpty())
		{
			final L2BasicBlock block = blocksToVisit.removeFirst();
			if (!reachableBlocks.contains(block))
			{
				reachableBlocks.add(block);
				for (L2PcOperand edge : block.successorEdges())
				{
					blocksToVisit.add(edge.targetBlock());
				}
			}
		}
		return reachableBlocks;
	}

	/**
	 * Given the set of instructions which are reachable, compute the subset
	 * which have side-effect or produce a value consumed by other reachable
	 * instructions.
	 *
	 * @param reachableBlocks
	 *        All {@link L2BasicBlock} which are reachable from the start.
	 * @return The instructions that are essential and should be kept.
	 */
	private Set<L2Instruction> findInstructionsThatProduceNeededValues (
		final Set<L2BasicBlock> reachableBlocks)
	{
		final Deque<L2Instruction> instructionsToVisit = new ArrayDeque<>();
		for (final L2BasicBlock block : reachableBlocks)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				if (instruction.hasSideEffect())
				{
					instructionsToVisit.add(instruction);
				}
			}
		}
		DebugFlag.DEAD_INSTRUCTION_REMOVAL.log(
			Level.FINEST,
			log ->
			{
				@SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
				final Formatter formatter = new Formatter();
				formatter.format(
					"Directly irremovable reachable instructions:%n");
				for (final L2BasicBlock block : reachableBlocks)
				{
					formatter.format("%n\t%s:", block.name());
					for (final L2Instruction instruction
						: block.instructions())
					{
						formatter.format(
							"\t%s: %s%n",
							instructionsToVisit.contains(instruction)
								? "Forced "
								: "Reach  ",
							increaseIndentation(instruction.toString(), 2));
					}
					log.value(formatter.toString(), null);
					log.value("Propagation of needed instructions:", null);
				}
			});
		// Recursively mark as needed all instructions that produce values
		// consumed by another needed instruction.
		final Set<L2Instruction> neededInstructions = new HashSet<>();
		while (!instructionsToVisit.isEmpty())
		{
			final L2Instruction instruction = instructionsToVisit.removeLast();
			if (!neededInstructions.contains(instruction))
			{
				neededInstructions.add(instruction);
				for (final L2Register sourceRegister
					: instruction.sourceRegisters())
				{
					final L2Instruction definingInstruction =
						sourceRegister.definition();
					DebugFlag.DEAD_INSTRUCTION_REMOVAL.log(
						Level.FINEST,
						log -> log.value(
							format(
								"\t\t#%s (%s) -> %s%n",
								instruction,
								sourceRegister,
								definingInstruction),
							null));
					instructionsToVisit.add(definingInstruction);
				}
			}
		}
		return neededInstructions;
	}

	/**
	 * Allow each kept instruction the opportunity to generate alternative
	 * instructions in place of itself due to its RegisterSet information.  For
	 * example, type propagation may allow conditional branches to be replaced
	 * by unconditional branches.  Fallible primitives may also be effectively
	 * infallible with a particular subtype of arguments.
	 *
	 * @param oldInstructions
	 *        The original sequence of instructions being examined.
	 * @param naiveTranslator
	 *        The {@link L1NaiveTranslator} to which the replacement sequence of
	 *        instructions is being written.
	 * @return Whether there were any significant changes.  This must eventually
	 *         converge to {@code false} after multiple passes in order to
	 *         ensure termination.
	 */
	private boolean regenerateInstructions (
		final List<L2Instruction> oldInstructions,
		final L1NaiveTranslator naiveTranslator)
	{
		boolean anyChanges = false;
		for (final L2Instruction instruction : oldInstructions)
		{
			// Wipe the original instruction's offset.
			instruction.setOffset(-1);
//			System.out.println(instruction + "\n" + registerSet + "\n\n");
			if (instruction.operation instanceof L2_LABEL)
			{
				naiveTranslator.addLabel(instruction);
			}
			else
			{
				anyChanges |= instruction.operation.regenerate(
					instruction,
					naiveTranslator.naiveRegisters(),
					naiveTranslator);
			}
		}
		return anyChanges;
	}

	/**
	 * Assign register numbers to every register.  Keep it simple for now.
	 */
	private void simpleColorRegisters ()
	{
		final List<L2Register> encounteredList = new ArrayList<>();
		final Set<L2Register> encounteredSet = new HashSet<>();
		int maxId = 0;
		for (final L2Instruction instruction : instructions)
		{
			final List<L2Register> allRegisters = new ArrayList<>(
				instruction.sourceRegisters());
			allRegisters.addAll(instruction.destinationRegisters());
			for (final L2Register register : allRegisters)
			{
				if (encounteredSet.add(register))
				{
					encounteredList.add(register);
					if (register.finalIndex() != -1)
					{
						maxId = max(maxId, register.finalIndex());
					}
				}
			}
		}
		encounteredList.sort((r1, r2) ->
		{
			assert r1 != null;
			assert r2 != null;
			return Long.compare(r2.uniqueValue, r1.uniqueValue);
		});
		for (final L2Register register : encounteredList)
		{
			if (register.finalIndex() == -1)
			{
				register.setFinalIndex(++maxId);
			}
		}
	}

	/**
	 * The {@linkplain L2Chunk level two chunk} generated by {@link
	 * #createChunk()}.  It can be retrieved via {@link #chunk()}.
	 */
	private @Nullable L2Chunk chunk;

	/**
	 * Generate a {@linkplain L2Chunk Level Two chunk} from the already written
	 * instructions.  Store it in the L2Translator, from which it can be
	 * retrieved via {@link #chunk()}.
	 */
	private void createChunk ()
	{
		assert chunk == null;
		final Set<L2Instruction> instructionsSet = new HashSet<>(instructions);
		final Mutable<Integer> intRegMaxIndex = new Mutable<>(-1);
		final Mutable<Integer> floatRegMaxIndex = new Mutable<>(-1);
		final Mutable<Integer> objectRegMaxIndex =
			new Mutable<>(fixedRegisterCount() - 1);
		final L2OperandDispatcher dispatcher = new L2OperandDispatcher()
		{
			@Override
			public void doOperand (final L2CommentOperand operand)
			{
				// Ignore
			}

			@Override
			public void doOperand (final L2ConstantOperand operand)
			{
				// Ignore, already made shared at creation time.
			}

			@Override
			public void doOperand (final L2ImmediateOperand operand)
			{
				// Ignore
			}

			@Override
			public void doOperand (final L2PcOperand operand)
			{
				assert instructionsSet.contains(operand.targetLabel());
			}

			@Override
			public void doOperand (final L2PrimitiveOperand operand)
			{
				// Ignore
			}

			@Override
			public void doOperand (final L2ReadIntOperand operand)
			{
				intRegMaxIndex.value = max(
					intRegMaxIndex.value,
					operand.finalIndex());
			}

			@Override
			public void doOperand (final L2ReadPointerOperand operand)
			{
				objectRegMaxIndex.value = max(
					objectRegMaxIndex.value,
					operand.finalIndex());
			}

			@Override
			public void doOperand (final L2ReadVectorOperand operand)
			{
				for (final L2ReadPointerOperand register : operand.elements())
				{
					objectRegMaxIndex.value = max(
						objectRegMaxIndex.value,
						register.finalIndex());
				}
			}

			@Override
			public void doOperand (final L2SelectorOperand operand)
			{
				// Ignore
			}

			@Override
			public void doOperand (final L2WriteIntOperand operand)
			{
				intRegMaxIndex.value = max(
					intRegMaxIndex.value,
					operand.finalIndex());
			}

			@Override
			public void doOperand (final L2WritePointerOperand operand)
			{
				objectRegMaxIndex.value = max(
					objectRegMaxIndex.value,
					operand.finalIndex());
			}

			@Override
			public void doOperand (final L2WriteVectorOperand operand)
			{
				for (final L2WritePointerOperand register : operand.elements())
				{
					objectRegMaxIndex.value = max(
						objectRegMaxIndex.value,
						register.finalIndex());
				}
			}

		};

		final List<L2Instruction> executableInstructions = new ArrayList<>();

		// Note: Number all instructions, but by their index in the
		// executableInstructions list.  Non-emitted instructions get the same
		// index as their successor.
		int offset = 0;
		for (final L2Instruction instruction : instructions)
		{
			instruction.setOffset(offset);
			if (instruction.operation.shouldEmit())
			{
				executableInstructions.add(instruction);
				offset++;
			}
			for (final L2Operand operand : instruction.operands)
			{
				operand.dispatchOperand(dispatcher);
			}
		}

		// Clean up a little.
		instructionRegisterSets.clear();
		chunk = L2Chunk.allocate(
			codeOrNull(),
			objectRegMaxIndex.value + 1,
			intRegMaxIndex.value + 1,
			floatRegMaxIndex.value + 1,
			afterOptionalInitialPrimitiveLabel.offset(),
			instructions,
			executableInstructions,
			contingentValues);
	}

	/**
	 * Return the {@link L2Chunk} previously created via {@link #createChunk()}.
	 *
	 * @return The chunk.
	 */
	private L2Chunk chunk ()
	{
		return stripNull(chunk);
	}

	/**
	 * Construct a new {@link L2Translator}.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor code} to translate.
	 * @param optimizationLevel
	 *        The optimization level.
	 * @param interpreter
	 *        An {@link Interpreter}.
	 */
	private L2Translator (
		final A_RawFunction code,
		final OptimizationLevel optimizationLevel,
		final Interpreter interpreter)
	{
		this.codeOrNull = code;
		this.optimizationLevel = optimizationLevel;
		this.interpreter = interpreter;
		final A_RawFunction theCode = codeOrFail();
		numArgs = theCode.numArgs();
		numLocals = theCode.numLocals();
		numSlots = theCode.numArgsAndLocalsAndStack();

		final int numFixed = firstArgumentRegisterIndex;
		final int numRegisters = numFixed + code.numArgsAndLocalsAndStack();
		architecturalRegisters = new ArrayList<>(numRegisters);
		fixedRegisterMap = new EnumMap<>(FixedRegister.class);
		for (final FixedRegister fixedRegister : all())
		{
			final L2ObjectRegister reg =
				L2ObjectRegister.precolored(
					nextUnique(),
					fixedRegister.ordinal());
			fixedRegisterMap.put(fixedRegister, reg);
			architecturalRegisters.add(reg);
		}
		for (int i = numFixed; i < numRegisters; i++)
		{
			architecturalRegisters.add(new L2ObjectRegister(nextUnique()));
		}
	}

	/**
	 * Construct a new {@code L2Translator} solely for the purpose of creating
	 * the default chunk.  Do everything here except the final chunk extraction.
	 */
	private L2Translator ()
	{
		codeOrNull = null;
		optimizationLevel = OptimizationLevel.UNOPTIMIZED;
		interpreter = null;
		fixedRegisterMap = new EnumMap<>(FixedRegister.class);
		architecturalRegisters = new ArrayList<>(firstArgumentRegisterIndex);

		L1NaiveTranslator naiveTranslator = new L1NaiveTranslator(this);
		final L2BasicBlock reenterFromCallBlock =
			naiveTranslator.createBasicBlock("reenter L1 from call");
		final L2BasicBlock reenterFromInterruptBlock =
			naiveTranslator.createBasicBlock("reenter L1 from interrupt");
		final L2BasicBlock reenterFromRestartBlock =
			naiveTranslator.createBasicBlock(
				"reenter L1 from restart primitive");
		// First we try to run it as a primitive.
		justAddInstruction(L2_TRY_PRIMITIVE.instance);
		// Only if the primitive fails should we even consider optimizing the
		// fallback code.
		naiveTranslator.startBlock(afterOptionalInitialPrimitiveBlock);
		instructions.add(afterOptionalInitialPrimitiveLabel);
		instructionRegisterSets.add(null);
		justAddInstruction(
			L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
			new L2ImmediateOperand(
				OptimizationLevel.FIRST_TRANSLATION.ordinal()));
		justAddInstruction(L2_PREPARE_NEW_FRAME_FOR_L1.instance);

		final L2Instruction loopStart = newLabel("main L1 loop");
		instructions.add(loopStart);
		instructionRegisterSets.add(null);
		justAddInstruction(
			L2_INTERPRET_LEVEL_ONE.instance,
			new L2PcOperand(reenterFromCallLabel, slotRegisters()),
			new L2PcOperand(reenterFromInterruptLabel, slotRegisters()));

		// If reified, calls return here.
		instructions.add(reenterFromCallLabel);
		instructionRegisterSets.add(null);
		justAddInstruction(L2_REENTER_L1_CHUNK_FROM_CALL.instance);
		justAddInstruction(L2_JUMP.instance, new L2PcOperand(loopStart, slotRegisters()));

		// If reified, interrupts return here.
		instructions.add(reenterFromInterruptLabel);
		instructionRegisterSets.add(null);
		justAddInstruction(L2_REENTER_L1_CHUNK_FROM_INTERRUPT.instance);
		justAddInstruction(L2_JUMP.instance, new L2PcOperand(loopStart, slotRegisters()));

		// Labels create continuations that start here.  The continuations
		// capture the arguments and nothing else from the original frame.
		// The ordinary P_RestartContinuation will have to set up locals, but
		// P_RestartContinuationWithArguments will also copy the continuation
		// with different arguments first.
		instructions.add(reenterFromRestartLabel);
		instructionRegisterSets.add(null);
		justAddInstruction(L2_REENTER_L1_CHUNK_FROM_RESTART.instance);
		justAddInstruction(L2_JUMP.instance, new L2PcOperand(loopStart, slotRegisters()));

		createChunk();
		assert loopStart.offset() ==
			L2Chunk.offsetToReenterAfterReification();
		assert reenterFromCallLabel.offset() ==
			L2Chunk.offsetToReturnIntoUnoptimizedChunk();
		assert reenterFromInterruptLabel.offset() ==
			L2Chunk.offsetToResumeInterruptedUnoptimizedChunk();
		assert reenterFromRestartLabel.offset() ==
			L2Chunk.offsetToRestartUnoptimizedChunk();
	}

	/**
	 * Translate the previously supplied {@link A_RawFunction} into a sequence
	 * of {@link L2Instruction}s.  The optimization level specifies how hard to
	 * try to optimize this method.  It is roughly equivalent to the level of
	 * inlining to attempt, or the ratio of code expansion that is permitted.
	 * An optimization level of zero is the bare minimum, which produces a naïve
	 * translation to {@linkplain L2Chunk Level Two code}.  The translation
	 * may include code to decrement a counter and reoptimize with greater
	 * effort when the counter reaches zero.
	 */
	private void translate ()
	{
		final A_RawFunction theCode = codeOrFail();
		numArgs = theCode.numArgs();
		numLocals = theCode.numLocals();
		numSlots = theCode.numArgsAndLocalsAndStack();
		// Now translate all the instructions. We already wrote a label as the
		// first instruction so that L1Ext_doPushLabel can always find it. Since
		// we only translate one method at a time, the first instruction always
		// represents the start of this compiledCode.
		final L1NaiveTranslator naiveTranslator = new L1NaiveTranslator(this);
		naiveTranslator.addNaiveInstructions();
		optimize();
		simpleColorRegisters();
		createChunk();
		assert theCode.startingChunk() == chunk;
	}

	/**
	 * Run the translator on the provided {@link A_RawFunction} to produce an
	 * optimized {@link L2Chunk} that is then written back into the code for
	 * subsequent executions.  Also update the {@link Interpreter}'s chunk and
	 * offset to use this new chunk right away.  If the code was a primitive,
	 * make sure to adjust the offset to just beyond its {@link
	 * L2_TRY_PRIMITIVE} instruction, which must have <em>already</em> been
	 * attempted and failed for us to have reached the {@link
	 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO} that caused this
	 * optimization to happen.
	 *
	 * @param code
	 *        The {@link A_RawFunction} to optimize.
	 * @param optimizationLevel
	 *        How much optimization to attempt.
	 * @param interpreter
	 *        The {@link Interpreter} used for folding expressions, and to be
	 *        updated with the new chunk and post-primitive offset.
	 */
	public static void translateToLevelTwo (
		final A_RawFunction code,
		final OptimizationLevel optimizationLevel,
		final Interpreter interpreter)
	{
		final L2Translator translator = new L2Translator(
			code, optimizationLevel, interpreter);
		translator.translate();
	}

	/**
	 * Create a chunk that will perform a naive translation of the current
	 * method to Level Two.  The naïve translation creates a counter that is
	 * decremented each time the method is invoked.  When the counter reaches
	 * zero, the method will be retranslated (with deeper optimization).
	 *
	 * @return The {@linkplain L2Chunk level two chunk} corresponding to the
	 * {@linkplain #codeOrNull} to be translated.
	 */
	public static L2Chunk createChunkForFirstInvocation ()
	{
		return new L2Translator().chunk();
	}
}

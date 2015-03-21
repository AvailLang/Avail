/**
 * L2Translator.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.optimizer;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.Primitive.Fallibility.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static java.lang.Math.max;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.MethodDescriptor.InternalLookupTree;
import com.avail.descriptor.MethodDescriptor.LookupTree;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.interpreter.primitive.P_058_RestartContinuation;
import com.avail.utility.*;
import com.avail.utility.evaluation.*;

/**
 * The {@code L2Translator} converts a level one {@linkplain FunctionDescriptor
 * function} into a {@linkplain L2Chunk level two chunk}.  It optimizes as it
 * does so, folding and inlining method invocations whenever possible.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2Translator
{
	/**
	 * {@code DebugFlag}s control which kinds of {@linkplain L2Translator
	 * translation} events should be {@linkplain #logger logged}.
	 */
	@InnerAccess static enum DebugFlag
	{
		/** Code generation. */
		GENERATION (false),

		/** Code optimization. */
		OPTIMIZATION (false),

		/** Data flow analysis. */
		DATA_FLOW (false),

		/** Dead instruction removal. */
		DEAD_INSTRUCTION_REMOVAL (false);

		/** The {@linkplain Logger logger}. */
		@InnerAccess final Logger logger;

		/**
		 * Should translation events of this kind be logged?
		 */
		private boolean shouldLog;

		/**
		 * Should translation events of this kind be logged?
		 *
		 * @return {@code true} if translation events of this kind should be
		 *         logged, {@code false} otherwise.
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
				logger.log(level, String.format(format, args), exception);
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
			final Continuation1<Continuation2<String, Throwable>> continuation)
		{
			if (shouldLog && logger.isLoggable(level))
			{
				continuation.value(
					new Continuation2<String, Throwable>()
					{
						@Override
						public void value (
							final @Nullable String message,
							final @Nullable Throwable exception)
						{
							logger.log(level, message, exception);
						}
					});
			}
		}

		/**
		 * Construct a new {@link DebugFlag}.
		 *
		 * @param shouldLog
		 *        Should translation events of this kind be logged?
		 */
		private DebugFlag (final boolean shouldLog)
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
	final static int maxPolymorphismToInlineDispatch = 10;

	/**
	 * Use a series of instance equality checks if we're doing type testing for
	 * method dispatch code and the type is a non-meta enumeration with at most
	 * this number of instances.  Otherwise do a type test.
	 */
	final static int maxExpandedEqualityChecks = 3;

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
		private static OptimizationLevel[] all = values();

		/**
		 * Answer an array of all {@link OptimizationLevel} enumeration values.
		 *
		 * @return An array of all {@link OptimizationLevel} enum values.  Do
		 *         not modify the array.
		 */
		public static OptimizationLevel[] all ()
		{
			return all;
		}
	}

	/**
	 * The current {@link CompiledCodeDescriptor compiled code} being optimized.
	 */
	@InnerAccess @Nullable A_RawFunction codeOrNull;

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
	@InnerAccess OptimizationLevel optimizationLevel;

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
		final Interpreter theInterpreter = interpreter;
		assert theInterpreter != null;
		return theInterpreter;
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
	 * The current sequence of level two instructions.
	 */
	@InnerAccess final List<L2Instruction> instructions =
		new ArrayList<>();

	/**
	 * A RegisterSet for each of my {@link #instructions}.
	 */
	@InnerAccess final List<RegisterSet> instructionRegisterSets =
		new ArrayList<>();

	/**
	 * All {@link A_ChunkDependable contingent values} for which changes should
	 * cause the current {@linkplain L2Chunk level two chunk} to be invalidated.
	 */
	@InnerAccess A_Set contingentValues = SetDescriptor.empty();

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
	public static int firstArgumentRegisterIndex =
		FixedRegister.all().length;

	/**
	 * Answer the specified fixed register.
	 *
	 * @param registerEnum The {@link FixedRegister} identifying the register.
	 * @return The {@link L2ObjectRegister} named by the registerEnum.
	 */
	public L2ObjectRegister fixed (final FixedRegister registerEnum)
	{
		return architecturalRegisters.get(registerEnum.ordinal());
	}

	/**
	 * An {@link EnumMap} from each {@link FixedRegister} to its manifestation
	 * as an architectural {@link L2ObjectRegister}.
	 */
	@InnerAccess final EnumMap<FixedRegister, L2ObjectRegister>
		fixedRegisterMap;

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
	 * @param stackIndex A stack position, for example stackp.
	 * @return A {@linkplain L2ObjectRegister register} representing the stack
	 *         at the given position.
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
	 * Allocate a fresh {@linkplain L2ObjectRegister object register} that
	 * nobody else has used yet.
	 *
	 * @return The new register.
	 */
	public L2ObjectRegister newObjectRegister ()
	{
		return new L2ObjectRegister(nextUnique());
	}

	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.  Do not attempt
	 * to propagate type or constant information.
	 *
	 * @param operation The operation to invoke.
	 * @param operands The operands of the instruction.
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
	public A_RawFunction codeOrFail ()
	{
		final A_RawFunction c = codeOrNull;
		if (c == null)
		{
			throw new RuntimeException("L2Translator code was null");
		}
		return c;
	}

	/**
	 * Create a {@linkplain L2RegisterVector vector register} that represents
	 * the given {@linkplain List list} of {@linkplain L2ObjectRegister object
	 * registers}.  Answer an existing vector if an equivalent one is already
	 * defined.
	 *
	 * @param objectRegisters The list of object registers to aggregate.
	 * @return A new L2RegisterVector.
	 */
	public L2RegisterVector createVector (
		final List<L2ObjectRegister> objectRegisters)
	{
		final L2RegisterVector vector = new L2RegisterVector(objectRegisters);
		return vector;
	}

	/**
	 * Create a new {@link L2_LABEL} pseudo-instruction}.
	 *
	 * @param comment A description of the label.
	 * @return The new label.
	 */
	public L2Instruction newLabel (final String comment)
	{
		return new L2Instruction(
			L2_LABEL.instance,
			new L2CommentOperand(comment));
	}

	/**
	 * A {@linkplain Transformer2 transformer} which converts from a {@linkplain
	 * L2Register register} to another (or the same) register.  At the point
	 * when the transformation happens, a source register is replaced by the
	 * earliest known register to contain the same value, thereby attempting to
	 * eliminate newer registers introduced by moves and decomposable primitive
	 * pairs (e.g., <a,b>[1]).
	 */
	final Transformer3<
			L2Register,
			L2OperandType,
			RegisterSet,
			L2Register>
		normalizer =
			new Transformer3<
				L2Register,
				L2OperandType,
				RegisterSet,
				L2Register>()
			{
				@Override
				public L2Register value (
					final @Nullable L2Register register,
					final @Nullable L2OperandType operandType,
					final @Nullable RegisterSet registerSet)
				{
					assert register != null;
					assert operandType != null;
					assert registerSet != null;
					return registerSet.normalize(register, operandType);
				}
			};

	/**
	 * Attempt to inline an invocation of this method definition.  If it can be
	 * (and was) inlined, return the primitive function; otherwise return null.
	 *
	 * @param function
	 *            The {@linkplain FunctionDescriptor function} to be inlined or
	 *            invoked.
	 * @param args
	 *            A {@link List} of {@linkplain L2ObjectRegister registers}
	 *            holding the actual constant values used to look up the method
	 *            definition for the call.
	 * @param registerSet
	 *            A {@link RegisterSet} indicating the current state of the
	 *            registers at this invocation point.
	 * @return
	 *            The provided method definition's primitive {@linkplain
	 *            FunctionDescriptor function}, or {@code null} otherwise.
	 */
	@InnerAccess
	@Nullable A_Function primitiveFunctionToInline (
		final A_Function function,
		final List<L2ObjectRegister> args,
		final RegisterSet registerSet)
	{
		final List<A_Type> argTypes = new ArrayList<>(args.size());
		for (final L2ObjectRegister arg : args)
		{
			argTypes.add(
				registerSet.hasTypeAt(arg) ? registerSet.typeAt(arg) : ANY.o());
		}
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
	 * The {@code L1NaiveTranslator} simply transliterates a sequence of
	 * {@linkplain L1Operation level one instructions} into one or more simple
	 * {@linkplain L2Instruction level two instructions}, under the assumption
	 * that further optimization steps will be able to transform this code into
	 * something much more efficient – without altering the level one semantics.
	 */
	public class L1NaiveTranslator
	implements L1OperationDispatcher
	{
		/**
		 * The {@linkplain CompiledCodeDescriptor raw function} to transliterate
		 * into level two code.
		 */
		private final A_RawFunction code = codeOrFail();

		/**
		 * The nybblecodes being optimized.
		 */
		private final A_Tuple nybbles = code.nybbles();

		/**
		 * The current level one nybblecode program counter during naive
		 * translation to level two.
		 */
		@InnerAccess int pc = 1;

		/**
		 * The current stack depth during naive translation to level two.
		 */
		@InnerAccess int stackp = code.maxStackDepth() + 1;

		/**
		 * The integer register to be used in this naive translation to hold the
		 * flag indicating whether this continuation can skip the type check
		 * when returning.
		 */
		@InnerAccess final L2IntegerRegister skipReturnCheckRegister =
			L2IntegerRegister.precolored(
				nextUnique(),
				L1InstructionStepper.skipReturnCheckRegister());

		/**
		 * Answer an integer extracted at the current program counter.  The
		 * program counter will be adjusted to skip over the integer.
		 *
		 * @return The integer encoded at the current nybblecode position.
		 */
		private int getInteger ()
		{
			final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc++);
			final int shift = firstNybble << 2;
			int count = 0xF & (int)(0x8421_1100_0000_0000L >>> shift);
			int value = 0;
			while (count-- > 0)
			{
				value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc++);
			}
			final int lowOff = 0xF & (int)(0x00AA_AA98_7654_3210L >>> shift);
			final int highOff = 0xF & (int)(0x0032_1000_0000_0000L >>> shift);
			return value + lowOff + (highOff << 4);
		}

		/**
		 * The current state of the registers after the most recently added
		 * {@link L2Instruction}.  The naive code generator keeps this up to
		 * date as it linearly transliterates each nybblecode.  Since the level
		 * one nybblecodes don't have branches or loops, this translation can be
		 * very simple.  Well, technically we have to allow branches in the
		 * level two code to deal with primitive attempts and interrupt
		 * processing, so we keep track of the RegisterSet information that has
		 * been asserted for labels that have not yet been emitted.  We deal
		 * specially with a reference to the {@link #restartLabel}, since use of
		 * a label phrase causes construction of a continuation which uses the
		 * restartLabel.
		 */
		@Nullable RegisterSet naiveRegisters = new RegisterSet(fixedRegisterMap);

		/**
		 * Answer the {@link RegisterSet} information at the current position in
		 * the level one code being transliterated.
		 *
		 * @return The current RegisterSet for level one transliteration.
		 */
		public RegisterSet naiveRegisters ()
		{
			final RegisterSet r = naiveRegisters;
			assert r != null;
			return r;
		}

		/**
		 * A {@link Map} from each label (an {@link L2Instruction} whose
		 * operation is an {@link L2_LABEL}) to the {@link RegisterSet} that
		 * holds the knowledge about the register states upon reaching that
		 * label.  Since labels (and only labels) may have multiple paths
		 * reaching them, we basically intersect the information from these
		 * paths.
		 */
		final Map<L2Instruction, RegisterSet> labelRegisterSets =
			new HashMap<>();

		/**
		 * A label (i.e., an {@link L2Instruction} whose operation is an {@link
		 * L2_LABEL}).  This is output right at the start of naive code
		 * generation.  It is reached through normal invocation, but it can also
		 * be reached by {@linkplain P_058_RestartContinuation restarting} a
		 * continuation created by a {@linkplain #L1Ext_doPushLabel()
		 * push-label} nybblecode instruction.
		 */
		final L2Instruction startLabel = newLabel("start");

		/**
		 * A label (i.e., an {@link L2Instruction} whose operation is an {@link
		 * L2_LABEL}).  This is only output after naive code generation if a
		 * {@linkplain #L1Ext_doPushLabel() push-label} instruction was
		 * encountered.  If so, a coda will be output starting with this
		 * restartLabel so that restarts of the created label continuations can
		 * correctly explode the fields into registers then jump to the main
		 * entry point at the start.
		 */
		final L2Instruction restartLabel = newLabel("restart");

		/**
		 * Whether any uses of the {@linkplain #L1Ext_doPushLabel() push-label}
		 * nybblecode instruction have been encountered so far.  After naive
		 * translation, if any push-labels occurred then the {@link
		 * #restartLabel} will be appended, followed by code that will explode
		 * the continuation and jump to the start.
		 */
		boolean anyPushLabelsEncountered = false;

		/**
		 * Answer the specified fixed register.
		 *
		 * @param registerEnum
		 *        The {@link FixedRegister} identifying the register.
		 * @return The {@link L2ObjectRegister} named by the registerEnum.
		 */
		public L2ObjectRegister fixed (final FixedRegister registerEnum)
		{
			return L2Translator.this.fixed(registerEnum);
		}

		/**
		 * Answer the register holding the specified continuation slot. The
		 * slots are the arguments, then the locals, then the stack entries. The
		 * first argument occurs just after the {@link FixedRegister}s.
		 *
		 * @param slotNumber
		 *        The index into the continuation's slots.
		 * @return A register representing that continuation slot.
		 */
		public L2ObjectRegister continuationSlot (final int slotNumber)
		{
			return L2Translator.this.continuationSlot(slotNumber);
		}

		/**
		 * Answer the register holding the specified argument/local number (the
		 * 1st argument is the 3rd architectural register).
		 *
		 * @param argumentNumber
		 *        The argument number for which the "architectural" register is
		 *        being requested.  If this is greater than the number of
		 *        arguments, then answer the register representing the local
		 *        variable at that position minus the number of registers.
		 * @return A register that represents the specified argument or local.
		 */
		public L2ObjectRegister argumentOrLocal (final int argumentNumber)
		{
			return L2Translator.this.argumentOrLocal(argumentNumber);
		}

		/**
		 * Answer the register representing the slot of the stack associated
		 * with the given index.
		 *
		 * @param stackIndex
		 *        A stack position.
		 * @return A {@linkplain L2ObjectRegister register} representing the
		 *         stack at the given position.
		 */
		public L2ObjectRegister stackRegister (final int stackIndex)
		{
			return L2Translator.this.stackRegister(stackIndex);
		}

		/**
		 * Allocate a fresh {@linkplain L2IntegerRegister integer register} that
		 * nobody else has used yet.
		 *
		 * @return The new register.
		 */
		public L2IntegerRegister newIntegerRegister ()
		{
			return L2Translator.this.newIntegerRegister();
		}

		/**
		 * Allocate a fresh {@linkplain L2ObjectRegister object register} that
		 * nobody else has used yet.
		 *
		 * @return The new register.
		 */
		public L2ObjectRegister newObjectRegister ()
		{
			return L2Translator.this.newObjectRegister();
		}

		/**
		 * Return the {@linkplain CompiledCodeDescriptor compiled Level One code}
		 * being translated.
		 *
		 * @return The code being translated.
		 */
		public A_RawFunction codeOrFail ()
		{
			return L2Translator.this.codeOrFail();
		}

		/**
		 * Create a {@linkplain L2RegisterVector vector register} that represents
		 * the given {@linkplain List list} of {@linkplain L2ObjectRegister object
		 * registers}.  Answer an existing vector if an equivalent one is already
		 * defined.
		 *
		 * @param objectRegisters
		 *        The list of object registers to aggregate.
		 * @return A new L2RegisterVector.
		 */
		public L2RegisterVector createVector (
			final List<L2ObjectRegister> objectRegisters)
		{
			return L2Translator.this.createVector(objectRegisters);
		}

		/**
		 * Create a new {@link L2_LABEL} pseudo-instruction}.
		 *
		 * @param comment
		 *        A description of the label.
		 * @return The new label.
		 */
		public L2Instruction newLabel (final String comment)
		{
			return L2Translator.this.newLabel(comment);
		}

		/**
		 * Answer the number of stack slots reserved for use by the code being
		 * optimized.
		 *
		 * @return The number of stack slots.
		 */
		public int numSlots ()
		{
			return numSlots;
		}

		/**
		 * Answer a {@linkplain List list} of {@linkplain L2ObjectRegister
		 * object registers} that correspond to the slots of the current
		 * {@linkplain A_Continuation continuation}.
		 *
		 * @param nSlots
		 *        The number of continuation slots.
		 * @return A list of object registers.
		 */
		public List<L2ObjectRegister> continuationSlotsList (final int nSlots)
		{
			final List<L2ObjectRegister> slots =
				new ArrayList<>(nSlots);
			for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
			{
				slots.add(continuationSlot(slotIndex));
			}
			return slots;
		}

		/**
		 * Create and add an {@link L2Instruction} with the given {@link
		 * L2Operation} and variable number of {@link L2Operand}s.
		 *
		 * @param operation The operation to invoke.
		 * @param operands The operands of the instruction.
		 */
		public void addInstruction (
			final L2Operation operation,
			final L2Operand... operands)
		{
			assert operation != L2_LABEL.instance
				: "Use newLabel() and addLabel(...) to add a label";
			final L2Instruction instruction =
				new L2Instruction(operation, operands);
			RegisterSet naiveRegs = naiveRegisters;
			if (naiveRegs == null)
			{
				naiveRegs = new RegisterSet(fixedRegisterMap);
				naiveRegisters = naiveRegs;
			}
			final L2Instruction normalizedInstruction =
				instruction.transformRegisters(naiveRegs.normalizer);
			final RegisterSet finalNaiveRegs = naiveRegs;
			DebugFlag.GENERATION.log(
				Level.FINEST,
				new Continuation1<Continuation2<String, Throwable>>()
				{
					@Override
					public void value (
						final @Nullable Continuation2<String, Throwable> log)
					{
						assert log != null;
						final StringBuilder builder = new StringBuilder(100);
						finalNaiveRegs.debugOn(builder);
						log.value(
							String.format(
								"%s%n\t#%d = %s",
								builder.toString().replace("\n", "\n\t"),
								instructions.size(),
								normalizedInstruction),
							null);
					}
				});
			normalizedInstruction.setOffset(instructions.size());
			instructions.add(normalizedInstruction);
			final List<L2Instruction> successors =
				new ArrayList<>(normalizedInstruction.targetLabels());
			if (normalizedInstruction.operation.reachesNextInstruction())
			{
				successors.add(0, null);
			}
			final int successorsSize = successors.size();
			final List<RegisterSet> successorRegisterSets =
				new ArrayList<>(successorsSize);
			for (int i = 0; i < successorsSize; i++)
			{
				successorRegisterSets.add(new RegisterSet(naiveRegs));
			}
			normalizedInstruction.propagateTypes(
				successorRegisterSets,
				L2Translator.this);
			naiveRegisters = null;
			for (int i = 0; i < successorsSize; i++)
			{
				final L2Instruction successor = successors.get(i);
				final RegisterSet successorRegisterSet =
					successorRegisterSets.get(i);
				if (successor == null)
				{
					naiveRegisters = successorRegisterSet;
				}
				else
				{
					assert successor.operation == L2_LABEL.instance;
					if (successor != startLabel)
					{
						assert !instructions.contains(successor)
							: "Backward branch in level one transliteration";
						final RegisterSet existing =
							labelRegisterSets.get(successor);
						if (existing == null)
						{
							labelRegisterSets.put(
								successor,
								successorRegisterSet);
						}
						else
						{
							existing.mergeFrom(successorRegisterSet);
						}
					}
				}
			}
		}

		/**
		 * Add a label instruction previously constructed with {@link
		 * #newLabel(String)}.
		 *
		 * @param label
		 *        An {@link L2Instruction} whose operation is {@link L2_LABEL}.
		 */
		public void addLabel (final L2Instruction label)
		{
			assert label.operation == L2_LABEL.instance;
			assert label.offset() == -1;
			label.setOffset(instructions.size());
			instructions.add(label);
			final RegisterSet storedRegisterSet = labelRegisterSets.get(label);
			if (storedRegisterSet != null)
			{
				final RegisterSet naiveRegs = naiveRegisters;
				if (naiveRegs != null)
				{
					storedRegisterSet.mergeFrom(naiveRegs);
				}
				naiveRegisters = storedRegisterSet;
			}
			DebugFlag.GENERATION.log(
				Level.FINEST,
				new Continuation1<Continuation2<String, Throwable>>()
				{
					@Override
					public void value (
						final @Nullable Continuation2<String, Throwable> log)
					{
						assert log != null;
						final StringBuilder builder = new StringBuilder(100);
						if (naiveRegisters == null)
						{
							builder.append("\n[[[no RegisterSet]]]");
						}
						else
						{
							naiveRegisters().debugOn(builder);
						}
						log.value(
							String.format(
								"%s%n\t#%d = %s",
								builder.toString().replace("\n", "\n\t"),
								instructions.size() - 1,
								label),
							null);
					}
				});
		}

		/**
		 * Generate instruction(s) to move the given {@link AvailObject} into
		 * the specified {@link L2Register}.
		 *
		 * @param value The value to move.
		 * @param destinationRegister Where to move it.
		 */
		public void moveConstant (
			final A_BasicObject value,
			final L2ObjectRegister destinationRegister)
		{
			if (value.equalsNil())
			{
				moveRegister(fixed(NULL), destinationRegister);
			}
			else
			{
				addInstruction(
					L2_MOVE_CONSTANT.instance,
					new L2ConstantOperand(value),
					new L2WritePointerOperand(destinationRegister));
			}
		}

		/**
		 * Generate instructions to move {@linkplain NilDescriptor#nil() nil}
		 * into the specified {@link L2ObjectRegister register}.
		 *
		 * @param destinationRegister Which register to clear.
		 */
		public void moveNil (
			final L2ObjectRegister destinationRegister)
		{
			addInstruction(
				L2_MOVE.instance,
				new L2ReadPointerOperand(fixed(NULL)),
				new L2WritePointerOperand(destinationRegister));
		}

		/**
		 * Generate instructions to move {@linkplain NilDescriptor#nil() nil}
		 * into each of the specified {@link L2ObjectRegister registers}.
		 *
		 * @param destinationRegisters Which registers to clear.
		 */
		public void moveNils (
			final Collection<L2ObjectRegister> destinationRegisters)
		{
			for (final L2ObjectRegister destinationRegister : destinationRegisters)
			{
				addInstruction(
					L2_MOVE.instance,
					new L2ReadPointerOperand(fixed(NULL)),
					new L2WritePointerOperand(destinationRegister));
			}
		}

		/**
		 * Generate instruction(s) to move from one register to another.
		 *
		 * @param sourceRegister Where to read the AvailObject.
		 * @param destinationRegister Where to write the AvailObject.
		 */
		public void moveRegister (
			final L2ObjectRegister sourceRegister,
			final L2ObjectRegister destinationRegister)
		{
			// Elide if the registers are the same.
			if (sourceRegister != destinationRegister)
			{
				addInstruction(
					L2_MOVE.instance,
					new L2ReadPointerOperand(sourceRegister),
					new L2WritePointerOperand(destinationRegister));
			}
		}

		/**
		 * Reify the current {@linkplain A_Continuation continuation}.
		 *
		 * @param slots
		 *        A {@linkplain List list} containing the {@linkplain
		 *        L2ObjectRegister object registers} that correspond to the
		 *        slots of the current continuation.
		 * @param newContinuationRegister
		 *        The destination register for the reified continuation.
		 * @param resumeLabel
		 *        Where to resume execution of the current continuation.
		 */
		public void reify (
			final List<L2ObjectRegister> slots,
			final L2ObjectRegister newContinuationRegister,
			final L2Instruction resumeLabel)
		{
			addInstruction(
				L2_CREATE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2ImmediateOperand(pc),
				new L2ImmediateOperand(stackp),
				new L2ReadIntOperand(skipReturnCheckRegister),
				new L2ReadVectorOperand(createVector(slots)),
				new L2PcOperand(resumeLabel),
				new L2WritePointerOperand(newContinuationRegister));
		}

		/**
		 * Add a label that indicates unreachable code.
		 *
		 * @param label
		 *        The label.
		 */
		public void unreachableCode (final L2Instruction label)
		{
			addLabel(label);
			addInstruction(L2_UNREACHABLE_CODE.instance);
			naiveRegisters = null;
		}

		/**
		 * A memento to be used for coordinating code generation between the
		 * branches of an {@link InternalLookupTree}.
		 */
		class InternalNodeMemento
		{
			/**
			 * Where to jump if the {@link InternalLookupTree}'s type test is
			 * false.
			 */
			final L2Instruction failCheckLabel;

			/**
			 * Construct a new memento.  Make the label something meaningful to
			 * make it easier to decipher.
			 *
			 * @param argumentIndexToTest
			 *            The subscript of the argument being tested.
			 * @param typeToTest
			 *            The type to test the argument against.
			 * @param branchLabelCounter
			 *            An int unique to this dispatch tree, monotonically
			 *            allocated at each branch.
			 */
			public InternalNodeMemento (
				final int argumentIndexToTest,
				final A_Type typeToTest,
				final int branchLabelCounter)
			{
				final String labelName = "Failed type test";
//				final String labelName = String.format(
//					"Failed type test [#%d]: #%d ∉ %s",
//					branchLabelCounter,
//					argumentIndexToTest,
//					typeToTest);
				this.failCheckLabel = newLabel(labelName);
			}
		}

		/**
		 * Generate code to perform a multimethod invocation.
		 *
		 * @param bundle
		 *            The {@linkplain MessageBundleDescriptor message bundle} to
		 *            invoke.
		 * @param expectedType
		 *            The expected return {@linkplain TypeDescriptor type}.
		 */
		private void generateCall (
			final A_Bundle bundle,
			final A_Type expectedType)
		{
			final A_Method method = bundle.bundleMethod();
			contingentValues =
				contingentValues.setWithElementCanDestroy(method, true);
			final L2Instruction afterCall =
				newLabel("After call"); // + bundle.message().toString());
			final int nArgs = method.numArgs();
			final List<A_Type> argTypes = new ArrayList<>(nArgs);
			final int initialStackp = stackp;
			for (int i = nArgs - 1; i >= 0; i--)
			{
				argTypes.add(
					naiveRegisters().typeAt(stackRegister(stackp + i)));
			}
			final List<A_Definition> allPossible = new ArrayList<>();
			for (final A_Definition definition : method.definitionsTuple())
			{
				if (definition.bodySignature().couldEverBeInvokedWith(argTypes))
				{
					allPossible.add(definition);
					if (allPossible.size() > maxPolymorphismToInlineDispatch)
					{
						// It has too many possible implementations to be worth
						// inlining all of them.
						generateSlowPolymorphicCall(
							bundle, expectedType, false);
						return;
					}
				}
			}
			// NOTE: Don't use the method's testing tree.  It encodes
			// information about the known types of arguments that may be too
			// weak for our purposes.  It's still correct, but it may produce
			// extra tests that supplying this site's argTypes would eliminate.
			final LookupTree tree =
				LookupTree.createRoot(method, allPossible, argTypes);
			final Mutable<Integer> branchLabelCounter = new Mutable<Integer>(1);
			tree.<InternalNodeMemento>traverseEntireTree(
				// preInternalNode
				new Transformer2<Integer, A_Type, InternalNodeMemento>()
				{
					@Override
					public @Nullable InternalNodeMemento value (
						final @Nullable Integer argumentIndexToTest,
						final @Nullable A_Type typeToTest)
					{
						assert argumentIndexToTest != null;
						assert typeToTest != null;
						assert stackp == initialStackp;
						final InternalNodeMemento memento =
							new InternalNodeMemento(
								argumentIndexToTest,
								typeToTest,
								branchLabelCounter.value++);
						final L2ObjectRegister argRegister = stackRegister(
							stackp + nArgs - argumentIndexToTest);
						final A_Type existingType =
							naiveRegisters().typeAt(argRegister);
						// Strengthen the test based on what's already known
						// about the argument.  Eventually we can decide whether
						// to strengthen based on the expected cost of the type
						// check.
						final A_Type intersection =
							existingType.typeIntersection(typeToTest);
						assert
							!intersection.isBottom()
							: "Impossible condition should have been excluded";
						if (intersection.isEnumeration()
							&& !intersection.isInstanceMeta()
							&& intersection.instanceCount().extractInt() <=
								maxExpandedEqualityChecks)
						{
							final A_Set instances = intersection.instances();
							if (instances.setSize() == 1)
							{
								addInstruction(
									L2_JUMP_IF_DOES_NOT_EQUAL_CONSTANT.instance,
									new L2PcOperand(memento.failCheckLabel),
									new L2ReadPointerOperand(argRegister),
									new L2ConstantOperand(
										instances.iterator().next()));
							}
							else
							{
								final L2Instruction matchedLabel =
									newLabel("matched enumeration");
								for (final A_BasicObject instance : instances)
								{
									addInstruction(
										L2_JUMP_IF_EQUALS_CONSTANT.instance,
										new L2PcOperand(matchedLabel),
										new L2ReadPointerOperand(argRegister),
										new L2ConstantOperand(instance));
								}
								addInstruction(
									L2_JUMP.instance,
									new L2PcOperand(memento.failCheckLabel));
								addLabel(matchedLabel);
							}
						}
						else
						{
							addInstruction(
								L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT.instance,
								new L2PcOperand(memento.failCheckLabel),
								new L2ReadPointerOperand(argRegister),
								new L2ConstantOperand(intersection));
						}
						return memento;
					}
				},
				// intraInternalNode
				new Continuation1<InternalNodeMemento> ()
				{
					@Override
					public void value (
						final @Nullable InternalNodeMemento memento)
					{
						assert memento != null;
						if (naiveRegisters != null)
						{
							addInstruction(
								L2_JUMP.instance,
								new L2PcOperand(afterCall));
						}
						addLabel(memento.failCheckLabel);
					}
				},
				// postInternalNode
				new Continuation1<InternalNodeMemento> ()
				{
					@Override
					public void value (
						final @Nullable InternalNodeMemento memento)
					{
						assert memento != null;
						if (naiveRegisters != null)
						{
							addInstruction(
								L2_JUMP.instance,
								new L2PcOperand(afterCall));
						}
					}
				},
				// forEachLeafNode
				new Continuation1<List<A_Definition>>()
				{
					@Override
					public void value(
						final @Nullable List<A_Definition> solutions)
					{
						assert solutions != null;
						assert stackp == initialStackp;
						A_Definition solution;
						if (solutions.size() == 1
							&& (solution = solutions.get(0)).isInstanceOf(
								METHOD_DEFINITION.o()))
						{
							generateFunctionInvocation(
								solution.bodyBlock(), expectedType, false);
							// Reset for next type test or call.
							stackp = initialStackp;
						}
						else
						{
							// Collect the arguments into a tuple and invoke the
							// handler for failed method lookups.
							final A_Set solutionsSet =
								SetDescriptor.fromCollection(solutions);
							final List<L2ObjectRegister> argumentRegisters =
								new ArrayList<>(nArgs);
							for (int i = nArgs - 1; i >= 0; i--)
							{
								final L2ObjectRegister arg =
									stackRegister(stackp + i);
								assert naiveRegisters().hasTypeAt(arg);
								argumentRegisters.add(arg);
							}
							final L2ObjectRegister errorCodeReg =
								newObjectRegister();
							addInstruction(
								L2_DIAGNOSE_LOOKUP_FAILURE.instance,
								new L2ConstantOperand(solutionsSet),
								new L2WritePointerOperand(errorCodeReg));
							final L2ObjectRegister invalidSendReg =
								newObjectRegister();
							addInstruction(
								L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
								new L2WritePointerOperand(invalidSendReg));
							// Make the method itself accessible to the code.
							final L2ObjectRegister methodReg =
								newObjectRegister();
							moveConstant(method, methodReg);
							// Collect the arguments into a tuple.
							final L2ObjectRegister argumentsTupleReg =
								newObjectRegister();
							addInstruction(
								L2_CREATE_TUPLE.instance,
								new L2ReadVectorOperand(
									createVector(argumentRegisters)),
								new L2WritePointerOperand(argumentsTupleReg));
							final List<L2ObjectRegister> slots =
								continuationSlotsList(numSlots);
							// The continuation must be reified prior to
							// invoking the failure function.
							final L2Instruction unreachable =
								newLabel("unreachable");
							final L2ObjectRegister reifiedRegister =
								newObjectRegister();
							reify(slots, reifiedRegister, unreachable);
							addInstruction(
								L2_INVOKE.instance,
								new L2ReadPointerOperand(reifiedRegister),
								new L2ReadPointerOperand(invalidSendReg),
								new L2ReadVectorOperand(createVector(
									Arrays.asList(
										errorCodeReg,
										methodReg,
										argumentsTupleReg))),
								new L2ImmediateOperand(1));
							unreachableCode(unreachable);
						}
					}
				});
			addLabel(afterCall);
			stackp = initialStackp + nArgs - 1;
		}

		/**
		 * Generate code to perform a multimethod invocation.
		 *
		 * @param bundle
		 *            The {@linkplain MessageBundleDescriptor message bundle} to
		 *            invoke.
		 * @param expectedType
		 *            The expected return {@linkplain TypeDescriptor type}.
		 */
		private void generateSuperCall (
			final A_Bundle bundle,
			final A_Type expectedType)
		{
			final A_Method method = bundle.bundleMethod();
			contingentValues =
				contingentValues.setWithElementCanDestroy(method, true);
			final L2Instruction afterCall =
				newLabel("After super call"); // + bundle.message().toString());
			final int nArgs = method.numArgs();
			final List<A_Type> argTypes = new ArrayList<>(nArgs);
			final int initialStackp = stackp;
			for (int i = nArgs * 2 - 2; i >= 0; i -= 2)
			{
				final L2ObjectRegister typeReg = stackRegister(stackp + i);
				final A_Type meta = naiveRegisters().typeAt(typeReg);
				final A_Type type = meta.instance();
				// Note that we lose information about exact types being used
				// for a lookup.  At most we'd save some redundant type tests.
				argTypes.add(type);
			}
			final List<A_Definition> allPossible = new ArrayList<>();
			for (final A_Definition definition : method.definitionsTuple())
			{
				if (definition.bodySignature().couldEverBeInvokedWith(argTypes))
				{
					allPossible.add(definition);
					if (allPossible.size() > maxPolymorphismToInlineDispatch)
					{
						// It has too many possible implementations to be worth
						// inlining all of them.
						generateSlowPolymorphicCall(
							bundle, expectedType, true);
						return;
					}
				}
			}
			// NOTE: Don't use the method's testing tree.  It encodes
			// information about the known types of arguments that may be too
			// weak for our purposes.  It's still correct, but it may produce
			// extra tests that supplying this site's argTypes would eliminate.
			final LookupTree tree =
				LookupTree.createRoot(method, allPossible, argTypes);
			final Mutable<Integer> branchLabelCounter = new Mutable<Integer>(1);
			tree.<InternalNodeMemento>traverseEntireTree(
				// preInternalNode
				new Transformer2<Integer, A_Type, InternalNodeMemento>()
				{
					@Override
					public @Nullable InternalNodeMemento value (
						final @Nullable Integer argumentIndexToTest,
						final @Nullable A_Type typeToTest)
					{
						assert argumentIndexToTest != null;
						assert typeToTest != null;
						assert stackp == initialStackp;
						final InternalNodeMemento memento =
							new InternalNodeMemento(
								argumentIndexToTest,
								typeToTest,
								branchLabelCounter.value++);
						final L2ObjectRegister argTypeReg = stackRegister(
							stackp + (nArgs - argumentIndexToTest) * 2);
						final A_Type existingType =
							naiveRegisters().typeAt(argTypeReg).instance();
						// Strengthen the test based on what's already known
						// about the argument.  Eventually we can decide whether
						// to strengthen based on the expected cost of the type
						// check.
						final A_Type intersection =
							existingType.typeIntersection(typeToTest);
						assert
							!intersection.isBottom()
							: "Impossible condition should have been excluded";
						addInstruction(
							L2_JUMP_IF_IS_NOT_SUBTYPE_OF_CONSTANT.instance,
							new L2PcOperand(memento.failCheckLabel),
							new L2ReadPointerOperand(argTypeReg),
							new L2ConstantOperand(intersection));
						return memento;
					}
				},
				// intraInternalNode
				new Continuation1<InternalNodeMemento> ()
				{
					@Override
					public void value (
						final @Nullable InternalNodeMemento memento)
					{
						assert memento != null;
						if (naiveRegisters != null)
						{
							addInstruction(
								L2_JUMP.instance,
								new L2PcOperand(afterCall));
						}
						addLabel(memento.failCheckLabel);
					}
				},
				// postInternalNode
				new Continuation1<InternalNodeMemento> ()
				{
					@Override
					public void value (
						final @Nullable InternalNodeMemento memento)
					{
						assert memento != null;
						if (naiveRegisters != null)
						{
							addInstruction(
								L2_JUMP.instance,
								new L2PcOperand(afterCall));
						}
					}
				},
				// forEachLeafNode
				new Continuation1<List<A_Definition>>()
				{
					@Override
					public void value(
						final @Nullable List<A_Definition> solutions)
					{
						assert solutions != null;
						assert stackp == initialStackp;
						A_Definition solution;
						if (solutions.size() == 1
							&& (solution = solutions.get(0)).isInstanceOf(
								METHOD_DEFINITION.o()))
						{
							generateFunctionInvocation(
								solution.bodyBlock(), expectedType, true);
							// Reset for next type test or call.
							stackp = initialStackp;
						}
						else
						{
							// Collect the arguments into a tuple and invoke the
							// handler for failed method lookups.
							final A_Set solutionsSet =
								SetDescriptor.fromCollection(solutions);
							final List<L2ObjectRegister> argumentRegisters =
								new ArrayList<>(nArgs);
							for (int i = (nArgs - 1) * 2 + 1; i >= 1; i -= 2)
							{
								final L2ObjectRegister argReg =
									stackRegister(stackp + i);
								argumentRegisters.add(argReg);
							}
							final L2ObjectRegister errorCodeReg =
								newObjectRegister();
							addInstruction(
								L2_DIAGNOSE_LOOKUP_FAILURE.instance,
								new L2ConstantOperand(solutionsSet),
								new L2WritePointerOperand(errorCodeReg));
							final L2ObjectRegister invalidSendReg =
								newObjectRegister();
							addInstruction(
								L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
								new L2WritePointerOperand(invalidSendReg));
							// Make the method itself accessible to the code.
							final L2ObjectRegister methodReg =
								newObjectRegister();
							moveConstant(method, methodReg);
							// Collect the arguments into a tuple.
							final L2ObjectRegister argumentsTupleReg =
								newObjectRegister();
							addInstruction(
								L2_CREATE_TUPLE.instance,
								new L2ReadVectorOperand(
									createVector(argumentRegisters)),
								new L2WritePointerOperand(argumentsTupleReg));
							final List<L2ObjectRegister> slots =
								continuationSlotsList(numSlots);
							// The continuation must be reified prior to
							// invoking the failure function.
							final L2Instruction unreachable =
								newLabel("unreachable");
							final L2ObjectRegister reifiedRegister =
								newObjectRegister();
							reify(slots, reifiedRegister, unreachable);
							addInstruction(
								L2_INVOKE.instance,
								new L2ReadPointerOperand(reifiedRegister),
								new L2ReadPointerOperand(invalidSendReg),
								new L2ReadVectorOperand(createVector(
									Arrays.asList(
										errorCodeReg,
										methodReg,
										argumentsTupleReg))),
								new L2ImmediateOperand(1));
							unreachableCode(unreachable);
						}
					}
				});
			addLabel(afterCall);
			stackp = initialStackp + nArgs * 2 - 1;
		}

		/**
		 * Generate code to perform a monomorphic invocation.  The exact method
		 * definition is known, so no lookup is needed at this position in the
		 * code stream.
		 *
		 * @param originalFunction
		 *            The {@linkplain FunctionDescriptor function} to invoke.
		 * @param expectedType
		 *            The expected return {@linkplain TypeDescriptor type}.
		 * @param argsAreInSuperLayout
		 *            Whether this was looked up as a super call, with the
		 *            arguments interspersed with their argument lookup types on
		 *            the stack.
		 */
		public void generateFunctionInvocation (
			final A_Function originalFunction,
			final A_Type expectedType,
			final boolean argsAreInSuperLayout)
		{
			// The registers holding slot values that will constitute the
			// continuation *during* a non-primitive call.
			final List<L2ObjectRegister> preSlots = new ArrayList<>(numSlots);
			for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
			{
				preSlots.add(continuationSlot(slotIndex));
			}
			final L2ObjectRegister expectedTypeReg = newObjectRegister();
			final L2ObjectRegister failureObjectReg = newObjectRegister();
			final A_RawFunction originalCode = originalFunction.code();
			final int nArgs = originalCode.numArgs();
			final List<L2ObjectRegister> preserved = new ArrayList<>(preSlots);
			assert preserved.size() == numSlots;
			final List<L2ObjectRegister> args = new ArrayList<>(nArgs);
			final List<A_Type> argTypes = new ArrayList<>(nArgs);
			for (int i = nArgs; i >= 1; i--)
			{
				if (argsAreInSuperLayout)
				{
					// Skip an argument type slot.  Note that argTypes will
					// still get the argument's type, not the argument *lookup*
					// type.
					preSlots.set(
						numArgs + numLocals + stackp - 1,
						fixed(NULL));
					stackp++;
				}
				final L2ObjectRegister arg = stackRegister(stackp);
				assert naiveRegisters().hasTypeAt(arg);
				argTypes.add(0, naiveRegisters().typeAt(arg));
				args.add(0, arg);
				preSlots.set(
					numArgs + numLocals + stackp - 1,
					fixed(NULL));
				stackp++;
			}
			stackp--;
			final L2ObjectRegister resultRegister = stackRegister(stackp);
			preSlots.set(numArgs + numLocals + stackp - 1, expectedTypeReg);
			// preSlots now contains the registers that will constitute the
			// continuation during a non-primitive call.
			@Nullable L2ObjectRegister functionReg = null;
			A_Function functionToInvoke = originalFunction;
			final Primitive prim = Primitive.byPrimitiveNumberOrNull(
				originalCode.primitiveNumber());
			if (prim != null && prim.hasFlag(Invokes))
			{
				// If it's an Invokes primitive, then allow it to substitute a
				// direct invocation of the actual function.  Note that the call
				// to foldOutInvoker may alter the arguments list to correspond
				// with the actual function being invoked.
				assert !prim.hasFlag(CanInline);
				assert !prim.hasFlag(CanFold);
				functionReg = prim.foldOutInvoker(args, this);
				if (functionReg != null)
				{
					// Replace the argument types to agree with updated args.
					argTypes.clear();
					for (final L2ObjectRegister arg : args)
					{
						assert naiveRegisters().hasTypeAt(arg);
						argTypes.add(naiveRegisters().typeAt(arg));
					}
					// Allow the target function to be folded or inlined in
					// place of the invocation if the exact function is known
					// statically.
					if (naiveRegisters().hasConstantAt(functionReg))
					{
						functionToInvoke =
							naiveRegisters().constantAt(functionReg);
					}
				}
				else
				{
					assert args.size() == nArgs;
					assert argTypes.size() == nArgs;
				}
			}
			final A_Function primFunction = primitiveFunctionToInline(
				functionToInvoke, args, naiveRegisters());
			// The convergence point for primitive success and failure paths.
			final L2Instruction successLabel;
			successLabel = newLabel("success"); //+bundle.message().atomName());
			final L2ObjectRegister reifiedCallerRegister = newObjectRegister();
			if (primFunction != null)
			{
				// Inline the primitive. Attempt to fold it if the primitive
				// says it's foldable and the arguments are all constants.
				final Mutable<Boolean> canFailPrimitive = new Mutable<>(false);
				final A_BasicObject folded = emitInlinePrimitiveAttempt(
					primFunction,
					args,
					resultRegister,
					preserved,
					expectedType,
					failureObjectReg,
					successLabel,
					canFailPrimitive,
					naiveRegisters());
				if (folded != null)
				{
					// It was folded to a constant.
					assert !canFailPrimitive.value;
					// Folding should have checked this already.
					assert folded.isInstanceOf(expectedType);
					return;
				}
				if (!canFailPrimitive.value)
				{
					// Primitive attempt was not inlined, but it can't fail, so
					// it didn't generate any branches to successLabel.
					return;
				}
			}
			// Deal with the non-primitive or failed-primitive case.  First
			// generate the move that puts the expected type on the stack.
			moveConstant(expectedType, expectedTypeReg);
			// Now deduce what the registers will look like after the
			// non-primitive call.  That should be similar to the preSlots'
			// registers.
			A_Map postSlotTypesMap = MapDescriptor.empty();
			A_Map postSlotConstants = MapDescriptor.empty();
			A_Set nullPostSlots = SetDescriptor.empty();
			final List<L2ObjectRegister> postSlots = new ArrayList<>(numSlots);
			for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
			{
				final L2ObjectRegister reg = preSlots.get(slotIndex - 1);
				A_Type slotType = naiveRegisters().hasTypeAt(reg)
					? naiveRegisters().typeAt(reg)
					: null;
				if (reg == expectedTypeReg)
				{
					// I.e., upon return from the call, this slot will contain
					// an *instance* of expectedType.
					slotType = expectedType;
				}
				if (slotType != null)
				{
					postSlotTypesMap = postSlotTypesMap.mapAtPuttingCanDestroy(
						IntegerDescriptor.fromInt(slotIndex),
						slotType,
						true);
				}
				if (reg != expectedTypeReg
					&& naiveRegisters().hasConstantAt(reg))
				{
					final A_BasicObject constant =
						naiveRegisters().constantAt(reg);
					if (constant.equalsNil())
					{
						nullPostSlots = nullPostSlots.setWithElementCanDestroy(
							IntegerDescriptor.fromInt(slotIndex),
							true);
					}
					else
					{
						postSlotConstants =
							postSlotConstants.mapAtPuttingCanDestroy(
								IntegerDescriptor.fromInt(slotIndex),
								constant,
								true);
					}
				}
				// But the place we want to write this slot during explosion is
				// the architectural register.  Eventually we'll support a
				// throw-away target register for don't-cares like nil stack
				// slots.
				postSlots.add(continuationSlot(slotIndex));
			}
			final L2Instruction postCallLabel = newLabel("postCall");
			reify(preSlots, reifiedCallerRegister, postCallLabel);
			final A_Type guaranteedReturnType;
			if (functionReg == null)
			{
				// If the function is not already in a register, then we didn't
				// inline an Invokes primitive.  Use the original function.
				functionReg = newObjectRegister();
				moveConstant(originalFunction, functionReg);
				guaranteedReturnType = originalFunction.kind().returnType();
			}
			else
			{
				guaranteedReturnType =
					naiveRegisters().typeAt(functionReg).returnType();
			}
			final boolean canSkip =
				guaranteedReturnType.isSubtypeOf(expectedType);
			// Now invoke the method definition's body.
			if (primFunction != null)
			{
				// Already tried the primitive.
				addInstruction(
					L2_INVOKE_AFTER_FAILED_PRIMITIVE.instance,
					new L2ReadPointerOperand(reifiedCallerRegister),
					new L2ReadPointerOperand(functionReg),
					new L2ReadVectorOperand(createVector(args)),
					new L2ReadPointerOperand(failureObjectReg),
					new L2ImmediateOperand(canSkip ? 1 : 0));
			}
			else
			{
				addInstruction(
					L2_INVOKE.instance,
					new L2ReadPointerOperand(reifiedCallerRegister),
					new L2ReadPointerOperand(functionReg),
					new L2ReadVectorOperand(createVector(args)),
					new L2ImmediateOperand(canSkip ? 1 : 0));
			}
			// The method being invoked will run until it returns, and the next
			// instruction will be here (if the chunk isn't invalidated in the
			// meanwhile).
			addLabel(postCallLabel);

			// After the call returns, the callerRegister will contain the
			// continuation to be exploded.
			addInstruction(
				L2_REENTER_L2_CHUNK.instance,
				new L2WritePointerOperand(fixed(CALLER)));
			if (expectedType.isBottom())
			{
				unreachableCode(newLabel("unreachable"));
			}
			else
			{
				addInstruction(
					L2_EXPLODE_CONTINUATION.instance,
					new L2ReadPointerOperand(fixed(CALLER)),
					new L2WriteVectorOperand(createVector(postSlots)),
					new L2WritePointerOperand(fixed(CALLER)),
					new L2WritePointerOperand(fixed(FUNCTION)),
					new L2WriteIntOperand(skipReturnCheckRegister),
					new L2ConstantOperand(postSlotTypesMap),
					new L2ConstantOperand(postSlotConstants),
					new L2ConstantOperand(nullPostSlots),
					new L2ConstantOperand(codeOrFail().functionType()));
			}
			addLabel(successLabel);
		}

		/**
		 * Generate a slower, but much more compact invocation of a polymorphic
		 * method.
		 *
		 * @param bundle
		 *            The {@linkplain MessageBundleDescriptor message bundle}
		 *            containing the definition to invoke.
		 * @param expectedType
		 *            The expected return {@linkplain TypeDescriptor type}.
		 * @param argsAreInSuperLayout
		 *            Whether the arguments are interspersed with their argument
		 *            lookup types on the stack for a super call.
		 */
		private void generateSlowPolymorphicCall (
			final A_Bundle bundle,
			final A_Type expectedType,
			final boolean argsAreInSuperLayout)
		{
			final A_Method method = bundle.bundleMethod();
			// The registers holding slot values that will constitute the
			// continuation *during* a non-primitive call.
			final List<L2ObjectRegister> preSlots = new ArrayList<>(numSlots);
			for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
			{
				preSlots.add(continuationSlot(slotIndex));
			}
			final L2ObjectRegister expectedTypeReg = newObjectRegister();
			final int nArgs = method.numArgs();
			final List<L2ObjectRegister> preserved = new ArrayList<>(preSlots);
			assert preserved.size() == numSlots;
			final List<L2ObjectRegister> argRegs = new ArrayList<>(nArgs);
			final List<L2ObjectRegister> argTypeRegs =
				new ArrayList<L2ObjectRegister>();
			for (int i = nArgs; i >= 1; i--)
			{
				if (argsAreInSuperLayout)
				{
					// Skip an argument type slot.  Note that argTypes will
					// still get the argument's type, not the argument *lookup*
					// type.
					final L2ObjectRegister argTypeReg = stackRegister(stackp);
					argTypeRegs.add(argTypeReg);
					assert naiveRegisters().hasTypeAt(argTypeReg);
					preSlots.set(
						numArgs + numLocals + stackp - 1,
						fixed(NULL));
					stackp++;
				}
				final L2ObjectRegister argReg = stackRegister(stackp);
				assert naiveRegisters().hasTypeAt(argReg);
				argRegs.add(0, argReg);
				preSlots.set(
					numArgs + numLocals + stackp - 1,
					fixed(NULL));
				stackp++;
			}
			stackp--;
			preSlots.set(numArgs + numLocals + stackp - 1, expectedTypeReg);
			// preSlots now contains the registers that will constitute the
			// continuation during a non-primitive call.
			final L2ObjectRegister tempCallerRegister = newObjectRegister();
			moveRegister(fixed(CALLER), tempCallerRegister);
			// Deal with the non-primitive or failed-primitive case.  First
			// generate the move that puts the expected type on the stack.
			moveConstant(expectedType, expectedTypeReg);
			// Now deduce what the registers will look like after the
			// non-primitive call.  That should be similar to the preSlots'
			// registers.
			A_Map postSlotTypesMap = MapDescriptor.empty();
			A_Map postSlotConstants = MapDescriptor.empty();
			A_Set nullPostSlots = SetDescriptor.empty();
			final List<L2ObjectRegister> postSlots = new ArrayList<>(numSlots);
			for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
			{
				final L2ObjectRegister reg = preSlots.get(slotIndex - 1);
				A_Type slotType = naiveRegisters().hasTypeAt(reg)
					? naiveRegisters().typeAt(reg)
					: null;
				if (reg == expectedTypeReg)
				{
					// I.e., upon return from the call, this slot will contain
					// an *instance* of expectedType.
					slotType = expectedType;
				}
				if (slotType != null)
				{
					postSlotTypesMap = postSlotTypesMap.mapAtPuttingCanDestroy(
						IntegerDescriptor.fromInt(slotIndex),
						slotType,
						true);
				}
				if (reg != expectedTypeReg
					&& naiveRegisters().hasConstantAt(reg))
				{
					final A_BasicObject constant =
						naiveRegisters().constantAt(reg);
					if (constant.equalsNil())
					{
						nullPostSlots = nullPostSlots.setWithElementCanDestroy(
							IntegerDescriptor.fromInt(slotIndex),
							true);
					}
					else
					{
						postSlotConstants =
							postSlotConstants.mapAtPuttingCanDestroy(
								IntegerDescriptor.fromInt(slotIndex),
								constant,
								true);
					}
				}
				// But the place we want to write this slot during explosion is
				// the architectural register.  Eventually we'll support a
				// throw-away target register for don't-cares like nil stack
				// slots.
				postSlots.add(continuationSlot(slotIndex));
			}
			final L2Instruction postCallLabel =
				newLabel("postCall"); // + bundle.message().atomName());
			reify(preSlots, tempCallerRegister, postCallLabel);
			final L2Instruction lookupSucceeded = newLabel("lookupSucceeded");
			final L2ObjectRegister functionReg = newObjectRegister();
			final L2ObjectRegister errorCodeReg = newObjectRegister();
			if (argsAreInSuperLayout)
			{
				addInstruction(
					L2_LOOKUP_BY_TYPES.instance,
					new L2SelectorOperand(bundle),
					new L2ReadVectorOperand(createVector(argTypeRegs)),
					new L2WritePointerOperand(functionReg),
					new L2PcOperand(lookupSucceeded),
					new L2WritePointerOperand(errorCodeReg));
			}
			else
			{
				addInstruction(
					L2_LOOKUP_BY_VALUES.instance,
					new L2SelectorOperand(bundle),
					new L2ReadVectorOperand(createVector(argRegs)),
					new L2WritePointerOperand(functionReg),
					new L2PcOperand(lookupSucceeded),
					new L2WritePointerOperand(errorCodeReg));
			}
			// Emit the failure off-ramp.
			final L2ObjectRegister invalidSendReg = newObjectRegister();
			addInstruction(
				L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
				new L2WritePointerOperand(invalidSendReg));
			// Make the method itself accessible to the code.
			final L2ObjectRegister methodReg = newObjectRegister();
			moveConstant(method, methodReg);
			// Collect the arguments into a tuple.
			final L2ObjectRegister argumentsTupleReg = newObjectRegister();
			addInstruction(
				L2_CREATE_TUPLE.instance,
				new L2ReadVectorOperand(createVector(argRegs)),
				new L2WritePointerOperand(argumentsTupleReg));
			addInstruction(
				L2_INVOKE.instance,
				new L2ReadPointerOperand(tempCallerRegister),
				new L2ReadPointerOperand(invalidSendReg),
				new L2ReadVectorOperand(createVector(
					Arrays.asList(
						errorCodeReg,
						methodReg,
						argumentsTupleReg))),
				new L2ImmediateOperand(1));
			unreachableCode(newLabel("unreachable"));
			addLabel(lookupSucceeded);
			// Now invoke the method definition's body.  Without looking at the
			// definitions we can't determine if the return type check can be
			// skipped.
			addInstruction(
				L2_INVOKE.instance,
				new L2ReadPointerOperand(tempCallerRegister),
				new L2ReadPointerOperand(functionReg),
				new L2ReadVectorOperand(createVector(argRegs)),
				new L2ImmediateOperand(0));
			// The method being invoked will run until it returns, and the next
			// instruction will be here (if the chunk isn't invalidated in the
			// meanwhile).
			addLabel(postCallLabel);
			// After the call returns, the callerRegister will contain the
			// continuation to be exploded.
			addInstruction(
				L2_REENTER_L2_CHUNK.instance,
				new L2WritePointerOperand(fixed(CALLER)));
			addInstruction(
				L2_EXPLODE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2WriteVectorOperand(createVector(postSlots)),
				new L2WritePointerOperand(fixed(CALLER)),
				new L2WritePointerOperand(fixed(FUNCTION)),
				new L2WriteIntOperand(skipReturnCheckRegister),
				new L2ConstantOperand(postSlotTypesMap),
				new L2ConstantOperand(postSlotConstants),
				new L2ConstantOperand(nullPostSlots),
				new L2ConstantOperand(codeOrFail().functionType()));
		}

		/**
		 * Inline the primitive.  Attempt to fold it (evaluate it right now) if
		 * the primitive says it's foldable and the arguments are all constants.
		 * Answer the result if it was folded, otherwise null.  If it was
		 * folded, generate code to push the folded value.  Otherwise generate
		 * an invocation of the primitive, jumping to the successLabel on
		 * success.
		 *
		 * <p>
		 * Special case if the flag {@link
		 * com.avail.interpreter.Primitive.Flag#SpecialReturnConstant} is
		 * specified: Always fold it, since it's just a constant.
		 * </p>
		 *
		 * <p>
		 * Another special case if the flag {@link
		 * com.avail.interpreter.Primitive.Flag#SpecialReturnSoleArgument} is
		 * specified:  Don't generate an inlined primitive invocation, but
		 * instead generate a move from the argument register to the output.
		 * </p>
		 *
		 * <p>
		 * The flag {@link com.avail.interpreter.Primitive.Flag
		 * #SpecialReturnGlobalValue} indicates the operation simply returns the
		 * value of some (global) variable.  In that circumstance, output
		 * alternative code to read the variable without leaving the current
		 * continuation.
		 * </p>
		 *
		 * @param primitiveFunction
		 *            A {@linkplain FunctionDescriptor function} for which its
		 *            primitive might be inlined, or even folded if possible.
		 * @param args
		 *            The {@link List} of arguments to the primitive function.
		 * @param resultRegister
		 *            The {@link L2Register} into which to write the primitive
		 *            result.
		 * @param preserved
		 *            A list of registers to consider preserved across this
		 *            call.  They have no effect at runtime, but affect analysis
		 *            of which instructions consume which writes.
		 * @param expectedType
		 *            The {@linkplain TypeDescriptor type} of object that this
		 *            primitive call site was expected to produce.
		 * @param failureValueRegister
		 *            The {@linkplain L2ObjectRegister register} into which to
		 *            write the failure information if the primitive fails.
		 * @param successLabel
		 *            The label to jump to if the primitive is not folded and is
		 *            inlined.
		 * @param canFailPrimitive
		 *            A {@linkplain Mutable Mutable<Boolean>} that this method
		 *            sets if a fallible primitive was inlined.
		 * @param registerSet
		 *            The {@link RegisterSet} with the register information at
		 *            this position in the instruction stream.
		 * @return
		 *            The value if the primitive was folded, otherwise {@code
		 *            null}.
		 */
		private @Nullable A_BasicObject emitInlinePrimitiveAttempt (
			final A_Function primitiveFunction,
			final List<L2ObjectRegister> args,
			final L2ObjectRegister resultRegister,
			final List<L2ObjectRegister> preserved,
			final A_Type expectedType,
			final L2ObjectRegister failureValueRegister,
			final L2Instruction successLabel,
			final Mutable<Boolean> canFailPrimitive,
			final RegisterSet registerSet)
		{
			final int primitiveNumber =
				primitiveFunction.code().primitiveNumber();
			final Primitive primitive =
				Primitive.byPrimitiveNumberOrFail(primitiveNumber);
			if (primitive.hasFlag(SpecialReturnConstant))
			{
				// Use the first literal as the return value.
				final AvailObject value = primitiveFunction.code().literalAt(1);
				moveNils(args);
				moveConstant(value, resultRegister);
				// Restriction might be too strong even on a constant method.
				if (value.isInstanceOf(expectedType))
				{
					canFailPrimitive.value = false;
					return value;
				}
				// Emit the failure off-ramp.
				final L2Instruction unreachable = newLabel("unreachable");
				final L2ObjectRegister invalidResultFunction =
					newObjectRegister();
				addInstruction(
					L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.instance,
					new L2WritePointerOperand(invalidResultFunction));
				final List<L2ObjectRegister> slots =
					continuationSlotsList(numSlots);
				// The continuation must be reified prior to invoking the
				// failure function.
				final L2ObjectRegister reifiedRegister = newObjectRegister();
				reify(slots, reifiedRegister, unreachable);
				addInstruction(
					L2_INVOKE.instance,
					new L2ReadPointerOperand(reifiedRegister),
					new L2ReadPointerOperand(invalidResultFunction),
					new L2ReadVectorOperand(createVector(
						Collections.<L2ObjectRegister>emptyList())),
					new L2ImmediateOperand(1));
				unreachableCode(unreachable);
				// No need to generate primitive failure handling code, since
				// technically the primitive succeeded but the return failed.
				canFailPrimitive.value = false;
				return null;
			}
			if (primitive.hasFlag(SpecialReturnSoleArgument))
			{
				// Use the only argument as the return value.
				assert primitiveFunction.code().numArgs() == 1;
				assert args.size() == 1;
				// moveNils(args); // No need, since that slot holds the result.
				final L2ObjectRegister arg = args.get(0);
				if (registerSet.hasConstantAt(arg))
				{
					final A_BasicObject constant = registerSet.constantAt(arg);
					// Restriction could be too strong even on such a simple
					// method.
					if (constant.isInstanceOf(expectedType))
					{
						// Actually fold it.
						canFailPrimitive.value = false;
						return constant;
					}
					// The restriction is definitely too strong.  Fall through.
				}
				else if (registerSet.hasTypeAt(arg))
				{
					final A_Type actualType = registerSet.typeAt(arg);
					if (actualType.isSubtypeOf(expectedType))
					{
						// It will always conform to the expected type.  Inline.
						moveRegister(arg, resultRegister);
						canFailPrimitive.value = false;
						return null;
					}
					// It might not conform, so inline it as a primitive.
					// Fall through.
				}
			}
			if (primitive.hasFlag(SpecialReturnGlobalValue))
			{
				// The first literal is a variable; return its value.
				// moveNils(args); // No need, since that slot holds the result.
				final A_Variable variable =
					primitiveFunction.code().literalAt(1);
				if (variable.isInitializedWriteOnceVariable())
				{
					// It's an initialized module constant, so it can never
					// change.  Use the variable's eternal value.
					final A_BasicObject value = variable.value();
					if (value.isInstanceOf(expectedType))
					{
						value.makeShared();
						moveConstant(value, resultRegister);
						canFailPrimitive.value = false;
						return value;
					}
					// Its type disagrees with its declaration; fall through.
				}
				final L2ObjectRegister varRegister = newObjectRegister();
				moveConstant(variable, varRegister);
				emitGetVariableOffRamp(
					L2_GET_VARIABLE.instance,
					new L2ReadPointerOperand(varRegister),
					new L2WritePointerOperand(resultRegister));
				// Restriction might be too strong even on this method.
				final A_Type guaranteedType = variable.kind().readType();
				if (!guaranteedType.isSubtypeOf(expectedType))
				{
					// Restriction is stronger than the variable's type
					// declaration, so we have to check the actual value.
					canFailPrimitive.value = false;
					final L2Instruction returnWasOkLabel =
						newLabel("after return check");
					addInstruction(
						L2_JUMP_IF_KIND_OF_CONSTANT.instance,
						new L2PcOperand(returnWasOkLabel),
						new L2ReadPointerOperand(resultRegister),
						new L2ConstantOperand(expectedType));
					// Emit the failure off-ramp.
					final L2Instruction unreachable = newLabel("unreachable");
					final L2ObjectRegister invalidResultFunction =
						newObjectRegister();
					addInstruction(
						L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.instance,
						new L2WritePointerOperand(invalidResultFunction));
					// Record the slot values of the continuation.
					final List<L2ObjectRegister> slots =
						continuationSlotsList(numSlots);
					// The continuation must be reified prior to invoking the
					// failure function.
					final L2ObjectRegister reifiedRegister =
						newObjectRegister();
					reify(slots, reifiedRegister, unreachable);
					addInstruction(
						L2_INVOKE.instance,
						new L2ReadPointerOperand(reifiedRegister),
						new L2ReadPointerOperand(invalidResultFunction),
						new L2ReadVectorOperand(createVector(
							Collections.<L2ObjectRegister>emptyList())),
						new L2ImmediateOperand(1));
					unreachableCode(unreachable);
					addLabel(returnWasOkLabel);
					return null;
				}
				// No need to generate primitive failure handling code, since
				// technically the primitive succeeded but the return failed.
				// The above instruction effectively makes the successor
				// instructions unreachable, so don't spend a lot of time
				// generating that dead code.
				canFailPrimitive.value = false;
				return null;
			}
			boolean allConstants = true;
			for (final L2ObjectRegister arg : args)
			{
				if (!registerSet.hasConstantAt(arg))
				{
					allConstants = false;
					break;
				}
			}
			final boolean canFold = allConstants && primitive.hasFlag(CanFold);
			final boolean hasInterpreter = allConstants && interpreter != null;
			if (allConstants && canFold && hasInterpreter)
			{
				final List<AvailObject> argValues =
					new ArrayList<>(args.size());
				for (final L2Register argReg : args)
				{
					argValues.add(registerSet.constantAt(argReg));
				}
				// The skipReturnCheck is irrelevant if the primitive can be
				// folded.
				final Result success = interpreter().attemptPrimitive(
					primitiveNumber,
					primitiveFunction,
					argValues,
					false);
				if (success == SUCCESS)
				{
					final AvailObject value = interpreter().latestResult();
					if (value.isInstanceOf(expectedType))
					{
						value.makeImmutable();
						moveNils(args);
						moveConstant(value, resultRegister);
						canFailPrimitive.value = false;
						return value;
					}
					// Fall through, since folded value is too weak.
				}
				assert success == SUCCESS || success == FAILURE;
			}
			final List<A_Type> argTypes = new ArrayList<>(args.size());
			for (final L2ObjectRegister arg : args)
			{
				assert registerSet.hasTypeAt(arg);
				argTypes.add(registerSet.typeAt(arg));
			}
			final A_Type guaranteedReturnType =
				primitive.returnTypeGuaranteedByVM(argTypes);
//			assert !guaranteedReturnType.isBottom();
			final boolean skipReturnCheck =
				guaranteedReturnType.isSubtypeOf(expectedType);
			// This is an *inlineable* primitive, so it can only succeed with
			// some value or fail.  The value can't be an instance of bottom, so
			// the primitive's guaranteed return type can't be bottom.  If the
			// primitive fails, the backup code can produce bottom, but since
			// this primitive could have succeeded instead, the function itself
			// must not be naturally bottom typed.  If a semantic restriction
			// has strengthened the result type to bottom, only the backup
			// code's return instruction would be at risk, but invalidly
			// returning some value from there would have to check the value's
			// type against the expected type -- and then fail to return.
			canFailPrimitive.value =
				primitive.fallibilityForArgumentTypes(argTypes)
					!= CallSiteCannotFail;
			primitive.generateL2UnfoldableInlinePrimitive(
				this,
				primitiveFunction,
				createVector(args),
				resultRegister,
				createVector(preserved),
				expectedType,
				failureValueRegister,
				successLabel,
				canFailPrimitive.value,
				skipReturnCheck);
			return null;
		}

		/**
		 * Emit an interrupt {@linkplain
		 * L2_JUMP_IF_NOT_INTERRUPT check}-and-{@linkplain L2_PROCESS_INTERRUPT
		 * process} off-ramp. May only be called when the architectural
		 * registers reflect an inter-nybblecode state.
		 *
		 * @param registerSet The {@link RegisterSet} to use and update.
		 */
		@InnerAccess void emitInterruptOffRamp (
			final RegisterSet registerSet)
		{
			final L2Instruction postInterruptLabel = newLabel("postInterrupt");
			final L2Instruction noInterruptLabel = newLabel("noInterrupt");
			final L2ObjectRegister reifiedRegister = newObjectRegister();
			addInstruction(
				L2_JUMP_IF_NOT_INTERRUPT.instance,
				new L2PcOperand(noInterruptLabel));
			final int nSlots = numSlots;
			final List<L2ObjectRegister> slots = continuationSlotsList(nSlots);
			final List<A_Type> savedSlotTypes = new ArrayList<>(nSlots);
			final List<A_BasicObject> savedSlotConstants =
				new ArrayList<>(nSlots);
			for (final L2ObjectRegister reg : slots)
			{
				savedSlotTypes.add(
					registerSet.hasTypeAt(reg)
						? registerSet.typeAt(reg)
						: null);
				savedSlotConstants.add(
					registerSet.hasConstantAt(reg)
						? registerSet.constantAt(reg)
						: null);
			}
			reify(slots, reifiedRegister, postInterruptLabel);
			addInstruction(
				L2_PROCESS_INTERRUPT.instance,
				new L2ReadPointerOperand(reifiedRegister));
			addLabel(postInterruptLabel);
			addInstruction(
				L2_REENTER_L2_CHUNK.instance,
				new L2WritePointerOperand(fixed(CALLER)));
			A_Map typesMap = MapDescriptor.empty();
			A_Map constants = MapDescriptor.empty();
			A_Set nullSlots = SetDescriptor.empty();
			for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
			{
				final A_Type type = savedSlotTypes.get(slotIndex - 1);
				if (type != null)
				{
					typesMap = typesMap.mapAtPuttingCanDestroy(
						IntegerDescriptor.fromInt(slotIndex),
						type,
						true);
				}
				final @Nullable A_BasicObject constant =
					savedSlotConstants.get(slotIndex - 1);
				if (constant != null)
				{
					if (constant.equalsNil())
					{
						nullSlots = nullSlots.setWithElementCanDestroy(
							IntegerDescriptor.fromInt(slotIndex),
							true);
					}
					else
					{
						constants = constants.mapAtPuttingCanDestroy(
							IntegerDescriptor.fromInt(slotIndex),
							constant,
							true);
					}
				}
			}
			addInstruction(
				L2_EXPLODE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2WriteVectorOperand(createVector(slots)),
				new L2WritePointerOperand(fixed(CALLER)),
				new L2WritePointerOperand(fixed(FUNCTION)),
				new L2WriteIntOperand(skipReturnCheckRegister),
				new L2ConstantOperand(typesMap),
				new L2ConstantOperand(constants),
				new L2ConstantOperand(nullSlots),
				new L2ConstantOperand(code.functionType()));
			addLabel(noInterruptLabel);
		}

		/**
		 * Emit the specified variable-reading instruction, and an off-ramp to
		 * deal with the case that the variable is unassigned.
		 *
		 * @param getOperation
		 *        The {@linkplain L2Operation#isVariableGet() variable reading}
		 *        {@linkplain L2Operation operation}.
		 * @param variable
		 *        The location of the {@linkplain A_Variable variable}.
		 * @param destination
		 *        The destination of the extracted value.
		 */
		@InnerAccess void emitGetVariableOffRamp (
			final L2Operation getOperation,
			final L2ReadPointerOperand variable,
			final L2WritePointerOperand destination)
		{
			assert getOperation.isVariableGet();
			final L2Instruction unreachable = newLabel("unreachable");
			final L2Instruction success = newLabel("success");
			// Emit the get-variable instruction.
			addInstruction(
				getOperation,
				variable,
				destination,
				new L2PcOperand(success));
			// Emit the failure off-ramp.
			final L2ObjectRegister unassignedReadFunction = newObjectRegister();
			addInstruction(
				L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION.instance,
				new L2WritePointerOperand(unassignedReadFunction));
			final List<L2ObjectRegister> slots =
				continuationSlotsList(numSlots);
			// The continuation must be reified prior to invoking the failure
			// function.
			final L2ObjectRegister reifiedRegister = newObjectRegister();
			reify(slots, reifiedRegister, unreachable);
			addInstruction(
				L2_INVOKE.instance,
				new L2ReadPointerOperand(reifiedRegister),
				new L2ReadPointerOperand(unassignedReadFunction),
				new L2ReadVectorOperand(createVector(
					Collections.<L2ObjectRegister>emptyList())),
				new L2ImmediateOperand(1));
			unreachableCode(unreachable);
			addLabel(success);
		}

		/**
		 * Emit the specified variable-writing instruction, and an off-ramp to
		 * deal with the case that the variable has {@linkplain
		 * VariableAccessReactor write reactors} but {@linkplain
		 * Interpreter#traceVariableWrites() variable write tracing} is
		 * disabled.
		 *
		 * @param setOperation
		 *        The {@linkplain L2Operation#isVariableSet() variable reading}
		 *        {@linkplain L2Operation operation}.
		 * @param variable
		 *        The location of the {@linkplain A_Variable variable}.
		 * @param newValue
		 *        The location of the new value.
		 */
		public void emitSetVariableOffRamp (
			final L2Operation setOperation,
			final L2ReadPointerOperand variable,
			final L2ReadPointerOperand newValue)
		{
			assert setOperation.isVariableSet();
			final L2Instruction postResume = newLabel("postResume");
			final L2Instruction success = newLabel("success");
			// Emit the set-variable instruction.
			addInstruction(
				setOperation,
				variable,
				newValue,
				new L2PcOperand(success));
			// Emit the failure off-ramp.
			final L2ObjectRegister observeFunction = newObjectRegister();
			addInstruction(
				L2_GET_IMPLICIT_OBSERVE_FUNCTION.instance,
				new L2WritePointerOperand(observeFunction));
			// The continuation must be reified prior to invoking the failure
			// function.
			final RegisterSet registerSet = naiveRegisters();
			final int nSlots = numSlots;
			final List<L2ObjectRegister> slots = continuationSlotsList(nSlots);
			final List<A_Type> savedSlotTypes = new ArrayList<>(nSlots);
			final List<A_BasicObject> savedSlotConstants =
				new ArrayList<>(nSlots);
			for (final L2ObjectRegister reg : slots)
			{
				savedSlotTypes.add(
					registerSet.hasTypeAt(reg)
						? registerSet.typeAt(reg)
						: null);
				savedSlotConstants.add(
					registerSet.hasConstantAt(reg)
						? registerSet.constantAt(reg)
						: null);
			}
			final L2ObjectRegister reifiedRegister = newObjectRegister();
			reify(slots, reifiedRegister, postResume);
			final L2ObjectRegister assignmentFunctionRegister =
				newObjectRegister();
			moveConstant(
				Interpreter.assignmentFunction(),
				assignmentFunctionRegister);
			final L2ObjectRegister tupleRegister = newObjectRegister();
			addInstruction(
				L2_CREATE_TUPLE.instance,
				new L2ReadVectorOperand(createVector(
					Arrays.asList(variable.register, newValue.register))),
				new L2WritePointerOperand(tupleRegister));
			addInstruction(
				L2_INVOKE.instance,
				new L2ReadPointerOperand(reifiedRegister),
				new L2ReadPointerOperand(observeFunction),
				new L2ReadVectorOperand(createVector(
					Arrays.asList(assignmentFunctionRegister, tupleRegister))),
				new L2ImmediateOperand(1));
			addLabel(postResume);
			addInstruction(
				L2_REENTER_L2_CHUNK.instance,
				new L2WritePointerOperand(fixed(CALLER)));
			A_Map typesMap = MapDescriptor.empty();
			A_Map constants = MapDescriptor.empty();
			A_Set nullSlots = SetDescriptor.empty();
			for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
			{
				final A_Type type = savedSlotTypes.get(slotIndex - 1);
				if (type != null)
				{
					typesMap = typesMap.mapAtPuttingCanDestroy(
						IntegerDescriptor.fromInt(slotIndex),
						type,
						true);
				}
				final @Nullable A_BasicObject constant =
					savedSlotConstants.get(slotIndex - 1);
				if (constant != null)
				{
					if (constant.equalsNil())
					{
						nullSlots = nullSlots.setWithElementCanDestroy(
							IntegerDescriptor.fromInt(slotIndex),
							true);
					}
					else
					{
						constants = constants.mapAtPuttingCanDestroy(
							IntegerDescriptor.fromInt(slotIndex),
							constant,
							true);
					}
				}
			}
			addInstruction(
				L2_EXPLODE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2WriteVectorOperand(createVector(slots)),
				new L2WritePointerOperand(fixed(CALLER)),
				new L2WritePointerOperand(fixed(FUNCTION)),
				new L2WriteIntOperand(skipReturnCheckRegister),
				new L2ConstantOperand(typesMap),
				new L2ConstantOperand(constants),
				new L2ConstantOperand(nullSlots),
				new L2ConstantOperand(code.functionType()));
			addLabel(success);
		}

		/**
		 * For each level one instruction, write a suitable transliteration into
		 * level two instructions.
		 */
		void addNaiveInstructions ()
		{
			addLabel(startLabel);
			if (optimizationLevel == OptimizationLevel.UNOPTIMIZED)
			{
				// Optimize it again if it's called frequently enough.
				code.countdownToReoptimize(
					L2Chunk.countdownForNewlyOptimizedCode());
				addInstruction(
					L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
					new L2ImmediateOperand(
						OptimizationLevel.FIRST_TRANSLATION.ordinal()));
			}
			final List<L2ObjectRegister> initialRegisters =
				new ArrayList<>(FixedRegister.all().length);
			initialRegisters.add(fixed(NULL));
			initialRegisters.add(fixed(CALLER));
			initialRegisters.add(fixed(FUNCTION));
			initialRegisters.add(fixed(PRIMITIVE_FAILURE));
			for (int i = 1, end = code.numArgs(); i <= end; i++)
			{
				final L2ObjectRegister r = continuationSlot(i);
				r.setFinalIndex(
					L2Translator.firstArgumentRegisterIndex + i - 1);
				initialRegisters.add(r);
			}
			addInstruction(
				L2_ENTER_L2_CHUNK.instance,
				new L2WriteVectorOperand(createVector(initialRegisters)));
			for (int local = 1; local <= numLocals; local++)
			{
				addInstruction(
					L2_CREATE_VARIABLE.instance,
					new L2ConstantOperand(code.localTypeAt(local)),
					new L2WritePointerOperand(
						argumentOrLocal(numArgs + local)));
			}
			final int prim = code.primitiveNumber();
			if (prim != 0)
			{
				assert !Primitive.byPrimitiveNumberOrFail(prim).hasFlag(
					CannotFail);
				// Move the primitive failure value into the first local. This
				// doesn't need to support implicit observation, so no off-ramp
				// is generated.
				final L2Instruction success = newLabel("success");
				addInstruction(
					L2_SET_VARIABLE.instance,
					new L2ReadPointerOperand(argumentOrLocal(numArgs + 1)),
					new L2ReadPointerOperand(fixed(PRIMITIVE_FAILURE)),
					new L2PcOperand(success));
				addLabel(success);
			}
			// Store nil into each of the stack slots.
			for (
				int stackSlot = 1, end = code.maxStackDepth();
				stackSlot <= end;
				stackSlot++)
			{
				moveNil(stackRegister(stackSlot));
			}
			// Check for interrupts. If an interrupt is discovered, then reify
			// and process the interrupt. When the chunk resumes, it will
			// explode the continuation again.
			emitInterruptOffRamp(new RegisterSet(naiveRegisters()));

			// Transliterate each level one nybblecode into L2Instructions.
			while (pc <= nybbles.tupleSize())
			{
				final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
				pc++;
				L1Operation.all()[nybble].dispatch(this);
			}
			// Translate the implicit L1_doReturn instruction that terminates
			// the instruction sequence.
			L1Operation.L1Implied_Return.dispatch(this);
			assert pc == nybbles.tupleSize() + 1;
			assert stackp == Integer.MIN_VALUE;

			// Write a coda if necessary to support push-label instructions
			// and give the resulting continuations a chance to explode before
			// restarting them.
			if (anyPushLabelsEncountered)
			{
				addLabel(restartLabel);
				A_Map typesMap = MapDescriptor.empty();
				final List<L2ObjectRegister> slots = new ArrayList<>(numSlots);
				final A_Type argsType = code.functionType().argsTupleType();
				A_Set nullSlots = SetDescriptor.empty();
				for (int i = 1; i <= numSlots; i++)
				{
					slots.add(continuationSlot(i));
					if (i <= numArgs)
					{
						typesMap = typesMap.mapAtPuttingCanDestroy(
							IntegerDescriptor.fromInt(i),
							argsType.typeAtIndex(i),
							true);
					}
					else
					{
						nullSlots = nullSlots.setWithElementCanDestroy(
							IntegerDescriptor.fromInt(i),
							true);
					}
				}
				addInstruction(
					L2_EXPLODE_CONTINUATION.instance,
					new L2ReadPointerOperand(fixed(CALLER)),
					new L2WriteVectorOperand(createVector(slots)),
					new L2WritePointerOperand(fixed(CALLER)),
					new L2WritePointerOperand(fixed(FUNCTION)),
					new L2WriteIntOperand(skipReturnCheckRegister),
					new L2ConstantOperand(typesMap),
					new L2ConstantOperand(MapDescriptor.empty()),
					new L2ConstantOperand(nullSlots),
					new L2ConstantOperand(code.functionType()));
				addInstruction(
					L2_JUMP.instance,
					new L2PcOperand(startLabel));
			}
		}

		@Override
		public void L1_doCall ()
		{
			final AvailObject method = code.literalAt(getInteger());
			final AvailObject expectedType = code.literalAt(getInteger());
			generateCall(method, expectedType);
		}

		@Override
		public void L1_doPushLiteral ()
		{
			final AvailObject constant = code.literalAt(getInteger());
			stackp--;
			moveConstant(constant, stackRegister(stackp));
		}

		@Override
		public void L1_doPushLastLocal ()
		{
			final int localIndex = getInteger();
			stackp--;
			moveRegister(
				argumentOrLocal(localIndex),
				stackRegister(stackp));
			moveNil(argumentOrLocal(localIndex));
		}

		@Override
		public void L1_doPushLocal ()
		{
			final int localIndex = getInteger();
			stackp--;
			moveRegister(
				argumentOrLocal(localIndex),
				stackRegister(stackp));
			addInstruction(
				L2_MAKE_IMMUTABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doPushLastOuter ()
		{
			final int outerIndex = getInteger();
			stackp--;
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2WritePointerOperand(stackRegister(stackp)),
				new L2ConstantOperand(code.outerTypeAt(outerIndex)));
			addInstruction(
				L2_MAKE_IMMUTABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doClose ()
		{
			final int count = getInteger();
			final AvailObject codeLiteral = code.literalAt(getInteger());
			final List<L2ObjectRegister> outers = new ArrayList<>(count);
			for (int i = count; i >= 1; i--)
			{
				outers.add(0, stackRegister(stackp));
				stackp++;
			}
			stackp--;
			addInstruction(
				L2_CREATE_FUNCTION.instance,
				new L2ConstantOperand(codeLiteral),
				new L2ReadVectorOperand(createVector(outers)),
				new L2WritePointerOperand(stackRegister(stackp)));

			// Now that the function has been constructed, clear the slots that
			// were used for outer values -- except the destination slot, which
			// is being overwritten with the resulting function anyhow.
			for (
				int stackIndex = stackp + 1 - count;
				stackIndex <= stackp - 1;
				stackIndex++)
			{
				moveNil(stackRegister(stackIndex));
			}
		}

		@Override
		public void L1_doSetLocal ()
		{
			final int localIndex = getInteger();
			final L2ObjectRegister local =
				argumentOrLocal(localIndex);
			emitSetVariableOffRamp(
				L2_SET_VARIABLE_NO_CHECK.instance,
				new L2ReadPointerOperand(local),
				new L2ReadPointerOperand(stackRegister(stackp)));
			stackp++;
		}

		@Override
		public void L1_doGetLocalClearing ()
		{
			final int index = getInteger();
			stackp--;
			emitGetVariableOffRamp(
				L2_GET_VARIABLE_CLEARING.instance,
				new L2ReadPointerOperand(argumentOrLocal(index)),
				new L2WritePointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doPushOuter ()
		{
			final int outerIndex = getInteger();
			stackp--;
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2WritePointerOperand(stackRegister(stackp)),
				new L2ConstantOperand(code.outerTypeAt(outerIndex)));
			addInstruction(
				L2_MAKE_IMMUTABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doPop ()
		{
			moveNil(stackRegister(stackp));
			stackp++;
		}

		@Override
		public void L1_doGetOuterClearing ()
		{
			final int outerIndex = getInteger();
			stackp--;
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2WritePointerOperand(stackRegister(stackp)),
				new L2ConstantOperand(code.outerTypeAt(outerIndex)));
			emitGetVariableOffRamp(
				L2_GET_VARIABLE_CLEARING.instance,
				new L2ReadPointerOperand(stackRegister(stackp)),
				new L2WritePointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doSetOuter ()
		{
			final int outerIndex = getInteger();
			final L2ObjectRegister tempReg = newObjectRegister();
			addInstruction(
				L2_MAKE_IMMUTABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)));
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2WritePointerOperand(tempReg),
				new L2ConstantOperand(code.outerTypeAt(outerIndex)));
			emitSetVariableOffRamp(
				L2_SET_VARIABLE_NO_CHECK.instance,
				new L2ReadPointerOperand(tempReg),
				new L2ReadPointerOperand(stackRegister(stackp)));
			stackp++;
		}

		@Override
		public void L1_doGetLocal ()
		{
			final int index = getInteger();
			stackp--;
			emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				new L2ReadPointerOperand(argumentOrLocal(index)),
				new L2WritePointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doMakeTuple ()
		{
			final int count = getInteger();
			final List<L2ObjectRegister> vector = new ArrayList<>(count);
			for (int i = 1; i <= count; i++)
			{
				vector.add(stackRegister(stackp + count - i));
			}
			stackp += count - 1;
			// Fold into a constant tuple if possible
			final List<AvailObject> constants = new ArrayList<>(count);
			for (final L2ObjectRegister reg : vector)
			{
				if (!naiveRegisters().hasConstantAt(reg))
				{
					break;
				}
				constants.add(naiveRegisters().constantAt(reg));
			}
			if (constants.size() == count)
			{
				// The tuple elements are all constants.  Fold it.
				final A_Tuple tuple = TupleDescriptor.fromList(constants);
				addInstruction(
					L2_MOVE_CONSTANT.instance,
					new L2ConstantOperand(tuple),
					new L2WritePointerOperand(stackRegister(stackp)));
			}
			else
			{
				addInstruction(
					L2_CREATE_TUPLE.instance,
					new L2ReadVectorOperand(createVector(vector)),
					new L2WritePointerOperand(stackRegister(stackp)));
			}
			// Clean up the stack slots.  That's all but the first slot,
			// which was just replaced by the tuple.
			for (int i = 1; i < vector.size(); i++)
			{
				moveNil(vector.get(i));
			}
		}

		@Override
		public void L1_doGetOuter ()
		{
			final int outerIndex = getInteger();
			stackp--;
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2WritePointerOperand(stackRegister(stackp)),
				new L2ConstantOperand(code.outerTypeAt(outerIndex)));
			emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)),
				new L2WritePointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doExtension ()
		{
			// The extension nybblecode was encountered.  Read another nybble and
			// add 16 to get the L1Operation's ordinal.
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.all()[nybble + 16].dispatch(this);
		}

		@Override
		public void L1Ext_doPushLabel ()
		{
			anyPushLabelsEncountered = true;
			stackp--;
			final List<L2ObjectRegister> vectorWithOnlyArgsPreserved =
				new ArrayList<>(numSlots);
			for (int i = 1; i <= numArgs; i++)
			{
				vectorWithOnlyArgsPreserved.add(
					continuationSlot(i));
			}
			for (int i = numArgs + 1; i <= numSlots; i++)
			{
				vectorWithOnlyArgsPreserved.add(fixed(NULL));
			}
			final L2ObjectRegister destReg = stackRegister(stackp);
			addInstruction(
				L2_CREATE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2ImmediateOperand(1),
				new L2ImmediateOperand(code.maxStackDepth() + 1),
				new L2ReadIntOperand(skipReturnCheckRegister),
				new L2ReadVectorOperand(
					createVector(vectorWithOnlyArgsPreserved)),
				new L2PcOperand(restartLabel),
				new L2WritePointerOperand(destReg));

			// Freeze all fields of the new object, including its caller,
			// function, and arguments.
			addInstruction(
				L2_MAKE_SUBOBJECTS_IMMUTABLE.instance,
				new L2ReadPointerOperand(destReg));
		}

		@Override
		public void L1Ext_doGetLiteral ()
		{
			final L2ObjectRegister tempReg = newObjectRegister();
			final AvailObject constant = code.literalAt(getInteger());
			stackp--;
			moveConstant(constant, tempReg);
			emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				new L2ReadPointerOperand(tempReg),
				new L2WritePointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1Ext_doSetLiteral ()
		{
			final AvailObject constant = code.literalAt(getInteger());
			final L2ObjectRegister tempReg = newObjectRegister();
			moveConstant(constant, tempReg);
			emitSetVariableOffRamp(
				L2_SET_VARIABLE_NO_CHECK.instance,
				new L2ReadPointerOperand(tempReg),
				new L2ReadPointerOperand(stackRegister(stackp)));
			stackp++;
		}

		@Override
		public void L1Ext_doDuplicate ()
		{
			final L2ObjectRegister originalTopOfStack = stackRegister(stackp);
			addInstruction(
				L2_MAKE_IMMUTABLE.instance,
				new L2ReadPointerOperand(originalTopOfStack));
			stackp--;
			moveRegister(
				originalTopOfStack,
				stackRegister(stackp));
		}

		@Override
		public void L1Ext_doPermute ()
		{
			// Move into the permuted temps, then back to the stack.  This puts
			// the responsibility for optimizing away extra moves (by coloring
			// the registers) on the optimizer.
   			final A_Tuple permutation = code.literalAt(getInteger());
			final int size = permutation.tupleSize();
			final L2ObjectRegister[] temps = new L2ObjectRegister[size];
			for (int i = size; i >= 1; i--)
			{
				final L2ObjectRegister temp = newObjectRegister();
				moveRegister(stackRegister(stackp + size - i), temp);
				temps[permutation.tupleIntAt(i) - 1] = temp;
			}
			for (int i = size; i >= 1; i--)
			{
				moveRegister(temps[i - 1], stackRegister(stackp + size - i));
			}
		}

		@Override
		public void L1Ext_doGetType ()
		{
			final L2ObjectRegister valueReg = stackRegister(stackp);
			stackp--;
			final L2ObjectRegister typeReg = stackRegister(stackp);
			addInstruction(
				L2_GET_TYPE.instance,
				new L2ReadPointerOperand(valueReg),
				new L2WritePointerOperand(typeReg));
		}

		@Override
		public void L1Ext_doMakeTupleAndType ()
		{
			final int count = getInteger();
			final List<L2ObjectRegister> valuesVector = new ArrayList<>(count);
			final List<L2ObjectRegister> typesVector = new ArrayList<>(count);
			for (int i = 1; i <= count; i++)
			{
				typesVector.add(0, stackRegister(stackp++));
				valuesVector.add(0, stackRegister(stackp++));
			}
			final L2ObjectRegister valueReg = stackRegister(--stackp);
			final L2ObjectRegister typeReg = stackRegister(--stackp);
			// Fold the values into a constant tuple if possible.
{
			final List<AvailObject> constants = new ArrayList<>(count);
			for (final L2ObjectRegister reg : valuesVector)
			{
				if (!naiveRegisters().hasConstantAt(reg))
				{
					break;
				}
				constants.add(naiveRegisters().constantAt(reg));
			}
			if (constants.size() == count)
			{
				// The tuple elements are all constants.  Fold it.
				final A_Tuple tuple = TupleDescriptor.fromList(constants);
				addInstruction(
					L2_MOVE_CONSTANT.instance,
					new L2ConstantOperand(tuple),
					new L2WritePointerOperand(valueReg));
			}
			else
			{
				addInstruction(
					L2_CREATE_TUPLE.instance,
					new L2ReadVectorOperand(createVector(valuesVector)),
					new L2WritePointerOperand(valueReg));
			}
}
			// Fold the types into constant tuple type if possible.
			final List<AvailObject> constantTypes = new ArrayList<>(count);
			for (final L2ObjectRegister reg : typesVector)
			{
				if (!naiveRegisters().hasConstantAt(reg))
				{
					break;
				}
				constantTypes.add(naiveRegisters().constantAt(reg));
			}
			if (constantTypes.size() == count)
			{
				// The types are all constants.  Fold it.
				final A_Type tupleType = TupleTypeDescriptor.forTypes(
					constantTypes.toArray(new A_Type[count]));
				addInstruction(
					L2_MOVE_CONSTANT.instance,
					new L2ConstantOperand(tupleType),
					new L2WritePointerOperand(typeReg));
			}
			else
			{
				addInstruction(
					L2_CREATE_TUPLE_TYPE.instance,
					new L2ReadVectorOperand(createVector(typesVector)),
					new L2WritePointerOperand(valueReg));
			}
			// Clean up the stack slots.  That's all but the first two slots,
			// which were just replaced by the tuple and tuple type.  Those two
			// slots are also the first elements of the valuesVector and
			// typesVector.
			for (int i = 1; i < count; i++)
			{
				moveNil(valuesVector.get(i));
				moveNil(typesVector.get(i));
			}
		}

		@Override
		public void L1Ext_doSuperCall ()
		{
			final AvailObject method = code.literalAt(getInteger());
			final AvailObject expectedType = code.literalAt(getInteger());
			generateSuperCall(method, expectedType);
		}

		@Override
		public void L1Ext_doReserved ()
		{
			// This shouldn't happen unless the compiler is out of sync with the
			// translator.
			error("That nybblecode is not supported");
			return;
		}

		@Override
		public void L1Implied_doReturn ()
		{
			addInstruction(
				L2_RETURN.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2ReadPointerOperand(stackRegister(stackp)),
				new L2ReadIntOperand(skipReturnCheckRegister));
			assert stackp == code.maxStackDepth();
			stackp = Integer.MIN_VALUE;
		}

	}


	/**
	 * Optimize the stream of instructions.
	 */
	private void optimize ()
	{
		final List<L2Instruction> originals = new ArrayList<>(instructions);
		while (removeDeadInstructions())
		{
			// Do it again.
		}
		DebugFlag.OPTIMIZATION.log(
			Level.FINEST,
			new Continuation1<Continuation2<String, Throwable>>()
			{
				@Override
				public void value (
					final @Nullable Continuation2<String, Throwable> log)
				{
					assert log != null;
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
				}
			});
	}

	/**
	 * Compute the program state at each instruction. This information includes,
	 * for each register, its constant value (if any) and type information,
	 * other registers that currently hold equivalent values, and the set of
	 * instructions that may have directly produced the current register value.
	 */
	void computeDataFlow ()
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
			final List<L2Instruction> successors =
				new ArrayList<>(instruction.targetLabels());
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
			instruction.propagateTypes(
				targetRegisterSets,
				L2Translator.this);

			for (int i = 0; i < successorsSize; i++)
			{
				final L2Instruction successor = successors.get(i);
				final RegisterSet targetRegisterSet = targetRegisterSets.get(i);
				final int targetInstructionNumber = successor.offset();
				DebugFlag.DATA_FLOW.log(
					Level.FINEST,
					new Continuation1<Continuation2<String, Throwable>>()
					{
						@Override
						public void value (
							final @Nullable
								Continuation2<String, Throwable> log)
						{
							assert log != null;
							final StringBuilder builder =
								new StringBuilder(100);
							targetRegisterSet.debugOn(builder);
							log.value(
								String.format(
									"\t->#%d:%s%n",
									targetInstructionNumber,
									builder.toString().replace("\n", "\n\t")),
								null);
						}
					});
				final RegisterSet existing =
					instructionRegisterSets.get(targetInstructionNumber);
				final boolean followIt;
				if (existing == null)
				{
					instructionRegisterSets.set(
						targetInstructionNumber,
						targetRegisterSet);
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
		checkThatAllTargetLabelsExist();
		computeDataFlow();
		final Set<L2Instruction> reachableInstructions =
			findReachableInstructions();
		final Set<L2Instruction> neededInstructions =
			findInstructionsThatProduceNeededValues(reachableInstructions);

		// We now have a complete list of which instructions should be kept.
		DebugFlag.DEAD_INSTRUCTION_REMOVAL.log(
			Level.FINEST,
			new Continuation1<Continuation2<String, Throwable>>()
			{
				@Override
				public void value (
					final @Nullable Continuation2<String, Throwable> log)
				{
					assert log != null;
					@SuppressWarnings("resource")
					final Formatter formatter = new Formatter();
					formatter.format("Keep/drop instruction list:%n");
					for (int i = 0, end = instructions.size(); i < end; i++)
					{
						final L2Instruction instruction = instructions.get(i);
						formatter.format(
							"\t%s #%d %s%n",
							neededInstructions.contains(instruction)
								? "+"
								: "-",
							i,
							instruction.toString().replace("\n", "\n\t\t"));
					}
					log.value(formatter.toString(), null);
				}
			});
		final List<L2Instruction> newInstructions =
			new ArrayList<>(instructions.size());
		boolean anyChanges = instructions.retainAll(neededInstructions);
		anyChanges |= regenerateInstructions(newInstructions);
		instructions.clear();
		instructions.addAll(newInstructions);
		instructionRegisterSets.clear();
		for (int i = 0, end = instructions.size(); i < end; i++)
		{
			instructions.get(i).setOffset(i);
			instructionRegisterSets.add(null);
		}
		assert instructions.size() == instructionRegisterSets.size();
		return anyChanges;
	}

	/**
	 * Perform a sanity check to be sure that all branch targets actually exist
	 * in the instruction stream.
	 */
	private void checkThatAllTargetLabelsExist ()
	{
		for (final L2Instruction instruction : instructions)
		{
			for (final L2Instruction targetLabel : instruction.targetLabels())
			{
				final int targetIndex = targetLabel.offset();
				assert instructions.get(targetIndex) == targetLabel;
			}
		}
	}

	/**
	 * Find the set of instructions that are actually reachable recursively from
	 * the first instruction.
	 *
	 * @return The {@link Set} of reachable {@link L2Instruction}s.
	 */
	private Set<L2Instruction> findReachableInstructions ()
	{
		final Set<L2Instruction> reachableInstructions = new HashSet<>();
		final Deque<L2Instruction> instructionsToVisit = new ArrayDeque<>();
		instructionsToVisit.add(instructions.get(0));
		while (!instructionsToVisit.isEmpty())
		{
			final L2Instruction instruction = instructionsToVisit.removeFirst();
			if (!reachableInstructions.contains(instruction))
			{
				reachableInstructions.add(instruction);
				instructionsToVisit.addAll(instruction.targetLabels());
				if (instruction.operation.reachesNextInstruction())
				{
					final int index = instruction.offset();
					instructionsToVisit.add(instructions.get(index + 1));
				}
			}
		}
		return reachableInstructions;
	}

	/**
	 * Given the set of instructions which are reachable and have side-effect,
	 * compute which instructions recursively provide values needed by them.
	 * Also include the original instructions that have side-effect.
	 *
	 * @param reachableInstructions
	 *            The instructions which are both reachable from the first
	 *            instruction and have a side-effect.
	 * @return The instructions that are essential and should be kept.
	 */
	private Set<L2Instruction> findInstructionsThatProduceNeededValues (
		final Set<L2Instruction> reachableInstructions)
	{
		final Deque<L2Instruction> instructionsToVisit = new ArrayDeque<>();
		final Set<L2Instruction> neededInstructions =
			new HashSet<>(instructions.size());
		for (final L2Instruction instruction : reachableInstructions)
		{
			if (instruction.hasSideEffect())
			{
				instructionsToVisit.add(instruction);
			}
		}
		DebugFlag.DEAD_INSTRUCTION_REMOVAL.log(
			Level.FINEST,
			new Continuation1<Continuation2<String, Throwable>>()
			{
				@Override
				public void value (
					final @Nullable Continuation2<String, Throwable> log)
				{
					assert log != null;
					@SuppressWarnings("resource")
					final Formatter formatter = new Formatter();
					formatter.format(
						"Directly irremovable reachable instructions:");
					for (int i = 0, end = instructions.size(); i < end; i++)
					{
						final L2Instruction instruction = instructions.get(i);
						formatter.format(
							"\t%s: #%d %s%n",
							(instructionsToVisit.contains(instruction)
								? "Forced "
								: (reachableInstructions.contains(instruction)
									? "Reach  "
									: "pending")),
							i,
							instruction.toString().replace("\n", "\n\t\t"));
					}
					log.value(formatter.toString(), null);
					log.value("Propagation of needed instructions:", null);
				}
			});
		// Recursively mark as needed all instructions that produce values
		// consumed by another needed instruction.
		while (!instructionsToVisit.isEmpty())
		{
			final L2Instruction instruction = instructionsToVisit.removeLast();
			if (!neededInstructions.contains(instruction))
			{
				neededInstructions.add(instruction);
				final RegisterSet registerSet =
					instructionRegisterSets.get(instruction.offset());
				for (final L2Register sourceRegister
					: instruction.sourceRegisters())
				{
					final List<L2Instruction> providingInstructions =
						registerSet.stateForReading(sourceRegister)
							.sourceInstructions();
					if (!providingInstructions.isEmpty())
					{
						DebugFlag.DEAD_INSTRUCTION_REMOVAL.log(
							Level.FINEST,
							new Continuation1<
								Continuation2<String, Throwable>>()
							{
								@Override
								public void value (
									final @Nullable
										Continuation2<String, Throwable> log)
								{
									assert log != null;
									final Set<Integer> providerIndices =
										new TreeSet<>();
									for (final L2Instruction need :
										providingInstructions)
									{
										providerIndices.add(need.offset());
									}
									log.value(
										String.format(
											"\t\t#%d (%s) -> %s%n",
											instruction.offset(),
											sourceRegister,
											providerIndices),
										null);
								}
							});
						instructionsToVisit.addAll(providingInstructions);
					}
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
	 * @param newInstructions
	 *            Where to write the original or replacement instructions.
	 * @return Whether there were any significant changes.  This must eventually
	 *         converge to {@code false} after multiple passes in order to
	 *         ensure termination.
	 */
	private boolean regenerateInstructions (
		final List<L2Instruction> newInstructions)
	{
		boolean anyChanges = false;
		for (final L2Instruction instruction : instructions)
		{
			anyChanges |= instruction.operation.regenerate(
				instruction,
				newInstructions,
				instructionRegisterSets.get(instruction.offset()));
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
		Collections.sort(
			encounteredList,
			new Comparator<L2Register>()
			{
				@Override
				public int compare (
					final @Nullable L2Register r1,
					final @Nullable L2Register r2)
				{
					assert r1 != null;
					assert r2 != null;
					return (int)(r2.uniqueValue - r1.uniqueValue);
				}
			});
		for (final L2Register register : encounteredList)
		{
			if (register.finalIndex() == - 1)
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
			new Mutable<>(FixedRegister.all().length - 1);
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
				operand.object.makeShared();
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
					operand.register.finalIndex());
			}

			@Override
			public void doOperand (final L2ReadPointerOperand operand)
			{
				objectRegMaxIndex.value = max(
					objectRegMaxIndex.value,
					operand.register.finalIndex());
			}

			@Override
			public void doOperand (final L2ReadVectorOperand operand)
			{
				for (final L2ObjectRegister register : operand.vector)
				{
					objectRegMaxIndex.value = max(
						objectRegMaxIndex.value,
						register.finalIndex());
				}
			}

			@Override
			public void doOperand (final L2ReadWriteIntOperand operand)
			{
				intRegMaxIndex.value = max(
					intRegMaxIndex.value,
					operand.register.finalIndex());
			}

			@Override
			public void doOperand (final L2ReadWritePointerOperand operand)
			{
				objectRegMaxIndex.value = max(
					objectRegMaxIndex.value,
					operand.register.finalIndex());
			}

			@Override
			public void doOperand (final L2ReadWriteVectorOperand operand)
			{
				for (final L2ObjectRegister register : operand.vector)
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
					operand.register.finalIndex());
			}

			@Override
			public void doOperand (final L2WritePointerOperand operand)
			{
				objectRegMaxIndex.value = max(
					objectRegMaxIndex.value,
					operand.register.finalIndex());
			}

			@Override
			public void doOperand (final L2WriteVectorOperand operand)
			{
				for (final L2ObjectRegister register : operand.vector)
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
			instructions,
			executableInstructions,
			contingentValues);
	}

	/**
	 * Return the {@linkplain L2Chunk chunk} previously created via {@link
	 * #createChunk()}.
	 *
	 * @return The chunk.
	 */
	private L2Chunk chunk ()
	{
		final L2Chunk c = chunk;
		assert c != null;
		return c;
	}

	/**
	 * Construct a new {@link L2Translator}.
	 *
	 * @param code The {@linkplain CompiledCodeDescriptor code} to translate.
	 * @param optimizationLevel The optimization level.
	 * @param interpreter An {@link Interpreter}.
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
		architecturalRegisters = new ArrayList<L2ObjectRegister>(numRegisters);
		fixedRegisterMap = new EnumMap<>(FixedRegister.class);
		for (final FixedRegister fixedRegister : FixedRegister.all())
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
	 * Construct a new {@link L2Translator} solely for the purpose of creating
	 * the default chunk.  Do everything here except the final chunk creation.
	 */
	private L2Translator ()
	{
		codeOrNull = null;
		optimizationLevel = OptimizationLevel.UNOPTIMIZED;
		interpreter = null;
		architecturalRegisters =
			new ArrayList<L2ObjectRegister>(firstArgumentRegisterIndex);
		fixedRegisterMap = new EnumMap<>(FixedRegister.class);
		for (final FixedRegister fixedRegister : FixedRegister.all())
		{
			final L2ObjectRegister reg =
				L2ObjectRegister.precolored(
					nextUnique(),
					fixedRegister.ordinal());
			fixedRegisterMap.put(fixedRegister, reg);
			architecturalRegisters.add(reg);
		}
		final L2Instruction reenterFromCallLabel =
			newLabel("reenter L1 from call");
		justAddInstruction(
			L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
			new L2ImmediateOperand(
				OptimizationLevel.FIRST_TRANSLATION.ordinal()));
		justAddInstruction(L2_PREPARE_NEW_FRAME.instance);
		final L2Instruction loopStart = newLabel("main L1 loop");
		instructions.add(loopStart);
		instructionRegisterSets.add(null);
		justAddInstruction(L2_INTERPRET_ONE_L1_INSTRUCTION.instance);
		justAddInstruction(L2_INTERPRET_UNTIL_INTERRUPT.instance);
		justAddInstruction(
			L2_PROCESS_INTERRUPT.instance,
			new L2ReadPointerOperand(fixed(CALLER)));
		justAddInstruction(L2_JUMP.instance, new L2PcOperand(loopStart));
		instructions.add(reenterFromCallLabel);
		instructionRegisterSets.add(null);
		justAddInstruction(L2_REENTER_L1_CHUNK.instance);
		justAddInstruction(L2_JUMP.instance, new L2PcOperand(loopStart));
		createChunk();
		assert reenterFromCallLabel.offset() ==
			L2Chunk.offsetToContinueUnoptimizedChunk();
	}

	/**
	 * Translate the previously supplied {@linkplain CompiledCodeDescriptor
	 * Level One compiled code object} into a sequence of {@linkplain
	 * L2Instruction Level Two instructions}. The optimization level specifies
	 * how hard to try to optimize this method. It is roughly equivalent to the
	 * level of inlining to attempt, or the ratio of code expansion that is
	 * permitted. An optimization level of zero is the bare minimum, which
	 * produces a naïve translation to {@linkplain L2Chunk Level Two code}. The
	 * translation creates a counter that the Level Two code decrements each
	 * time it is invoked.  When it reaches zero, the method will be reoptimized
	 * with a higher optimization effort.
	 */
	private void translate ()
	{
		final A_RawFunction theCode = codeOrFail();
		numArgs = theCode.numArgs();
		numLocals = theCode.numLocals();
		numSlots = theCode.numArgsAndLocalsAndStack();
		// Now translate all the instructions. We already wrote a label as
		// the first instruction so that L1Ext_doPushLabel can always find
		// it. Since we only translate one method at a time, the first
		// instruction always represents the start of this compiledCode.
		final L1NaiveTranslator naiveTranslator =
			new L1NaiveTranslator();
		naiveTranslator.addNaiveInstructions();
		optimize();
		simpleColorRegisters();
		createChunk();
		assert theCode.startingChunk() == chunk;
	}

	/**
	 * @param code
	 * @param optimizationLevel
	 * @param interpreter
	 */
	public static void translateToLevelTwo (
		final A_RawFunction code,
		final OptimizationLevel optimizationLevel,
		final Interpreter interpreter)
	{
		final L2Translator translator = new L2Translator(
			code,
			optimizationLevel,
			interpreter);
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
		final L2Translator translator = new L2Translator();
		final L2Chunk newChunk = translator.chunk();
		return newChunk;
	}
}

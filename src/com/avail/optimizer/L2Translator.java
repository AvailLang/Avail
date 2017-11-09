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

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_ChunkDependable;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation
	.L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO;
import com.avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
	.TO_RESTART;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
	.TO_RESUME;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
	.TO_RETURN_INTO;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Math.max;

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
	 * Don't inline dispatch logic if there are more than this many possible
	 * implementations at a call site.
	 */
	static final int maxPolymorphismToInlineDispatch = 10;

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
	 * All {@link A_ChunkDependable contingent values} for which changes should
	 * cause the current {@linkplain L2Chunk level two chunk} to be
	 * invalidated.
	 */
	@InnerAccess
	A_Set contingentValues = emptySet();

	/** The block at which to start code generation. */
	@Nullable L2BasicBlock initialBlock;

	/** The block at which to resume execution after a failed primitive. */
	@Nullable L2BasicBlock afterOptionalInitialPrimitiveBlock;

	/**
	 * Return the {@linkplain CompiledCodeDescriptor compiled Level One code}
	 * being translated.
	 *
	 * @return The code being translated.
	 */
	public A_RawFunction codeOrFail ()
	{
		final @Nullable A_RawFunction c = codeOrNull;
		if (c == null)
		{
			throw new RuntimeException("L2Translator code was null");
		}
		return c;
	}

	/**
	 * The {@linkplain L2Chunk level two chunk} generated by {@link
	 * #createChunk(L2ControlFlowGraph)}.  It can be retrieved via {@link
	 * #chunk()}.
	 */
	private @Nullable L2Chunk chunk;

	/**
	 * Generate a {@linkplain L2Chunk Level Two chunk} from the control flow
	 * graph.  Store it in the L2Translator, from which it can be retrieved via
	 * {@link #chunk()}.
	 */
	private void createChunk (final L2ControlFlowGraph controlFlowGraph)
	{
		assert chunk == null;
		final List<L2Instruction> instructions = new ArrayList<>();
		final RegisterCounter registerCounter = new RegisterCounter();

		controlFlowGraph.generateOn(instructions);

		for (final L2Instruction instruction : instructions)
		{
			Arrays.stream(instruction.operands).forEach(
				operand -> operand.dispatchOperand(registerCounter));
			instruction.setAction();
		}

		final int afterPrimitiveOffset =
			afterOptionalInitialPrimitiveBlock == null
				? stripNull(initialBlock).offset()
				: afterOptionalInitialPrimitiveBlock.offset();

		chunk = L2Chunk.allocate(
			codeOrNull,
			registerCounter.objectMax + 1,
			registerCounter.intMax + 1,
			registerCounter.floatMax + 1,
			afterPrimitiveOffset,
			instructions,
			controlFlowGraph,
			contingentValues);
	}

	/**
	 * Return the {@link L2Chunk} previously created via {@link
	 * #createChunk(L2ControlFlowGraph)}.
	 *
	 * @return The chunk.
	 */
	private L2Chunk chunk ()
	{
		return stripNull(chunk);
	}

	/**
	 * Construct a new {@code L2Translator}.
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
	}

	/**
	 * Construct a new {@code L2Translator} solely for the purpose of creating
	 * the default chunk.  Do everything here except the final chunk extraction.
	 */
	private L2Translator ()
	{
		// Use a dummy raw function to keep it happy.
		codeOrNull = null;
		optimizationLevel = OptimizationLevel.UNOPTIMIZED;
		interpreter = null;

		@SuppressWarnings("ThisEscapedInObjectConstruction")
		final L1Translator translator = new L1Translator(this);

		final L2BasicBlock reenterFromRestartBlock =
			translator.createBasicBlock("Default reentry from restart");
		final L2BasicBlock loopBlock =
			translator.createBasicBlock("Default L1 loop");
		final L2BasicBlock reenterFromCallBlock =
			translator.createBasicBlock("Default reentry from call");
		final L2BasicBlock reenterFromInterruptBlock =
			translator.createBasicBlock("Default reentry from interrupt");

		translator.generateDefaultChunk(
			reenterFromRestartBlock,
			loopBlock,
			reenterFromCallBlock,
			reenterFromInterruptBlock);

		initialBlock = translator.initialBlock;
		afterOptionalInitialPrimitiveBlock =
			translator.afterOptionalInitialPrimitiveBlock;
		createChunk(translator.controlFlowGraph);

		final List<L2Instruction> instructions = new ArrayList<>();
		translator.initialBlock.generateOn(instructions);
		reenterFromRestartBlock.generateOn(instructions);
		loopBlock.generateOn(instructions);
		reenterFromCallBlock.generateOn(instructions);
		reenterFromInterruptBlock.generateOn(instructions);

		assert translator.initialBlock.offset() == 0;
		assert reenterFromRestartBlock.offset()
			== TO_RESTART.offsetInDefaultChunk;
		assert loopBlock.offset() == 3;
		assert reenterFromCallBlock.offset()
			== TO_RETURN_INTO.offsetInDefaultChunk;
		assert reenterFromInterruptBlock.offset()
			== TO_RESUME.offsetInDefaultChunk;
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
		// Now translate all the instructions. We already wrote a label as the
		// first instruction so that L1Ext_doPushLabel can always find it. Since
		// we only translate one method at a time, the first instruction always
		// represents the start of this compiledCode.
		final L1Translator translator = new L1Translator(this);
		translator.translateL1Instructions();
		initialBlock = translator.initialBlock;
		afterOptionalInitialPrimitiveBlock =
			translator.afterOptionalInitialPrimitiveBlock;
		translator.controlFlowGraph.optimize();
		createChunk(translator.controlFlowGraph);
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
		final @Nullable A_Function savedFunction = interpreter.function;
		final List<AvailObject> savedArguments =
			new ArrayList<>(interpreter.argsBuffer);
		final boolean savedSkip = interpreter.skipReturnCheck;
		final @Nullable AvailObject savedFailureValue =
			interpreter.latestResultOrNull();

		final L2Translator translator = new L2Translator(
			code, optimizationLevel, interpreter);

		translator.translate();
		interpreter.function = savedFunction;
		interpreter.argsBuffer.clear();
		interpreter.argsBuffer.addAll(savedArguments);
		interpreter.skipReturnCheck = savedSkip;
		interpreter.latestResult(savedFailureValue);
	}

	/**
	 * Create a chunk that will perform a naive translation of the current
	 * method to Level Two.  The naive translation creates a counter that is
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

	public static class RegisterCounter implements L2OperandDispatcher
	{
		int objectMax = -1;
		int intMax = -1;
		int floatMax = -1;

		@Override
		public void doOperand (final L2CommentOperand operand) { }

		@Override
		public void doOperand (final L2ConstantOperand operand) { }

		@Override
		public void doOperand (final L2ImmediateOperand operand) { }

		@Override
		public void doOperand (final L2PcOperand operand) { }

		@Override
		public void doOperand (final L2PrimitiveOperand operand) { }

		@Override
		public void doOperand (final L2ReadIntOperand operand)
		{
			intMax = max(intMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2ReadPointerOperand operand)
		{
			objectMax = max(objectMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2ReadVectorOperand operand)
		{
			for (final L2ReadPointerOperand register : operand.elements())
			{
				objectMax = max(objectMax, register.finalIndex());
			}
		}

		@Override
		public void doOperand (final L2SelectorOperand operand) { }

		@Override
		public void doOperand (final L2WriteIntOperand operand)
		{
			intMax = max(intMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2WritePointerOperand operand)
		{
			objectMax = max(objectMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2WriteVectorOperand operand)
		{
			for (final L2WritePointerOperand register : operand.elements())
			{
				objectMax = max(objectMax, register.finalIndex());
			}
		}
	}
}

/*
 * L2Generator.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
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

import com.avail.descriptor.A_ChunkDependable;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2ControlFlowGraph.Zone;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.performance.Statistic;
import com.avail.utility.Casts;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.avail.descriptor.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForConstant;
import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.FLOAT;
import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.INTEGER;
import static com.avail.optimizer.values.L2SemanticValue.constant;
import static com.avail.performance.StatisticReport.L2_OPTIMIZATION_TIME;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Math.max;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * The {@code L2Generator} converts a level one {@linkplain FunctionDescriptor
 * function} into a {@linkplain L2Chunk level two chunk}.  It optimizes as it
 * does so, folding and inlining method invocations whenever possible.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2Generator
{
	/**
	 * Don't inline dispatch logic if there are more than this many possible
	 * implementations at a call site.  This may seem so small that it precludes
	 * many fruitful opportunities, but code splitting should help eliminate all
	 * but a few possibilities at many call sites.
	 */
	static final int maxPolymorphismToInlineDispatch = 4;

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
		 * should tend very strongly to get inlined, as the potential to turn
		 * things like continuation-based conditionals and loops into mere jumps
		 * is expected to be highly profitable.
		 */
		@Deprecated
		CHASED_BLOCKS;

		/** An array of all {@link OptimizationLevel} enumeration values. */
		private static final OptimizationLevel[] all = values();

		/**
		 * Answer the {@code OptimizationLevel} for the given ordinal value.
		 *
		 * @param targetOptimizationLevel
		 *        The ordinal value, an {@code int}.
		 * @return The corresponding {@code OptimizationLevel}, failing if the
		 *         ordinal was out of range.
		 */
		public static OptimizationLevel optimizationLevel (
			final int targetOptimizationLevel)
		{
			return all[targetOptimizationLevel];
		}
	}

	/**
	 * The amount of {@linkplain OptimizationLevel effort} to apply to the
	 * current optimization attempt.
	 */
	final OptimizationLevel optimizationLevel;

	/**
	 * All {@link A_ChunkDependable contingent values} for which changes should
	 * cause the current {@linkplain L2Chunk level two chunk} to be
	 * invalidated.
	 */
	A_Set contingentValues = emptySet();

	/**
	 * The head of the loop formed when a P_RestartContinuation is invoked on
	 * a label created for the current frame.
	 */
	public @Nullable L2BasicBlock restartLoopHeadBlock;

	/**
	 * An {@code int} used to quickly generate unique integers which serve to
	 * visually distinguish new registers.
	 */
	private int uniqueCounter = 0;

	/**
	 * Answer the next value from the unique counter.  This is only used to
	 * distinguish registers for visual debugging.
	 *
	 * @return A int.
	 */
	public int nextUnique ()
	{
		return uniqueCounter++;
	}

	/**
	 * The topmost {@link Frame} for translation.
	 */
	public final Frame topFrame;

	/**
	 * The {@linkplain L2Chunk level two chunk} generated by {@link
	 * #createChunk(A_RawFunction)}.  It can be retrieved via {@link #chunk()}.
	 */
	private @Nullable L2Chunk chunk;

	/**
	 * The {@link L2BasicBlock} which is the entry point for a function that has
	 * just been invoked.
	 */
	final L2BasicBlock initialBlock;

	/** The block at which to resume execution after a failed primitive. */
	public final L2BasicBlock afterOptionalInitialPrimitiveBlock =
		createLoopHeadBlock("After optional primitive");

	/** The {@link L2BasicBlock} that code is currently being generated into. */
	private @Nullable L2BasicBlock currentBlock;

	/**
	 * Use this {@link L2ValueManifest} to track which {@link L2Register} holds
	 * which {@link L2SemanticValue} at the current code generation point.
	 */
	final L2ValueManifest currentManifest = new L2ValueManifest();

	/**
	 * Answer the current {@link L2ValueManifest}, which tracks which {@link
	 * L2Register} holds which {@link L2SemanticValue} at the current code
	 * generation point.
	 *
	 * @return The current {@link L2ValueManifest}.
	 */
	public L2ValueManifest currentManifest ()
	{
		return currentManifest;
	}

	/** The control flow graph being generated. */
	final L2ControlFlowGraph controlFlowGraph = new L2ControlFlowGraph();

	/**
	 * An {@link L2BasicBlock} that shouldn't actually be dynamically reachable.
	 */
	@Nullable L2BasicBlock unreachableBlock = null;

	/**
	 * Add an instruction that's not supposed to be reachable.
	 */
	public void addUnreachableCode ()
	{
		addInstruction(L2_JUMP.instance, unreachablePcOperand());
	}

	/**
	 * Answer an L2PcOperand that targets an {@link L2BasicBlock} which should
	 * never actually be dynamically reached.
	 *
	 * @return An {@link L2PcOperand} that should never be traversed.
	 */
	public L2PcOperand unreachablePcOperand ()
	{
		if (unreachableBlock == null)
		{
			// Create it as a normal node, so L1 translation can produce simple
			// edges to it, then switch it to be a loop head so that placeholder
			// instructions can still connect to it with back-edges when they
			// get they generate their replacement code.
			unreachableBlock = createBasicBlock("UNREACHABLE");
		}
		return unreachableBlock.isLoopHead
			? backEdgeTo(unreachableBlock)
			: edgeTo(unreachableBlock);
	}

	/**
	 * Allocate a new {@link L2BoxedRegister}.  Answer an {@link
	 * L2WriteBoxedOperand} that writes to it as a new temporary {@link
	 * L2SemanticValue}, restricting it with the given {@link TypeRestriction}.
	 *
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new operand.
	 * @return The new boxed write operand.
	 */
	public L2WriteBoxedOperand boxedWriteTemp (
		final TypeRestriction restriction)
	{
		return boxedWrite(
			topFrame.temp(nextUnique()),
			restriction);
	}

	/**
	 * Allocate a new {@link L2BoxedRegister}.  Answer an {@link
	 * L2WriteBoxedOperand} that writes to it as the given {@link
	 * L2SemanticValue}, restricting it with the given {@link TypeRestriction}.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to write.
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new write.
	 * @return The new boxed write operand.
	 */
	public L2WriteBoxedOperand boxedWrite (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction)
	{
		assert restriction.isBoxed();
		return new L2WriteBoxedOperand(
			singleton(semanticValue),
			restriction,
			new L2BoxedRegister(nextUnique()));
	}

	/**
	 * Allocate a new {@link L2IntRegister}.  Answer an {@link
	 * L2WriteIntOperand} that writes to it as a new temporary {@link
	 * L2SemanticValue}, restricting it with the given {@link TypeRestriction}.
	 *
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new operand.
	 * @return The new unboxed int write operand.
	 */
	public L2WriteIntOperand intWriteTemp (
		final TypeRestriction restriction)
	{
		return intWrite(topFrame.temp(nextUnique()), restriction);
	}

	/**
	 * Allocate a new {@link L2IntRegister}.  Answer an {@link
	 * L2WriteIntOperand} that writes to it as the given {@link
	 * L2SemanticValue}, restricting it with the given {@link TypeRestriction}.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to write.
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new write.
	 * @return The new unboxed int write operand.
	 */
	public L2WriteIntOperand intWrite (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction)
	{
		assert restriction.isUnboxedInt();
		return new L2WriteIntOperand(
			singleton(semanticValue),
			restriction,
			new L2IntRegister(nextUnique()));
	}

	/**
	 * Allocate a new {@link L2FloatRegister}.  Answer an {@link
	 * L2WriteFloatOperand} that writes to it as a new temporary {@link
	 * L2SemanticValue}, restricting it with the given {@link TypeRestriction}.
	 *
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new operand.
	 * @return The new unboxed float write operand.
	 */
	public L2WriteFloatOperand floatWriteTemp (
		final TypeRestriction restriction)
	{
		return floatWrite(topFrame.temp(nextUnique()), restriction);
	}

	/**
	 * Allocate a new {@link L2FloatRegister}.  Answer an {@link
	 * L2WriteFloatOperand} that writes to it as the given {@link
	 * L2SemanticValue}, restricting it with the given {@link TypeRestriction}.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to write.
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new write.
	 * @return The new unboxed float write operand.
	 */
	public L2WriteFloatOperand floatWrite (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction)
	{
		assert restriction.isUnboxedFloat();
		return new L2WriteFloatOperand(
			singleton(semanticValue),
			restriction,
			new L2FloatRegister(nextUnique()));
	}

	/**
	 * Generate code to move the given constant into a boxed register, if it's
	 * not already known to be in a boxed register.  Answer an {@link
	 * L2ReadBoxedOperand} to retrieve this value.
	 *
	 * @param value
	 *        The constant value to write to a register.
	 * @return The {@link L2ReadBoxedOperand} that retrieves the value.
	 */
	public L2ReadBoxedOperand boxedConstant (final A_BasicObject value)
	{
		final L2SemanticValue semanticConstant = constant(value);
		if (currentManifest.hasSemanticValue(semanticConstant))
		{
			final TypeRestriction restriction =
				currentManifest.restrictionFor(semanticConstant);
			if (restriction.isBoxed() && restriction.isImmutable())
			{
				return readBoxed(semanticConstant);
			}
			// Even though the exact value is known up to equality, the Java
			// structure that implements it might not be immutable.  If not,
			// fall through and let the L2_MOVE_CONSTANT ensure it.
		}
		final TypeRestriction restriction =
			restrictionForConstant(value, BOXED);
		addInstruction(
			L2_MOVE_CONSTANT.boxed,
			new L2ConstantOperand(value),
			boxedWrite(semanticConstant, restriction));
		return readBoxed(semanticConstant);
	}

	/**
	 * Generate code to move the given {@code int} constant into an unboxed int
	 * register, if it's not already known to be in such a register.  Answer an
	 * {@link L2ReadIntOperand} to retrieve this value.
	 *
	 * @param value
	 *        The constant {@code int} to write to an int register.
	 * @return The {@link L2ReadIntOperand} that retrieves the value.
	 */
	public L2ReadIntOperand unboxedIntConstant (final int value)
	{
		final A_Number boxedValue = fromInt(value);
		final L2SemanticValue semanticConstant = constant(boxedValue);
		TypeRestriction restriction;
		if (currentManifest.hasSemanticValue(semanticConstant))
		{
			restriction = currentManifest.restrictionFor(semanticConstant);
			if (restriction.isUnboxedInt())
			{
				return currentManifest.readInt(semanticConstant);
			}
			restriction = restriction.withFlag(UNBOXED_INT);
			currentManifest.setRestriction(semanticConstant, restriction);
		}
		else
		{
			final L2Synonym synonym =
				new L2Synonym(singleton(semanticConstant));
			restriction = restrictionForConstant(boxedValue, UNBOXED_INT);
			currentManifest.introduceSynonym(synonym, restriction);
		}
		addInstruction(
			L2_MOVE_CONSTANT.unboxedInt,
			new L2IntImmediateOperand(value),
			intWrite(semanticConstant, restriction));
		return new L2ReadIntOperand(
			semanticConstant, restriction, currentManifest);
	}

	/**
	 * Generate code to move the given {@code double} constant into an unboxed
	 * float register, if it's not already known to be in such a register.
	 * Answer an {@link L2ReadFloatOperand} to retrieve this value.
	 *
	 * @param value
	 *        The constant {@code double} to write to a float register.
	 * @return The {@link L2ReadFloatOperand} that retrieves the value.
	 */
	public L2ReadFloatOperand unboxedFloatConstant (final double value)
	{
		final A_Number boxedValue = fromDouble(value);
		final L2SemanticValue semanticConstant = constant(boxedValue);
		TypeRestriction restriction;
		if (currentManifest.hasSemanticValue(semanticConstant))
		{
			restriction = currentManifest.restrictionFor(semanticConstant);
			if (restriction.isUnboxedFloat())
			{
				return currentManifest.readFloat(semanticConstant);
			}
			restriction = restriction.withFlag(UNBOXED_FLOAT);
			currentManifest.setRestriction(semanticConstant, restriction);
		}
		else
		{
			final L2Synonym synonym =
				new L2Synonym(singleton(semanticConstant));
			restriction = restrictionForConstant(boxedValue, UNBOXED_FLOAT);
			currentManifest.introduceSynonym(synonym, restriction);
		}
		addInstruction(
			L2_MOVE_CONSTANT.unboxedFloat,
			new L2FloatImmediateOperand(value),
			floatWrite(semanticConstant, restriction));
		return new L2ReadFloatOperand(
			semanticConstant, restriction, currentManifest);
	}

	/**
	 * Given an {@link L2WriteBoxedOperand}, produce an {@link
	 * L2ReadBoxedOperand} of the same value, but with the current manifest's
	 * {@link TypeRestriction} applied.
	 *
	 * @param write
	 *        The {@link L2WriteBoxedOperand} for which to generate a read.
	 * @return The {@link L2ReadBoxedOperand} that reads the value.
	 */
	public L2ReadBoxedOperand readBoxed (
		final L2WriteBoxedOperand write)
	{
		return currentManifest.readBoxed(write.pickSemanticValue());
	}

	/**
	 * Answer an {@link L2ReadBoxedOperand} for the given {@link
	 * L2SemanticValue}, generating code to transform it as necessary.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to read.
	 * @return A suitable {@link L2ReadBoxedOperand} that captures the current
	 *         {@link TypeRestriction} for the semantic value.
	 */
	public L2ReadBoxedOperand readBoxed (
		final L2SemanticValue semanticValue)
	{
		final TypeRestriction restriction =
			currentManifest.restrictionFor(semanticValue);
		if (restriction.isBoxed())
		{
			return currentManifest.readBoxed(semanticValue);
		}
		final TypeRestriction boxedRestriction = restriction.withFlag(BOXED);
		currentManifest.setRestriction(semanticValue, boxedRestriction);
		final L2WriteBoxedOperand writer =
			new L2WriteBoxedOperand(
				currentManifest.semanticValueToSynonym(semanticValue)
					.semanticValues(),
				boxedRestriction,
				new L2BoxedRegister(nextUnique()));
		if (restriction.isUnboxedInt())
		{
			addInstruction(
				L2_BOX_INT.instance,
				currentManifest.readInt(semanticValue),
				writer);
		}
		else
		{
			assert restriction.isUnboxedFloat();
			addInstruction(
				L2_BOX_FLOAT.instance,
				currentManifest.readFloat(semanticValue),
				writer);
		}
		return currentManifest.readBoxed(semanticValue);
	}

	/**
	 * Return an {@link L2ReadIntOperand} for the given {@link L2SemanticValue}.
	 * The {@link TypeRestriction} must have been proven by the VM.  If the
	 * semantic value only has a boxed form, generate code to unbox it.
	 *
	 * <p>In the case that unboxing may fail, a branch to the supplied onFailure
	 * {@link L2BasicBlock} will be generated. If the unboxing cannot fail (or
	 * if a corresponding {@link L2IntRegister} already exists), no branch will
	 * lead to onFailure, which can be determined by the client by testing
	 * {@link L2BasicBlock#currentlyReachable()}.</p>
	 *
	 * <p>In any case, the generation position after this call is along the
	 * success path.  This may itself be unreachable in the event that the
	 * unboxing will <em>always</em> fail.</p>
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to read as an unboxed int.
	 * @param onFailure
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_INT}
	 *        fails. The manifest at this location will not contain bindings for
	 *        the unboxed {@code int} (since unboxing was not possible).
	 * @return The unboxed {@link L2ReadIntOperand}.
	 */
	public L2ReadIntOperand readInt (
		final L2SemanticValue semanticValue,
		final L2BasicBlock onFailure)
	{
		final TypeRestriction restriction =
			currentManifest.restrictionFor(semanticValue);
		if (restriction.isUnboxedInt())
		{
			// It already exists in an unboxed int register.
			assert restriction.type.isSubtypeOf(int32());
			return currentManifest.readInt(semanticValue);
		}
		// It's not available as an unboxed int, so generate code to unbox it.
		if (!restriction.isBoxed() || !restriction.intersectsType(int32()))
		{
			// It's not an unboxed int, and it's either not boxed or it has a
			// type that can never be an int32, so it must always fail.
			addInstruction(
				L2_JUMP.instance,
				edgeTo(onFailure));
			// Return a dummy, which should get suppressed or optimized away.
			return unboxedIntConstant(-999);
		}
		// Check for constant.  It can be infallibly converted.
		if (restriction.containedByType(int32())
			&& restriction.constantOrNull != null)
		{
			// Make it available as a constant in an int register.
			return unboxedIntConstant(restriction.constantOrNull.extractInt());
		}
		// Write it to a new int register.
		final L2WriteIntOperand intWrite =
			new L2WriteIntOperand(
				currentManifest.semanticValueToSynonym(semanticValue)
					.semanticValues(),
				restriction
					.intersectionWithType(int32())
					.withFlag(UNBOXED_INT),
				new L2IntRegister(nextUnique()));
		final L2ReadBoxedOperand boxedRead =
			currentManifest.readBoxed(semanticValue);
		if (restriction.containedByType(int32()))
		{
			addInstruction(
				L2_UNBOX_INT.instance,
				boxedRead,
				intWrite);
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			final L2BasicBlock onSuccess =
				createBasicBlock("successfully unboxed");
			addInstruction(
				L2_JUMP_IF_UNBOX_INT.instance,
				boxedRead,
				intWrite,
				edgeTo(onFailure),
				edgeTo(onSuccess));
			startBlock(onSuccess);
		}
		// This is the success path.  The operations have already ensured the
		// intWrite is in the same synonym as the boxedRead.

		// This checks that the synonyms were merged nicely.
		return currentManifest.readInt(semanticValue);
	}

	/**
	 * Return an {@link L2ReadFloatOperand} for the given {@link
	 * L2SemanticValue}. The {@link TypeRestriction} must have been proven by
	 * the VM.  If the semantic value only has a boxed form, generate code to
	 * unbox it.
	 *
	 * <p>In the case that unboxing may fail, a branch to the supplied onFailure
	 * {@link L2BasicBlock} will be generated. If the unboxing cannot fail (or
	 * if a corresponding {@link L2FloatRegister} already exists), no branch
	 * will lead to onFailure, which can be determined by the client by testing
	 * {@link L2BasicBlock#currentlyReachable()}.</p>
	 *
	 * <p>In any case, the generation position after this call is along the
	 * success path.  This may itself be unreachable in the event that the
	 * unboxing will <em>always</em> fail.</p>
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to read as an unboxed float.
	 * @param onFailure
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_FLOAT}
	 *        fails. The manifest at this location will not contain bindings for
	 *        the unboxed {@code float} (since unboxing was not possible).
	 * @return The unboxed {@link L2ReadFloatOperand}.
	 */
	public L2ReadFloatOperand readFloat (
		final L2SemanticValue semanticValue,
		final L2BasicBlock onFailure)
	{
		final TypeRestriction restriction =
			currentManifest.restrictionFor(semanticValue);
		if (restriction.isUnboxedFloat())
		{
			// It already exists in an unboxed float register.
			assert restriction.type.isSubtypeOf(DOUBLE.o());
			return currentManifest.readFloat(semanticValue);
		}
		// It's not available as an unboxed float, so generate code to unbox it.
		if (!restriction.isBoxed() || !restriction.intersectsType(DOUBLE.o()))
		{
			// It's not an unboxed float, and it's either not boxed or it has a
			// type that can never be a float, so it must always fail.
			addInstruction(
				L2_JUMP.instance,
				edgeTo(onFailure));
			// Return a dummy, which should get suppressed or optimized away.
			return unboxedFloatConstant(-99.9);
		}
		// Check for constant.  It can be infallibly converted.
		if (restriction.containedByType(DOUBLE.o())
			&& restriction.constantOrNull != null)
		{
			// Make it available as a constant in a float register.
			return unboxedFloatConstant(
				restriction.constantOrNull.extractDouble());
		}
		// Write it to a new float register.
		final L2WriteFloatOperand floatWrite =
			new L2WriteFloatOperand(
				currentManifest.semanticValueToSynonym(semanticValue)
					.semanticValues(),
				restriction
					.intersectionWithType(DOUBLE.o())
					.withFlag(UNBOXED_FLOAT),
				new L2FloatRegister(nextUnique()));
		final L2ReadBoxedOperand boxedRead =
			currentManifest.readBoxed(semanticValue);
		if (restriction.containedByType(DOUBLE.o()))
		{
			addInstruction(
				L2_UNBOX_FLOAT.instance,
				boxedRead,
				floatWrite);
		}
		else
		{
			// Conversion may succeed or fail at runtime.
			final L2BasicBlock onSuccess =
				createBasicBlock("successfully unboxed");
			addInstruction(
				L2_JUMP_IF_UNBOX_FLOAT.instance,
				boxedRead,
				floatWrite,
				edgeTo(onSuccess),
				edgeTo(onFailure));
			startBlock(onSuccess);
		}
		// This is the success path.  The operations have already ensured the
		// floatWrite is in the same synonym as the boxedRead.

		// This checks that the synonyms were merged nicely.
		return currentManifest.readFloat(semanticValue);
	}

	/**
	 * Generate instructions to arrange for the value in the given {@link
	 * L2ReadOperand} to end up in an {@link L2Register} associated in the
	 * {@link L2ValueManifest} with the new {@link L2SemanticValue}.  After the
	 * move, the synonyms for the source and destination are effectively merged,
	 * which is justified by virtue of SSA (static-single-assignment) being in
	 * effect.
	 *
	 * @param <R>
	 *        The kind of {@link L2Register} to move.
	 * @param <RR>
	 *        The kind of {@link L2ReadOperand} for reading.
	 * @param <WR>
	 *        The kind of {@link L2WriteOperand} for writing.
	 * @param moveOperation
	 *        The {@link L2_MOVE} operation to generate.
	 * @param sourceSemanticValue
	 *        Which {@link L2SemanticValue} to read.
	 * @param targetSemanticValue
	 *        Which {@link L2SemanticValue} will have the same value as the
	 *        source semantic value.
	 */
	public <
		R extends L2Register,
		RR extends L2ReadOperand<R>,
		WR extends L2WriteOperand<R>>
	void moveRegister (
		final L2_MOVE<R, RR, WR> moveOperation,
		final L2SemanticValue sourceSemanticValue,
		final L2SemanticValue targetSemanticValue)
	{
		assert !currentManifest.hasSemanticValue(targetSemanticValue);
		final L2BasicBlock block = currentBlock();
		final List<L2Register> sourceRegisters = currentManifest.getDefinitions(
			sourceSemanticValue, moveOperation.kind);
		final List<WR> sourceWritesInBlock =
			sourceRegisters.stream()
				.flatMap(reg -> reg.definitions().stream())
				.filter(w -> w.instruction().basicBlock() == block)
				.map(Casts::<L2WriteOperand<?>, WR>cast)
				.collect(toList());
		if (!sourceWritesInBlock.isEmpty())
		{
			// Find the latest equivalent write in this block.
			final WR latestWrite = Collections.max(
				sourceWritesInBlock,
				comparing(
					w -> w.instruction().basicBlock().instructions().indexOf(
						w.instruction())));
			// Walk backward through instructions until the latest equivalent
			// write, watching for disqualifying pitfalls.
			for (int i = block.instructions().size() - 1; i >= 0; i--)
			{
				final L2Instruction eachInstruction =
					block.instructions().get(i);
				if (eachInstruction == latestWrite.instruction())
				{
					// We reached the writing instruction without trouble.
					// Augment the write's semantic values retroactively to
					// include the targetSemanticValue.
					final L2SemanticValue pickedSemanticValue =
						latestWrite.pickSemanticValue();
					// This line must be after we pick a representative semantic
					// value, otherwise it might choose the new one.
					latestWrite.retroactivelyIncludeSemanticValue(
						targetSemanticValue);
					currentManifest.extendSynonym(
						currentManifest.semanticValueToSynonym(
							pickedSemanticValue),
						targetSemanticValue);
					return;
				}
				// Here's where we would check eachInstruction to see if it's a
				// pitfall that prevents us from retroactively updating an
				// earlier write.  Break if this happens.
			}
			// Fall through, due to a break from a pitfall.
		}
		// Note that even though we couldn't avoid the move in this case, this
		// move can still be updated by subsequent moves from the same synonym.
		final TypeRestriction restriction =
			currentManifest.restrictionFor(sourceSemanticValue);
		addInstruction(
			moveOperation,
			moveOperation.kind.readOperand(
				sourceSemanticValue,
				restriction,
				currentManifest.getDefinition(
					sourceSemanticValue, moveOperation.kind)),
			moveOperation.createWrite(this, targetSemanticValue, restriction));
	}

	/**
	 * Generate code to ensure an immutable version of the given register is
	 * written to the returned register.  Update the {@link
	 * L2Generator#currentManifest()} to indicate the returned register should
	 * be used for all of the given register's semantic values after this point.
	 *
	 * @param read
	 *        The {@link L2ReadBoxedOperand} that was given.
	 * @return The resulting {@link L2ReadBoxedOperand}, holding an immutable
	 *         version of the given register.
	 */
	public L2ReadBoxedOperand makeImmutable (
		final L2ReadBoxedOperand read)
	{
		final TypeRestriction restriction = read.restriction();
		assert restriction.isBoxed();
		if (restriction.isImmutable())
		{
			// The source read is definitely immutable.
			return read;
		}
		// Pick a semantic value from the read's synonym.  Pass the original
		// boxed value through an L2_MAKE_IMMUTABLE into that semantic value,
		// then generate boxed moves from that semantic value into each of the
		// other semantic values, effectively reforming a new synonym.  If there
		// are int or float unboxed forms, move those directly as well, ensuring
		// the unboxed forms remain available.  The unboxed moves will not act
		// as *register level* data dependencies in the final L2 code, so the
		// makeImmutable will be independently movable from the earlier unboxing
		// operations and the potential later uses of the unboxed value.
		final L2SemanticValue semanticValue = read.semanticValue();
		final L2SemanticValue temp = topFrame.temp(nextUnique());
		final TypeRestriction immutableRestriction =
			restriction.withFlag(IMMUTABLE);
		// If there is an unboxed form, preserve the first definition of that
		// unboxed kind.
		final List<L2Register> unboxedRegisters = new ArrayList<>(1);
		if (immutableRestriction.isUnboxedInt())
		{
			unboxedRegisters.add(
				currentManifest.getDefinition(semanticValue, INTEGER));
		}
		if (immutableRestriction.isUnboxedFloat())
		{
			unboxedRegisters.add(
				currentManifest.getDefinition(semanticValue, FLOAT));
		}
		final L2Synonym synonym =
			currentManifest.semanticValueToSynonym(semanticValue);
		// L2_MAKE_IMMUTABLE.instructionWasAdded(instr, manifest) will run when
		// the instruction is added.  The subsequent moves take care of
		// themselves.
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			read,
			boxedWrite(temp, immutableRestriction));
		synonym.semanticValues().forEach(sv ->
		{
			moveRegister(L2_MOVE.boxed, temp, sv);
			currentManifest.setRestriction(sv, immutableRestriction);
		});
		// Now restore each kind of unboxed definition, if there were any.
		unboxedRegisters.forEach(reg ->
			currentManifest.recordDefinitionForNewKind(reg.definition()));
		return currentManifest.readBoxed(temp);
	}

	/**
	 * Answer a semantic value representing the result of invoking a foldable
	 * primitive.
	 *
	 * @param primitive
	 *        The {@link Primitive} that was executed.
	 * @param argumentReads
	 *        {@link L2SemanticValue}s that supplied the arguments to the
	 *        primitive.
	 * @return The semantic value representing the primitive result.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public L2SemanticValue primitiveInvocation (
		final Primitive primitive,
		final List<L2ReadBoxedOperand> argumentReads)
	{
		return L2SemanticValue.primitiveInvocation(
			primitive,
			argumentReads.stream()
				.map(L2ReadOperand::semanticValue)
				.collect(toList()));
	}

	/**
	 * Create a new {@link L2BasicBlock}.  It's initially not connected to
	 * anything, and is ignored if it is never actually added with {@link
	 * #startBlock(L2BasicBlock)}.
	 *
	 * @param name The descriptive name of the new basic block.
	 * @return The new {@link L2BasicBlock}.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public L2BasicBlock createBasicBlock (final String name)
	{
		return new L2BasicBlock(name);
	}

	/**
	 * Create an {@link L2BasicBlock}, and mark it as a loop head.
	 *
	 * @param name The name of the new loop head block.
	 * @return The loop head block.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public L2BasicBlock createLoopHeadBlock (final String name)
	{
		return new L2BasicBlock(name, true, null);
	}

	/**
	 * Create an {@link L2BasicBlock}, and mark it as used for reification.
	 *
	 * @param name
	 *        The name of the new block.
	 * @param zone
	 *        The {@link Zone} (or {@code null}) into which to group this block
	 *        in the {@link L2ControlFlowGraphVisualizer}.
	 * @return The new block.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public L2BasicBlock createBasicBlock (
		final String name,
		final @Nullable Zone zone)
	{
		return new L2BasicBlock(name, false, zone);
	}

	/**
	 * Start code generation for the given {@link L2BasicBlock}.  Unless this is
	 * a loop head, ensure all predecessor blocks have already finished
	 * generation.
	 *
	 * <p>Also, reconcile the live {@link L2SemanticValue}s and how they're
	 * grouped into {@link L2Synonym}s in each predecessor edge, creating
	 * {@link L2_PHI_PSEUDO_OPERATION}s as needed.</p>
	 *
	 * @param block The {@link L2BasicBlock} beginning code generation.
	 */
	public void startBlock (final L2BasicBlock block)
	{
		if (!block.isIrremovable())
		{
			if (block.predecessorEdgesCount() == 0)
			{
				currentBlock = null;
				return;
			}
			if (!block.isLoopHead && block.predecessorEdgesCount() == 1)
			{
				final L2PcOperand predecessorEdge =
					block.predecessorEdgesIterator().next();
				final L2BasicBlock predecessorBlock =
					predecessorEdge.sourceBlock();
				final L2Instruction jump = predecessorBlock.finalInstruction();
				if (jump.operation() == L2_JUMP.instance)
				{
					// The new block has only one predecessor, which
					// unconditionally jumps to it.  Remove the jump and
					// continue generation in the predecessor block.  Restore
					// the manifest from the jump edge.
					currentManifest.clear();
					currentManifest.populateFromIntersection(
						singletonList(predecessorEdge.manifest()),
						this,
						false,
						false);
					predecessorBlock.instructions().remove(
						predecessorBlock.instructions().size() - 1);
					jump.justRemoved();
					currentBlock = predecessorBlock;
					return;
				}
			}
		}
		currentBlock = block;
		controlFlowGraph.startBlock(block);
		block.startIn(this);
	}

	/**
	 * Answer the current {@link L2BasicBlock} being generated.
	 *
	 * @return The current {@link L2BasicBlock}.
	 */
	public L2BasicBlock currentBlock ()
	{
		return stripNull(currentBlock);
	}

	/**
	 * Determine whether the current block is probably reachable.  If it has no
	 * predecessors and is removable, it's unreachable, but otherwise we assume
	 * it's reachable, at least until dead code elimination.
	 *
	 * @return Whether the current block is probably reachable.
	 */
	public boolean currentlyReachable ()
	{
		return currentBlock != null && currentBlock.currentlyReachable();
	}

	/**
	 * Create an {@link L2PcOperand} leading to the given {@link L2BasicBlock}.
	 *
	 * @param targetBlock
	 *        The target {@link L2BasicBlock}.
	 * @return The new {@link L2PcOperand}.
	 */
	public static L2PcOperand edgeTo (
		final L2BasicBlock targetBlock)
	{
		// Only back-edges may reach a block that has already been generated.
		assert targetBlock.instructions().isEmpty();
		return new L2PcOperand(targetBlock, false);
	}

	/**
	 * Create an {@link L2PcOperand} leading to the given {@link L2BasicBlock},
	 * which must be {@link L2BasicBlock#isLoopHead}.
	 *
	 * @param targetBlock
	 *        The target {@link L2BasicBlock}.
	 * @return The new {@link L2PcOperand}.
	 */
	public static L2PcOperand backEdgeTo (
		final L2BasicBlock targetBlock)
	{
		assert targetBlock.isLoopHead;
		return new L2PcOperand(targetBlock, true);
	}

	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.
	 *
	 * @param operation
	 *        The operation to invoke.
	 * @param operands
	 *        The operands of the instruction.
	 */
	public void addInstruction (
		final L2Operation operation,
		final L2Operand... operands)
	{
		if (currentBlock != null)
		{
			currentBlock.addInstruction(
				new L2Instruction(currentBlock, operation, operands),
				currentManifest);
		}
	}

	/**
	 * Add an {@link L2Instruction}.
	 *
	 * @param instruction
	 *        The instruction to add.
	 */
	public void addInstruction (
		final L2Instruction instruction)
	{
		if (currentBlock != null)
		{
			currentBlock.addInstruction(instruction, currentManifest);
		}
	}

	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.  However, this
	 * may happen after dead code has been eliminated, including moves into
	 * semantic values that propagated into synonyms and were subsequently
	 * looked up by reads.  If necessary, fall back on using the register itself
	 * to identify a suitable semantic value.
	 *
	 * @param operation
	 *        The operation to invoke.
	 * @param operands
	 *        The operands of the instruction.
	 */
	public void reinsertInstruction (
		final L2Operation operation,
		final L2Operand... operands)
	{
		if (currentBlock == null)
		{
			return;
		}
		final L2Operand[] replacementOperands = Arrays.stream(operands)
			.map(op -> op.adjustedForReinsertion(currentManifest))
			.toArray(L2Operand[]::new);
		currentBlock.addInstruction(
			new L2Instruction(currentBlock, operation, replacementOperands),
			currentManifest);
	}

	/**
	 * Replace the already-generated instruction with a code sequence produced
	 * by setting up conditions, asking the instruction to
	 * {@link L2Operation#generateReplacement(L2Instruction, L2Generator)}
	 * itself, then cleaning up afterward.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to replace.  Any registers that it
	 *        writes must be replaced by suitable writes in the generated
	 *        replacement.
	 */
	public void replaceInstructionByGenerating (
		final L2Instruction instruction)
	{
		assert !instruction.altersControlFlow();
		final L2BasicBlock startingBlock = instruction.basicBlock();
		final List<L2Instruction> originalInstructions =
			startingBlock.instructions();
		final int instructionIndex = originalInstructions.indexOf(instruction);
		// Stash the instructions before the doomed one, as well as the ones
		// after the doomed one.
		final List<L2Instruction> startInstructions = new ArrayList<>(
			originalInstructions.subList(0, instructionIndex));
		final List<L2Instruction> endInstructions = new ArrayList<>(
			originalInstructions.subList(
				instructionIndex + 1, originalInstructions.size()));
		// Remove all instructions from the block.  Each will get sent a
		// justRemoved() just *after* a replacement has been generated.  This
		// ensures there is always a definition of every register, and allows
		// phis in the target blocks of the final instruction's edges to stay as
		// consistent phis, rather than collapsing to inappropriate moves.
		originalInstructions.clear();
		startingBlock.removedControlFlowInstruction();

		// Regenerate the start instructions.
		currentBlock = startingBlock;
		// Reconstruct the manifest at the start of the block.
		currentManifest.clear();
		// Keep semantic values that are common to all incoming paths.
		final List<L2ValueManifest> manifests = new ArrayList<>();
		startingBlock.predecessorEdgesIterator().forEachRemaining(
			edge -> manifests.add(edge.manifest()));
		currentManifest.populateFromIntersection(manifests, this, false, true);
		// Replay the effects on the manifest of the leading instructions.
		startInstructions.forEach(originalInstruction ->
			{
				reinsertInstruction(
					originalInstruction.operation(),
					originalInstruction.operands());
				originalInstruction.justRemoved();
			});

		// Let the instruction regenerate its replacement code.  It must write
		// to all of the write operand *registers* of the original instruction.
		// Writing to the same semantic value isn't good enough.
		instruction.operation().generateReplacement(instruction, this);
		instruction.justRemoved();

		if (!currentlyReachable())
		{
			// This regenerated code should no longer reach the old flow.
			// Clean up the rest of the original instructions.
			endInstructions.forEach(L2Instruction::justRemoved);
			return;
		}

		// Make sure every L2WriteOperand in the replaced instruction has been
		// written to by the replacement code.
		instruction.writeOperands().forEach(writeOp ->
		{
			assert !writeOp.register().definitions().isEmpty();
		});

		// Finally, add duplicates of the instructions that came after the
		// doomed one in the original block.  Since the regenerated code writes
		// to all the same L2Registers, no special provisions are needed for
		// translating registers or fiddling with the manifest.
		endInstructions.forEach(originalInstruction ->
		{
			reinsertInstruction(
				originalInstruction.operation(),
				originalInstruction.operands());
			originalInstruction.justRemoved();
		});
	}

	/**
	 * Record the fact that the chunk being created depends on the given {@link
	 * A_ChunkDependable}.  If that {@code A_ChunkDependable} changes, the chunk
	 * will be invalidated.
	 *
	 * @param contingentValue
	 *        The {@link AvailObject} that the chunk will be contingent on.
	 */
	public void addContingentValue (final A_ChunkDependable contingentValue)
	{
		contingentValues =
			contingentValues.setWithElementCanDestroy(contingentValue, true);
	}

	/**
	 * Generate a {@linkplain L2Chunk Level Two chunk} from the control flow
	 * graph.  Store it in the {@code L2Generator}, from which it can be
	 * retrieved via {@link #chunk()}.
	 *
	 * @param code
	 *        The {@link A_RawFunction} which is the source of chunk creation.
	 */
	void createChunk (
		final A_RawFunction code)
	{
		assert chunk == null;
		final List<L2Instruction> instructions = new ArrayList<>();
		controlFlowGraph.generateOn(instructions);
		final RegisterCounter registerCounter = new RegisterCounter();
		for (final L2Instruction instruction : instructions)
		{
			instruction.operandsDo(
				operand -> operand.dispatchOperand(registerCounter));
		}

		final int afterPrimitiveOffset =
			afterOptionalInitialPrimitiveBlock.offset();
		assert afterPrimitiveOffset >= 0;

		chunk = L2Chunk.allocate(
			code,
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
	 * #createChunk(A_RawFunction)}.
	 *
	 * @return The chunk.
	 */
	L2Chunk chunk ()
	{
		return stripNull(chunk);
	}

	/**
	 * Construct a new {@code L2Generator}.
	 *
	 * @param optimizationLevel
	 *        The {@link OptimizationLevel} for controlling code generation.
	 * @param topFrame
	 *        The topmost {@link Frame} for code generation.
	 * @param codeName
	 *        The descriptive name of the code being generated.
	 */
	L2Generator (
		final OptimizationLevel optimizationLevel,
		final Frame topFrame,
		final String codeName)
	{
		this.optimizationLevel = optimizationLevel;
		this.topFrame = topFrame;
		this.initialBlock = createBasicBlock("START for " + codeName);
		this.currentBlock = this.initialBlock;
	}

	/**
	 * Statistics about final chunk generation from the optimized {@link
	 * L2ControlFlowGraph}.
	 */
	static final Statistic finalGenerationStat = new Statistic(
		"Final chunk generation", L2_OPTIMIZATION_TIME);

	/**
	 * A class for finding the highest numbered register of each time.
	 */
	public static class RegisterCounter
	implements L2OperandDispatcher
	{
		/** The highest numbered boxed register encountered so far. */
		int objectMax = -1;

		/** The highest numbered int register encountered so far. */
		int intMax = -1;

		/** The highest numbered float register encountered so far. */
		int floatMax = -1;

		@Override
		public void doOperand (final L2CommentOperand operand) { }

		@Override
		public void doOperand (final L2ConstantOperand operand) { }

		@Override
		public void doOperand (final L2IntImmediateOperand operand) { }

		@Override
		public void doOperand (final L2FloatImmediateOperand operand) { }

		@Override
		public void doOperand (final L2PcOperand operand) { }

		@Override
		public void doOperand (final L2PrimitiveOperand operand) { }

		@Override
		public void doOperand (final L2InternalCounterOperand operand) { }

		@Override
		public void doOperand (final L2ReadIntOperand operand)
		{
			intMax = max(intMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2ReadFloatOperand operand)
		{
			floatMax = max(floatMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2ReadBoxedOperand operand)
		{
			objectMax = max(objectMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2ReadBoxedVectorOperand operand)
		{
			for (final L2ReadBoxedOperand register : operand.elements())
			{
				objectMax = max(objectMax, register.finalIndex());
			}
		}

		@Override
		public void doOperand (final L2ReadIntVectorOperand operand)
		{
			for (final L2ReadIntOperand register : operand.elements())
			{
				intMax = max(intMax, register.finalIndex());
			}
		}

		@Override
		public void doOperand (final L2ReadFloatVectorOperand operand)
		{
			for (final L2ReadFloatOperand register : operand.elements())
			{
				floatMax = max(floatMax, register.finalIndex());
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
		public void doOperand (final L2WriteFloatOperand operand)
		{
			floatMax = max(floatMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2WriteBoxedOperand operand)
		{
			objectMax = max(objectMax, operand.finalIndex());
		}
	}
}

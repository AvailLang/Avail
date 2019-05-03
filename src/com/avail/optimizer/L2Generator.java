/*
 * L2Generator.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_ChunkDependable;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.performance.Statistic;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restriction;
import static com.avail.optimizer.L2Synonym.SynonymFlag.KNOWN_IMMUTABLE;
import static com.avail.performance.StatisticReport.L2_OPTIMIZATION_TIME;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Math.max;
import static java.util.Collections.singletonList;

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
	@InnerAccess final OptimizationLevel optimizationLevel;

	/**
	 * All {@link A_ChunkDependable contingent values} for which changes should
	 * cause the current {@linkplain L2Chunk level two chunk} to be
	 * invalidated.
	 */
	@InnerAccess A_Set contingentValues = emptySet();

	/** The block at which to resume execution after a failed primitive. */
	@Nullable L2BasicBlock afterOptionalInitialPrimitiveBlock;

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
	 * The {@linkplain L2Chunk level two chunk} generated by {@link
	 * #createChunk(A_RawFunction)}.  It can be retrieved via {@link #chunk()}.
	 */
	private @Nullable L2Chunk chunk;

	/**
	 * The {@link L2BasicBlock} which is the entry point for a function that has
	 * just been invoked.
	 */
	final L2BasicBlock initialBlock = createBasicBlock("START");

	/** The {@link L2BasicBlock} that code is currently being generated into. */
	private @Nullable L2BasicBlock currentBlock = initialBlock;

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
	@InnerAccess void addUnreachableCode ()
	{
		addInstruction(L2_JUMP.instance, unreachablePcOperand());
		startBlock(createBasicBlock("an unreachable block"));
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
			unreachableBlock = createBasicBlock("UNREACHABLE");
			// Because we generate the initial code in control flow order, we
			// have to wait until later to generate the instructions.  We strip
			// out all phi information here.
		}
		return new L2PcOperand(unreachableBlock, currentManifest);
	}

	/**
	 * Answer a {@link L2WritePhiOperand} that writes to the specified
	 * {@link L2Register}.
	 *
	 * @param register
	 *        The register.
	 * @return The new register write operand.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public <R extends L2Register, T extends A_BasicObject>
	L2WritePhiOperand<R> newPhiRegisterWriter (final R register)
	{
		return new L2WritePhiOperand<>(register);
	}

	/**
	 * Allocate a new {@link L2ObjectRegister}.  Answer an {@link
	 * L2WritePointerOperand} that writes to it, using the given {@link
	 * TypeRestriction}.
	 *
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new register.
	 * @return The new register write operand.
	 */
	public L2WritePointerOperand newObjectRegisterWriter (
		final TypeRestriction restriction)
	{
		return new L2WritePointerOperand(
			new L2ObjectRegister(nextUnique(), restriction));
	}

	/**
	 * Allocate a new {@link L2IntRegister}.  Answer an {@link
	 * L2WriteIntOperand} that writes to it, using the given {@link
	 * TypeRestriction}.
	 *
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new register.
	 * @return The new register write operand.
	 */
	public L2WriteIntOperand newIntRegisterWriter (
		final TypeRestriction restriction)
	{
		return new L2WriteIntOperand(
			new L2IntRegister(nextUnique(), restriction));
	}

	/**
	 * Allocate a new {@link L2FloatRegister}.  Answer an {@link
	 * L2WriteFloatOperand} that writes to it, using the given {@link
	 * TypeRestriction}.
	 *
	 * @param restriction
	 *        The initial {@link TypeRestriction} for the new register.
	 * @return The new register write operand.
	 */
	public L2WriteFloatOperand newFloatRegisterWriter (
		final TypeRestriction restriction)
	{
		return new L2WriteFloatOperand(
			new L2FloatRegister(nextUnique(), restriction));
	}

	/**
	 * Write a constant value into a new register. Answer an {@link
	 * L2ReadPointerOperand} for that register. If another register has already
	 * been assigned the same value within the same {@link L2BasicBlock}, just
	 * use that instead of emitting another constant-move.
	 *
	 * @param value
	 *        The constant value to write to a register.
	 * @return The {@link L2ReadPointerOperand} for the new register.
	 */
	public L2ReadPointerOperand constantRegister (final A_BasicObject value)
	{
		final L2SemanticValue constant = L2SemanticValue.constant(value);
		final @Nullable L2Synonym synonym =
			currentManifest().semanticValueToSynonym(constant);
		if (synonym != null)
		{
			synonym.setFlag(KNOWN_IMMUTABLE);
			final @Nullable L2ReadPointerOperand read =
				synonym.defaultObjectRead();
			if (read != null)
			{
				return read;
			}
		}
		// The constant is not available in boxed form in any register.
		final A_Type type = value.equalsNil()
			? TOP.o()
			: instanceTypeOrMetaOn(value);
		final L2WritePointerOperand registerWrite =
			newObjectRegisterWriter(restriction(type, value));
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(value),
			registerWrite);
		currentManifest().addBinding(constant, registerWrite.register());
		stripNull(currentManifest().semanticValueToSynonym(constant))
			.setFlag(KNOWN_IMMUTABLE);
		return registerWrite.read();
	}

	/**
	 * Write a constant value into a new int register.  Answer an {@link
	 * L2ReadIntOperand} for that register.  If another register has already
	 * been assigned the same value within the same {@link L2BasicBlock}, just
	 * use that instead of emitting another constant-move.
	 *
	 * @param value
	 *        The immediate int to write to a new int register.
	 * @return The {@link L2ReadIntOperand} for the new register.
	 */
	public L2ReadIntOperand constantIntRegister (final int value)
	{
		final A_Number boxed = fromInt(value);
		final L2SemanticValue boxedConstant = L2SemanticValue.constant(boxed);
		final @Nullable L2Synonym synonym =
			currentManifest().semanticValueToSynonym(boxedConstant);
		if (synonym != null)
		{
			final @Nullable L2ReadIntOperand read = synonym.defaultIntRead();
			if (read != null)
			{
				return read;
			}
		}
		// The constant is not available in unboxed form in any int register.
		final L2WriteIntOperand registerWrite =
			newIntRegisterWriter(restriction(instanceType(boxed), boxed));
		addInstruction(
			L2_MOVE_INT_CONSTANT.instance,
			new L2IntImmediateOperand(value),
			registerWrite);
		currentManifest().addBinding(
			boxedConstant, registerWrite.register());
		return registerWrite.read();
	}

	/**
	 * Write a constant value into a new float register.  Answer an {@link
	 * L2ReadFloatOperand} for that register.  If another register has already
	 * been assigned the same value within the same {@link L2BasicBlock}, just
	 * use that instead of emitting another constant-move.
	 *
	 * @param value
	 *        The immediate {@code double} to write to a new float register.
	 * @return The {@link L2ReadFloatOperand} for the new register.
	 */
	public L2ReadFloatOperand constantFloatRegister (final double value)
	{
		final A_Number boxed = fromDouble(value);
		final L2SemanticValue boxedConstant = L2SemanticValue.constant(boxed);
		final @Nullable L2Synonym synonym =
			currentManifest().semanticValueToSynonym(boxedConstant);
		if (synonym!= null)
		{
			final @Nullable L2ReadFloatOperand read =
				synonym.defaultFloatRead();
			if (read != null)
			{
				return read;
			}
		}
		// The constant is not available in unboxed form in any float register.
		final L2WriteFloatOperand registerWrite =
			newFloatRegisterWriter(restriction(instanceType(boxed), boxed));
		addInstruction(
			L2_MOVE_FLOAT_CONSTANT.instance,
			new L2FloatImmediateOperand(value),
			registerWrite);
		currentManifest().addBinding(
			boxedConstant, registerWrite.register());
		return registerWrite.read();
	}

	/**
	 * Return an {@link L2ReadPointerOperand} for the given synonym, restricted
	 * to the specified type.  The type restriction must have been proven by the
	 * VM, and requires no tests or failure cases.  If the synonym only has an
	 * unboxed form, generate code to box it.
	 *
	 * @param register
	 *        The {@link L2Register} (any variety) for which to produce a read
	 *        of the boxed value.
	 * @param restriction
	 *        The {@link TypeRestriction} to apply to the resulting read.
	 * @return The requested {@link L2ReadPointerOperand}.
	 */
	public L2ReadPointerOperand readBoxedRegister (
		final L2Register register,
		final TypeRestriction restriction)
	{
		final L2ValueManifest manifest = currentManifest();
		final @Nullable L2Synonym synonym =
			manifest.forceSynonymForRegister(register);
		final @Nullable L2ReadPointerOperand objectRead =
			synonym.defaultObjectRead();
		if (objectRead != null)
		{
			return objectRead.register().read(restriction);
		}
		final L2WritePointerOperand boxedWriter =
			newObjectRegisterWriter(restriction);
		// It's not available in boxed form yet.
		final @Nullable L2ReadIntOperand intRead = synonym.defaultIntRead();
		if (intRead != null)
		{
			// Box it from the int value.
			addInstruction(L2_BOX_INT.instance, intRead, boxedWriter);
			currentManifest().linkEqualRegisters(
				intRead.register(), boxedWriter.register());
		}
		else
		{
			final @Nullable L2ReadFloatOperand floatRead =
				synonym.defaultFloatRead();
			assert floatRead != null : "Synonym was not boxed, int, or float";
			// Box it from the float value.
			addInstruction(L2_BOX_FLOAT.instance, floatRead, boxedWriter);
			currentManifest().linkEqualRegisters(
				floatRead.register(), boxedWriter.register());
		}
		return boxedWriter.register().read(restriction);
	}

	/**
	 * Return an {@link L2ReadIntOperand} for the given {@link L2Register} (of
	 * any variety), restricted to the specified type.  The type restriction
	 * must have been proven by the VM.  If the register's synonym only has a
	 * boxed form, generate code to unbox it.
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
	 * @param register
	 *        The {@link L2Register} to read as an unboxed int.
	 * @param restriction
	 *        The {@link TypeRestriction} to use for the {@link
	 *        L2ReadIntOperand}.
	 * @param onFailure
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_INT}
	 *        fails. The manifest at this location will not contain bindings for
	 *        the unboxed {@code int} (since unboxing was not possible).
	 * @return The unboxed {@link L2ReadIntOperand}.
	 */
	public L2ReadIntOperand readIntRegister (
		final L2Register register,
		final TypeRestriction restriction,
		final L2BasicBlock onFailure)
	{
		assert !restriction.type.typeIntersection(int32()).isBottom();
		final L2ValueManifest manifest = currentManifest();
		final @Nullable L2Synonym synonym =
			manifest.forceSynonymForRegister(register);
		final @Nullable L2ReadIntOperand intRead = synonym.defaultIntRead();
		if (intRead != null)
		{
			return intRead.register().read(restriction);
		}
		// It's not available as an unboxed int yet.
		final @Nullable L2ReadPointerOperand objectRead =
			synonym.defaultObjectRead();
		if (objectRead == null
			|| objectRead.type().typeIntersection(int32()).isBottom())
		{
			// It's not available unboxed as an int, and it's not available
			// boxed.  The only other possibility is that it's some other
			// unboxed value, which can't be an int, so it should always fail.
			// This clause is also used if there was a boxed form, but the type
			// ensured it couldn't be an int32.
			addInstruction(
				L2_JUMP.instance,
				new L2PcOperand(onFailure, currentManifest()));
			// Return a dummy, which should get suppressed or optimized away.
			return constantIntRegister(-999);
		}
		if (objectRead.type().isSubtypeOf(int32()))
		{
			// It can be infallibly converted.
			final @Nullable AvailObject constant = objectRead.constantOrNull();
			if (constant != null)
			{
				// The boxed value is a constant within int32, so create an
				// equivalent int constant register.
				return constantIntRegister(constant.extractInt());
			}
			final L2WriteIntOperand intWriter =
				newIntRegisterWriter(restriction);
			addInstruction(
				L2_UNBOX_INT.instance,
				objectRead,
				intWriter);
			currentManifest().linkEqualRegisters(
				objectRead.register(), intWriter.register());
			return intWriter.register().read(restriction);
		}
		// Conversion may succeed or fail at runtime.
		final L2BasicBlock onSuccess = createBasicBlock("successfully unboxed");
		final L2WriteIntOperand intWriter = newIntRegisterWriter(restriction);
		addInstruction(
			L2_JUMP_IF_UNBOX_INT.instance,
			objectRead,
			intWriter,
			new L2PcOperand(
				onSuccess,
				currentManifest(),
				intWriter.read().restrictedToType(int32())),
			new L2PcOperand(
				onFailure,
				currentManifest(),
				intWriter.read().restrictedWithoutType(int32())));
		startBlock(onSuccess);
		currentManifest().linkEqualRegisters(
			objectRead.register(), intWriter.register());
		return intWriter.register().read(restriction);
	}

	/**
	 * Return an {@link L2ReadFloatOperand} for the given {@link L2Register} (of
	 * any variety), restricted to the specified type.  The type restriction
	 * must have been proven by the VM.  If the register's synonym only has a
	 * boxed form, generate code to unbox it.
	 *
	 * <p>In the case that unboxing may fail, a branch to the supplied onFailure
	 * {@link L2BasicBlock} will be generated. If the unboxing cannot fail (or
	 * if a corresponding {@link L2FloatRegister}  already exists), no branch
	 * will lead to onFailure, which can be determined by the client by testing
	 * {@link L2BasicBlock#currentlyReachable()}. </p>
	 *
	 * <p>In any case, the generation position after this call is along the
	 * success path.  This may itself be unreachable in the event the unboxing
	 * will always fail.</p>
	 *
	 * @param register
	 *        The {@link L2Register} to read as an unboxed double.
	 * @param restriction
	 *        The {@link TypeRestriction} to use for the {@link
	 *        L2ReadFloatOperand}.
	 * @param onFailure
	 *        Where to jump in the event that an {@link L2_JUMP_IF_UNBOX_FLOAT}
	 *        fails. The manifest at this location will not contain bindings for
	 *        the unboxed {@code int} (since unboxing was not possible).
	 * @return The unboxed {@link L2ReadFloatOperand}.
	 */
	public L2ReadFloatOperand readFloatRegister (
		final L2Register register,
		final TypeRestriction restriction,
		final L2BasicBlock onFailure)
	{
		assert !restriction.type.typeIntersection(int32()).isBottom();
		final L2ValueManifest manifest = currentManifest();
		final @Nullable L2Synonym synonym =
			manifest.forceSynonymForRegister(register);
		final @Nullable L2ReadFloatOperand floatRead =
			synonym.defaultFloatRead();
		if (floatRead != null)
		{
			return floatRead.register().read(restriction);
		}
		// It's not available as an unboxed double yet.
		final @Nullable L2ReadPointerOperand objectRead =
			synonym.defaultObjectRead();
		if (objectRead == null
			|| objectRead.type().typeIntersection(DOUBLE.o()).isBottom())
		{
			// It's not available unboxed as a double, and it's not available
			// boxed.  The only other possibility is that it's some other
			// unboxed value, which can't be a double, so it should always fail.
			// This clause is also used if there was a boxed form, but the type
			// ensured it couldn't be a double.
			addInstruction(
				L2_JUMP.instance,
				new L2PcOperand(onFailure, currentManifest()));
			// Return a dummy, which should get suppressed or optimized away.
			return constantFloatRegister(-99.9);
		}
		if (objectRead.type().isSubtypeOf(DOUBLE.o()))
		{
			// It can be infallibly converted.
			final @Nullable AvailObject constant = objectRead.constantOrNull();
			if (constant != null)
			{
				// The boxed value is a constant double, so create an
				// equivalent double constant register.
				return constantFloatRegister(constant.extractDouble());
			}
			final L2WriteFloatOperand floatWriter =
				newFloatRegisterWriter(restriction);
			addInstruction(
				L2_UNBOX_FLOAT.instance,
				objectRead,
				floatWriter);
			currentManifest().linkEqualRegisters(
				objectRead.register(), floatWriter.register());
			return floatWriter.register().read(restriction);
		}
		// Conversion may succeed or fail at runtime.
		final L2BasicBlock onSuccess = createBasicBlock("successfully unboxed");
		final L2WriteFloatOperand floatWriter =
			newFloatRegisterWriter(restriction);
		addInstruction(
			L2_JUMP_IF_UNBOX_INT.instance,
			objectRead,
			floatWriter,
			new L2PcOperand(
				onSuccess,
				currentManifest(),
				floatWriter.read().restrictedToType(DOUBLE.o())),
			new L2PcOperand(
				onFailure,
				currentManifest(),
				floatWriter.read().restrictedWithoutType(DOUBLE.o())));
		startBlock(onSuccess);
		currentManifest().linkEqualRegisters(
			objectRead.register(), floatWriter.register());
		return floatWriter.register().read(restriction);
	}

	/**
	 * Generate instruction(s) to move from one register to another.
	 *
	 * @param sourceRead
	 *        Which object register to read.
	 * @param destinationWrite
	 *        Which object register to write.
	 */
	void moveRegister (
		final L2ReadPointerOperand sourceRead,
		final L2WritePointerOperand destinationWrite)
	{
		addInstruction(L2_MOVE.instance, sourceRead, destinationWrite);
		currentManifest().linkEqualRegisters(
			sourceRead.register(), destinationWrite.register());
	}

	/**
	 * Generate code to ensure an immutable version of the given register is
	 * written to the returned register.  Update the {@link
	 * L2Generator#currentManifest()} to indicate the returned register should
	 * be used for all of the given register's semantic values after this point.
	 *
	 * @param sourceRegister
	 *        The register that was given.
	 * @return The resulting register, holding an immutable version of the given
	 *         register.
	 */
	L2ReadPointerOperand makeImmutable (
		final L2ReadPointerOperand sourceRegister)
	{
		if (currentManifest().isAlreadyImmutable(
			sourceRegister.register()))
		{
			return sourceRegister;
		}
		final L2WritePointerOperand destinationWrite =
			newObjectRegisterWriter(sourceRegister.restriction());
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			sourceRegister,
			destinationWrite);
		currentManifest().introduceImmutable(sourceRegister, destinationWrite);
		return destinationWrite.read();
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
	 * Start code generation for the given {@link L2BasicBlock}.  This naive
	 * translator doesn't create loops, so ensure all predecessor blocks have
	 * already finished generation.
	 *
	 * <p>Also, reconcile the slot registers that were collected for each
	 * predecessor, creating an {@link L2_PHI_PSEUDO_OPERATION} if needed.</p>
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
			if (block.predecessorEdgesCount() == 1)
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
						singletonList(predecessorEdge.manifest()), this);
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
				new L2Instruction(currentBlock, operation, operands));
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
			currentBlock.addInstruction(instruction);
		}
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
			afterOptionalInitialPrimitiveBlock == null
				? stripNull(initialBlock).offset()
				: afterOptionalInitialPrimitiveBlock.offset();
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
	 *        The optimization level.
	 */
	L2Generator (
		final OptimizationLevel optimizationLevel)
	{
		this.optimizationLevel = optimizationLevel;
	}

	/**
	 * Statistics about final chunk generation from the optimized {@link
	 * L2ControlFlowGraph}.
	 */
	static final Statistic finalGenerationStat = new Statistic(
		"Final chunk generation", L2_OPTIMIZATION_TIME);

	public static class RegisterCounter
	implements L2OperandDispatcher
	{
		int objectMax = -1;
		int intMax = -1;
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
		public void doOperand (final L2ReadPointerOperand operand)
		{
			objectMax = max(objectMax, operand.finalIndex());
		}

		@Override
		public <
			RR extends L2ReadOperand<R>,
			R extends L2Register>
		void
			doOperand (final L2ReadVectorOperand<RR, R> operand)
		{
			for (final L2ReadOperand<?> register : operand.elements())
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
		public void doOperand (final L2WriteFloatOperand operand)
		{
			floatMax = max(floatMax, operand.finalIndex());
		}

		@Override
		public void doOperand (final L2WritePointerOperand operand)
		{
			objectMax = max(objectMax, operand.finalIndex());
		}

		@Override
		public <R extends L2Register>
		void doOperand (final L2WritePhiOperand<R> operand)
		{
			final L2Register register = operand.register();
			if (register instanceof L2ObjectRegister)
			{
				objectMax = max(objectMax, operand.finalIndex());
			}
			else if (register instanceof L2IntRegister)
			{
				intMax = max(intMax, operand.finalIndex());
			}
			else
			{
				assert register instanceof L2FloatRegister;
				floatMax = max(floatMax, operand.finalIndex());
			}
		}
	}
}

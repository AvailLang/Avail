/**
 * L2Translator.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static java.lang.Math.max;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.utility.*;

/**
 * The {@code L2Translator} converts a level one {@linkplain FunctionDescriptor
 * function} into a {@linkplain L2ChunkDescriptor level two chunk}.  It
 * optimizes as it does so, folding and inlining method invocations whenever
 * possible.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2Translator
{
	/**
	 * Whether detailed optimization information should be logged.
	 */
	final static boolean debugGeneration = false;

	/**
	 * Whether detailed optimization information should be logged.
	 */
	final static boolean debugOptimizer = false;

	/**
	 * Whether detailed optimization information should be logged.
	 */
	final static boolean debugDataFlow = false;

	/**
	 * Whether detailed optimization information should be logged.
	 */
	final static boolean debugRemoveDeadInstructions = false;

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
		NATIVE
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
	 * All {@link MethodDescriptor methods} for which changes should cause the
	 * current {@linkplain L2ChunkDescriptor level two chunk} to be invalidated.
	 */
	@InnerAccess final Set<A_Method> contingentMethods = new HashSet<>();

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
		FixedRegister.values().length;

	/**
	 * Answer the specified fixed register.
	 *
	 * @param registerEnum The {@link FixedRegister} identifying the register.
	 * @return The {@link L2ObjectRegister} named by the registerEnum.
	 */
	public L2ObjectRegister fixed (
		final FixedRegister registerEnum)
	{
		return architecturalRegisters.get(registerEnum.ordinal());
	}

	/**
	 * Answer the register holding the specified continuation slot.  The slots
	 * are the arguments, then the locals, then the stack entries.  The first
	 * argument occurs just after the {@link FixedRegister}s.
	 *
	 * @param slotNumber
	 *            The index into the continuation's slots.
	 * @return
	 *            A register representing that continuation slot.
	 */
	public L2ObjectRegister continuationSlot (
		final int slotNumber)
	{
		return architecturalRegisters.get(
			firstArgumentRegisterIndex - 1 + slotNumber);
	}

	/**
	 * Answer the register holding the specified argument/local number (the
	 * 1st argument is the 3rd architectural register).
	 *
	 * @param argumentNumber
	 *            The argument number for which the "architectural" register is
	 *            being requested.  If this is greater than the number of
	 *            arguments, then answer the register representing the local
	 *            variable at that position minus the number of registers.
	 * @return A register that represents the specified argument or local.
	 */
	L2ObjectRegister argumentOrLocal (
		final int argumentNumber)
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
	@InnerAccess L2ObjectRegister stackRegister (
		final int stackIndex)
	{
		assert 1 <= stackIndex && stackIndex <= codeOrFail().maxStackDepth();
		return continuationSlot(numArgs + numLocals + stackIndex);
	}

	/**
	 * Allocate a fresh {@linkplain L2ObjectRegister object register} that
	 * nobody else has used yet.
	 *
	 * @return The new register.
	 */
	L2ObjectRegister newObjectRegister ()
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
	 * Only inline truly monomorphic messages for now -- i.e., method calls
	 * where only one method definition is possible and it can be inlined.
	 * Return the primitive function in such a case, otherwise null.
	 *
	 * @param method
	 *            The {@linkplain MethodDescriptor method} containing the
	 *            method(s) that may be inlined or invoked.
	 * @param args
	 *            A {@link List} of {@linkplain L2ObjectRegister registers}
	 *            holding the actual constant values used to look up the method
	 *            definition for the call.
	 * @param registerSet
	 *            A {@link RegisterSet} indicating the current state of the
	 *            registers at this invocation point.
	 * @return
	 *            The sole applicable method definition's primitive {@linkplain
	 *            FunctionDescriptor function}, or {@code null} otherwise.
	 */
	@InnerAccess
	@Nullable A_Function primitiveFunctionToInline (
		final A_Method method,
		final List<L2ObjectRegister> args,
		final RegisterSet registerSet)
	{
		final List<A_Type> argTypes = new ArrayList<>(args.size());
		for (final L2ObjectRegister arg : args)
		{
			argTypes.add(
				registerSet.hasTypeAt(arg) ? registerSet.typeAt(arg) : ANY.o());
		}
		return primitiveFunctionToInline(method, argTypes);
	}

	/**
	 * Only inline truly monomorphic messages for now -- i.e., method calls
	 * where only one method definition is possible and it can be inlined.
	 * Return the primitive function in such a case, otherwise null.
	 *
	 * @param method
	 *            The {@linkplain MethodDescriptor method} containing the
	 *            method(s) that may be inlined or invoked.
	 * @param argTypes
	 *            The types of the arguments to the call.
	 * @return
	 *            The sole applicable method definition's primitive {@linkplain
	 *            FunctionDescriptor function}, or {@code null} otherwise.
	 */
	private @Nullable A_Function primitiveFunctionToInline (
		final A_Method method,
		final List<A_Type> argTypes)
	{
		final List<A_Definition> defs = method.definitionsAtOrBelow(argTypes);
		A_Function primitiveBody = null;
		for (final A_Definition def : defs)
		{
			// If a forward or abstract method is possible, don't inline.
			if (!def.isMethodDefinition())
			{
				return null;
			}
			final A_Function body = def.bodyBlock();
			final int primitiveNumber = body.code().primitiveNumber();
			if (primitiveNumber == 0 || primitiveBody != null)
			{
				return null;
			}
			primitiveBody = body;
		}
		if (primitiveBody == null)
		{
			return null;
		}
		final Primitive primitive = Primitive.byPrimitiveNumberOrFail(
			primitiveBody.code().primitiveNumber());
		if (primitive.hasFlag(SpecialReturnConstant)
				|| primitive.hasFlag(CanInline)
				|| primitive.hasFlag(CanFold))
		{
			return primitiveBody;
		}
		return null;
	}

	/**
	 * The {@code L1NaiveTranslator} simply transliterates a sequence of
	 * {@linkplain L1Instruction level one instructions} into one or more simple
	 * {@linkplain L2Instruction level two instructions}, under the assumption
	 * that further optimization steps will be able to transform this code into
	 * something much more efficient – without altering the level one semantics.
	 */
	public class L1NaiveTranslator implements L1OperationDispatcher
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
		private int pc = 1;
		/**
		 * The current stack depth during naive translation to level two.
		 */
		private int stackp = code.maxStackDepth() + 1;

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
		@Nullable RegisterSet naiveRegisters =
			new RegisterSet(L2Translator.this);

		/**
		 * Answer the {@link RegisterSet} information at the current position in
		 * the level one code being transliterated.
		 *
		 * @return The current RegisterSet for level one transliteration.
		 */
		RegisterSet naiveRegisters ()
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
		 * A label (an {@link L2Instruction} whose operation is a {@link
		 * L2_LABEL}) which is generated before any other level two code.  It
		 * is used when the {@link #L1Ext_doPushLabel()} needs to synthesize a
		 * continuation to push.
		 */
		final L2Instruction restartLabel = newLabel("continuation restart");

		/**
		 * Create and add an {@link L2Instruction} with the given {@link
		 * L2Operation} and variable number of {@link L2Operand}s.
		 *
		 * @param operation The operation to invoke.
		 * @param operands The operands of the instruction.
		 */
		@InnerAccess void addInstruction (
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
				naiveRegs = new RegisterSet(L2Translator.this);
				naiveRegisters = naiveRegs;
			}
			final L2Instruction normalizedInstruction =
				instruction.transformRegisters(naiveRegs.normalizer);
			if (debugGeneration)
			{
				final StringBuilder builder = new StringBuilder(100);
				naiveRegs.debugOn(builder);
				System.out.format(
					"\t#%d = NAIVE: %s%n\t<-%s%n",
					instructions.size(),
					normalizedInstruction,
					builder.toString().replace("\n", "\n\t"));
			}
			normalizedInstruction.setOffset(instructions.size());
			instructions.add(normalizedInstruction);
			final List<L2Instruction> successors =
				new ArrayList<>(normalizedInstruction.targetLabels());
			if (normalizedInstruction.operation.reachesNextInstruction())
			{
				successors.add(0, null);
			}
			final List<RegisterSet> successorRegisterSets =
				new ArrayList<>(successors.size());
			for (int i = 0, end = successors.size(); i < end; i++)
			{
				successorRegisterSets.add(new RegisterSet(naiveRegs));
			}
			normalizedInstruction.propagateTypes(successorRegisterSets);
			naiveRegisters = null;
			for (int i = 0, end = successors.size(); i < end; i++)
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
					if (successor == restartLabel)
					{
						// Referring back is allowed if this block starts with a
						// label declaration.  And in this case, the only state
						// that the continuation can encode is the fixed
						// registers and the function arguments -- which is what
						// was known when we first output the label.  Therefore
						// the reference to this label doesn't expand or
						// restrict the register set's type/constant
						// information.  It might (maybe?) affect information
						// about the source of values, but we're currently doing
						// the naive transliteration pass where this has no
						// effect.
						assert successor.offset() == 0;
						assert instructions.get(0) == restartLabel;
					}
					else
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
							existing.add(successorRegisterSet);
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
		@InnerAccess void addLabel (
			final L2Instruction label)
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
					storedRegisterSet.add(naiveRegs);
				}
				naiveRegisters = storedRegisterSet;
			}
			if (debugGeneration)
			{
				final StringBuilder builder = new StringBuilder(100);
				naiveRegisters().debugOn(builder);
				System.out.format(
					"\t#%d = NAIVE: %s%n\t<-%s%n",
					label.offset(),
					label,
					builder.toString().replace("\n", "\n\t"));
			}
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
			contingentMethods.add(method);
			// The registers holding slot values that will constitute the
			// continuation *during* a non-primitive call.
			final List<L2ObjectRegister> preSlots = new ArrayList<>(numSlots);
			for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
			{
				preSlots.add(naiveRegisters().continuationSlot(slotIndex));
			}
			final L2ObjectRegister expectedTypeReg = newObjectRegister();
			final L2ObjectRegister failureObjectReg = newObjectRegister();
			final int nArgs = method.numArgs();
			final List<L2ObjectRegister> preserved = new ArrayList<>(preSlots);
			assert preserved.size() == numSlots;
			final List<L2ObjectRegister> args = new ArrayList<>(nArgs);
			final List<A_Type> argTypes = new ArrayList<>(nArgs);
			for (int i = nArgs; i >= 1; i--)
			{
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
			final A_Function primFunction =
				primitiveFunctionToInline(method, args, naiveRegisters());
			// The convergence point for primitive success and failure paths.
			final L2Instruction successLabel;
			successLabel = newLabel("success: " + bundle.message().atomName());
			final L2ObjectRegister tempCallerRegister = newObjectRegister();
			moveRegister(fixed(CALLER), tempCallerRegister);
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
			final List<A_Type> postSlotTypes = new ArrayList<>(numSlots);
			A_Map postSlotConstants = MapDescriptor.empty();
			A_Set nullPostSlots = SetDescriptor.empty();
			final List<L2ObjectRegister> postSlots = new ArrayList<>(numSlots);
			for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
			{
				final L2ObjectRegister reg = preSlots.get(slotIndex - 1);
				A_Type slotType = naiveRegisters().typeAt(reg);
				if (reg == expectedTypeReg)
				{
					// I.e., upon return from the call, this slot will contain
					// an *instance* of expectedType.
					slotType = expectedType;
				}
				postSlotTypes.add(slotType != null ? slotType : Types.TOP.o());
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
				postSlots.add(naiveRegisters().continuationSlot(slotIndex));
			}
			final L2Instruction postCallLabel =
				newLabel("postCall " + bundle.message().atomName());
			addInstruction(
				L2_CREATE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2ImmediateOperand(pc),
				new L2ImmediateOperand(stackp),
				new L2ReadVectorOperand(createVector(preSlots)),
				new L2PcOperand(postCallLabel),
				new L2WritePointerOperand(tempCallerRegister));
			final L2ObjectRegister function = newObjectRegister();
			// Look up the method body to invoke.
			final List<A_Definition> possibleMethods =
				method.definitionsAtOrBelow(argTypes);
			if (possibleMethods.size() == 1
				&& possibleMethods.get(0).isMethodDefinition())
			{
				// If there was only one possible definition to invoke, store it
				// as a constant instead of looking it up at runtime.
				moveConstant(possibleMethods.get(0).bodyBlock(), function);
			}
			else
			{
				// Look it up at runtime.
				addInstruction(
					L2_LOOKUP_BY_VALUES.instance,
					new L2SelectorOperand(bundle),
					new L2ReadVectorOperand(createVector(args)),
					new L2WritePointerOperand(function));
			}

			// Now invoke what was looked up.
			if (primFunction != null)
			{
				// Already tried the primitive.
				addInstruction(
					L2_INVOKE_AFTER_FAILED_PRIMITIVE.instance,
					new L2ReadPointerOperand(tempCallerRegister),
					new L2ReadPointerOperand(function),
					new L2ReadVectorOperand(createVector(args)),
					new L2ReadPointerOperand(failureObjectReg));
			}
			else
			{
				addInstruction(
					L2_INVOKE.instance,
					new L2ReadPointerOperand(tempCallerRegister),
					new L2ReadPointerOperand(function),
					new L2ReadVectorOperand(createVector(args)));
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
			addInstruction(
				L2_EXPLODE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2WriteVectorOperand(createVector(postSlots)),
				new L2WritePointerOperand(fixed(CALLER)),
				new L2WritePointerOperand(fixed(FUNCTION)),
				new L2ConstantOperand(TupleDescriptor.fromList(postSlotTypes)),
				new L2ConstantOperand(postSlotConstants),
				new L2ConstantOperand(nullPostSlots),
				new L2ConstantOperand(codeOrFail().functionType()));
			addLabel(successLabel);
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
				moveConstant(value, resultRegister);
				// Restriction might be too strong even on a constant method.
				if (value.isInstanceOf(expectedType))
				{
					canFailPrimitive.value = false;
					return value;
				}
				// The primitive will technically succeed, but the return will
				// trip a failure to meet the strengthened return type.
				addInstruction(
					L2_REPORT_INVALID_RETURN_TYPE.instance,
					new L2PrimitiveOperand(primitive),
					new L2ReadPointerOperand(resultRegister),
					new L2ConstantOperand(expectedType));
				// No need to generate primitive failure handling code, since
				// technically the primitive succeeded but the return failed.
				// The above instruction effectively makes the successor
				// instructions unreachable, so don't spend a lot of time
				// generating that dead code.
				canFailPrimitive.value = false;
				return null;
			}
			if (primitive.hasFlag(SpecialReturnSoleArgument))
			{
				// Use the only argument as the return value.
				assert primitiveFunction.code().numArgs() == 1;
				assert args.size() == 1;
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
				final Result success = interpreter().attemptPrimitive(
					primitiveNumber,
					primitiveFunction,
					argValues);
				if (success == SUCCESS)
				{
					final AvailObject value = interpreter().latestResult();
					if (value.isInstanceOf(expectedType))
					{
						value.makeImmutable();
						moveConstant(value, resultRegister);
						canFailPrimitive.value = false;
						return value;
					}
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
			final boolean skipReturnCheck =
				guaranteedReturnType.isSubtypeOf(expectedType);
			if (primitive.hasFlag(CannotFail))
			{
				if (skipReturnCheck)
				{
					addInstruction(
						L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK.instance,
						new L2PrimitiveOperand(primitive),
						new L2ReadVectorOperand(createVector(args)),
						new L2WritePointerOperand(resultRegister));
				}
				else
				{
					addInstruction(
						L2_RUN_INFALLIBLE_PRIMITIVE.instance,
						new L2PrimitiveOperand(primitive),
						new L2ReadVectorOperand(createVector(args)),
						new L2ConstantOperand(expectedType),
						new L2WritePointerOperand(resultRegister));
				}
				canFailPrimitive.value = false;
			}
			else
			{
				if (skipReturnCheck)
				{
					addInstruction(
						L2_ATTEMPT_INLINE_PRIMITIVE_NO_CHECK.instance,
						new L2PrimitiveOperand(primitive),
						new L2ConstantOperand(primitiveFunction),
						new L2ReadVectorOperand(createVector(args)),
						new L2WritePointerOperand(resultRegister),
						new L2WritePointerOperand(failureValueRegister),
						new L2ReadWriteVectorOperand(createVector(preserved)),
						new L2PcOperand(successLabel));
				}
				else
				{
					addInstruction(
						L2_ATTEMPT_INLINE_PRIMITIVE.instance,
						new L2PrimitiveOperand(primitive),
						new L2ConstantOperand(primitiveFunction),
						new L2ReadVectorOperand(createVector(args)),
						new L2ConstantOperand(expectedType),
						new L2WritePointerOperand(resultRegister),
						new L2WritePointerOperand(failureValueRegister),
						new L2ReadWriteVectorOperand(createVector(preserved)),
						new L2PcOperand(successLabel));
				}
				canFailPrimitive.value = true;
			}
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
			// Capture numSlots into a local final variable for use with
			// L2_EXPLODE_CONTINUATION's propagation logic.
			final int nSlots = numSlots;
			final List<L2ObjectRegister> slots = new ArrayList<>(nSlots);
			for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
			{
				slots.add(continuationSlot(slotIndex));
			}
			final List<A_Type> savedSlotTypes = new ArrayList<>(nSlots);
			final List<A_BasicObject> savedSlotConstants =
				new ArrayList<>(nSlots);
			for (final L2ObjectRegister reg : slots)
			{
				savedSlotTypes.add(registerSet.typeAt(reg));
				savedSlotConstants.add(registerSet.constantAt(reg));
			}
			addInstruction(
				L2_CREATE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2ImmediateOperand(pc),
				new L2ImmediateOperand(stackp),
				new L2ReadVectorOperand(createVector(slots)),
				new L2PcOperand(postInterruptLabel),
				new L2WritePointerOperand(reifiedRegister));
			addInstruction(
				L2_PROCESS_INTERRUPT.instance,
				new L2ReadPointerOperand(reifiedRegister));
			addLabel(postInterruptLabel);
			addInstruction(
				L2_REENTER_L2_CHUNK.instance,
				new L2WritePointerOperand(fixed(CALLER)));
			final List<A_Type> typesList = new ArrayList<>(nSlots);
			A_Map constants = MapDescriptor.empty();
			A_Set nullSlots = SetDescriptor.empty();
			for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
			{
				final A_Type type = savedSlotTypes.get(slotIndex - 1);
				typesList.add(type != null ? type : Types.TOP.o());
				final A_BasicObject constant =
					savedSlotConstants.get(slotIndex - 1);
				if (constant != null && !constant.equalsNil())
				{
					constants = constants.mapAtPuttingCanDestroy(
						IntegerDescriptor.fromInt(slotIndex),
						constant,
						true);
				}
				else if (constant != null)
				{
					// It's nil.
					nullSlots = nullSlots.setWithElementCanDestroy(
						IntegerDescriptor.fromInt(slotIndex),
						true);
				}
			}
			addInstruction(
				L2_EXPLODE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2WriteVectorOperand(createVector(slots)),
				new L2WritePointerOperand(fixed(CALLER)),
				new L2WritePointerOperand(fixed(FUNCTION)),
				new L2ConstantOperand(TupleDescriptor.fromList(typesList)),
				new L2ConstantOperand(constants),
				new L2ConstantOperand(nullSlots),
				new L2ConstantOperand(codeOrFail().functionType()));
			addLabel(noInterruptLabel);
		}

		/**
		 * For each level one instruction, write a suitable transliteration into
		 * level two instructions.
		 */
		void addNaiveInstructions ()
		{
			if (optimizationLevel == OptimizationLevel.UNOPTIMIZED)
			{
				// Optimize it again if it's called frequently enough.
				code.countdownToReoptimize(
					L2ChunkDescriptor.countdownForNewlyOptimizedCode());
				addInstruction(
					L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
					new L2ImmediateOperand(
						OptimizationLevel.FIRST_TRANSLATION.ordinal()));
			}
			final List<L2ObjectRegister> initialRegisters =
				new ArrayList<>(FixedRegister.values().length);
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
			addLabel(restartLabel);
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
				// Move the primitive failure value into the first local.
				addInstruction(
					L2_SET_VARIABLE.instance,
					new L2ReadPointerOperand(argumentOrLocal(numArgs + 1)),
					new L2ReadPointerOperand(fixed(PRIMITIVE_FAILURE)));
			}
			// Store nil into each of the stack slots.
			for (
				int stackSlot = 1, end = code.maxStackDepth();
				stackSlot <= end;
				stackSlot++)
			{
				moveConstant(
					NilDescriptor.nil(),
					stackRegister(stackSlot));
			}
			// Check for interrupts. If an interrupt is discovered, then reify
			// and process the interrupt. When the chunk resumes, it will
			// explode the continuation again.
			emitInterruptOffRamp(new RegisterSet(naiveRegisters()));

			// Transliterate each level one nybblecode into level two wordcodes.
			while (pc <= nybbles.tupleSize())
			{
				final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
				pc++;
				L1Operation.values()[nybble].dispatch(this);
			}
			// Translate the implicit L1_doReturn instruction that terminates
			// the instruction sequence.
			L1Operation.L1Implied_Return.dispatch(this);
			assert pc == nybbles.tupleSize() + 1;
			assert stackp == Integer.MIN_VALUE;
		}

		@Override
		public void L1_doCall ()
		{
			final AvailObject method = code.literalAt(getInteger());
			final AvailObject expectedType = code.literalAt(getInteger());
			generateCall(method, expectedType);
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
				moveConstant(
					NilDescriptor.nil(),
					stackRegister(stackIndex));
			}
		}

		@Override
		public void L1_doExtension ()
		{
			// The extension nybblecode was encountered.  Read another nybble and
			// add 16 to get the L1Operation's ordinal.
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.values()[nybble + 16].dispatch(this);
		}

		@Override
		public void L1_doGetLocal ()
		{
			final int index = getInteger();
			stackp--;
			addInstruction(
				L2_GET_VARIABLE.instance,
				new L2ReadPointerOperand(argumentOrLocal(index)),
				new L2WritePointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doGetLocalClearing ()
		{
			final int index = getInteger();
			stackp--;
			addInstruction(
				L2_GET_VARIABLE_CLEARING.instance,
				new L2ReadPointerOperand(argumentOrLocal(index)),
				new L2WritePointerOperand(stackRegister(stackp)));
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
				new L2WritePointerOperand(stackRegister(stackp)));
			addInstruction(
				L2_GET_VARIABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)),
				new L2WritePointerOperand(stackRegister(stackp)));
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
				new L2WritePointerOperand(stackRegister(stackp)));
			addInstruction(
				L2_GET_VARIABLE_CLEARING.instance,
				new L2ReadPointerOperand(stackRegister(stackp)),
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
		}

		@Override
		public void L1_doPop ()
		{
			assert stackp == code.maxStackDepth()
			: "Pop should only only occur at end of statement";
			moveConstant(NilDescriptor.nil(), stackRegister(stackp));
			stackp++;
		}

		@Override
		public void L1_doPushLastLocal ()
		{
			final int localIndex = getInteger();
			stackp--;
			moveRegister(
				argumentOrLocal(localIndex),
				stackRegister(stackp));
			moveConstant(
				NilDescriptor.nil(),
				argumentOrLocal(localIndex));
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
				new L2WritePointerOperand(stackRegister(stackp)));
			addInstruction(
				L2_MAKE_IMMUTABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doPushLiteral ()
		{
			final AvailObject constant = code.literalAt(getInteger());
			stackp--;
			moveConstant(constant, stackRegister(stackp));
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
		public void L1_doPushOuter ()
		{
			final int outerIndex = getInteger();
			stackp--;
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				new L2ReadPointerOperand(fixed(FUNCTION)),
				new L2WritePointerOperand(stackRegister(stackp)));
			addInstruction(
				L2_MAKE_IMMUTABLE.instance,
				new L2ReadPointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1_doSetLocal ()
		{
			final int localIndex = getInteger();
			final L2ObjectRegister local =
				argumentOrLocal(localIndex);
			addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				new L2ReadPointerOperand(local),
				new L2ReadPointerOperand(stackRegister(stackp)));
			stackp++;
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
				new L2WritePointerOperand(tempReg));
			addInstruction(
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
		public void L1Ext_doGetLiteral ()
		{
			final L2ObjectRegister tempReg = newObjectRegister();
			final AvailObject constant = code.literalAt(getInteger());
			stackp--;
			moveConstant(constant, tempReg);
			addInstruction(
				L2_GET_VARIABLE.instance,
				new L2ReadPointerOperand(tempReg),
				new L2WritePointerOperand(stackRegister(stackp)));
		}

		@Override
		public void L1Ext_doPushLabel ()
		{
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
		public void L1Ext_doReserved ()
		{
			// This shouldn't happen unless the compiler is out of sync with the
			// translator.
			error("That nybblecode is not supported");
			return;
		}

		@Override
		public void L1Ext_doSetLiteral ()
		{
			final AvailObject constant = code.literalAt(getInteger());
			final L2ObjectRegister tempReg = newObjectRegister();
			moveConstant(constant, tempReg);
			addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				new L2ReadPointerOperand(tempReg),
				new L2ReadPointerOperand(stackRegister(stackp)));
			stackp++;
		}

		@Override
		public void L1Implied_doReturn ()
		{
			addInstruction(
				L2_RETURN.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2ReadPointerOperand(stackRegister(stackp)));
			assert stackp == code.maxStackDepth();
			stackp = Integer.MIN_VALUE;
		}
	}


	/**
	 * Keep track of the total number of generated L2 instructions.
	 */
	public static long generatedInstructionCount = 0;

	/**
	 * Keep track of how many L2 instructions survived dead code elimination and
	 * redundant move elimination.
	 */
	public static long keptInstructionCount = 0;

	/**
	 * Keep track of how many L2 instructions were removed as part of dead code
	 * elimination and redundant move elimination.
	 */
	public static long removedInstructionCount = 0;

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
		if (debugOptimizer)
		{
			System.out.printf("%nOPTIMIZED: %s%n", codeOrFail());
			final Set<L2Instruction> kept = new HashSet<>(instructions);
			for (final L2Instruction instruction : originals)
			{
				System.out.printf("%n%s\t%s",
					kept.contains(instruction)
						? instruction.operation.shouldEmit()
							? "+"
							: "-"
						: "",
					instruction);
			}
			System.out.println();
		}
		final int survived = instructions.size();
		generatedInstructionCount += originals.size();
		keptInstructionCount += survived;
		removedInstructionCount += originals.size() - survived;
	}

	/**
	 * Compute the program state at each instruction. This information includes,
	 * for each register, its constant value (if any) and type information,
	 * other register that currently hold equivalent values, and the set of
	 * instructions that may have directly produced the current register value.
	 */
	void computeDataFlow ()
	{
		instructionRegisterSets.clear();
		instructionRegisterSets.add(new RegisterSet(this));
		for (int i = 1, end = instructions.size(); i < end; i++)
		{
			instructionRegisterSets.add(null);
		}
		final Deque<L2Instruction> instructionsToVisit = new ArrayDeque<>();
		instructionsToVisit.add(instructions.get(0));
		while (!instructionsToVisit.isEmpty())
		{
			final L2Instruction instruction = instructionsToVisit.removeFirst();
			final int instructionIndex = instruction.offset();
			if (debugDataFlow)
			{
				System.out.format(
					"Trace #%d (%s):%n",
					instructionIndex,
					instruction);
			}
			final RegisterSet regs =
				instructionRegisterSets.get(instructionIndex);
			final List<L2Instruction> successors =
				new ArrayList<>(instruction.targetLabels());
			if (instruction.operation.reachesNextInstruction())
			{
				successors.add(0, instructions.get(instructionIndex + 1));
			}
			// The list allTargets now holds every target instruction, starting
			// with the instruction following this one if this one
			// reachesNextInstruction().
			final List<RegisterSet> targetRegisterSets =
				new ArrayList<>(successors.size());
			for (int i = 0, end = successors.size(); i < end; i++)
			{
				targetRegisterSets.add(new RegisterSet(regs));
			}
			instruction.propagateTypes(targetRegisterSets);

			final List<L2Instruction> toAdd =
				new ArrayList<>(successors.size());
			for (int i = 0, end = successors.size(); i < end; i++)
			{
				final L2Instruction successor = successors.get(i);
				final RegisterSet targetRegisterSet = targetRegisterSets.get(i);
				final int targetInstructionNumber = successor.offset();
				if (debugDataFlow)
				{
					final StringBuilder builder = new StringBuilder(100);
					targetRegisterSet.debugOn(builder);
					System.out.format(
						"\t->#%d:%s%n",
						targetInstructionNumber,
						builder.toString().replace("\n", "\n\t"));
				}
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
					final boolean changed = existing.add(targetRegisterSet);
					followIt = changed;
				}
				if (followIt)
				{
					toAdd.add(successor);
				}
			}
			for (int i = toAdd.size() - 1; i >= 0; i--)
			{
				instructionsToVisit.addFirst(toAdd.get(i));
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
		if (debugRemoveDeadInstructions)
		{
			System.out.println("\nRemove dead instructions...\n");
		}
		// Sanity check to make sure all target labels are actually present.
		for (final L2Instruction instruction : instructions)
		{
			for (final L2Instruction targetLabel : instruction.targetLabels())
			{
				final int targetIndex = targetLabel.offset();
				assert instructions.get(targetIndex) == targetLabel;
			}
		}

		computeDataFlow();

		// Figure out which instructions are reachable.
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

		// We now know which instructions can be *reached* by the control flow.
		// Figure out which of those are actually *needed*.  An instruction is
		// needed if it has a side-effect, or if it produces a value consumed by
		// a needed instruction.  Seed with all reachable instructions that have
		// side-effects.
		final Set<L2Instruction> neededInstructions =
			new HashSet<>(instructions.size());
		for (final L2Instruction instruction : reachableInstructions)
		{
			if (instruction.hasSideEffect())
			{
				instructionsToVisit.add(instruction);
			}
		}

		if (debugRemoveDeadInstructions)
		{
			System.out.println("\nDirectly irremovable reachable instructions:");
			for (int i = 0, end = instructions.size(); i < end; i++)
			{
				final L2Instruction instruction = instructions.get(i);
				System.out.format(
					"\t%s: #%d %s%n",
					(instructionsToVisit.contains(instruction)
						? "Forced "
						: (reachableInstructions.contains(instruction)
							? "Reach  "
							: "pending")),
					i,
					instruction.toString().replace("\n", "\n\t\t"));
			}
			System.out.println("\nPropagation of needed instructions:");
		}
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
					final Set<L2Instruction> providingInstructions =
						registerSet.registerSourceInstructions.get(
							sourceRegister);
					if (providingInstructions != null)
					{
						if (debugRemoveDeadInstructions)
						{
							final Set<Integer> providingInstructionIndices =
								new TreeSet<>();
							for (final L2Instruction need :
								providingInstructions)
							{
								providingInstructionIndices.add(
									need.offset());
							}
							System.out.format(
								"\t\t#%d (%s) -> %s%n",
								instruction.offset(),
								sourceRegister,
								providingInstructionIndices);
						}
						instructionsToVisit.addAll(providingInstructions);
					}
				}
			}
		}

		// We now have a complete list of which instructions should be kept.
		assert instructions.containsAll(neededInstructions);
		if (debugRemoveDeadInstructions)
		{
			System.out.println("\nKeep/drop instruction list:");
			for (int i = 0, end = instructions.size(); i < end; i++)
			{
				final L2Instruction instruction = instructions.get(i);
				System.out.format(
					"\t%s #%d %s%n",
					neededInstructions.contains(instruction) ? "+" : "-",
					i,
					instruction.toString().replace("\n", "\n\t\t"));
			}
		}
		boolean anyChanges = instructions.retainAll(neededInstructions);

		// Now allow each instruction the opportunity to generate alternative
		// instructions in place of itself due to its RegisterSet information.
		// For example, it may be the case that only one method definition can
		// ever be looked up by a call, so its body can be looked up statically,
		// and possibly even inlined.  Fallible primitives may also be
		// effectively infallible with a particular subtype of arguments.
		// This is also the place where primitive folding can take place, since
		// it's the first time we have precise type and constant information.
		final List<L2Instruction> newInstructions =
			new ArrayList<>(instructions.size());
		for (final L2Instruction instruction : instructions)
		{
			anyChanges |= instruction.operation.regenerate(
				instruction,
				newInstructions,
				instructionRegisterSets.get(instruction.offset()));
		}
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
	 * The {@linkplain L2ChunkDescriptor level two chunk} generated by
	 * {@link #createChunk()}.  It can be retrieved via {@link #chunk()}.
	 */
	private @Nullable A_Chunk chunk;

	/**
	 * Generate a {@linkplain L2ChunkDescriptor Level Two chunk} from the
	 * already written instructions.  Store it in the L2Translator, from which
	 * it can be retrieved via {@link #chunk()}.
	 */
	private void createChunk ()
	{
		assert chunk == null;
		final L2CodeGenerator codeGen = new L2CodeGenerator();
		codeGen.setInstructions(instructions);
		codeGen.addContingentMethods(contingentMethods);
		chunk = codeGen.createChunkFor(codeOrNull()).makeShared();
	}

	/**
	 * Return the {@linkplain L2ChunkDescriptor chunk} previously created via
	 * {@link #createChunk()}.
	 *
	 * @return The chunk.
	 */
	private A_Chunk chunk ()
	{
		final A_Chunk c = chunk;
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
		for (int i = 0; i < numFixed; i++)
		{
			architecturalRegisters.add(
				L2ObjectRegister.precolored(nextUnique(), i));
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
		for (final FixedRegister regEnum : FixedRegister.values())
		{
			architecturalRegisters.add(
				L2ObjectRegister.precolored(nextUnique(), regEnum.ordinal()));
		}

		final L2Instruction loopStart = newLabel("main L1 loop");
		final L2Instruction reenterFromCallLabel =
			newLabel("reenter L1 from call");
		justAddInstruction(
			L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
			new L2ImmediateOperand(
				OptimizationLevel.FIRST_TRANSLATION.ordinal()));
		justAddInstruction(L2_PREPARE_NEW_FRAME.instance);
		instructions.add(loopStart);
		instructionRegisterSets.add(null);
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
			L2ChunkDescriptor.offsetToContinueUnoptimizedChunk();
	}

	/**
	 * Translate the previously supplied {@linkplain CompiledCodeDescriptor
	 * Level One compiled code object} into a sequence of {@linkplain
	 * L2Instruction Level Two instructions}. The optimization level specifies
	 * how hard to try to optimize this method. It is roughly equivalent to the
	 * level of inlining to attempt, or the ratio of code expansion that is
	 * permitted. An optimization level of zero is the bare minimum, which
	 * produces a naïve translation to {@linkplain L2ChunkDescriptor Level Two
	 * code}. The translation creates a counter that the Level Two code
	 * decrements each time it is invoked.  When it reaches zero, the method
	 * will be reoptimized with a higher optimization effort.
	 */
	private void translate ()
	{
		final A_RawFunction theCode = codeOrFail();
		numArgs = theCode.numArgs();
		numLocals = theCode.numLocals();
		numSlots = theCode.numArgsAndLocalsAndStack();
		contingentMethods.clear();
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
		assert chunk().index() != 0;
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
	 * @return The {@linkplain L2ChunkDescriptor level two chunk} corresponding
	 *         to the {@linkplain #codeOrNull} to be translated.
	 */
	public static A_Chunk createChunkForFirstInvocation ()
	{
		final L2Translator translator = new L2Translator();
		final A_Chunk newChunk = translator.chunk();
		assert newChunk.index() == 0;
		return newChunk;
	}
}

/**
 * L1NaiveTranslator.java
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

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.dispatch.InternalLookupTree;
import com.avail.dispatch.LookupTree;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelOne.L1OperationDispatcher;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.*;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.controlflow
	.P_RestartContinuationWithArguments;
import com.avail.utility.Mutable;
import com.sun.xml.internal.bind.v2.TODO;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import static com.avail.AvailRuntime.invalidMessageSendFunctionType;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.continuationTypeForFunctionType;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.*;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerDescriptor.one;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.METHOD_DEFINITION;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.FAILURE;
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static com.avail.optimizer.L2Translator.*;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

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
	 * The {@link L2Translator} for which I'm producing an initial translation.
	 */
	private final L2Translator translator;

	/**
	 * The {@linkplain CompiledCodeDescriptor raw function} to transliterate
	 * into level two code.
	 */
	private final A_RawFunction code;

	/**
	 * The nybblecodes being optimized.
	 */
	private final A_Tuple nybbles;

	/**
	 * The current level one nybblecode program counter during naive translation
	 * to level two.
	 */
	private int pc;

	/**
	 * The current stack depth during naive translation to level two.
	 */
	private int stackp;

	private final @Nullable A_Function exactFunctionOrNull;

	/**
	 * The {@link L2BasicBlock} which is the entry point for a function that has
	 * just been invoked.
	 */
	final L2BasicBlock startBlock = new L2BasicBlock("start");

	/**
	 * The {@link L2BasicBlock} which is the entry point with which {@link
	 * P_RestartContinuation} and {@link P_RestartContinuationWithArguments} are
	 * able to restart a continuation.  The continuation must have been created
	 * with a {@link L1NaiveTranslator#L1Ext_doPushLabel()} instruction, which
	 * corresponds to a label declaration at the start of a function.  This is
	 * only created if at least one push-label instruction is actually
	 * encountered.
	 */
	@Nullable L2BasicBlock restartBlock = null;

	/** The control flow graph being generated. */
	final L2ControlFlowGraph controlFlowGraph =
		new L2ControlFlowGraph(startBlock);

	/** The {@link L2BasicBlock} that code is currently being generated into. */
	L2BasicBlock currentBlock = startBlock;

	/**
	 * During naive L1 → L2 translation, the stack-oriented L1 nybblecodes are
	 * translated into register-oriented L2 instructions, treating arguments,
	 * locals, and operand stack slots as registers.  We build a control flow
	 * graph of basic blocks, each of which contains a sequence of instructions.
	 * The graph is built in Static Single Assignment (SSA) form, where a
	 * register may only be assigned by a single instruction.  Merged control
	 * flows use "phi-functions", for which there is ample literature.  Our
	 * slight variation takes into account our complex operations that perform
	 * branches, allowing each branch direction to specify its own phi-mapping.
	 *
	 * <p>This array is indexed by slot index within an L1 continuation.  Each
	 * slot holds an {@link L2ObjectRegister} corresponding to the most recently
	 * written value.  As the L1 instructions are translated, any reads from the
	 * effective continuation are dealt with by generating a read from the
	 * register at the appropriate index (at the time of translation), and a
	 * write always involves creating a new {@link L2ObjectRegister}, replacing
	 * the previous register in the array.  This ensures SSA form, as long as
	 * the naive translation's control flow is loop-less, which it is.</p>
	 */
	final L2ReadPointerOperand[] slotRegisters;

	/**
	 * Create a new L1 naive translator for the given {@link L2Translator}.
	 * @param translator
	 *        The {@link L2Translator} for which I'm producing an initial
	 *        translation from L1.
	 */
	L1NaiveTranslator (final L2Translator translator)
	{
		this.translator = translator;
		code = translator.codeOrFail();
		nybbles = code.nybbles();
		pc = 1;
		stackp = code.maxStackDepth() + 1;
		slotRegisters =
			new L2ReadPointerOperand[code.numArgsAndLocalsAndStack()];
		exactFunctionOrNull = computeExactFunctionOrNullForCode(code);
	}

	/**
	 * Start code generation for the given {@link L2BasicBlock}.  This naive
	 * translator doesn't create loops, so ensure all predecessors blocks have
	 * already finished generation.
	 *
	 * <p>Also, reconcile the slotRegisters that were collected for each
	 * predecessor, creating an {@link L2_PHI_PSEUDO_OPERATION} if needed.</p>
	 *
	 * @param block The {@link L2BasicBlock} beginning code generation.
	 */
	public void startBlock (final L2BasicBlock block)
	{
		currentBlock = block;
		block.start();
		TODO MvG - Compare slot vectors and build phi function.
	}

	boolean currentlyReachable ()
	{
		return currentBlock == controlFlowGraph.initialBlock
			|| currentBlock.hasPredecessors();
	}

	/**
	 * Determine if the given {@link A_RawFunction}'s instantiations as {@link
	 * A_Function}s must be mutually equal.
	 *
	 * @param theCode The {@link A_RawFunction}.
	 * @return Either a canonical {@link A_Function} or {@code null}.
	 */
	private static @Nullable A_Function computeExactFunctionOrNullForCode (
		final A_RawFunction theCode)
	{
		final int numOuters = theCode.numOuters();
		final List<AvailObject> outerConstants = new ArrayList<>(numOuters);
		for (int i = 1; i <= numOuters; i++)
		{
			final A_Type outerType = theCode.outerTypeAt(i);
			if (outerType.instanceCount().equalsInt(1)
				&& !outerType.isInstanceMeta())
			{
				outerConstants.add(outerType.instance());
			}
			else
			{
				return null;
			}
		}
		// This includes the case of there being no outers.
		return createFunction(theCode, tupleFromList(outerConstants));
	}

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
		int count = 0xF & (int) (0x8421_1100_0000_0000L >>> shift);
		int value = 0;
		while (count-- > 0)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc++);
		}
		final int lowOff = 0xF & (int) (0x00AA_AA98_7654_3210L >>> shift);
		final int highOff = 0xF & (int) (0x0032_1000_0000_0000L >>> shift);
		return value + lowOff + (highOff << 4);
	}

	/**
	 * Answer the register holding the latest assigned version of the specified
	 * continuation slot. The slots are the arguments, then the locals, then the
	 * stack entries. The slots are numbered starting at 1.
	 *
	 * @param slotNumber
	 *        The index into the continuation's slots.
	 * @return A register representing that continuation slot.
	 */
	public L2ReadPointerOperand readSlot (final int slotNumber)
	{
		return slotRegisters[slotNumber - 1];
	}

	/**
	 * Answer the register holding the latest assigned version of the specified
	 * continuation slot. The slots are the arguments, then the locals, then the
	 * stack entries. The slots are numbered starting at 1.
	 *
	 * @param slotNumber
	 *        The index into the continuation's slots.
	 * @return A register representing that continuation slot.
	 */
	public L2WritePointerOperand writeSlot (
		final int slotNumber,
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		final L2WritePointerOperand writer = newObjectRegisterWriter(
			type, constantOrNull);
		slotRegisters[slotNumber - 1] = new L2ReadPointerOperand(
			writer.register, null);
		return writer;
	}

	/**
	 * Write nil into a new register representing the specified continuation
	 * slot.  The slots are the arguments, then the locals, then the stack
	 * entries.  The slots are numbered starting at 1.
	 *
	 * @param slotNumber
	 *        The index into the continuation's slots.
	 */
	public void nilSlot (final int slotNumber)
	{
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(nil),
			writeSlot(slotNumber, TOP.o(), nil));
	}

	/**
	 * Allocate a new {@link L2ObjectRegister}.  Answer an {@link
	 * L2WritePointerOperand} that writes to it, using the given type and
	 * optional constant value information.
	 *
	 * @return The new register write operand.
	 */
	public L2WritePointerOperand newObjectRegisterWriter (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		return new L2WritePointerOperand(
			translator.nextUnique(), type, constantOrNull);
	}

	/**
	 * Write instructions to extract the current function, and answer an {@link
	 * L2ReadPointerOperand} for the register that will hold the function
	 * afterward.
	 */
	public L2ReadPointerOperand getCurrentFunction ()
	{
		if (exactFunctionOrNull != null)
		{
			// The exact function is known.
			return constantRegister(exactFunctionOrNull);
		}
		// The exact function isn't known, but we know the raw function, so
		// we statically know the function type.
		final L2WritePointerOperand functionWrite =
			newObjectRegisterWriter(code.functionType(), null);
		addInstruction(
			L2_GET_CURRENT_FUNCTION.instance,
			functionWrite);
		return functionWrite.read();
	}

	/**
	 * Write instructions to extract the current reified continuation, and
	 * answer an {@link L2ReadPointerOperand} for the register that will hold
	 * the continuation afterward.
	 */
	public L2ReadPointerOperand getCurrentContinuation ()
	{
		final L2WritePointerOperand continuationTempReg =
			newObjectRegisterWriter(mostGeneralContinuationType(), null);
		addInstruction(
			L2_GET_CURRENT_CONTINUATION.instance,
			continuationTempReg);
		return continuationTempReg.read();
	}

	/**
	 * Write instructions to extract the current skip-return-check flag into a
	 * new {@link L2IntegerRegister}, and answer an {@link L2ReadIntOperand} for
	 * that register.
	 */
	public L2ReadIntOperand getSkipReturnCheck ()
	{
		final L2IntegerRegister tempIntReg = translator.newIntegerRegister();
		addInstruction(
			L2_GET_SKIP_RETURN_CHECK.instance,
			new L2WriteIntOperand(tempIntReg));
		return new L2ReadIntOperand(tempIntReg);
	}

	/**
	 * Return the {@linkplain CompiledCodeDescriptor compiled Level One code}
	 * being translated.
	 *
	 * @return The code being translated.
	 */
	public A_RawFunction codeOrFail ()
	{
		return translator.codeOrFail();
	}

	/**
	 * Create a {@link L2ReadVectorOperand} for reading each element of the
	 * given list of {@link L2ReadPointerOperand}s.
	 *
	 * @param sources
	 *        The list of register-read sources to aggregate.
	 * @return A new {@link L2ReadVectorOperand}.
	 */
	public static L2ReadVectorOperand readVector (
		final List<L2ReadPointerOperand> sources)
	{
		return new L2ReadVectorOperand(sources);
	}

	/**
	 * Answer the number of stack slots reserved for use by the code being
	 * optimized.
	 *
	 * @return The number of stack slots.
	 */
	public int numSlots ()
	{
		return translator.numSlots;
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
	public List<L2ReadPointerOperand> continuationSlotsList (final int nSlots)
	{
		final List<L2ReadPointerOperand> slots = new ArrayList<>(nSlots);
		for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
		{
			slots.add(readSlot(slotIndex));
		}
		return slots;
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
		addInstruction(new L2Instruction(currentBlock, operation, operands));
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
		currentBlock.addInstruction(instruction);
	}

	/**
	 * Generate instruction(s) to move the given {@link AvailObject} into
	 * the specified {@link L2Register}.
	 *
	 * @param value
	 *        The value to move.
	 * @param destination
	 *        Where to move it.
	 */
	public void moveConstant (
		final A_BasicObject value,
		final L2WritePointerOperand destination)
	{
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(value),
			destination);
	}

	/**
	 * Generate instructions to move {@linkplain NilDescriptor#nil nil}
	 * into each of the specified {@link L2ObjectRegister registers}.
	 *
	 * @param destinations
	 *        Which registers to clear.
	 */
	public void moveNils (
		final Collection<L2WritePointerOperand> destinations)
	{
		for (final L2WritePointerOperand destination : destinations)
		{
			addInstruction(
				L2_MOVE_CONSTANT.instance,
				new L2ConstantOperand(nil),
				destination);
		}
	}

	/**
	 * Write a constant value into a new register.  Answer an {@link
	 * L2ReadPointerOperand} for that register.
	 *
	 * @param value The constant value to write to a register.
	 * @return The {@link L2ReadPointerOperand} for the new register.
	 */
	public L2ReadPointerOperand constantRegister (final A_BasicObject value)
	{
		final A_Type type = value.equalsNil()
			? TOP.o()
			: instanceTypeOrMetaOn(value);
		final L2WritePointerOperand registerWrite =
			newObjectRegisterWriter(type, value);
		addInstruction(
			L2_MOVE_CONSTANT.instance,
			new L2ConstantOperand(value),
			registerWrite);
		return registerWrite.read();
	}

	/**
	 * Generate instruction(s) to move from one register to another.
	 *
	 * @param sourceRegister
	 *        Where to read the AvailObject.
	 * @param destinationRegister
	 *        Where to write the AvailObject.
	 */
	public void moveRegister (
		final L2ReadPointerOperand sourceRegister,
		final L2WritePointerOperand destinationRegister)
	{
		addInstruction(L2_MOVE.instance, sourceRegister, destinationRegister);
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
	 * @param resumeBlock
	 *        Where to resume execution of the current continuation.
	 */
	@Deprecated
	public void reify (
		final List<L2ReadPointerOperand> slots,
		final L2WritePointerOperand newContinuationRegister,
		final L2BasicBlock resumeBlock)
	{
		throw new UnsupportedOperationException();
//			addInstruction(
//				L2_CREATE_CONTINUATION.instance,
//				new L2ReadPointerOperand(fixed(CALLER)),
//				new L2ReadPointerOperand(fixed(FUNCTION)),
//				new L2ImmediateOperand(pc),
//				new L2ImmediateOperand(stackp),
//				new L2ReadIntOperand(skipReturnCheckRegister),
//				new L2ReadVectorOperand(createVector(slots)),
//				new L2PcOperand(resumeLabel),
//				new L2WritePointerOperand(newContinuationRegister));
	}

	/**
	 * Add an instruction that's not supposed to be reachable.
	 */
	public void addUnreachableCode ()
	{
		addInstruction(L2_UNREACHABLE_CODE.instance);
	}

	/**
	 * A memento to be used for coordinating code generation between the
	 * branches of an {@link InternalLookupTree}.
	 */
	@InnerAccess class InternalNodeMemento
	{
		/**
		 * Where to jump if the {@link InternalLookupTree}'s type test is true.
		 */
		final L2BasicBlock passCheckBasicBlock;

		/**
		 * Where to jump if the {@link InternalLookupTree}'s type test is false.
		 */
		final L2BasicBlock failCheckBasicBlock;

		/**
		 * Construct a new memento.  Make the label something meaningful to
		 * make it easier to decipher.
		 *
		 * @param argumentIndexToTest
		 *        The subscript of the argument being tested.
		 * @param typeToTest
		 *        The type to test the argument against.
		 * @param branchLabelCounter
		 *        An int unique to this dispatch tree, monotonically
		 *        allocated at each branch.
		 */
		@InnerAccess InternalNodeMemento (
			final int argumentIndexToTest,
			final A_Type typeToTest,
			final int branchLabelCounter)
		{
			final String shortTypeName =
				branchLabelCounter
					+ " (arg#"
					+ argumentIndexToTest
					+ " is a "
					+ typeToTest.traversed().descriptor().typeTag.name()
					+ ")";
			this.passCheckBasicBlock = controlFlowGraph.addBasicBlock(
				"pass lookup test #" + shortTypeName);
			this.failCheckBasicBlock = controlFlowGraph.addBasicBlock(
				"fail lookup test #" + shortTypeName);
		}
	}

	/**
	 * Generate code to perform a method invocation.  If a superUnionType other
	 * than {@link BottomTypeDescriptor#bottom() bottom} is supplied, produce a
	 * super-directed multimethod invocation.
	 *
	 * @param bundle
	 *        The {@linkplain MessageBundleDescriptor message bundle} to
	 *        invoke.
	 * @param expectedType
	 *        The expected return {@linkplain TypeDescriptor type}.
	 * @param superUnionType
	 *        A tuple type to combine through a type union with the pushed
	 *        arguments' dynamic types, to use during method lookup.  This is
	 *        {@link BottomTypeDescriptor#bottom() bottom} for non-super calls.
	 */
	private void generateCall (
		final A_Bundle bundle,
		final A_Type expectedType,
		final A_Type superUnionType)
	{
		final A_Method method = bundle.bundleMethod();
		translator.contingentValues =
			translator.contingentValues.setWithElementCanDestroy(method, true);
		final A_String bundleName = bundle.message().atomName();
		final L2BasicBlock afterCall = controlFlowGraph.addBasicBlock(
			superUnionType.isBottom()
				? "After call of " + bundleName
				: "After super call of " + bundleName);
		final int nArgs = method.numArgs();
		final List<L2ReadPointerOperand> arguments = new ArrayList<>(nArgs);
		final List<A_Type> argumentTypes = new ArrayList<>(nArgs);
		for (int i = nArgs - 1; i >= 0; i--)
		{
			final L2ReadPointerOperand argument = readSlot(stackp + i);
			arguments.add(argument);
			argumentTypes.add(argument.type());
			// No point nilling the first argument, since it'll be overwritten
			// below with a constant move of the expectedType.
			if (i != nArgs - 1)
			{
				moveConstant(nil, writeSlot(stackp + i, TOP.o(), nil));
			}
		}
		// Pop the arguments, but push a slot for the expectedType.
		stackp += nArgs - 1;
		// Write the expected type.  Note that even though it's a type, the
		// exact value is known statically, not just its meta.
		moveConstant(
			expectedType,
			writeSlot(stackp, instanceMeta(expectedType), expectedType));

		// At this point we've captured and popped the argument registers,
		// nilled their new SSA versions, and pushed the expectedType.  That's
		// the reifiable state of the continuation during the call.
		final List<A_Definition> allPossible = new ArrayList<>();
		for (final A_Definition definition : method.definitionsTuple())
		{
			final A_Type signature = definition.bodySignature();
			if (signature.couldEverBeInvokedWith(argumentTypes)
				&& superUnionType.isSubtypeOf(signature.argsTupleType()))
			{
				allPossible.add(definition);
				if (allPossible.size() > maxPolymorphismToInlineDispatch)
				{
					// It has too many applicable implementations to be worth
					// inlining all of them.
					generateSlowPolymorphicCall(
						bundle, arguments, expectedType, superUnionType);
					return;
				}
			}
		}
		// NOTE: Don't use the method's testing tree.  It encodes information
		// about the known types of arguments that may be too weak for our
		// purposes.  It's still correct, but it may produce extra tests that
		// this site's argumentTypes would eliminate.
		final LookupTree<A_Definition, A_Tuple, Void> tree =
			MethodDescriptor.runtimeDispatcher.createRoot(
				allPossible, argumentTypes, null);
		final Mutable<Integer> branchLabelCounter = new Mutable<>(1);
		// If a reification exception happens while an L2_INVOKE is in progress,
		// it will run the instructions at the off-ramp up to an L2_RETURN –
		// which will return the reified (but callerless) continuation.
		final L2BasicBlock offRamp =
			controlFlowGraph.addBasicBlock("reify in call");
		final L2BasicBlock onRamp =
			controlFlowGraph.addBasicBlock("reentry return from call");
		tree.traverseEntireTree(
			MethodDescriptor.runtimeDispatcher,
			null,
			// preInternalNode
			(argumentIndexToTest, typeToTest) ->
			{
				final InternalNodeMemento memento =
					new InternalNodeMemento(
						argumentIndexToTest,
						typeToTest,
						branchLabelCounter.value++);
				// If no paths lead here, don't generate code.  This can happen
				// when we short-circuit type-tests into unconditional jumps,
				// due to the complexity of super calls.  We short-circuit code
				// generation within this entire subtree by performing the same
				// test in each callback.
				if (!currentlyReachable())
				{
					return memento;
				}
				final L2ReadPointerOperand arg =
					arguments.get(argumentIndexToTest - 1);
				final A_Type existingType = arg.type();
				// Strengthen the test based on what's already known about the
				// argument.  Eventually we can decide whether to strengthen
				// based on the expected cost of the type check.
				final A_Type intersection =
					existingType.typeIntersection(typeToTest);
				assert !intersection.isBottom()
					: "Impossible condition should have been excluded";
				// Tricky here.  We have the type we want to test for, and we
				// have the argument for which we want to test the type, but we
				// also have an element of the superUnionType to consider.  And
				// that element might be a combination of restrictions and
				// bottoms.  Deal with the easy, common cases first.
				final A_Type superUnionElementType =
					superUnionType.typeAtIndex(argumentIndexToTest);
				final @Nullable A_BasicObject constantOrNull =
					arg.constantOrNull();
				if (constantOrNull != null)
				{
					// The argument is a constant, so test it now.  Take into
					// account any supercasts.
					final A_Type unionType =
						instanceTypeOrMetaOn(constantOrNull).typeUnion(
							superUnionElementType);
					// Unconditionally jump to either the true or the false
					// path.
					final boolean isSubtype =
						unionType.isSubtypeOf(intersection);
					addInstruction(
						L2_JUMP.instance,
						isSubtype
							? new L2PcOperand(
								memento.passCheckBasicBlock,
								arg.restrictedTo(intersection, null))
							: new L2PcOperand(
								memento.failCheckBasicBlock,
								arg.restrictedWithoutType(intersection)));
				}
				else if (existingType.isSubtypeOf(superUnionElementType))
				{
					// It's a pure supercast of this argument, not a mix of some
					// parts being supercast and others not.  Do the test once,
					// right now.
					final boolean passed =
						superUnionElementType.isSubtypeOf(intersection);
					addInstruction(
						L2_JUMP.instance,
						passed
							? new L2PcOperand(
								memento.passCheckBasicBlock)
							: new L2PcOperand(
								memento.failCheckBasicBlock));
				}
				else if (superUnionElementType.isBottom()
					&& intersection.isEnumeration()
					&& !intersection.isInstanceMeta()
					&& intersection.instanceCount().extractInt() <=
						maxExpandedEqualityChecks)
				{
					// It doesn't contain a supercast, and the type is a small
					// non-meta enumeration.  Use equality checks rather than
					// the more general type checks.
					final Iterator<AvailObject> iterator =
						intersection.instances().iterator();
					while (iterator.hasNext())
					{
						final A_BasicObject instance = iterator.next();
						final L2BasicBlock nextCheckOrFail =
							iterator.hasNext()
								? controlFlowGraph.addBasicBlock(
									"test next case of enumeration")
								: memento.failCheckBasicBlock;
						addInstruction(
							L2_JUMP_IF_EQUALS_CONSTANT.instance,
							arg,
							new L2ConstantOperand(instance),
							new L2PcOperand(
								memento.passCheckBasicBlock,
								arg.restrictedToValue(instance)),
							new L2PcOperand(
								nextCheckOrFail,
								arg.restrictedWithoutValue(instance)));
						startBlock(nextCheckOrFail);
					}
				}
				else if (superUnionElementType.isBottom())
				{
					// Use the argument's type unaltered.  In fact, just check
					// if the argument is an instance of the type.
					addInstruction(
						L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT.instance,
						arg,
						new L2ConstantOperand(intersection),
						new L2PcOperand(memento.passCheckBasicBlock),
						new L2PcOperand(memento.failCheckBasicBlock));
				}
				else
				{
					// This argument dispatch type is a mixture of supercasts
					// and non-supercasts.  Do it the slow way with a type
					// union.  Technically, the superUnionElementType's
					// recursive tuple structure mimics the call site, so it
					// must have a fixed, finite structure corresponding with
					// occurrences of supercasts syntactically.  Thus, in theory
					// we could analyze the superUnionElementType and generate a
					// more complex collection of branches – but this is already
					// a pretty rare case.
					final A_Type argMeta = instanceMeta(arg.type());
					final L2WritePointerOperand argTypeWrite =
						newObjectRegisterWriter(argMeta, null);
					addInstruction(
						L2_GET_TYPE.instance, arg, argTypeWrite);
					final L2ReadPointerOperand superUnionReg =
						constantRegister(superUnionElementType);
					final L2WritePointerOperand unionReg =
						newObjectRegisterWriter(
							argMeta.typeUnion(superUnionReg.type()), null);
					addInstruction(
						L2_TYPE_UNION.instance,
						argTypeWrite.read(),
						superUnionReg,
						unionReg);
					addInstruction(
						L2_JUMP_IF_IS_NOT_SUBTYPE_OF_CONSTANT.instance,
						unionReg.read(),
						new L2ConstantOperand(intersection),
						new L2PcOperand(memento.passCheckBasicBlock),
						new L2PcOperand(memento.failCheckBasicBlock));
				}
				return memento;
			},
			// intraInternalNode
			memento ->
			{
				if (currentlyReachable())
				{
					addInstruction(
						L2_JUMP.instance,
						new L2PcOperand(afterCall));
				}
				startBlock(memento.failCheckBasicBlock);
			},
			// postInternalNode
			memento ->
			{
				if (currentlyReachable())
				{
					addInstruction(
						L2_JUMP.instance,
						new L2PcOperand(afterCall));
				}
			},
			// forEachLeafNode
			solutions ->
			{
				if (!currentlyReachable())
				{
					return;
				}
				if (solutions.tupleSize() == 1)
				{
					final A_Definition solution = solutions.tupleAt(1);
					if (solution.isInstanceOf(METHOD_DEFINITION.o()))
					{
						generateFunctionInvocation(
							solution.bodyBlock(), expectedType);
						return;
					}
				}
				// Collect the arguments into a tuple and invoke the handler
				// for failed method lookups.
				final A_Set solutionsSet = solutions.asSet();
				final L2WritePointerOperand errorCodeWrite =
					newObjectRegisterWriter(naturalNumbers(), null);
				addInstruction(
					L2_DIAGNOSE_LOOKUP_FAILURE.instance,
					new L2ConstantOperand(solutionsSet),
					errorCodeWrite);
				final L2WritePointerOperand invalidSendReg =
					newObjectRegisterWriter(
						mostGeneralFunctionType(), null);
				addInstruction(
					L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
					invalidSendReg);
				// Make the method itself accessible to the code.
				final L2ReadPointerOperand methodReg = constantRegister(method);
				// Collect the arguments into a tuple.
				final L2WritePointerOperand argumentsTupleWrite =
					newObjectRegisterWriter(mostGeneralTupleType(), null);
				addInstruction(
					L2_CREATE_TUPLE.instance,
					new L2ReadVectorOperand(arguments),
					argumentsTupleWrite);
				addInstruction(
					L2_INVOKE.instance,
					invalidSendReg.read(),
					new L2ReadVectorOperand(
						asList(
							errorCodeWrite.read(),
							methodReg,
							argumentsTupleWrite.read())),
					new L2ImmediateOperand(1),
					new L2PcOperand(afterCall),
					new L2PcOperand(offRamp));
				addUnreachableCode();
			});

		// In the event of reification while in any of the potential invokes...
		startBlock(offRamp);
		final L2BasicBlock afterContinuationCreation =
			controlFlowGraph.addBasicBlock(
				"after continuation creation");
		final L2ReadPointerOperand functionRead =
			getCurrentFunction();
		final L2ReadIntOperand skipReturnCheckRead =
			getSkipReturnCheck();
		final List<L2ReadPointerOperand> allSlots =
			IntStream.rangeClosed(1, numSlots())
				.mapToObj(this::readSlot)
				.collect(toList());
		final L2WritePointerOperand newContinuationReg =
			newObjectRegisterWriter(mostGeneralContinuationType(), null);
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			constantRegister(nil),
			functionRead,
			new L2ImmediateOperand(pc),
			new L2ImmediateOperand(stackp),
			skipReturnCheckRead,
			new L2ReadVectorOperand(allSlots),
			newContinuationReg,
			new L2PcOperand(onRamp),
			new L2PcOperand(afterContinuationCreation));
		startBlock(afterContinuationCreation);
		addInstruction(
			L2_RETURN.instance,
			newContinuationReg.read(),
			new L2ConstantOperand(one()));

		// This is where the continuation will continue when it's eventually
		// returned into.
		startBlock(onRamp);
		addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(afterCall));

		// It gets here from either the onRamp (if reification happened during
		// the call) or directly from the L2_INVOKE.
		startBlock(afterCall);
	}

	/**
	 * Generate code to perform a monomorphic invocation.  The exact method
	 * definition is known, so no lookup is needed at this position in the
	 * code stream.
	 *
	 * @param originalFunction
	 *        The {@linkplain FunctionDescriptor function} to invoke.
	 * @param expectedType
	 *        The expected return {@linkplain TypeDescriptor type}.
	 */
	public void generateFunctionInvocation (
		final A_Function originalFunction,
		final A_Type expectedType)
	{
		// The registers holding slot values that will constitute the
		// continuation *during* a non-primitive call.
		final List<L2ReadPointerOperand> slotsDuringCall = new ArrayList<>(
			translator.numSlots);
		for (int slotIndex = 1; slotIndex <= translator.numSlots; slotIndex++)
		{
			final L2WritePointerOperand newWriter =
				writeSlot(slotIndex, TOP.o(), nil);
			moveConstant(nil, newWriter);
			slotsDuringCall.add(newWriter.read());
		}
		final A_RawFunction originalCode = originalFunction.code();
		final int nArgs = originalCode.numArgs();
		final List<L2ReadPointerOperand> preserved = new ArrayList<>(preSlots);
		assert preserved.size() == translator.numSlots;
		final List<L2ReadPointerOperand> args = new ArrayList<>(nArgs);
		final List<A_Type> argTypes = new ArrayList<>(nArgs);
		for (int i = stackp + nArgs - 1; i >= stackp; i--)
		{
			final L2ReadPointerOperand arg = readSlot(i);
			argTypes.add(arg.type());
			args.add(arg);
			preSlots.set(i, fixed(NULL));
		}
		stackp += nArgs - 1;
		// This may seem a bit odd, but allocated both writes to the same slot
		// position.  Note that this causes two registers to be created, with
		// different lifetimes and types.
		final L2WritePointerOperand expectedTypeReg =
			writeSlot(stackp);
		final L2WritePointerOperand resultRegister =
			writeSlot(stackp);
		preSlots.set(
			translator.numArgs + translator.numLocals + stackp - 1,
			expectedTypeReg);
		// preSlots now contains the registers that will constitute the
		// continuation during a non-primitive call.
		@Nullable L2ReadPointerOperand functionReg = null;
		A_Function functionToInvoke = originalFunction;
		final @Nullable Primitive prim = Primitive.byPrimitiveNumberOrNull(
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
				for (final L2ReadPointerOperand arg : args)
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
		final @Nullable A_Function primFunction = primitiveFunctionToInline(
			functionToInvoke, args, naiveRegisters());
		// The convergence point for primitive success and failure paths.
		final L2BasicBlock successLabel =
			controlFlowGraph.addBasicBlock("success");
		final L2ObjectRegister reifiedCallerRegister = newObjectRegister();
		final L2WritePointerOperand failureObjectReg =
			newObjectRegisterWriter();
		if (primFunction != null)
		{
			// Inline the primitive. Attempt to fold it if the primitive
			// says it's foldable and the arguments are all constants.
			final Mutable<Boolean> canFailPrimitive = new Mutable<>(false);
			final @Nullable A_BasicObject folded = emitInlinePrimitiveAttempt(
				primFunction,
				args,
				resultRegister,
				preserved,
				expectedType,
				failureObjectReg,
				successLabel,
				canFailPrimitive);
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
		A_Map postSlotTypesMap = emptyMap();
		A_Map postSlotConstants = emptyMap();
		A_Set nullPostSlots = emptySet();
		final List<L2ObjectRegister> postSlots = new ArrayList<>(
			translator.numSlots);
		for (int slotIndex = 1; slotIndex <= translator.numSlots; slotIndex++)
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
					fromInt(slotIndex),
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
						fromInt(slotIndex),
						true);
				}
				else
				{
					postSlotConstants =
						postSlotConstants.mapAtPuttingCanDestroy(
							fromInt(slotIndex),
							constant,
							true);
				}
			}
			// Write the result to the architectural stack slot register.
			postSlots.add(readSlot(slotIndex));
		}
		final L2Instruction postCallLabel = newLabel("postCall");
		reify(preSlots, reifiedCallerRegister, postCallLabel);
		final A_Type guaranteedReturnType;
		if (functionReg == null)
		{
			// If the function is not already in a register, then we didn't
			// inline an Invokes primitive.  Use the original function.
			functionReg = constantRegister(originalFunction);
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
			addUnreachableCode(newLabel("unreachable"));
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
	 * method.  The slots have already been adjusted to be consistent with
	 * having popped the arguments and pushed the expected type.
	 *
	 * @param bundle
	 *        The {@linkplain MessageBundleDescriptor message bundle} containing
	 *        the definition to invoke.
	 * @param arguments
	 *        The list of argument registers to use for the call.
	 * @param expectedType
	 *        The expected return {@link A_Type}.
	 * @param superUnionType
	 *        A tuple type whose union with the type of the arguments tuple at
	 *        runtime is used for lookup.  The type ⊥ ({@link
	 *        BottomTypeDescriptor#bottom() bottom}) is used to indicate this is
	 *        not a super call.
	 */
	private void generateSlowPolymorphicCall (
		final A_Bundle bundle,
		final List<L2ReadPointerOperand> arguments,
		final A_Type expectedType,
		final A_Type superUnionType)
	{
		final A_Method method = bundle.bundleMethod();
		final int nArgs = method.numArgs();
		final L2BasicBlock lookupSucceeded =
			controlFlowGraph.addBasicBlock("lookup succeeded");
		final L2BasicBlock lookupFailed =
			controlFlowGraph.addBasicBlock("lookup failed");
		final L2WritePointerOperand functionReg = newObjectRegisterWriter(
			TOP.o(), null);
		final L2WritePointerOperand errorCodeReg = newObjectRegisterWriter(
			TOP.o(), null);

		final List<A_Type> argumentTypes = IntStream.rangeClosed(1, nArgs)
			.mapToObj(
				i -> arguments.get(i - 1).type().typeUnion(
					superUnionType.typeAtIndex(i)))
			.collect(toList());
		final List<A_Function> possibleFunctions =
			bundle.bundleMethod().filterByTypes(argumentTypes).stream()
				.filter(A_Definition::isMethodDefinition)
				.map(A_Definition::bodyBlock)
				.collect(toList());
		final A_Type functionTypeUnion = enumerationWith(
			setFromCollection(possibleFunctions));
		if (superUnionType.isBottom())
		{
			// Not a super-call.
			addInstruction(
				L2_LOOKUP_BY_VALUES.instance,
				new L2ConstantOperand(bundle),
				new L2ReadVectorOperand(arguments),
				functionReg,
				errorCodeReg,
				new L2PcOperand(
					lookupSucceeded,
					new PhiRestriction(
						functionReg.register,
						functionTypeUnion,
						possibleFunctions.size() == 1
							? possibleFunctions.get(0)
							: null)),
				new L2PcOperand(
					lookupFailed,
					new PhiRestriction(
						errorCodeReg.register,
						L2_LOOKUP_BY_VALUES.lookupErrorsType,
						null)));
		}
		else
		{
			// Extract a tuple type from the runtime types of the arguments,
			// take the type union with the superUnionType, then perform a
			// lookup-by-types using that tuple type.
			final List<L2ReadPointerOperand> argTypeRegs =
				new ArrayList<>(nArgs);
			for (int i = 1; i <= nArgs; i++)
			{
				final L2ReadPointerOperand argReg = arguments.get(i - 1);
				final A_Type argStaticType = argReg.type();
				final A_Type superUnionElementType =
					superUnionType.typeAtIndex(i);
				final L2ReadPointerOperand argTypeReg;
				if (argStaticType.isSubtypeOf(superUnionElementType))
				{
					// The lookup is entirely determined by the super-union.
					argTypeReg = constantRegister(superUnionElementType);
				}
				else
				{
					final A_Type typeBound =
						argStaticType.typeUnion(superUnionElementType);
					final L2WritePointerOperand argTypeWrite =
						newObjectRegisterWriter(instanceMeta(typeBound), null);
					if (superUnionElementType.isBottom())
					{
						// Only this argument's actual type matters.
						addInstruction(
							L2_GET_TYPE.instance, argReg, argTypeWrite);
					}
					else
					{
						// The lookup is constrained by the actual argument's
						// type *and* the super-union.  This is possible because
						// this is a top-level argument, but it's the leaf
						// arguments that individually specify supercasts.
						final L2WritePointerOperand originalArgTypeWrite =
							newObjectRegisterWriter(
								instanceMeta(typeBound), null);
						addInstruction(
							L2_GET_TYPE.instance, argReg, originalArgTypeWrite);
						addInstruction(
							L2_TYPE_UNION.instance,
							originalArgTypeWrite.read(),
							constantRegister(superUnionElementType),
							argTypeWrite);
					}
					argTypeReg = argTypeWrite.read();
				}
				argTypeRegs.add(argTypeReg);
			}
			addInstruction(
				L2_LOOKUP_BY_TYPES.instance,
				new L2ConstantOperand(bundle),
				new L2ReadVectorOperand(argTypeRegs),
				functionReg,
				errorCodeReg,
				new L2PcOperand(
					lookupSucceeded,
					new PhiRestriction(
						functionReg.register,
						functionTypeUnion,
						possibleFunctions.size() == 1
							? possibleFunctions.get(0)
							: null)),
				new L2PcOperand(
					lookupFailed,
					new PhiRestriction(
						errorCodeReg.register,
						L2_LOOKUP_BY_VALUES.lookupErrorsType,
						null)));
		}
		// At this point, we've attempted to look up the method, and either
		// jumped to lookupSucceeded with functionReg set to the body function,
		// or jumped to lookupFailed with errorCodeReg set to the lookup error
		// code.

		// Emit the lookup failure case.
		startBlock(lookupFailed);
		final L2WritePointerOperand invalidSendReg = newObjectRegisterWriter(
			invalidMessageSendFunctionType, null);
		addInstruction(
			L2_GET_INVALID_MESSAGE_SEND_FUNCTION.instance,
			invalidSendReg);
		// Collect the arguments into a tuple.
		final L2ReadVectorOperand argsVector =
			new L2ReadVectorOperand(arguments);
		final L2WritePointerOperand argumentsTupleWrite =
			newObjectRegisterWriter(
				tupleTypeForSizesTypesDefaultType(
					singleInt(nArgs),
					tupleFromList(argsVector.types()),
					bottom()),
				null);
		addInstruction(
			L2_CREATE_TUPLE.instance,
			argsVector,
			argumentsTupleWrite);
		final L2BasicBlock unreachable =
			controlFlowGraph.addBasicBlock(
				"unreachable after reporting failed lookup");
		final L2BasicBlock onReification =
			controlFlowGraph.addBasicBlock(
				"reification while reporting failed lookup");
		addInstruction(
			L2_INVOKE.instance,
			invalidSendReg.read(),
			new L2ReadVectorOperand(
				asList(
					errorCodeReg.read(),
					constantRegister(method),
					argumentsTupleWrite.read())),
			new L2ImmediateOperand(1),
			new L2PcOperand(unreachable),
			new L2PcOperand(onReification));

		// The unreachable block in the case of an impossible return.
		startBlock(unreachable);
		addUnreachableCode();

		// If reification is requested while running either the failure
		// invocation or the actual method call.  Only the method call can
		// return, so the new continuation's L2 offset will be to the generated
		// instructions right after this L1 call.
		startBlock(onReification);
		final List<L2ReadPointerOperand> allSlots =
			IntStream.rangeClosed(1, numSlots())
				.mapToObj(this::readSlot)
				.collect(toList());
		final L2WritePointerOperand newContinuationReg =
			newObjectRegisterWriter(mostGeneralContinuationType(), null);
		final L2BasicBlock onRamp =
			controlFlowGraph.addBasicBlock(
				"on-ramp to return from reified call");
		final L2BasicBlock fallThroughReification =
			controlFlowGraph.addBasicBlock(
				"continue reification");
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			constantRegister(nil),
			getCurrentFunction(),
			new L2ImmediateOperand(pc),
			new L2ImmediateOperand(stackp),
			getSkipReturnCheck(),
			new L2ReadVectorOperand(allSlots),
			newContinuationReg,
			new L2PcOperand(onRamp),
			new L2PcOperand(fallThroughReification));

		// After the create-continuation completes, return it to the interpreter
		// code that requested reification of this stack frame.
		startBlock(fallThroughReification);
		addInstruction(
			L2_RETURN.instance,
			newContinuationReg.read(),
			getSkipReturnCheck());

		// When the reified continuation is returned into, continue here.
		startBlock(onRamp);
		final L2WritePointerOperand poppedContinuationReg =
			newObjectRegisterWriter(mostGeneralContinuationType(), null);
		addInstruction(
			L2_POP_CURRENT_CONTINUATION.instance,
			poppedContinuationReg);
		final List<L2WritePointerOperand> targetSlots =
			IntStream.rangeClosed(1, slotRegisters.length)
				.mapToObj(i ->
					{
						final L2ReadPointerOperand oldSlot = readSlot(i);
						return writeSlot(
							i, oldSlot.type(), oldSlot.constantOrNull());
					})
				.collect(toList());
		addInstruction(
			L2_EXPLODE_CONTINUATION.instance,
			poppedContinuationReg,
			new L2WriteVectorOperand(targetSlots),
			new L2WritePointerOperand(fixed(CALLER)),
			new L2WritePointerOperand(fixed(FUNCTION)),
			new L2WriteIntOperand(skipReturnCheckRegister),
			new L2ConstantOperand(postSlotTypesMap),
			new L2ConstantOperand(postSlotConstants),
			new L2ConstantOperand(nullPostSlots),
			new L2ConstantOperand(codeOrFail().functionType()));
		);



		startBlock(lookupSucceeded);
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
	 * Inline the primitive.  Attempt to fold it (evaluate it right now) if the
	 * primitive says it's foldable and the arguments are all constants.  Answer
	 * the result if it was folded, otherwise null.  If it was folded, generate
	 * code to push the folded value.  Otherwise generate an invocation of the
	 * primitive, jumping to the successLabel on success.
	 *
	 * <p>Special case if the flag {@link Flag#SpecialReturnConstant} is
	 * specified: Always fold it, since it's just a constant.</p>
	 *
	 * <p>Another special case if the flag {@link
	 * Flag#SpecialReturnSoleArgument} is specified:  Don't generate an inlined
	 * primitive invocation, but instead generate a move from the argument
	 * register to the output.</p>
	 *
	 * <p>The flag {@link Flag #SpecialReturnGlobalValue} indicates the
	 * operation simply returns the value of some (global) variable.  In that
	 * circumstance, output alternative code to read the variable without
	 * leaving the current continuation.</p>
	 *
	 * @param primitiveFunction
	 *        A {@linkplain FunctionDescriptor function} for which its
	 *        primitive might be inlined, or even folded if possible.
	 * @param args
	 *        The {@link List} of arguments to the primitive function.
	 * @param resultWrite
	 *        The {@link L2WritePointerOperand} into which to write the
	 *        primitive result.
	 * @param preserved
	 *        A list of registers to consider preserved across this
	 *        call.  They have no effect at runtime, but affect analysis
	 *        of which instructions consume which writes.
	 * @param expectedType
	 *        The {@linkplain TypeDescriptor type} of object that this
	 *        primitive call site was expected to produce.
	 * @param failureValueWrite
	 *        The {@linkplain L2WritePointerOperand} into which to write the
	 *        failure information if the primitive fails.
	 * @param successLabel
	 *        The label to jump to if the primitive is not folded and is
	 *        inlined.
	 * @param canFailPrimitive
	 *        A {@linkplain Mutable Mutable<Boolean>} that this method
	 *        sets if a fallible primitive was inlined.
	 * @return The value if the primitive was folded, otherwise {@code
	 *         null}.
	 */
	private @Nullable A_BasicObject emitInlinePrimitiveAttempt (
		final A_Function primitiveFunction,
		final List<L2ReadPointerOperand> args,
		final L2WritePointerOperand resultWrite,
		final List<L2ObjectRegister> preserved,
		final A_Type expectedType,
		final L2WritePointerOperand failureValueWrite,
		final L2Instruction successLabel,
		final Mutable<Boolean> canFailPrimitive)
	{
		final @Nullable Primitive primitive = stripNull(
			primitiveFunction.code().primitive());
		if (primitive.hasFlag(SpecialReturnConstant))
		{
			// Use the first literal as the return value.
			final AvailObject value = primitiveFunction.code().literalAt(1);
			moveConstant(value, resultWrite);
			// Restriction might be too strong even on a constant method.
			if (value.isInstanceOf(expectedType))
			{
				canFailPrimitive.value = false;
				return value;
			}
			// Emit the failure off-ramp.
			final L2BasicBlock unreachable =
				controlFlowGraph.addBasicBlock("unreachable");
			final L2WritePointerOperand invalidResultFunction =
				newObjectRegisterWriter(mostGeneralFunctionType(), null);
			addInstruction(
				L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.instance,
				invalidResultFunction);
			final List<L2ReadPointerOperand> slots =
				continuationSlotsList(translator.numSlots);
			// The continuation must be reified prior to invoking the
			// failure function.
			final L2WritePointerOperand reifiedRegister =
				newObjectRegisterWriter(mostGeneralContinuationType(), null);
			reify(slots, reifiedRegister, unreachable);
			final L2WritePointerOperand expectedTypeRegister =
				newObjectRegisterWriter(topMeta(), null);
			moveConstant(expectedType, expectedTypeRegister);
			addInstruction(
				L2_INVOKE.instance,
				reifiedRegister.read(),
				invalidResultFunction.read(),
				new L2ReadVectorOperand(
					asList(
						getCurrentFunction(),
						expectedTypeRegister.read(),
						resultWrite.read()))),
				new L2ImmediateOperand(1));
			startBlock(unreachable);
			addUnreachableCode();
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
			final L2ReadPointerOperand arg = args.get(0);
			final @Nullable A_BasicObject constant =
				arg.constantOrNull();
			if (constant != null)
			{
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
			else
			{
				final A_Type actualType = (A_Type) arg.type();
				if (actualType.isSubtypeOf(expectedType))
				{
					// It will always conform to the expected type.  Inline.
					moveRegister(arg, resultWrite);
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
					moveConstant(value, resultWrite);
					canFailPrimitive.value = false;
					return value;
				}
				// Its type disagrees with its declaration; fall through.
			}
			final L2WritePointerOperand varRegister =
				newObjectRegisterWriter(
					variable.resultType(),
					variable.isInitializedWriteOnceVariable()
						? variable.getValue()
						: null);
			moveConstant(variable, varRegister);
			emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				varRegister.read(),
				resultWrite);
			// Restriction might be too strong even on this method.
			final A_Type guaranteedType = variable.kind().readType();
			if (!guaranteedType.isSubtypeOf(expectedType))
			{
				// Restriction is stronger than the variable's type
				// declaration, so we have to check the actual value.
				canFailPrimitive.value = false;
				final L2BasicBlock returnWasOkLabel =
					controlFlowGraph.addBasicBlock("after return check");
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
					continuationSlotsList(translator.numSlots);
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
						emptyList())),
					new L2ImmediateOperand(1));
				addUnreachableCode(unreachable);
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
		final boolean hasInterpreter = allConstants && translator.interpreter != null;
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
			final A_Function savedFunction = translator.interpreter.function;
			translator.interpreter.function = primitiveFunction;
			final Result success = translator.interpreter().attemptPrimitive(
				primitive, argValues, false);
			translator.interpreter.function = savedFunction;
			if (success == SUCCESS)
			{
				final AvailObject value = translator.interpreter().latestResult();
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
		for (final L2ReadPointerOperand arg : args)
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
//		final L1NaiveTranslator translator,
//		final A_Function primitiveFunction,
//		final L2ReadVectorOperand args,
//		final L2WritePointerOperand resultWrite,
//		final L2ReadVectorOperand preserved,
//		final A_Type expectedType,
//		final L2WritePointerOperand failureValueWrite,
//		final L2BasicBlock successBlock,
//		final boolean canFailPrimitive,
//		final boolean skipReturnCheck)
		primitive.generateL2UnfoldableInlinePrimitive(
			this,
			primitiveFunction,
			new L2ReadVectorOperand(args),
			resultWrite,
			new L2ReadVectorOperand(preserved),
			expectedType,
			failureValueWrite,
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
	 * @param registerSet
	 *        The {@link RegisterSet} to use and update.
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
		final int nSlots = translator.numSlots;
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
		A_Map typesMap = emptyMap();
		A_Map constants = emptyMap();
		A_Set nullSlots = emptySet();
		for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
		{
			final A_Type type = savedSlotTypes.get(slotIndex - 1);
			if (type != null)
			{
				typesMap = typesMap.mapAtPuttingCanDestroy(
					fromInt(slotIndex),
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
						fromInt(slotIndex),
						true);
				}
				else
				{
					constants = constants.mapAtPuttingCanDestroy(
						fromInt(slotIndex),
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
		final L2BasicBlock success =
			controlFlowGraph.addBasicBlock("successfully read variable");
		final L2BasicBlock failure =
			controlFlowGraph.addBasicBlock("failed to read variable");
		// Emit the get-variable instruction variant.
		addInstruction(
			getOperation,
			variable,
			destination,
			new L2PcOperand(success),
			new L2PcOperand(failure));
		// Emit the failure path.
		startBlock(failure);
		final L2WritePointerOperand unassignedReadFunction =
			newObjectRegisterWriter(
				functionTypeReturning(bottom()),
				null);
		addInstruction(
			L2_GET_UNASSIGNED_VARIABLE_READ_FUNCTION.instance,
			unassignedReadFunction);
		addInstruction(
			L2_INVOKE.instance,
			unassignedReadFunction.read(),
			new L2ReadVectorOperand(emptyList()),
			new L2ImmediateOperand(1));
		addUnreachableCode();
		startBlock(success);
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
		final L2BasicBlock successBlock =
			controlFlowGraph.addBasicBlock("set local success");
		final L2BasicBlock failureBlock =
			controlFlowGraph.addBasicBlock("set local failure");
		// Emit the set-variable instruction.
		addInstruction(
			setOperation,
			variable,
			newValue,
			new L2PcOperand(successBlock),
			new L2PcOperand(failureBlock));

		// Emit the failure off-ramp.
		startBlock(failureBlock);
		final L2WritePointerOperand observeFunction = newObjectRegisterWriter(
			functionType(
				tuple(
					mostGeneralFunctionType(),
					mostGeneralTupleType()),
				TOP.o()),
			null);
		addInstruction(
			L2_GET_IMPLICIT_OBSERVE_FUNCTION.instance,
			observeFunction);
		addInstruction(
			L2_INVOKE.instance,
****
		);


		// The continuation must be reified prior to invoking the failure
		// function.
		final RegisterSet registerSet = naiveRegisters();
		final int nSlots = translator.numSlots;
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
		final L2ReadPointerOperand assignmentFunctionRegister =
			constantRegister(Interpreter.assignmentFunction());
		final L2ObjectRegister tupleRegister = newObjectRegister();
		addInstruction(
			L2_CREATE_TUPLE.instance,
			new L2ReadVectorOperand(createVector(
				asList(variable.register, newValue.register))),
			new L2WritePointerOperand(tupleRegister));
		addInstruction(
			L2_INVOKE.instance,
			new L2ReadPointerOperand(reifiedRegister),
			new L2ReadPointerOperand(observeFunction),
			new L2ReadVectorOperand(createVector(
				asList(assignmentFunctionRegister, tupleRegister))),
			new L2ImmediateOperand(1));
		addLabel(postResume);
		addInstruction(
			L2_REENTER_L2_CHUNK.instance,
			new L2WritePointerOperand(fixed(CALLER)));
		A_Map typesMap = emptyMap();
		A_Map constants = emptyMap();
		A_Set nullSlots = emptySet();
		for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
		{
			final A_Type type = savedSlotTypes.get(slotIndex - 1);
			if (type != null)
			{
				typesMap = typesMap.mapAtPuttingCanDestroy(
					fromInt(slotIndex),
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
						fromInt(slotIndex),
						true);
				}
				else
				{
					constants = constants.mapAtPuttingCanDestroy(
						fromInt(slotIndex),
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
		final @Nullable Primitive primitive = code.primitive();
		if (primitive != null)
		{
			addInstruction(L2_TRY_PRIMITIVE.instance);
		}
		addLabel(translator.afterOptionalInitialPrimitiveLabel);
		if (translator.optimizationLevel == OptimizationLevel.UNOPTIMIZED)
		{
			// Optimize it again if it's called frequently enough.
			code.countdownToReoptimize(
				L2Chunk.countdownForNewlyOptimizedCode());
			addInstruction(
				L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance,
				new L2ImmediateOperand(
					OptimizationLevel.FIRST_TRANSLATION.ordinal()));
		}
		final List<L2WritePointerOperand> initialRegisters =
			new ArrayList<>(fixedRegisterCount());
		initialRegisters.add(fixed(NULL));
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			final L2ReadPointerOperand r = readSlot(i);
			r.setFinalIndex(
				L2Translator.firstArgumentRegisterIndex + i - 1);
			initialRegisters.add(r);
		}
		addInstruction(
			L2_ENTER_L2_CHUNK.instance,
			new L2WriteVectorOperand(initialRegisters));
		for (int local = 1; local <= translator.numLocals; local++)
		{
			addInstruction(
				L2_CREATE_VARIABLE.instance,
				new L2ConstantOperand(code.localTypeAt(local)),
				new L2WritePointerOperand(
					argumentOrLocal(translator.numArgs + local)));
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
				L2_SET_VARIABLE_NO_CHECK.instance,
				new L2ReadPointerOperand(argumentOrLocal(translator.numArgs + 1)),
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
			nilSlot(stackSlot);
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
		if (restartBlock != null)
		{
			startBlock(restartBlock);
			A_Map typesMap = emptyMap();
			final List<L2ObjectRegister> slots = new ArrayList<>(
				translator.numSlots);
			final A_Type argsType = code.functionType().argsTupleType();
			A_Set nullSlots = emptySet();
			for (int i = 1; i <= translator.numSlots; i++)
			{
				slots.add(readSlot(i));
				if (i <= translator.numArgs)
				{
					typesMap = typesMap.mapAtPuttingCanDestroy(
						fromInt(i), argsType.typeAtIndex(i), true);
				}
				else
				{
					nullSlots = nullSlots.setWithElementCanDestroy(
						fromInt(i), true);
				}
			}
			addInstruction(
				L2_EXPLODE_CONTINUATION.instance,
				new L2ReadPointerOperand(fixed(CALLER)),
				new L2WriteVectorOperand(slots),
				new L2WritePointerOperand(fixed(CALLER)),
				new L2WritePointerOperand(fixed(FUNCTION)),
				new L2WriteIntOperand(skipReturnCheckRegister),
				new L2ConstantOperand(typesMap.makeImmutable()),
				new L2ConstantOperand(emptyMap()),
				new L2ConstantOperand(nullSlots.makeImmutable()),
				new L2ConstantOperand(code.functionType()));
			addInstruction(
				L2_JUMP.instance,
				new L2PcOperand(startLabel));
		}
	}

	@Override
	public void L1_doCall ()
	{
		final A_Bundle bundle = code.literalAt(getInteger());
		final A_Type expectedType = code.literalAt(getInteger());
		generateCall(bundle, expectedType, bottom());
	}

	@Override
	public void L1_doPushLiteral ()
	{
		final AvailObject constant = code.literalAt(getInteger());
		stackp--;
		moveConstant(
			constant,
			writeSlot(stackp, instanceTypeOrMetaOn(constant), constant));
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		final int localIndex = getInteger();
		stackp--;
		final L2ReadPointerOperand source = readSlot(localIndex);
		moveRegister(
			source,
			writeSlot(stackp, source.type(), source.constantOrNull()));
		nilSlot(localIndex);
	}

	@Override
	public void L1_doPushLocal ()
	{
		final int localIndex = getInteger();
		stackp--;
		final L2ReadPointerOperand source = readSlot(localIndex);
		moveRegister(
			source,
			writeSlot(stackp, source.type(), source.constantOrNull()));
		addInstruction(L2_MAKE_IMMUTABLE.instance, readSlot(stackp));
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		final int outerIndex = getInteger();
		stackp--;
		final A_Type type = code.outerTypeAt(outerIndex);
		if (type.instanceCount().equalsInt(1) && !type.isInstanceMeta())
		{
			// The exact outer is known statically.
			final AvailObject outerValue = type.instance();
			moveConstant(
				outerValue,
				writeSlot(
					stackp, instanceTypeOrMetaOn(outerValue), outerValue));
		}
		else
		{
			// The exact outer isn't known statically, but we still have its
			// type information.
			final L2ReadPointerOperand functionTempRead = getCurrentFunction();
			addInstruction(
				L2_MOVE_OUTER_VARIABLE.instance,
				new L2ImmediateOperand(outerIndex),
				functionTempRead,
				writeSlot(stackp, type, null));
			// Simplify the logic related to L1's nilling of mutable outers upon
			// their final use.
			addInstruction(L2_MAKE_IMMUTABLE.instance, readSlot(stackp));
		}
	}

	@Override
	public void L1_doClose ()
	{
		final int count = getInteger();
		final A_RawFunction codeLiteral = code.literalAt(getInteger());
		final List<L2ReadPointerOperand> outers = new ArrayList<>(count);
		for (int i = count; i >= 1; i--)
		{
			outers.add(readSlot(stackp + count - i));
		}
		// Pop the outers, but reserve room for the pushed function.
		stackp += count - 1;
		addInstruction(
			L2_CREATE_FUNCTION.instance,
			new L2ConstantOperand(codeLiteral),
			new L2ReadVectorOperand(outers),
			writeSlot(stackp, codeLiteral.functionType(), null));

		// Now that the function has been constructed, clear the slots that
		// were used for outer values -- except the destination slot, which
		// is being overwritten with the resulting function anyhow.
		for (int i = stackp + 1 - count; i <= stackp - 1; i++)
		{
			nilSlot(i);
		}
	}

	@Override
	public void L1_doSetLocal ()
	{
		final int localIndex = getInteger();
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			readSlot(localIndex),
			readSlot(stackp));
		stackp++;
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		final int localIndex = getInteger();
		stackp--;
		final A_Type type = code.localTypeAt(localIndex);
		emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			readSlot(localIndex),
			writeSlot(stackp, type, null));
	}

	@Override
	public void L1_doPushOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			writeSlot(stackp, outerType, null));
		addInstruction(
			L2_MAKE_IMMUTABLE.instance, readSlot(stackp));
	}

	@Override
	public void L1_doPop ()
	{
		nilSlot(stackp);
		stackp++;
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final A_Type innerType = outerType.readType();
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		final L2WritePointerOperand tempVarReg =
			newObjectRegisterWriter(outerType, null);
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			tempVarReg);
		emitGetVariableOffRamp(
			L2_GET_VARIABLE_CLEARING.instance,
			tempVarReg.read(),
			writeSlot(stackp, innerType, null));
	}

	@Override
	public void L1_doSetOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		final L2WritePointerOperand tempVarReg =
			newObjectRegisterWriter(outerType, null);
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			readSlot(stackp));
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			tempVarReg);
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			tempVarReg.read(),
			readSlot(stackp));
		stackp++;
	}

	@Override
	public void L1_doGetLocal ()
	{
		final int index = getInteger();
		final A_Type innerType = code.localTypeAt(index).readType();
		stackp--;
		emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			readSlot(index),
			writeSlot(stackp, innerType, null));
	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = getInteger();
		final List<L2ReadPointerOperand> vector = new ArrayList<>(count);
		for (int i = 1; i <= count; i++)
		{
			vector.add(readSlot(stackp + count - i));
		}
		stackp += count - 1;
		// Fold into a constant tuple if possible
		final List<A_BasicObject> constants = new ArrayList<>(count);
		for (final L2ReadPointerOperand regRead : vector)
		{
			if (regRead.constantOrNull() == null)
			{
				break;
			}
			constants.add(regRead.constantOrNull());
		}
		if (constants.size() == count)
		{
			// The tuple elements are all constants.  Fold it.
			final A_Tuple tuple = tupleFromList(constants);
			moveConstant(tuple, writeSlot(stackp, instanceType(tuple), tuple));
		}
		else
		{
			final A_Type tupleType = tupleTypeForTypes(
				vector.stream()
					.map(L2ReadPointerOperand::type)
					.toArray(A_Type[]::new));
			addInstruction(
				L2_CREATE_TUPLE.instance,
				new L2ReadVectorOperand(vector),
				writeSlot(stackp, tupleType, null));
		}
		// Clean up the stack slots.  That's all but the first slot,
		// which was just replaced by the tuple.
		for (int i = 2; i <= count; i++)
		{
			nilSlot(stackp + count - i);
		}
	}

	@Override
	public void L1_doGetOuter ()
	{
		final int outerIndex = getInteger();
		final A_Type outerType = code.outerTypeAt(outerIndex);
		final A_Type innerType = outerType.readType();
		final L2ReadPointerOperand functionReg = getCurrentFunction();
		stackp--;
		final L2WritePointerOperand tempVarReg =
			newObjectRegisterWriter(outerType, null);
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			functionReg,
			tempVarReg);
		emitGetVariableOffRamp(
			L2_GET_VARIABLE.instance,
			tempVarReg.read(),
			writeSlot(stackp, innerType, null));
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
		stackp--;
		final int numSlots = code.numArgsAndLocalsAndStack();
		final int numArgs = code.numArgs();
		final List<L2ReadPointerOperand> vectorWithOnlyArgsPreserved =
			new ArrayList<>(numSlots);
		for (int i = 1; i <= numArgs; i++)
		{
			vectorWithOnlyArgsPreserved.add(readSlot(i));
		}
		final L2ReadPointerOperand nilTemp = constantRegister(nil);
		for (int i = numArgs + 1; i <= numSlots; i++)
		{
			vectorWithOnlyArgsPreserved.add(nilTemp);
		}
		final A_Type continuationType =
			continuationTypeForFunctionType(code.functionType());
		final L2WritePointerOperand destReg =
			writeSlot(stackp, continuationType, null);
		if (restartBlock == null)
		{
			restartBlock = controlFlowGraph.addBasicBlock("restart on-ramp");
		}
		final L2ReadPointerOperand functionRead = getCurrentFunction();
		final L2ReadIntOperand skipReturnCheckRead = getSkipReturnCheck();
		addInstruction(
			L2_REIFY_CALLERS.instance,
			new L2ImmediateOperand(1));
		final L2ReadPointerOperand continuationRead = getCurrentContinuation();
		final L2BasicBlock afterCreation =
			controlFlowGraph.addBasicBlock("after push label");
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			continuationRead,
			functionRead,
			new L2ImmediateOperand(0),
			new L2ImmediateOperand(numSlots + 1),
			skipReturnCheckRead,
			new L2ReadVectorOperand(vectorWithOnlyArgsPreserved),
			destReg,
			new L2PcOperand(restartBlock),
			new L2PcOperand(afterCreation));
		startBlock(afterCreation);

		// Freeze all fields of the new object, including its caller,
		// function, and arguments.
		addInstruction(
			L2_MAKE_SUBOBJECTS_IMMUTABLE.instance,
			destReg.read());
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		final A_Variable literalVariable = code.literalAt(getInteger());
		stackp--;
		if (literalVariable.isInitializedWriteOnceVariable())
		{
			// It's an initialized module constant, so it can never change.  Use
			// the variable's eternal value.
			final A_BasicObject value = literalVariable.value();
			moveConstant(
				value,
				writeSlot(stackp, instanceTypeOrMetaOn(value), value));
		}
		else
		{
			final A_Type innerType = literalVariable.kind().readType();
			emitGetVariableOffRamp(
				L2_GET_VARIABLE.instance,
				constantRegister(literalVariable),
				writeSlot(stackp, innerType, null));
		}
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		final A_Variable literalVariable = code.literalAt(getInteger());
		emitSetVariableOffRamp(
			L2_SET_VARIABLE_NO_CHECK.instance,
			constantRegister(literalVariable),
			readSlot(stackp));
		stackp++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final L2ReadPointerOperand sourceReg = readSlot(stackp);
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			sourceReg);
		stackp--;
		moveRegister(
			sourceReg,
			writeSlot(
				stackp,
				sourceReg.type(),
				sourceReg.constantOrNull()));
	}

	@Override
	public void L1Ext_doPermute ()
	{
		// Move into the permuted temps, then back to the stack.  This puts the
		// responsibility for optimizing away extra moves (by coloring the
		// registers) on the optimizer.
		final A_Tuple permutation = code.literalAt(getInteger());
		final int size = permutation.tupleSize();
		final L2WritePointerOperand[] temps = new L2WritePointerOperand[size];
		for (int i = size; i >= 1; i--)
		{
			final L2ReadPointerOperand source = readSlot(stackp + size - i);
			final L2WritePointerOperand temp = newObjectRegisterWriter(
				source.type(), source.constantOrNull());
			moveRegister(source, temp);
			temps[permutation.tupleIntAt(i) - 1] = temp;
		}
		for (int i = size; i >= 1; i--)
		{
			final L2ReadPointerOperand temp = temps[i - 1].read();
			moveRegister(
				temp,
				writeSlot(
					stackp + size - i, temp.type(), temp.constantOrNull()));
		}
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		final AvailObject method = code.literalAt(getInteger());
		final AvailObject expectedType = code.literalAt(getInteger());
		final AvailObject superUnionType = code.literalAt(getInteger());
		generateCall(method, expectedType, superUnionType);
	}

	@Override
	public void L1Ext_doReserved ()
	{
		// This shouldn't happen unless the compiler is out of sync with the
		// translator.
		error("That nybblecode is not supported");
	}

	@Override
	public void L1Implied_doReturn ()
	{
		final L2ReadIntOperand skipReturnCheckRead = getSkipReturnCheck();
		addInstruction(
			L2_RETURN.instance,
			readSlot(stackp),
			skipReturnCheckRead);
		assert stackp == code.maxStackDepth();
		stackp = Integer.MIN_VALUE;
	}
}

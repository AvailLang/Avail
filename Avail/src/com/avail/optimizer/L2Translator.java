/**
 * L2Translator.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.annotations.*;
import com.avail.descriptor.*;
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
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class L2Translator implements L1OperationDispatcher
{
	final static boolean debugOptimized = false;

	@InnerAccess AvailObject code;
	@InnerAccess AvailObject nybbles;
	@InnerAccess int numArgs;
	@InnerAccess int numLocals;
	@InnerAccess int numSlots;
	@InnerAccess int pc;
	@InnerAccess int stackp;
	@InnerAccess int optimizationLevel;
	@InnerAccess L2Interpreter interpreter;
	@InnerAccess private final List<L2Instruction> instructions =
		new ArrayList<L2Instruction>(10);

	private final Set<AvailObject> contingentMethods =
		new HashSet<AvailObject>();

	/**
	 * The bank of registers defined at the current level two instruction being
	 * generated.
	 */
	final RegisterSet registers;

	/**
	 * Answer my current {@link RegisterSet}.
	 *
	 * @return The mechanism for tracking register usage.
	 */
	public RegisterSet registers ()
	{
		return registers;
	}

	/**
	 * Construct a new {@link L2Translator}.
	 *
	 * @param code The {@linkplain CompiledCodeDescriptor code} to translate.
	 */
	public L2Translator (final AvailObject code)
	{
		this.code = code;
		registers = new RegisterSet(this);
	}

	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.  Also give it an
	 * alternative way to propagate register type/value information.
	 *
	 * @param propagationAction The propagation action perform.
	 * @param operation The operation to invoke.
	 * @param operands The operands of the instruction.
	 */
	private void addInstruction (
		final Continuation0 propagationAction,
		final L2Operation operation,
		final L2Operand... operands)
	{
		assert operation != L2_LABEL.instance
		: "Use newLabel() and addLabel(...) to add a label";
		final L2Instruction instruction =
			new L2Instruction(propagationAction, operation, operands);
		final L2Instruction normalizedInstruction =
			instruction.transformRegisters(registers.normalizer);
		instructions.add(normalizedInstruction);
		normalizedInstruction.propagateTypesFor(this);
		if (debugOptimized)
		{
			System.out.println();
			System.out.println(normalizedInstruction);
			System.out.println(registers.registerConstants);
			System.out.println(registers.registerTypes);
			System.out.println(registers.registerOrigins);
		}
	}

	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.
	 *
	 * @param operation The operation to invoke.
	 * @param operands The operands of the instruction.
	 */
	private void addInstruction (
		final L2Operation operation,
		final L2Operand... operands)
	{
		addInstruction(null, operation, operands);
	}

	/**
	 * Add a label instruction previously constructed with {@link
	 * #newLabel(String)}.
	 *
	 * @param label
	 *            An {@link L2Instruction} whose operation is {@link L2_LABEL}
	 */
	private void addLabel (
		final L2Instruction label)
	{
		assert label.operation == L2_LABEL.instance;
		// Don't transform its registers -- we need *this* instruction.
		assert !instructions.contains(label);  // TODO [MvG] Remove - slow.
		instructions.add(label);
	}

	/**
	 * Answer the {@link L2Register#finalIndex() final index} of the register
	 * holding the first argument to this compiled code (or where the first
	 * argument would be if there were any).
	 */
	static int firstArgumentRegisterIndex = FixedRegister.values().length;


	/**
	 * Return the {@linkplain CompiledCodeDescriptor compiled Level One code}
	 * being translated.
	 *
	 * @return The code being translated.
	 */
	public AvailObject code ()
	{
		return code;
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
	private L2RegisterVector createVector (
		final List<L2ObjectRegister> objectRegisters)
	{
		final L2RegisterVector vector = new L2RegisterVector(objectRegisters);
		return vector;
	}

	/**
	 * Answer an integer extracted at the current program counter.  The program
	 * counter will be adjusted to skip over the integer.
	 *
	 * @return The integer encoded at the current nybblecode position.
	 */
	private int getInteger ()
	{
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[firstNybble]; count > 0; count--, pc++)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc);
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[firstNybble];
		return value;
	}

	/**
	 * Create a new {@link L2_LABEL} pseudo-instruction}.
	 *
	 * @param comment A description of the label.
	 * @return The new label.
	 */
	private L2Instruction newLabel (final String comment)
	{
		return new L2Instruction(
			L2_LABEL.instance,
			new L2CommentOperand(comment));
	}

	/**
	 * Only inline effectively monomorphic messages for now -- i.e., methods
	 * where every possible method uses the same primitive number.  Return one
	 * of the method implementation bodies if it's unambiguous and can be
	 * inlined (or is a {@code Primitive.Flag#SpecialReturnConstant}), otherwise
	 * return null.
	 *
	 * @param impSet The {@linkplain MethodDescriptor method}
	 *               containing the method(s) that may be inlined or invoked.
	 * @param args A {@link List} of {@linkplain L2ObjectRegister registers}
	 *             holding the actual constant values used to look up the
	 *             implementation for the call.
	 * @return A method body (a {@code FunctionDescriptor function}) that
	 *         exemplifies the primitive that should be inlined.
	 */
	private AvailObject primitiveToInlineForArgumentRegisters (
		final AvailObject impSet,
		final List<L2ObjectRegister> args)
	{
		final List<AvailObject> argTypes =
			new ArrayList<AvailObject>(args.size());
		for (final L2ObjectRegister arg : args)
		{
			AvailObject type;
			type = registers.hasTypeAt(arg) ? registers.typeAt(arg) : ANY.o();
			argTypes.add(type);
		}
		return primitiveToInlineForWithArgumentTypes(impSet, argTypes);
	}

	/**
	 * Only inline effectively monomorphic messages for now -- i.e., method
	 * methods where every possible method uses the same primitive
	 * number.  Return one of the method implementation bodies if it's
	 * unambiguous and can be inlined (or is a {@code
	 * Primitive.Flag#SpecialReturnConstant}), otherwise return null.
	 *
	 * @param impSet The {@linkplain MethodDescriptor method}
	 *               containing the method(s) that may be inlined or invoked.
	 * @param argTypeRegisters A {@link List} of {@linkplain L2ObjectRegister
	 *                         registers} holding the types used to look up the
	 *                         implementation for the call.
	 * @return A method body (a {@code FunctionDescriptor function}) that
	 *         exemplifies the primitive that should be inlined.
	 */
	private AvailObject primitiveToInlineForArgumentTypeRegisters (
		final AvailObject impSet,
		final List<L2ObjectRegister> argTypeRegisters)
	{
		final List<AvailObject> argTypes =
			new ArrayList<AvailObject>(argTypeRegisters.size());
		for (final L2ObjectRegister argTypeRegister : argTypeRegisters)
		{
			// Map the list of argTypeRegisters to any bound constants,
			// which must be types.  It's probably an error if one isn't bound
			// to a type constant, but we'll allow it anyhow for the moment.
			AvailObject type;
			type = registers.hasConstantAt(argTypeRegister)
				? registers.constantAt(argTypeRegister)
				: ANY.o();
			argTypes.add(type);
		}
		return primitiveToInlineForWithArgumentTypes(impSet, argTypes);
	}


	/**
	 * Only inline effectively monomorphic messages for now -- i.e., method
	 * methods where every possible method uses the same primitive
	 * number.  Return the primitive number if it's unambiguous and can be
	 * inlined, otherwise zero.
	 *
	 * @param impSet The {@linkplain MethodDescriptor method}
	 *               containing the method(s) that may be inlined or invoked.
	 * @param argTypes The types of the arguments to the call.
	 * @return One of the (equivalent) primitive method bodies, or null.
	 */
	private AvailObject primitiveToInlineForWithArgumentTypes (
		final AvailObject impSet,
		final List<AvailObject> argTypes)
	{
		final List<AvailObject> imps =
			impSet.implementationsAtOrBelow(argTypes);
		AvailObject firstBody = null;
		for (final AvailObject bundle : imps)
		{
			// If a forward or abstract method is possible, don't inline.
			if (!bundle.isMethod())
			{
				return null;
			}

			final AvailObject body = bundle.bodyBlock();
			if (body.code().primitiveNumber() == 0)
			{
				return null;
			}

			final int primitiveNumber = body.code().primitiveNumber();
			if (firstBody == null)
			{
				firstBody = body;
			}
			else if (primitiveNumber != firstBody.code().primitiveNumber())
			{
				// Another possible implementation has a different primitive
				// number.  Don't attempt to inline.
				return null;
			}
			else
			{
				// Same primitive number.
				if (Primitive.byPrimitiveNumber(primitiveNumber).hasFlag(
					SpecialReturnConstant))
				{
					// It's the push-the-first-literal primitive.
					if (!firstBody.code().literalAt(1).equals(
						body.code().literalAt(1)))
					{
						// The push-the-first-literal primitive methods push
						// different literals.  Give up.
						return null;
					}
				}
			}
		}
		if (firstBody == null)
		{
			return null;
		}
		final Primitive primitive = Primitive.byPrimitiveNumber(
			firstBody.code().primitiveNumber());
		if (primitive.hasFlag(SpecialReturnConstant)
				|| primitive.hasFlag(CanInline)
				|| primitive.hasFlag(CanFold))
		{
			return firstBody;
		}
		return null;
	}

	/**
	 * Answer the register representing the slot of the stack associated with
	 * the given index.
	 *
	 * @param stackIndex A stack position, for example stackp.
	 * @param toWrite True to get a register for writing, false for reading.
	 * @return A {@linkplain L2ObjectRegister register} representing the stack
	 *         at the given position.
	 */
	private L2ObjectRegister stackRegister (
		final int stackIndex,
		final boolean toWrite)
	{
		assert 1 <= stackIndex && stackIndex <= code.maxStackDepth();
		return registers.continuationSlot(
			numArgs + numLocals + stackIndex);
	}

	/**
	 * Answer the register representing the slot of the stack associated with
	 * the current value of stackp.  In particular, assume this register is
	 * about to be written to, allowing "register renaming" to take place.
	 *
	 * @return A {@linkplain L2ObjectRegister register} representing the top of
	 *         the stack (for writing) right now.
	 */
	private L2ObjectRegister writeTopOfStackRegister ()
	{
		assert 1 <= stackp && stackp <= code.maxStackDepth();
		return stackRegister(stackp, true);
	}

	/**
	 * Answer the register representing the slot of the stack associated with
	 * the current value of stackp.  In particular, assume this register is
	 * about to be read.
	 *
	 * @return A {@linkplain L2ObjectRegister register} representing the top of
	 *         the stack (for reading) right now.
	 */
	private L2ObjectRegister readTopOfStackRegister ()
	{
		assert 1 <= stackp && stackp <= code.maxStackDepth();
		return stackRegister(stackp, false);
	}

	/**
	 * Generate instruction(s) to move from one register to another.
	 *
	 * @param sourceRegister Where to read the AvailObject.
	 * @param destinationRegister Where to write the AvailObject.
	 */
	private void moveRegister (
		final @NotNull L2ObjectRegister sourceRegister,
		final @NotNull L2ObjectRegister destinationRegister)
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
	 * Generate instruction(s) to move the given {@link AvailObject} into the
	 * specified {@link L2Register}.
	 *
	 * @param value The value to move.
	 * @param destinationRegister Where to move it.
	 */
	private void moveConstant (
		final @NotNull AvailObject value,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		if (value.equalsNull())
		{
			moveRegister(registers.fixed(NULL), destinationRegister);
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
	 * Inline the primitive.  Attempt to fold it (evaluate it right now) if the
	 * primitive says it's foldable and the arguments are all constants.  Answer
	 * the result if it was folded, otherwise null.  If it was folded, generate
	 * code to push the folded value.  Otherwise generate an invocation of the
	 * primitive, jumping to the successLabel on success.
	 *
	 * <p>
	 * Special case if the flag {@link Primitive.Flag#SpecialReturnConstant}
	 * is specified:  Always fold it, since it's just a constant.
	 * </p>
	 *
	 * <p>
	 * Another special case if the flag {@link Primitive.Flag
	 * #SpecialReturnSoleArgument} is specified:  Don't generate an inlined
	 * primitive invocation, but instead generate a move from the argument
	 * register to the output.
	 * </p>
	 *
	 * @param primitiveFunction
	 *            A {@linkplain FunctionDescriptor function} for which its
	 *            primitive might be inlined, or even folded if possible.
	 * @param impSet
	 *            The method containing the primitive to be invoked.
	 * @param args
	 *            The {@link List} of arguments to the primitive function.
	 * @param preserved
	 *            A list of registers to consider preserved across this call.
	 *            They have no effect at runtime, but affect analysis of which
	 *            instructions consume which writes.
	 * @param expectedType
	 *            The {@linkplain TypeDescriptor type} of object that this
	 *            primitive call site was expected to produce.
	 * @param failureValueRegister
	 *            The {@linkplain L2ObjectRegister register} into which to write
	 *            the failure information if the primitive fails.
	 * @param successLabel
	 *            The label to jump to if the primitive is not folded and is
	 *            inlined.
	 * @param canFailPrimitive
	 *            A {@linkplain Mutable Mutable<Boolean>} that this method sets
	 *            if a fallible primitive was inlined.
	 * @return
	 *            The value if the primitive was folded, otherwise null.
	 */
	private AvailObject emitInlinePrimitiveAttempt (
		final AvailObject primitiveFunction,
		final AvailObject impSet,
		final List<L2ObjectRegister> args,
		final List<L2ObjectRegister> preserved,
		final AvailObject expectedType,
		final L2ObjectRegister failureValueRegister,
		final L2Instruction successLabel,
		final Mutable<Boolean> canFailPrimitive)
	{
		final int primitiveNumber = primitiveFunction.code().primitiveNumber();
		final Primitive primitive =
			Primitive.byPrimitiveNumber(primitiveNumber);
		if (primitive.hasFlag(SpecialReturnConstant))
		{
			// Use the first literal as the return value.
			final AvailObject value = primitiveFunction.code().literalAt(1);
			moveConstant(value, writeTopOfStackRegister());
			// Restriction might be too strong even on a constant method.
			if (value.isInstanceOf(expectedType))
			{
				canFailPrimitive.value = false;
				return value;
			}
			// The primitive will technically succeed, but the return will trip
			// a failure to meet the strengthened return type.
			addInstruction(
				L2_REPORT_INVALID_RETURN_TYPE.instance,
				new L2PrimitiveOperand(primitive),
				new L2ReadPointerOperand(readTopOfStackRegister()),
				new L2ConstantOperand(expectedType));
			// No need to generate primitive failure handling code, since
			// technically the primitive succeeded but the return failed.
			// The above instruction effectively makes the successor
			// instructions unreachable, so don't spend a lot of time generating
			// that dead code.
			canFailPrimitive.value = false;
			return null;
		}
		if (primitive.hasFlag(SpecialReturnSoleArgument))
		{
			// Use the only argument as the return value.
			assert primitiveFunction.code().numArgs() == 1;
			assert args.size() == 1;
			final L2ObjectRegister arg = args.get(0);
			if (registers.hasConstantAt(arg))
			{
				final AvailObject constant = registers.constantAt(arg);
				// Restriction might be too strong even on such a simple method.
				if (constant.isInstanceOf(expectedType))
				{
					// Actually fold it.
					canFailPrimitive.value = false;
					return constant;
				}
				// The restriction is definitely too strong.  Fall through.
			}
			else if (registers.hasTypeAt(arg))
			{
				final AvailObject actualType = registers.typeAt(arg);
				if (actualType.isSubtypeOf(expectedType))
				{
					// It will always conform to the expected type.  Inline it.
					moveRegister(arg, writeTopOfStackRegister());
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
			if (!registers.hasConstantAt(arg))
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
				new ArrayList<AvailObject>(args.size());
			for (final L2Register argReg : args)
			{
				argValues.add(registers.constantAt(argReg));
			}
			final Result success = interpreter.attemptPrimitive(
				primitiveNumber,
				primitiveFunction.code(),
				argValues);
			if (success == SUCCESS)
			{
				final AvailObject value = interpreter.primitiveResult();
				if (value.isInstanceOf(expectedType))
				{
					value.makeImmutable();
					moveConstant(value, writeTopOfStackRegister());
					canFailPrimitive.value = false;
					return value;
				}
			}
			assert success != CONTINUATION_CHANGED
			: "This foldable primitive changed the continuation!";
		}
		final L2ObjectRegister expectedTypeRegister = registers.newObject();
		moveConstant(expectedType, expectedTypeRegister);
		if (primitive.hasFlag(CannotFail))
		{
			addInstruction(
				L2_RUN_INFALLIBLE_PRIMITIVE.instance,
				new L2PrimitiveOperand(primitive),
				new L2ReadVectorOperand(createVector(args)),
				new L2ReadPointerOperand(expectedTypeRegister),
				new L2WritePointerOperand(writeTopOfStackRegister()));
			canFailPrimitive.value = false;
		}
		else
		{
			addInstruction(
				L2_ATTEMPT_INLINE_PRIMITIVE.instance,
				new L2PrimitiveOperand(primitive),
				new L2ReadVectorOperand(createVector(args)),
				new L2ReadPointerOperand(expectedTypeRegister),
				new L2WritePointerOperand(writeTopOfStackRegister()),
				new L2WritePointerOperand(failureValueRegister),
				new L2ReadWriteVectorOperand(createVector(preserved)),
				new L2PcOperand(successLabel));
			canFailPrimitive.value = true;
		}
		return null;
	}

	/**
	 * Generate code to perform a multimethod invocation.
	 *
	 * @param method
	 *            The {@linkplain MethodDescriptor method} to invoke.
	 * @param expectedType
	 *            The expected return {@linkplain TypeDescriptor type}.
	 * @param isSuper
	 *            Whether this is a {@linkplain L1Operation#L1Ext_doSuperCall
	 *            super call}.
	 */
	private void generateCall (
		final AvailObject method,
		final AvailObject expectedType,
		final boolean isSuper)
	{
		contingentMethods.add(method);
		final L2ObjectRegister tempCallerRegister = registers.newObject();
		moveRegister(registers.fixed(CALLER), tempCallerRegister);
		final List<L2ObjectRegister> preSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
		{
			preSlots.add(registers.continuationSlot(slotIndex));
		}
		final L2ObjectRegister expectedTypeReg = registers.newObject();
		final L2ObjectRegister failureObjectReg = registers.newObject();
		final int nArgs = method.numArgs();
		List<L2ObjectRegister> argTypes = null;
		if (isSuper)
		{
			argTypes = new ArrayList<L2ObjectRegister>(nArgs);
			for (int i = nArgs; i >= 1; i--)
			{
				argTypes.add(0, readTopOfStackRegister());
				preSlots.set(
					numArgs + numLocals + stackp - 1,
					registers.fixed(NULL));
				stackp++;
			}
		}
		final List<L2ObjectRegister> preserved =
			new ArrayList<L2ObjectRegister>(preSlots);
		assert preserved.size() == numSlots;
		final List<L2ObjectRegister> args =
			new ArrayList<L2ObjectRegister>(nArgs);
		for (int i = nArgs; i >= 1; i--)
		{
			args.add(0, readTopOfStackRegister());
			preSlots.set(
				numArgs + numLocals + stackp - 1,
				registers.fixed(NULL));
			stackp++;
		}
		stackp--;
		preSlots.set(numArgs + numLocals + stackp - 1, expectedTypeReg);
		final AvailObject primFunction;
		if (isSuper)
		{
			primFunction = primitiveToInlineForArgumentTypeRegisters(
				method,
				argTypes);
		}
		else
		{
			primFunction = primitiveToInlineForArgumentRegisters(
				method,
				args);
		}

		final L2Instruction successLabel;
		successLabel = newLabel("success: " + method.name().name());
		if (primFunction != null)
		{
			// Inline the primitive.  Attempt to fold it if the primitive says
			// it's foldable and the arguments are all constants.
			final Mutable<Boolean> canFailPrimitive = new Mutable<Boolean>();
			final AvailObject folded = emitInlinePrimitiveAttempt(
				primFunction,
				method,
				args,
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
				// Primitive attempt was not inlined, but it can't fail.
				return;
			}
		}
		moveConstant(expectedType, expectedTypeReg);
		final List<AvailObject> savedSlotTypes =
			new ArrayList<AvailObject>(numSlots);
		final List<AvailObject> savedSlotConstants =
			new ArrayList<AvailObject>(numSlots);
		for (final L2ObjectRegister reg : preSlots)
		{
			savedSlotTypes.add(registers.typeAt(reg));
			savedSlotConstants.add(registers.constantAt(reg));
		}
		final L2Instruction postCallLabel =
			newLabel("postCall " + method.name().name());
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			new L2ReadPointerOperand(registers.fixed(CALLER)),
			new L2ReadPointerOperand(registers.fixed(FUNCTION)),
			new L2ImmediateOperand(pc),
			new L2ImmediateOperand(stackp),
			new L2ReadVectorOperand(createVector(preSlots)),
			new L2PcOperand(postCallLabel),
			new L2WritePointerOperand(tempCallerRegister));
		final L2ObjectRegister function = registers.newObject();
		// Look it up...
		if (isSuper)
		{
			addInstruction(
				L2_LOOKUP_BY_TYPES.instance,
				new L2SelectorOperand(method),
				new L2ReadVectorOperand(createVector(argTypes)),
				new L2WritePointerOperand(function));
		}
		else
		{
			addInstruction(
				L2_LOOKUP_BY_VALUES.instance,
				new L2SelectorOperand(method),
				new L2ReadVectorOperand(createVector(args)),
				new L2WritePointerOperand(function));
		}
		// If there was only one possible function to invoke don't bother
		// looking it up at runtime.
		if (registers.hasConstantAt(function))
		{
			// Don't bother to remove the lookup instruction -- it'll evaporate
			// since nobody will use its result.
			moveConstant(registers.constantAt(function), function);
		}
		// Now invoke what was looked up.
		if (primFunction != null)
		{
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

		// Rebuild new architectural registers for the state upon return
		// from the call.
		final int nSlots = numSlots;
		// The primitive was attempted, so we must get the registers in
		// sync between the success and failure cases.
		final List<L2ObjectRegister> slotRegisters =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int i = 1; i <= numSlots; i++)
		{
			// If a primitive function was attempted then we reuse the same
			// registers for the explode (since it's a join-point in the flow
			// graph).  Otherwise there was no primitive so we allocate fresh
			// registers for maximum flexibility.
			slotRegisters.add(
				registers.continuationSlot(i));
		}
		// After the call returns, the callerRegister will contain the
		// continuation to be exploded.
		addInstruction(
			L2_REENTER_L2_CHUNK.instance,
			new L2WritePointerOperand(registers.fixed(CALLER)));
		addInstruction(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					// Remove all information about and dependence on
					// non-slot registers, since the call destroyed it all.
					// Keep the information about the slot registers,
					// however, other than origin information that's no
					// longer applicable (i.e., mentioning non-slot
					// registers that are no longer valid).
					final Set<L2ObjectRegister> live =
						new HashSet<L2ObjectRegister>(slotRegisters);
					live.add(registers.fixed(NULL));
					live.add(registers.fixed(CALLER));
					live.add(registers.fixed(FUNCTION));
					registers.registerOrigins.keySet().retainAll(live);
					registers.invertedOrigins.keySet().retainAll(live);
					registers.registerConstants.keySet().retainAll(live);
					registers.registerTypes.keySet().retainAll(live);
					for (final List<L2Register> history
						: registers.registerOrigins.values())
					{
						history.retainAll(live);
					}
					for (final Set<L2Register> future
						: registers.invertedOrigins.values())
					{
						future.retainAll(live);
					}
					for (int slotIndex = 1; slotIndex <= nSlots; slotIndex++)
					{
						final L2Register postSlot =
							registers.continuationSlot(slotIndex);
						final AvailObject type =
							savedSlotTypes.get(slotIndex - 1);
						if (type != null)
						{
							registers.typeAtPut(postSlot, type);
						}
						final AvailObject constant =
							savedSlotConstants.get(slotIndex - 1);
						if (constant != null)
						{
							registers.constantAtPut(postSlot, constant);
						}
					}
				}
			},
			L2_EXPLODE_CONTINUATION.instance,
			new L2ReadPointerOperand(registers.fixed(CALLER)),
			new L2WriteVectorOperand(createVector(slotRegisters)),
			new L2WritePointerOperand(registers.fixed(CALLER)),
			new L2WritePointerOperand(registers.fixed(FUNCTION)));
		// At this point the implied return instruction in the called code has
		// verified the value matched the expected type, so we know that much
		// has to be true.
		registers.removeConstantAt(readTopOfStackRegister());
		registers.typeAtPut(readTopOfStackRegister(), expectedType);
		addLabel(successLabel);
	}

	/**
	 * [n] - Send the message at index n in the compiledCode's literals.  Pop
	 * the arguments for this message off the stack (the message itself knows
	 * how many to expect).  The first argument was pushed first, and is the
	 * deepest on the stack.  Use these arguments to look up the method
	 * dynamically.  Before invoking the method, push the expected return type
	 * onto the stack.  Its presence will help distinguish continuations
	 * produced by the pushLabel instruction from their senders.  When the call
	 * completes (if ever), it will use the implied return instruction, which
	 * will first check that the returned object agrees with the expected type
	 * and then replace the type on the stack with the returned object.
	 */
	@Override
	public void L1_doCall ()
	{
		final AvailObject impSet = code.literalAt(getInteger());
		final AvailObject expectedType = code.literalAt(getInteger());

		generateCall(impSet, expectedType, false);
	}

	@Override
	public void L1_doClose ()
	{
		// [n,m] - Pop the top n items off the stack, and use them as outer
		// variables in the construction of a function based on the compiledCode
		// that's the literal at index m of the current compiledCode.

		final int count = getInteger();
		final AvailObject codeLiteral = code.literalAt(getInteger());
		final List<L2ObjectRegister> outers =
			new ArrayList<L2ObjectRegister>(count);
		for (int i = count; i >= 1; i--)
		{
			outers.add(0, readTopOfStackRegister());
			stackp++;
		}
		stackp--;
		addInstruction(
			L2_CREATE_FUNCTION.instance,
			new L2ConstantOperand(codeLiteral),
			new L2ReadVectorOperand(createVector(outers)),
			new L2WritePointerOperand(writeTopOfStackRegister()));

		// Now that the function has been constructed, clear the slots that
		// were used for outer values (except the destination slot, which is
		// being overwritten with the resulting function anyhow).
		for (
			int stackIndex = stackp + 1 - count;
			stackIndex <= stackp - 1;
			stackIndex++)
		{
			moveConstant(
				NullDescriptor.nullObject(),
				stackRegister(stackIndex, true));
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
		// [n] - Push the value of the local variable (not an argument) indexed
		// by n (index 1 is first argument).
		final int index = getInteger();
		stackp--;
		addInstruction(
			L2_GET_VARIABLE.instance,
			new L2ReadPointerOperand(registers.argumentOrLocal(index)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		// [n] - Push the value of the local variable (not an argument) indexed
		// by n (index 1 is first argument).
		final int index = getInteger();
		stackp--;
		addInstruction(
			L2_GET_VARIABLE_CLEARING.instance,
			new L2ReadPointerOperand(registers.argumentOrLocal(index)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
	}

	@Override
	public void L1_doGetOuter ()
	{
		// [n] - Push the value of the outer variable indexed by n in the
		// current function.
		final int outerIndex = getInteger();
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(registers.fixed(FUNCTION)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
		addInstruction(
			L2_GET_VARIABLE.instance,
			new L2ReadPointerOperand(readTopOfStackRegister()),
			new L2WritePointerOperand(writeTopOfStackRegister()));
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		// [n] - Push the value of the outer variable indexed by n in the
		// current function.  If the variable itself is mutable, clear it at
		// this time - nobody will know.  Actually, right now we don't optimize
		// this in level two, for simplicity.

		final int outerIndex = getInteger();
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(registers.fixed(FUNCTION)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
		addInstruction(
			L2_GET_VARIABLE_CLEARING.instance,
			new L2ReadPointerOperand(readTopOfStackRegister()),
			new L2WritePointerOperand(writeTopOfStackRegister()));
	}

	@Override
	public void L1_doMakeTuple ()
	{
		// [n] - Construct a tuple from the top n stack items.
		final int count = getInteger();
		final List<L2ObjectRegister> vector =
			new ArrayList<L2ObjectRegister>(count);
		for (int i = 1; i <= count; i++)
		{
			vector.add(stackRegister(stackp + count - i, false));
		}
		stackp += count - 1;
		addInstruction(
			L2_CREATE_TUPLE.instance,
			new L2ReadVectorOperand(createVector(vector)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
	}

	@Override
	public void L1_doPop ()
	{
		// Remove the top item from the stack.
		assert stackp == code.maxStackDepth()
		: "Pop should only only occur at end of statement";
		moveConstant(NullDescriptor.nullObject(), writeTopOfStackRegister());
		stackp++;
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		// [n] - Push the argument (actual value) or local variable (the
		// variable itself) indexed by n.  Since this is known to be the last
		// use (non-debugger) of the argument or local, clear that slot of the
		// current continuation.
		final int localIndex = getInteger();
		stackp--;
		moveRegister(
			registers.argumentOrLocal(localIndex),
			writeTopOfStackRegister());
		moveConstant(
			NullDescriptor.nullObject(),
			registers.argumentOrLocal(localIndex));
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		// [n] - Push the outer variable indexed by n in the current function.
		// If the variable is mutable, clear it (no one will know).  If the
		// variable and function are both mutable, remove the variable from the
		// function by clearing it.
		final int outerIndex = getInteger();
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(registers.fixed(FUNCTION)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			new L2ReadPointerOperand(readTopOfStackRegister()));
	}

	@Override
	public void L1_doPushLiteral ()
	{
		// [n] - Push the literal indexed by n in the current compiledCode.
		final AvailObject constant = code.literalAt(getInteger());
		stackp--;
		moveConstant(constant, writeTopOfStackRegister());
	}

	@Override
	public void L1_doPushLocal ()
	{
		// [n] - Push the argument (actual value) or local variable (the
		// variable itself) indexed by n.
		final int localIndex = getInteger();
		stackp--;
		moveRegister(
			registers.argumentOrLocal(localIndex),
			writeTopOfStackRegister());
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			new L2ReadPointerOperand(readTopOfStackRegister()));
	}

	@Override
	public void L1_doPushOuter ()
	{
		// [n] - Push the outer variable indexed by n in the current function.
		final int outerIndex = getInteger();
		stackp--;
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(registers.fixed(FUNCTION)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			new L2ReadPointerOperand(readTopOfStackRegister()));
	}

	@Override
	public void L1_doSetLocal ()
	{
		// [n] - Pop the stack and assign this value to the local variable (not
		// an argument) indexed by n (index 1 is first argument).
		final int localIndex = getInteger();
		final L2ObjectRegister local =
			registers.argumentOrLocal(localIndex);
		addInstruction(
			L2_SET_VARIABLE.instance,
			new L2ReadPointerOperand(local),
			new L2ReadPointerOperand(readTopOfStackRegister()));
		stackp++;
	}

	@Override
	public void L1_doSetOuter ()
	{
		// [n] - Pop the stack and assign this value to the outer variable
		// indexed by n in the current function.
		final int outerIndex = getInteger();
		final L2ObjectRegister tempReg = registers.newObject();
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			new L2ReadPointerOperand(readTopOfStackRegister()));
		addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(registers.fixed(FUNCTION)),
			new L2WritePointerOperand(tempReg));
		addInstruction(
			L2_SET_VARIABLE.instance,
			new L2ReadPointerOperand(tempReg),
			new L2ReadPointerOperand(readTopOfStackRegister()));
		stackp++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		// Duplicate the top stack element.  I.e., pop value x, push x, and push
		// x again.  Make x immutable for safety.
		final L2ObjectRegister originalTopOfStack = readTopOfStackRegister();
		addInstruction(
			L2_MAKE_IMMUTABLE.instance,
			new L2ReadPointerOperand(originalTopOfStack));
		stackp--;
		moveRegister(
			originalTopOfStack,
			writeTopOfStackRegister());
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		// [n] - Push the value of the variable that's literal number n in the
		// current compiledCode.
		final L2ObjectRegister tempReg = registers.newObject();
		final AvailObject constant = code.literalAt(getInteger());
		stackp--;
		moveConstant(constant, tempReg);
		addInstruction(
			L2_GET_VARIABLE.instance,
			new L2ReadPointerOperand(tempReg),
			new L2WritePointerOperand(writeTopOfStackRegister()));
	}

	/**
	 * [n] - Push the (n+1)st stack element's type.  This is only used by the
	 * supercast mechanism to produce types for arguments not being cast.  See
	 * {@link #L1Ext_doSuperCall()}.  This implies the type will be used for a
	 * lookup and then discarded.  We therefore don't treat the type as
	 * acquiring a new reference from the stack, so it doesn't have to become
	 * immutable.  This could be a sticky point with the garbage collector if it
	 * finds only one reference to the type, but I think it should still work.
	 */
	@Override
	public void L1Ext_doGetType ()
	{
		final int index = getInteger();
		stackp--;
		addInstruction(
			L2_GET_TYPE.instance,
			new L2ReadPointerOperand(stackRegister(stackp + 1 + index, false)),
			new L2WritePointerOperand(writeTopOfStackRegister()));
	}

	/**
	 * Build a continuation which, when restarted, will be just like restarting
	 * the current continuation.
	 */
	@Override
	public void L1Ext_doPushLabel ()
	{
		stackp--;
		final L2Instruction startLabel = newLabel("continuation start");
		instructions.add(0, startLabel);
		final List<L2ObjectRegister> vectorWithOnlyArgsPreserved =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int i = 1; i <= numArgs; i++)
		{
			vectorWithOnlyArgsPreserved.add(
				registers.continuationSlot(i));
		}
		for (int i = numArgs + 1; i <= numSlots; i++)
		{
			vectorWithOnlyArgsPreserved.add(registers.fixed(NULL));
		}
		final L2ObjectRegister destReg = writeTopOfStackRegister();
		addInstruction(
			L2_CREATE_CONTINUATION.instance,
			new L2ReadPointerOperand(registers.fixed(CALLER)),
			new L2ReadPointerOperand(registers.fixed(FUNCTION)),
			new L2ImmediateOperand(1),
			new L2ImmediateOperand(code.maxStackDepth() + 1),
			new L2ReadVectorOperand(createVector(vectorWithOnlyArgsPreserved)),
			new L2PcOperand(startLabel),
			new L2WritePointerOperand(destReg));

		// Freeze all fields of the new object, including its caller, function,
		// and arguments.
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
		// [n] - Pop the stack and assign this value to the variable that's the
		// literal indexed by n in the current compiledCode.
		final AvailObject constant = code.literalAt(getInteger());
		final L2ObjectRegister tempReg = registers.newObject();
		moveConstant(constant, tempReg);
		addInstruction(
			L2_SET_VARIABLE.instance,
			new L2ReadPointerOperand(tempReg),
			new L2ReadPointerOperand(readTopOfStackRegister()));
		stackp++;
	}

	/**[n] - Send the message at index n in the compiledCode's literals.  Like
	 * the call instruction, the arguments will have been pushed on the stack in
	 * order, but unlike call, each argument's type will also have been pushed
	 * (all arguments are pushed, then all argument types).  These are either
	 * the arguments' exact types, or constant types (that must be supertypes of
	 * the arguments' types), or any mixture of the two.  These types will be
	 * used for method lookup, rather than the argument types.  This supports a
	 * 'super'-like mechanism in the presence of multi-methods.  Like the call
	 * instruction, all arguments (and types) are popped, then the expected
	 * return type is pushed, and the looked up method is started.  When the
	 * invoked method returns (via an implicit return instruction), the return
	 * value will be checked against the previously pushed expected type, and
	 * then the type will be replaced by the return value on the stack.
	 */
	@Override
	public void L1Ext_doSuperCall ()
	{
		final AvailObject method = code.literalAt(getInteger());
		final AvailObject expectedType = code.literalAt(getInteger());

		generateCall(method, expectedType, true);
	}

	/**
	 * Return to the calling continuation with top of stack.  Must be the last
	 * instruction in block.  Note that the calling continuation has
	 * automatically pushed the expected return type as a sentinel, which after
	 * validating the actual return value should be replaced by this value.  The
	 * {@code L2ReturnInstruction return instruction} will deal with all of
	 * that.
	 */
	@Override
	public void L1Implied_doReturn ()
	{
		addInstruction(
			L2_RETURN.instance,
			new L2ReadPointerOperand(registers.fixed(CALLER)),
			new L2ReadPointerOperand(readTopOfStackRegister()));
		assert stackp == code.maxStackDepth();
		stackp = Integer.MIN_VALUE;
	}

	/**
	 * Generate a {@linkplain L2ChunkDescriptor Level Two chunk} from the
	 * already written instructions.
	 *
	 * @return The new {@linkplain L2ChunkDescriptor Level Two chunk}.
	 */
	private AvailObject createChunk ()
	{
		final L2CodeGenerator codeGen = new L2CodeGenerator();
		codeGen.setInstructions(instructions);
		codeGen.addContingentMethods(contingentMethods);
		final AvailObject chunk = codeGen.createChunkFor(code);
		return chunk;
	}

	/**
	 * Create a chunk that will perform a naive translation of the current
	 * method to Level Two.  The naïve translation creates a counter that is
	 * decremented each time the method is invoked.  When the counter reaches
	 * zero, the method will be retranslated (with deeper optimization).
	 *
	 * @return The {@linkplain L2ChunkDescriptor level two chunk} corresponding
	 *         to the {@linkplain #code} to be translated.
	 */
	public AvailObject createChunkForFirstInvocation ()
	{
		code = null;
		optimizationLevel = -1;
		interpreter = null;
		nybbles = null;

		final L2Instruction loopStart = newLabel("main L1 loop");
		final L2Instruction reenterFromCallLabel =
			newLabel("reenter L1 from call");
		addInstruction(L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance);
		addInstruction(L2_PREPARE_NEW_FRAME.instance);
		addLabel(loopStart);
		addInstruction(
			L2_INTERPRET_UNTIL_INTERRUPT.instance,
			new L2PcOperand(reenterFromCallLabel));
		addInstruction(
			L2_PROCESS_INTERRUPT.instance,
			new L2ReadPointerOperand(registers.fixed(CALLER)));
		addInstruction(L2_JUMP.instance, new L2PcOperand(loopStart));
		addLabel(reenterFromCallLabel);
		addInstruction(L2_REENTER_L1_CHUNK.instance);
		addInstruction(L2_JUMP.instance, new L2PcOperand(loopStart));
		final AvailObject newChunk = createChunk();
		assert newChunk.index() == 0;
		assert reenterFromCallLabel.offset() ==
			L2ChunkDescriptor.offsetToContinueUnoptimizedChunk();
		return newChunk;
	}

	//TODO[MvG] Remove basic performance gathering code...
	public static long generatedInstructionCount = 0;
	public static long keptInstructionCount = 0;
	public static long removedInstructionCount = 0;

	/**
	 * Optimize the stream of instructions.
	 */
	private void optimize ()
	{
		final List<L2Instruction> originals =
			new ArrayList<L2Instruction>(instructions);
		while (removeDeadInstructions())
		{
			// Do it again.
		}
		if (debugOptimized)
		{
			System.out.printf("%nOPTIMIZED: %s\n", code);
			final Set<L2Instruction> kept =
				new HashSet<L2Instruction>(instructions);
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
		simpleColorRegisters();
	}

	/**
	 * Remove any unnecessary instructions.  Answer true if any were removed.
	 *
	 * @return Whether any dead instructions were removed.
	 */
	private boolean removeDeadInstructions ()
	{
		final Map<L2Register, L2Instruction> pendingWriters =
			new HashMap<L2Register, L2Instruction>();
		final Set<L2Instruction> neededInstructions =
			new HashSet<L2Instruction>();
		// Assume (conservatively) that all labels that are targets are
		// reachable.  If an instruction that reaches the label is removed, the
		// next pass (which will happen because of the removal) will remove the
		// label and its successor instructions.
		for (final L2Instruction instruction : instructions)
		{
			neededInstructions.addAll(instruction.targetLabels());
		}
		boolean reachable = true;
		for (final L2Instruction instruction : instructions)
		{
			// Branch targets are considered reachable...
			reachable |= neededInstructions.contains(instruction);
			if (reachable)
			{
				if (instruction.hasSideEffect())
				{
					neededInstructions.add(instruction);
				}
				for (final L2Register readRegister
					: instruction.sourceRegisters())
				{
					// We just read something some earlier instruction went to
					// the trouble of producing.  Keep the earlier instruction.
					if (readRegister.finalIndex() != NULL.ordinal())
					{
						assert pendingWriters.containsKey(readRegister);
						neededInstructions.add(
							pendingWriters.get(readRegister));
					}
				}
				for (final L2Register writeRegister
					: instruction.destinationRegisters())
				{
					// Ignore any previous writes to the same register, since
					// whether the value was consumed or not was already dealt
					// with and no longer matters to me.
					pendingWriters.put(writeRegister, instruction);
				}
				neededInstructions.addAll(instruction.targetLabels());
				reachable &= instruction.operation.reachesNextInstruction();
			}
		}
		return instructions.retainAll(neededInstructions);
	}

	/**
	 * Assign register numbers to every register.  Keep it simple for now.
	 */
	private void simpleColorRegisters ()
	{
		final List<L2Register> encounteredList =
			new ArrayList<L2Register>();
		final Set<L2Register> encounteredSet =
			new HashSet<L2Register>();
		int maxId = 0;
		for (final L2Instruction instruction : instructions)
		{
			final List<L2Register> allRegisters = new ArrayList<L2Register>(
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
				public int compare (final L2Register r1, final L2Register r2)
				{
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
	 * Translate the given {@linkplain CompiledCodeDescriptor Level One
	 * CompiledCode object} into a sequence of {@linkplain L2Instruction Level
	 * Two instructions}. The optimization level specifies how hard to try to
	 * optimize this method. It is roughly equivalent to the level of inlining
	 * to attempt, or the ratio of code expansion that is permitted. An
	 * optimization level of zero is the bare minimum, which produces a naïve
	 * translation to {@linkplain L2ChunkDescriptor Level Two code}. The
	 * translation creates a counter that the Level Two code decrements each
	 * time it is invoked.  When it reaches zero, the method will be reoptimized
	 * with a higher optimization effort.
	 *
	 * @param aCompiledCodeObject
	 *            A {@linkplain CompiledCodeDescriptor Level One CompiledCode
	 *            object}.
	 * @param optLevel
	 *            The optimization level.
	 * @param anL2Interpreter
	 *            An {@link L2Interpreter}.
	 */
	public void translateOptimizationFor (
		final int optLevel,
		final @NotNull L2Interpreter anL2Interpreter)
	{
		optimizationLevel = optLevel;
		interpreter = anL2Interpreter;
		nybbles = code.nybbles();
		numArgs = code.numArgs();
		numLocals = code.numLocals();
		numSlots = code.numArgsAndLocalsAndStack();
		contingentMethods.clear();
		registers.constantAtPut(
			registers.fixed(NULL),
			NullDescriptor.nullObject());
		registers.typeAtPut(registers.fixed(FUNCTION), code.functionType());
		if (optLevel == 0)
		{
			// Optimize it again if it's called frequently enough.
			code.countdownToReoptimize(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			addInstruction(L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.instance);
		}
		final AvailObject tupleType = code.functionType().argsTupleType();
		for (int i = 1; i <= numArgs; i++)
		{
			registers.typeAtPut(
				registers.argumentOrLocal(i),
				tupleType.typeAtIndex(i));
		}
		pc = 1;
		stackp = code.maxStackDepth() + 1;
		// Just past end.  This is not the same offset it would have during
		// execution.
		final List<L2ObjectRegister> initialRegisters =
			new ArrayList<L2ObjectRegister>(FixedRegister.values().length);
		initialRegisters.add(registers.fixed(NULL));
		initialRegisters.add(registers.fixed(CALLER));
		initialRegisters.add(registers.fixed(FUNCTION));
		initialRegisters.add(registers.fixed(PRIMITIVE_FAILURE));
		for (int i = 1; i <= numArgs; i++)
		{
			final L2ObjectRegister r = registers.continuationSlot(i);
			r.setFinalIndex(firstArgumentRegisterIndex + i - 1);
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
					registers.argumentOrLocal(numArgs + local)));
		}
		final int prim = code.primitiveNumber();
		if (prim != 0)
		{
			assert !Primitive.byPrimitiveNumber(prim).hasFlag(
				CannotFail);
			// Move the primitive failure value into the first local.
			addInstruction(
				L2_SET_VARIABLE.instance,
				new L2ReadPointerOperand(
					registers.argumentOrLocal(numArgs + 1)),
				new L2ReadPointerOperand(
					registers.fixed(PRIMITIVE_FAILURE)));
		}
		for (
				int stackSlot = 1, end = code.maxStackDepth();
				stackSlot <= end;
				stackSlot++)
		{
			moveConstant(
				NullDescriptor.nullObject(),
				stackRegister(stackSlot, true));
		}
		// Now translate all the instructions.  We already wrote a label as
		// the first instruction so that L1Ext_doPushLabel can always find
		// it.  Since we only translate one method at a time, the first
		// instruction always represents the start of this compiledCode.
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

		optimize();
		final AvailObject newChunk = createChunk();
		assert code.startingChunk() == newChunk;
	}
}

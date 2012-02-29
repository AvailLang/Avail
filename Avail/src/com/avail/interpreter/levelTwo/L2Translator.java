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

package com.avail.interpreter.levelTwo;

import static com.avail.interpreter.levelTwo.L2Operation.*;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.*;
import static java.lang.Math.max;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.utility.Mutable;

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
	private List<L2Instruction> instructions;
	private List<L2ObjectRegister> architecturalRegisters;
	private int pc;
	private int stackp;
	private AvailObject code;
	private AvailObject nybbles;
	private int optimizationLevel;
	private Map<L2RegisterIdentity, AvailObject> registerTypes;
	private Map<L2RegisterIdentity, AvailObject> registerConstants;
	private Map<L2Register, List<L2Register>> registerOrigins;
	private Map<L2Register, Set<L2Register>> invertedOrigins;
	private Set<AvailObject> contingentImpSets;
	private L2Interpreter interpreter;



	public void clearRegisterConstants ()
	{
		registerConstants.clear();
		registerConstants.put(
			nullRegister().identity(),
			NullDescriptor.nullObject());
	}

	public boolean registerHasConstantAt (
		final L2Register register)
	{
		return registerConstants.containsKey(register.identity());
	}

	public AvailObject registerConstantAt (
		final L2Register register)
	{
		return registerConstants.get(register.identity());
	}

	public void registerConstantAtPut (
		final L2Register register,
		final AvailObject value)
	{
		registerConstants.put(register.identity(), value);
	}

	public void removeConstantForRegister (
		final L2Register register)
	{
		registerConstants.remove(register.identity());
	}

	public void clearRegisterTypes ()
	{
		registerTypes.clear();
	}

	public boolean registerHasTypeAt (
		final L2Register register)
	{
		return registerTypes.containsKey(register.identity());
	}

	public AvailObject registerTypeAt (
		final L2Register register)
	{
		return registerTypes.get(register.identity());
	}

	public void registerTypeAtPut (
		final L2Register register,
		final AvailObject type)
	{
		registerTypes.put(register.identity(), type);
	}

	public void removeTypeForRegister (
		final L2Register register)
	{
		registerTypes.remove(register.identity());
	}

	public void clearRegisterOrigins ()
	{
		registerOrigins.clear();
		invertedOrigins.clear();
	}

	/**
	 * The sourceRegister's value was just written to the destinationRegister.
	 * Propagate this information into the registerOrigins to allow the earliest
	 * remaining register with the same value to always be used during register
	 * source normalization.  This is essential for eliminating redundant moves.
	 *
	 * <p>
	 * Eventually primitive constructor/deconstructor pairs (e.g., tuple
	 * creation and tuple subscripting) could be combined in a similar way to
	 * perform a simple object escape analysis.  For example, consider this
	 * sequence of level two instructions:
	 * <ul>
	 * <li>r1 := ...</li>
	 * <li>r2 := ...</li>
	 * <li>r3 := makeTuple(r1, r2)</li>
	 * <li>r4 := tupleAt(r3, 1)</li>
	 * </ul>
	 * It can be shown that r4 will always contain the value that was in r1.
	 * In fact, if r3 is no longer needed then the tuple doesn't even have to be
	 * constructed at all.  While this isn't expected to be useful by itself,
	 * inlining is expected to reveal a great deal of such combinations.
	 * </p>
	 *
	 * @param sourceRegister
	 *            The {@link L2Register} which is the source of a move.
	 * @param destinationRegister
	 *            The {@link L2Register} which is the destination of a move.
	 */
	public void propagateMove (
		final L2Register sourceRegister,
		final L2Register destinationRegister)
	{
		if (sourceRegister == destinationRegister)
		{
			return;
		}
		propagateWriteTo(destinationRegister);
		final List<L2Register> sourceOrigins = registerOrigins.get(
			sourceRegister);
		final List<L2Register> destinationOrigins =
			sourceOrigins == null
				? new ArrayList<L2Register>(1)
				: new ArrayList<L2Register>(sourceOrigins);
		destinationOrigins.add(sourceRegister);
		registerOrigins.put(destinationRegister, destinationOrigins);
		for (final L2Register origin : destinationOrigins)
		{
			Set<L2Register> set = invertedOrigins.get(origin);
			if (set == null)
			{
				set = new HashSet<L2Register>();
				invertedOrigins.put(origin, set);
			}
			set.add(destinationRegister);
		}
	}

	/**
	 * Some sort of write to the destinationRegister has taken place.  Moves are
	 * handled differently.
	 *
	 * <p>
	 * Update the {@link #registerOrigins} and {@link #invertedOrigins} maps to
	 * reflect the fact that the destination register is no longer related to
	 * its earlier sources.
	 * </p>
	 *
	 * @param destinationRegister The {@link L2Register} being overwritten.
	 */
	public void propagateWriteTo (
		final L2Register destinationRegister)
	{
		final List<L2Register> origins =
			registerOrigins.get(destinationRegister);
		for (final L2Register origin : origins)
		{
			invertedOrigins.get(origin).remove(destinationRegister);
		}
		registerOrigins.remove(destinationRegister);
	}

	/**
	 * Answer a register which contains the same value as the givenRegister.
	 * Use the register which has held this value for the longest time, as this
	 * should eliminate the most redundant moves.
	 *
	 * @param givenRegister An L2Register.
	 * @return An L2Register to use instead of the givenRegister.
	 */
	public L2Register normalize (
		final L2Register givenRegister)
	{
		final List<L2Register> origins = registerOrigins.get(givenRegister);
		if (origins == null || origins.isEmpty())
		{
			// The origin of the register's value is indeterminate here.
			return givenRegister;
		}
		// Use the register that has been holding this value the longest.
		return origins.get(0);
	}


	/**
	 * Trim all type and constant information to those that are preserved in
	 * architectural registers.  These are the caller, the function, and all
	 * continuation slots.
	 */
	public void restrictPropagationInformationToArchitecturalRegisters ()
	{
		final HashSet<L2RegisterIdentity> archRegs =
			new HashSet<L2RegisterIdentity>();
		archRegs.add(callerRegister().identity());
		archRegs.add(functionRegister().identity());
		for (int i = 1; i <= code.numArgsAndLocalsAndStack(); i++)
		{
			archRegs.add(continuationSlotRegister(i).identity());
		}
		final Map<L2RegisterIdentity, AvailObject> oldRegisterTypes =
			registerTypes;
		registerTypes = new HashMap<L2RegisterIdentity, AvailObject>(
			oldRegisterTypes.size());
		for (final Map.Entry<L2RegisterIdentity, AvailObject> entry
			: oldRegisterTypes.entrySet())
		{
			if (archRegs.contains(entry.getKey()))
			{
				registerTypes.put(entry.getKey(), entry.getValue());
			}
		}

		final Map<L2RegisterIdentity, AvailObject> oldRegisterConstants =
			registerConstants;
		registerConstants = new HashMap<L2RegisterIdentity, AvailObject>(
			oldRegisterConstants.size());
		for (final Map.Entry<L2RegisterIdentity, AvailObject> entry
			: oldRegisterConstants.entrySet())
		{
			if (archRegs.contains(entry.getKey()))
			{
				registerConstants.put(entry.getKey(), entry.getValue());
			}
		}

		clearRegisterOrigins();
	}


	/**
	 * Create and add an {@link L2Instruction} with the given {@link
	 * L2Operation} and variable number of {@link L2Operand}s.
	 *
	 * @param operation The operation to invoke.
	 * @param operands The operands of the instruction.
	 */
	private void addGenericInstruction (
		final L2Operation operation,
		final L2Operand... operands)
	{
		final L2Instruction genericInstruction =
			new L2Instruction(operation, operands);
		final L2Instruction normalizedInstruction =
			genericInstruction.normalizeRegisters(this);
		instructions.add(normalizedInstruction);
		normalizedInstruction.propagateTypesFor(this);
	}

	/**
	 * Add the specified {@linkplain L2Instruction instruction} to the
	 * instruction stream.
	 *
	 * @param anL2Instruction The {@link L2Instruction} to add.
	 */
	private void addInstruction (
		final L2Instruction anL2Instruction)
	{
		anL2Instruction.normalizeRegisters(this);
		instructions.add(anL2Instruction);
		anL2Instruction.propagateTypesFor(this);
	}

	/**
	 * Answer an {@link L2ObjectRegister} representing the specified
	 * architectural register.  This is not physical machine level architectural
	 * register, but rather an abstract representation that the {@link
	 * L2Interpreter} uses at execution time.
	 *
	 * <p>
	 * The architectural registers have a fixed numbering, which include the
	 * {@link #callerRegister()} of the current block and the {@link
	 * #functionRegister()} being executed.  The {@linkplain
	 * #localOrArgumentRegister(int) arguments and locals} come next, but they
	 * are not pre-colored registers like the caller and function registers.
	 * </p>
	 *
	 * @param registerNumber
	 *            Which architectural register to produce.
	 * @return
	 *            An {@link L2ObjectRegister} corresponding to the specified
	 *            architectural register number.
	 */
	private L2ObjectRegister architecturalRegister (
		final int registerNumber)
	{
		while (registerNumber >= architecturalRegisters.size())
		{
			final L2ObjectRegister newRegister = new L2ObjectRegister();
			newRegister.identity().setFinalIndex(architecturalRegisters.size());
			architecturalRegisters.add(newRegister);
		}
		return new L2ObjectRegister(
			architecturalRegisters.get(registerNumber));
	}

	/**
	 * Answer the read-only register reserved for holding the {@linkplain
	 * NullDescriptor#nullObject() null object}.
	 *
	 * @return
	 *            An {@link L2ObjectRegister} that is used exclusively to hold
	 *            the null object.
	 */
	private L2ObjectRegister nullRegister ()
	{
		return architecturalRegister(0);
	}

	/**
	 * Answer the register reserved for holding the current context's calling
	 * context.
	 *
	 * @return
	 *            An {@link L2ObjectRegister} that is used exclusively to hold
	 *            the current context's caller.
	 */
	private L2ObjectRegister callerRegister ()
	{
		return architecturalRegister(1);
	}

	/**
	 * Answer the register reserved for holding the current context's function.
	 *
	 * @return
	 *            The {@link L2ObjectRegister} that holds the current context's
	 *            function.
	 */
	private L2ObjectRegister functionRegister ()
	{
		return architecturalRegister(2);
	}

	/**
	 * Answer the register holding the specified continuation slot.  The slots
	 * are the arguments, then the locals, then the stack entries.  The first
	 * argument is in the 3rd architectural register.
	 *
	 * @param slotNumber
	 *            The index into the continuation's slots.
	 * @return
	 *            A register representing that continuation slot.
	 */
	private L2ObjectRegister continuationSlotRegister (
		final int slotNumber)
	{
		return architecturalRegister(2 + slotNumber);
	}

	/**
	 * Return the {@linkplain CompiledCodeDescriptor compiled Level One code}
	 * being translated.
	 *
	 * @return
	 *            The code being translated.
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
	 * @param objectRegisters
	 *            The list of object registers to aggregate.
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
	private L2ObjectRegister localOrArgumentRegister (
		final int argumentNumber)
	{
		return continuationSlotRegister(argumentNumber);
	}

	/**
	 * Create a new {@linkplain L2Operation#L2_doLabel label
	 * pseudo-instruction}.
	 *
	 * @return The new label.
	 */
	private L2Instruction newLabel ()
	{
		return new L2Instruction(L2_doLabel);
	}

	/**
	 * Allocate a fresh {@linkplain L2ObjectRegister object register} that
	 * nobody else has used yet.
	 *
	 * @return The new register.
	 */
	private L2ObjectRegister newRegister ()
	{
		return new L2ObjectRegister();
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
	 * @param args A {@link List} of {@linkplain L2ObjectRegister registers} holding
	 *             the actual constant values used to look up the implementation
	 *             for the call.
	 * @return A method body (a {@code FunctionDescriptor function}) that
	 *         exemplifies the primitive that should be inlined.
	 */
	private AvailObject primitiveToInlineForArgumentRegisters (
		final AvailObject impSet,
		final List<L2ObjectRegister> args)
	{
		final List<AvailObject> argTypes = new ArrayList<AvailObject>(args.size());
		for (final L2ObjectRegister arg : args)
		{
			AvailObject type;
			type = registerHasTypeAt(arg) ? registerTypeAt(arg) : ANY.o();
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
			type = registerHasConstantAt(argTypeRegister)
				? registerConstantAt(argTypeRegister)
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
		final List<AvailObject> imps = impSet.implementationsAtOrBelow(argTypes);
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
					Primitive.Flag.SpecialReturnConstant))
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
	 * @return A {@linkplain L2ObjectRegister register} representing the stack at the
	 *         given position.
	 */
	private L2ObjectRegister stackRegister (
		final int stackIndex)
	{
		assert 1 <= stackIndex && stackIndex <= code.maxStackDepth();
		return continuationSlotRegister(
			code.numArgs()
			+ code.numLocals()
			+ stackIndex);
	}


	/**
	 * Answer the register representing the slot of the stack associated with
	 * the current value of stackp.
	 *
	 * @return A {@linkplain L2ObjectRegister register} representing the top of the
	 *         stack right now.
	 */
	private L2ObjectRegister topOfStackRegister ()
	{
		assert 1 <= stackp && stackp <= code.maxStackDepth();
		return stackRegister(stackp);
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
		final AvailObject expectedType,
		final L2ObjectRegister failureValueRegister,
		final L2Instruction successLabel,
		final Mutable<Boolean> canFailPrimitive)
	{
		final int primitiveNumber = primitiveFunction.code().primitiveNumber();
		final Primitive primitive =
			Primitive.byPrimitiveNumber(primitiveNumber);
		contingentImpSets.add(impSet);
		if (primitive.hasFlag(SpecialReturnConstant))
		{
			// Use the first literal as the return value.
			final AvailObject value = primitiveFunction.code().literalAt(1);
			// Restriction might be too strong even on a constant method.
			if (value.isInstanceOf(expectedType))
			{
				addGenericInstruction(
					L2_doMoveFromConstant_destObject_,
					new L2ConstantOperand(value),
					new L2WritePointerOperand(topOfStackRegister()));
				canFailPrimitive.value = false;
				return value;
			}
		}
		if (primitive.hasFlag(SpecialReturnSoleArgument))
		{
			// Use the only argument as the return value.
			assert primitiveFunction.code().numArgs() == 1;
			assert args.size() == 1;
			final L2ObjectRegister arg = args.get(0);
			if (registerHasConstantAt(arg))
			{
				final AvailObject constant = registerConstantAt(arg);
				// Restriction might be too strong even on such a simple method.
				if (constant.isInstanceOf(expectedType))
				{
					// Actually fold it.
					canFailPrimitive.value = false;
					return constant;
				}
				// The restriction is definitely too strong.  Fall through.
			}
			else if (registerHasTypeAt(arg))
			{
				final AvailObject actualType = registerTypeAt(arg);
				if (actualType.isSubtypeOf(expectedType))
				{
					// It will always conform to the expected type.  Inline it.
					addGenericInstruction(
						L2_doMoveFromObject_destObject_,
						new L2ReadPointerOperand(arg),
						new L2WritePointerOperand(topOfStackRegister()));
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
			if (!registerHasConstantAt(arg))
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
				argValues.add(registerConstantAt(argReg));
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
					addGenericInstruction(
						L2_doMoveFromConstant_destObject_,
						new L2ConstantOperand(value),
						new L2WritePointerOperand(topOfStackRegister()));
					canFailPrimitive.value = false;
					return value;
				}
			}
			assert success != CONTINUATION_CHANGED
			: "This foldable primitive changed the continuation!";
		}
		if (primitive.hasFlag(CannotFail))
		{
			addGenericInstruction(
				L2_doNoFailPrimitive_withArguments_result_,
				new L2PrimitiveOperand(primitive),
				new L2ReadVectorOperand(createVector(args)),
				new L2WritePointerOperand(topOfStackRegister()));
			canFailPrimitive.value = false;
			return null;
		}
		addGenericInstruction(
			L2_doAttemptPrimitive_arguments_result_failure_ifSuccess_,
			new L2PrimitiveOperand(primitive),
			new L2ReadVectorOperand(createVector(args)),
			new L2WritePointerOperand(topOfStackRegister()),
			new L2WritePointerOperand(failureValueRegister),
			new L2PcOperand(successLabel));
		canFailPrimitive.value = true;
		return null;
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
		final int numSlots = code.numArgsAndLocalsAndStack();
		final int numArgsAndLocals = code.numArgs() + code.numLocals();
		final List<L2ObjectRegister> preSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		final List<L2ObjectRegister> postSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
		{
			final L2ObjectRegister register =
				continuationSlotRegister(slotIndex);
			preSlots.add(register);
			postSlots.add(register);
		}
		final L2ObjectRegister expectedTypeReg = newRegister();
		final L2ObjectRegister failureObjectReg = newRegister();
		final int nArgs = impSet.numArgs();
		final List<L2ObjectRegister> args =
			new ArrayList<L2ObjectRegister>(nArgs);
		for (int i = nArgs; i >= 1; i--)
		{
			args.add(0, topOfStackRegister());
			preSlots.set(
				numArgsAndLocals + stackp - 1,
				nullRegister());
			stackp++;
		}
		stackp--;
		preSlots.set(
			numArgsAndLocals + stackp - 1,
			expectedTypeReg);
		final L2Instruction postExplodeLabel = newLabel();
		final AvailObject primFunction = primitiveToInlineForArgumentRegisters(
			impSet,
			args);
		if (primFunction != null)
		{
			// Inline the primitive.  Attempt to fold it if the primitive says
			// it's foldable and the arguments are all constants.
			final Mutable<Boolean> canFailPrimitive = new Mutable<Boolean>();
			final AvailObject folded = emitInlinePrimitiveAttempt(
				primFunction,
				impSet,
				args,
				expectedType,
				failureObjectReg,
				postExplodeLabel,
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
		addGenericInstruction(
			L2_doMoveFromConstant_destObject_,
			new L2ConstantOperand(expectedType),
			new L2WritePointerOperand(expectedTypeReg));
		final List<AvailObject> savedSlotTypes =
			new ArrayList<AvailObject>(numSlots);
		final List<AvailObject> savedSlotConstants =
			new ArrayList<AvailObject>(numSlots);
		for (final L2ObjectRegister reg : preSlots)
		{
			savedSlotTypes.add(registerTypeAt(reg));
			savedSlotConstants.add(registerConstantAt(reg));
		}
		final L2Instruction postCallLabel = newLabel();
		addGenericInstruction(
			L2_doCreateContinuationSender_function_pc_stackp_size_slots_offset_dest_,
			new L2ReadPointerOperand(callerRegister()),
			new L2ReadPointerOperand(functionRegister()),
			new L2ImmediateOperand(pc),
			new L2ImmediateOperand(stackp),
			new L2ImmediateOperand(numSlots),
			new L2ReadVectorOperand(createVector(preSlots)),
			new L2PcOperand(postCallLabel),
			new L2WritePointerOperand(callerRegister()));
		if (primFunction != null)
		{
			addGenericInstruction(
				L2_doSendAfterFailedPrimitive_arguments_failureValue_,
				new L2SelectorOperand(impSet),
				new L2ReadVectorOperand(createVector(args)),
				new L2ReadPointerOperand(failureObjectReg));
		}
		else
		{
			addGenericInstruction(
				L2_doSend_argumentsVector_,
				new L2SelectorOperand(impSet),
				new L2ReadVectorOperand(createVector(args)));
		}
		// The method being invoked will run until it returns, and the next
		// instruction will be here (if the chunk isn't invalidated in the
		// meanwhile).
		addInstruction(postCallLabel);
		// And after the call returns, the callerRegister will contain the
		// continuation to be exploded.
		for (int i = 0; i < postSlots.size(); i++)
		{
			final AvailObject type = savedSlotTypes.get(i);
			if (type != null)
			{
				registerTypeAtPut(postSlots.get(i), type);
			}
			final AvailObject constant = savedSlotConstants.get(i);
			if (constant != null)
			{
				registerConstantAtPut(postSlots.get(i), constant);
			}
		}
		// At this point the implied return instruction in the called code has
		// verified the value matched the expected type, so we know that much
		// has to be true.
		removeConstantForRegister(topOfStackRegister());
		registerTypeAtPut(topOfStackRegister(), expectedType);
		addInstruction(postExplodeLabel);
	}


	@Override
	public void L1_doClose ()
	{
		//  [n,m] - Pop the top n items off the stack, and use them as outer variables in the
		//  construction of a function based on the compiledCode that's the literal at index m
		//  of the current compiledCode.

		final int count = getInteger();
		final AvailObject codeLiteral = code.literalAt(getInteger());
		final List<L2ObjectRegister> outers = new ArrayList<L2ObjectRegister>(count);
		for (int i = count; i >= 1; i--)
		{
			outers.add(0, topOfStackRegister());
			stackp++;
		}
		stackp--;
		addGenericInstruction(
			L2_doCreateFunctionFromCodeObject_outersVector_destObject_,
			new L2ConstantOperand(codeLiteral),
			new L2ReadVectorOperand(createVector(outers)),
			new L2WritePointerOperand(topOfStackRegister()));

		// Now that the function has been constructed, clear the slots that
		// were used for outer values (except the destination slot, which is
		// being overwritten with the resulting function anyhow).
		for (
			int stackIndex = stackp + 1 - count;
			stackIndex <= stackp - 1;
			stackIndex++)
		{
			addGenericInstruction(
				L2_doMoveFromObject_destObject_,
				new L2ReadPointerOperand(nullRegister()),
				new L2WritePointerOperand(stackRegister(stackIndex)));
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
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).

		final int index = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doGetVariable_destObject_,
			new L2ReadPointerOperand(localOrArgumentRegister(index)),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).

		final int index = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doGetVariableClearing_destObject_,
			new L2ReadPointerOperand(localOrArgumentRegister(index)),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doGetOuter ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current function.

		final int outerIndex = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doMoveFromOuterVariable_ofFunctionObject_destObject_,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(functionRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doGetVariable_destObject_,
			new L2ReadPointerOperand(topOfStackRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current function.
		//  If the variable itself is mutable, clear it at this time - nobody will know.
		//  Actually, right now we don't optimize this in level two, for simplicity.

		final int outerIndex = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doMoveFromOuterVariable_ofFunctionObject_destObject_,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(functionRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doGetVariableClearing_destObject_,
			new L2ReadPointerOperand(topOfStackRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = getInteger();
		final List<L2ObjectRegister> vector = new ArrayList<L2ObjectRegister>(count);
		for (int i = 1; i <= count; i++)
		{
			vector.add(stackRegister(stackp + count - i));
		}
		stackp += count - 1;
		addGenericInstruction(
			L2_doCreateTupleFromValues_destObject_,
			new L2ReadVectorOperand(createVector(vector)),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doPop ()
	{
		//  Remove the top item from the stack.

		assert stackp == code.maxStackDepth()
		: "Pop should only only occur at end of statement";
		addGenericInstruction(
			L2_doMoveFromObject_destObject_,
			new L2ReadPointerOperand(nullRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
		stackp++;
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.
		//  Since this is known to be the last use (nondebugger) of the argument or local, clear that
		//  slot of the current continuation.

		final int localIndex = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doMoveFromObject_destObject_,
			new L2ReadPointerOperand(localOrArgumentRegister(localIndex)),
			new L2WritePointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doMoveFromObject_destObject_,
			new L2ReadPointerOperand(nullRegister()),
			new L2WritePointerOperand(localOrArgumentRegister(localIndex)));
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		//  [n] - Push the outer variable indexed by n in the current function.  If the variable is
		//  mutable, clear it (no one will know).  If the variable and function are both mutable,
		//  remove the variable from the function by clearing it.

		final int outerIndex = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doMoveFromOuterVariable_ofFunctionObject_destObject_,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(functionRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doMakeImmutableObject_,
			new L2ReadPointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doPushLiteral ()
	{
		//  [n] - Push the literal indexed by n in the current compiledCode.

		final AvailObject constant = code.literalAt(getInteger());
		stackp--;
		addGenericInstruction(
			L2_doMoveFromConstant_destObject_,
			new L2ConstantOperand(constant),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doPushLocal ()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.

		final int localIndex = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doMoveFromObject_destObject_,
			new L2ReadPointerOperand(localOrArgumentRegister(localIndex)),
			new L2WritePointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doMakeImmutableObject_,
			new L2ReadPointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doPushOuter ()
	{
		//  [n] - Push the outer variable indexed by n in the current function.

		final int outerIndex = getInteger();
		stackp--;
		addGenericInstruction(
			L2_doMoveFromOuterVariable_ofFunctionObject_destObject_,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(functionRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doMakeImmutableObject_,
			new L2ReadPointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1_doSetLocal ()
	{
		//  [n] - Pop the stack and assign this value to the local variable (not an argument) indexed by n (index 1 is first argument).

		final int localIndex = getInteger();
		final L2ObjectRegister local = localOrArgumentRegister(localIndex);
		addGenericInstruction(
			L2_doSetVariable_sourceObject_,
			new L2ReadPointerOperand(local),
			new L2ReadPointerOperand(topOfStackRegister()));
		stackp++;
	}

	@Override
	public void L1_doSetOuter ()
	{
		//  [n] - Pop the stack and assign this value to the outer variable indexed by n in the current function.

		final int outerIndex = getInteger();
		final L2ObjectRegister tempReg = newRegister();
		addGenericInstruction(
			L2_doMakeImmutableObject_,
			new L2ReadPointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doMoveFromOuterVariable_ofFunctionObject_destObject_,
			new L2ImmediateOperand(outerIndex),
			new L2ReadPointerOperand(functionRegister()),
			new L2WritePointerOperand(tempReg));
		addGenericInstruction(
			L2_doSetVariable_sourceObject_,
			new L2ReadPointerOperand(tempReg),
			new L2ReadPointerOperand(topOfStackRegister()));
		stackp++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final L2ObjectRegister originalTopOfStack = topOfStackRegister();
		addGenericInstruction(
			L2_doMakeImmutableObject_,
			new L2ReadPointerOperand(originalTopOfStack));
		stackp--;
		addGenericInstruction(
			L2_doMoveFromObject_destObject_,
			new L2ReadPointerOperand(originalTopOfStack),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		//  [n] - Push the value of the variable that's literal number n in the current compiledCode.

		final AvailObject constant = code.literalAt(getInteger());
		stackp--;
		addGenericInstruction(
			L2_doMoveFromConstant_destObject_,
			new L2ConstantOperand(constant),
			new L2WritePointerOperand(topOfStackRegister()));
		addGenericInstruction(
			L2_doGetVariable_destObject_,
			new L2ReadPointerOperand(topOfStackRegister()),
			new L2WritePointerOperand(topOfStackRegister()));
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
		addGenericInstruction(
			L2_doGetType_destObject_,
			new L2ReadPointerOperand(stackRegister(stackp + 1 + index)),
			new L2WritePointerOperand(topOfStackRegister()));
	}

	/**
	 * Build a continuation which, when restarted, will be just like restarting
	 * the current continuation.
	 */
	@Override
	public void L1Ext_doPushLabel ()
	{
		stackp--;
		final L2ObjectRegister destReg = topOfStackRegister();
		final L2Instruction startLabel = newLabel();
		instructions.add(0, startLabel);
		final int numSlots = code.numArgsAndLocalsAndStack();
		final List<L2ObjectRegister> vector =
			new ArrayList<L2ObjectRegister>(numSlots);
		final List<L2ObjectRegister> vectorWithOnlyArgsPreserved =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int i = 1; i <= numSlots; i++)
		{
			vector.add(continuationSlotRegister(i));
			vectorWithOnlyArgsPreserved.add(
				i <= code.numArgs()
					? continuationSlotRegister(i)
					: nullRegister());
		}
		addGenericInstruction(
			L2_doCreateContinuationSender_function_pc_stackp_size_slots_offset_dest_,
			new L2ReadPointerOperand(callerRegister()),
			new L2ReadPointerOperand(functionRegister()),
			new L2ImmediateOperand(1),
			new L2ImmediateOperand(code.maxStackDepth() + 1),
			new L2ImmediateOperand(numSlots),
			new L2ReadVectorOperand(createVector(vectorWithOnlyArgsPreserved)),
			new L2PcOperand(startLabel),
			new L2WritePointerOperand(destReg));

		// Freeze all fields of the new object, including its caller, function,
		// and arguments.
		addGenericInstruction(
			L2_doMakeSubobjectsImmutableInObject_,
			new L2ReadPointerOperand(destReg));
	}

	@Override
	public void L1Ext_doReserved ()
	{
		//  This shouldn't happen unless the compiler is out of sync with the translator.

		error("That nybblecode is not supported");
		return;
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		//  [n] - Pop the stack and assign this value to the variable that's the literal
		//  indexed by n in the current compiledCode.

		final AvailObject constant = code.literalAt(getInteger());
		final L2ObjectRegister tempReg = newRegister();
		addGenericInstruction(
			L2_doMoveFromConstant_destObject_,
			new L2ConstantOperand(constant),
			new L2WritePointerOperand(tempReg));
		addGenericInstruction(
			L2_doSetVariable_sourceObject_,
			new L2ReadPointerOperand(tempReg),
			new L2ReadPointerOperand(topOfStackRegister()));
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
		final AvailObject impSet = code.literalAt(getInteger());
		final AvailObject expectedType = code.literalAt(getInteger());
		final int numSlots = code.numArgsAndLocalsAndStack();
		final int numArgsAndLocals = code.numArgs() + code.numLocals();
		final List<L2ObjectRegister> preSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		final List<L2ObjectRegister> postSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
		{
			final L2ObjectRegister register =
				continuationSlotRegister(slotIndex);
			preSlots.add(register);
			postSlots.add(register);
		}
		final L2ObjectRegister expectedTypeReg = newRegister();
		final L2ObjectRegister failureObjectReg = newRegister();
		final int nArgs = impSet.numArgs();
		final List<L2ObjectRegister> argTypes =
			new ArrayList<L2ObjectRegister>(nArgs);
		for (int i = nArgs; i >= 1; i--)
		{
			argTypes.add(0, topOfStackRegister());
			preSlots.set(
				numArgsAndLocals + stackp - 1,
				nullRegister());
			stackp++;
		}
		final List<L2ObjectRegister> args =
			new ArrayList<L2ObjectRegister>(nArgs);
		for (int i = nArgs; i >= 1; i--)
		{
			args.add(0, topOfStackRegister());
			preSlots.set(
				numArgsAndLocals + stackp - 1,
				nullRegister());
			stackp++;
		}
		stackp--;
		preSlots.set(
			numArgsAndLocals + stackp - 1,
			expectedTypeReg);
		final L2Instruction postExplodeLabel = newLabel();
		final AvailObject primFunction =
			primitiveToInlineForArgumentTypeRegisters(
				impSet,
				argTypes);
		final Mutable<Boolean> canFailPrimitive = new Mutable<Boolean>();
		if (primFunction != null)
		{
			// Inline the primitive.  Attempt to fold it if the primitive says
			// it's foldable and the arguments are all constants.
			final AvailObject folded = emitInlinePrimitiveAttempt(
				primFunction,
				impSet,
				args,
				expectedType,
				failureObjectReg,
				postExplodeLabel,
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
		addGenericInstruction(
			L2_doMoveFromConstant_destObject_,
			new L2ConstantOperand(expectedType),
			new L2WritePointerOperand(expectedTypeReg));
		final List<AvailObject> savedSlotTypes =
			new ArrayList<AvailObject>(numSlots);
		final List<AvailObject> savedSlotConstants =
			new ArrayList<AvailObject>(numSlots);
		for (final L2ObjectRegister reg : preSlots)
		{
			savedSlotTypes.add(registerTypeAt(reg));
			savedSlotConstants.add(registerConstantAt(reg));
		}
		final L2Instruction postCallLabel = newLabel();
		addGenericInstruction(
			L2_doCreateContinuationSender_function_pc_stackp_size_slots_offset_dest_,
			new L2ReadPointerOperand(callerRegister()),
			new L2ReadPointerOperand(functionRegister()),
			new L2ImmediateOperand(pc),
			new L2ImmediateOperand(stackp),
			new L2ImmediateOperand(numSlots),
			new L2ReadVectorOperand(createVector(preSlots)),
			new L2PcOperand(postCallLabel),
			new L2WritePointerOperand(callerRegister()));
		if (primFunction != null)
		{
			addGenericInstruction(
				L2_doSendAfterFailedPrimitive_arguments_failureValue_,
				new L2SelectorOperand(impSet),
				new L2ReadVectorOperand(createVector(args)),
				new L2ReadPointerOperand(failureObjectReg));
		}
		else
		{
			addGenericInstruction(
				L2_doSuperSend_argumentsVector_argumentTypesVector_,
				new L2SelectorOperand(impSet),
				new L2ReadVectorOperand(createVector(args)),
				new L2ReadVectorOperand(createVector(argTypes)));
		}
		// The method being invoked will run until it returns, and the next
		// instruction will be here.
		addInstruction(postCallLabel);
		// And after the call returns, the callerRegister will contain the
		// continuation to be exploded.
		for (int i = 0; i < postSlots.size(); i++)
		{
			final AvailObject type = savedSlotTypes.get(i);
			if (type != null)
			{
				registerTypeAtPut(postSlots.get(i), type);
			}
			final AvailObject constant = savedSlotConstants.get(i);
			if (constant != null)
			{
				registerConstantAtPut(postSlots.get(i), constant);
			}
		}
		// At this point the implied return instruction in the called code has
		// verified the value matched the expected type, so we know that much
		// has to be true.
		removeConstantForRegister(topOfStackRegister());
		registerTypeAtPut(topOfStackRegister(), expectedType);
		addInstruction(postExplodeLabel);
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
		addGenericInstruction(
			L2_doReturnToContinuationObject_valueObject_,
			new L2ReadPointerOperand(callerRegister()),
			new L2ReadPointerOperand(topOfStackRegister()));
		assert stackp == code.maxStackDepth();
		stackp = -666;
	}

	/**
	 * Generate a {@linkplain L2ChunkDescriptor Level Two chunk} from the already
	 * written instructions.
	 *
	 * @return The new {@linkplain L2ChunkDescriptor Level Two chunk}.
	 */
	private AvailObject createChunk ()
	{
		final L2CodeGenerator codeGen = new L2CodeGenerator();
		codeGen.setInstructions(instructions);
		codeGen.addContingentMethods(contingentImpSets);
		final AvailObject chunk = codeGen.createChunkFor(code);
		return chunk;
	}

	/**
	 * Create a chunk that will perform a naive translation of the current
	 * method to Level Two.  The naive translation creates a counter that is
	 * decremented each time the method is invoked.  When the counter reaches
	 * zero, the method will be retranslated (with deeper optimization).
	 *
	 * @return The {@linkplain L2ChunkDescriptor level two chunk} corresponding
	 *         to the {@linkplain #code} to be translated.
	 */
	public AvailObject createChunkForFirstInvocation ()
	{
		instructions = new ArrayList<L2Instruction>(10);
		architecturalRegisters = new ArrayList<L2ObjectRegister>(10);
		registerTypes = new HashMap<L2RegisterIdentity, AvailObject>(10);
		registerConstants = new HashMap<L2RegisterIdentity, AvailObject>(10);
		registerOrigins = new HashMap<L2Register, List<L2Register>>();
		invertedOrigins = new HashMap<L2Register, Set<L2Register>>();
		clearRegisterTypes();
		clearRegisterConstants();
		clearRegisterOrigins();
		code = null;
		nybbles = null;

		contingentImpSets = new HashSet<AvailObject>();
		final L2Instruction loopStart = newLabel();
		addGenericInstruction(L2_doDecrementCounterAndReoptimizeOnZero);
		addGenericInstruction(L2_doPrepareNewFrame);
		addInstruction(loopStart);
		addGenericInstruction(
			L2_doInterpretOneInstructionAndBranchBackIfNoInterrupt);
		addGenericInstruction(
			L2_doProcessInterruptNowWithContinuationObject_,
			new L2ReadPointerOperand(callerRegister()));
		addGenericInstruction(
			L2Operation.L2_doJump_,
			new L2PcOperand(loopStart));
		final AvailObject newChunk = createChunk();
		assert newChunk.index() == 0;
		assert loopStart.offset() ==
			L2ChunkDescriptor.offsetToContinueUnoptimizedChunk();
		return newChunk;
	}

	/**
	 * Optimize the stream of instructions.
	 */
	private void optimize ()
	{
		simpleColorRegisters();
	}

	/**
	 * Assign register numbers to every register.  Keep it simple for now.
	 */
	private void simpleColorRegisters ()
	{
		final Set<L2RegisterIdentity> identities =
			new HashSet<L2RegisterIdentity>();
		for (final L2Instruction instruction : instructions)
		{
			for (final L2Register reg : instruction.sourceRegisters())
			{
				identities.add(reg.identity());
			}
			for (final L2Register reg : instruction.destinationRegisters())
			{
				identities.add(reg.identity());
			}
		}
		int maxId = 0;
		for (final L2RegisterIdentity identity : identities)
		{
			if (identity.finalIndex() != -1)
			{
				maxId = max(maxId, identity.finalIndex());
			}
		}
		for (final L2RegisterIdentity identity : identities)
		{
			if (identity.finalIndex() == - 1)
			{
				identity.setFinalIndex(++maxId);
			}
		}
	}

	/**
	 * Translate the given {@linkplain CompiledCodeDescriptor Level One
	 * CompiledCode object} into a sequence of {@linkplain L2Instruction Level
	 * Two instructions}. The optimization level specifies how hard to try to
	 * optimize this method. It is roughly equivalent to the level of inlining
	 * to attempt, or the ratio of code expansion that is permitted. An
	 * optimization level of zero is the bare minimum, which produces a naive
	 * translation to {@linkplain L2ChunkDescriptor Level Two code}. The
	 * translation creates a counter that the Level Two code decrements each
	 * time it is invoked.  When it reaches zero, the method will be reoptimized
	 * with a higher optimization level.
	 *
	 * @param aCompiledCodeObject A {@linkplain CompiledCodeDescriptor Level One
	 *                            CompiledCode object}.
	 * @param optLevel The optimization level.
	 * @param anL2Interpreter An {@link L2Interpreter}.
	 */
	void translateOptimizationFor (
		final @NotNull AvailObject aCompiledCodeObject,
		final int optLevel,
		final @NotNull L2Interpreter anL2Interpreter)
	{
		interpreter = anL2Interpreter;
		instructions = new ArrayList<L2Instruction>(10);
		architecturalRegisters = new ArrayList<L2ObjectRegister>(10);
		registerTypes = new HashMap<L2RegisterIdentity, AvailObject>(10);
		registerConstants = new HashMap<L2RegisterIdentity, AvailObject>(10);
		registerOrigins = new HashMap<L2Register, List<L2Register>>();
		invertedOrigins = new HashMap<L2Register, Set<L2Register>>();
		clearRegisterTypes();
		clearRegisterConstants();
		clearRegisterOrigins();

		code = aCompiledCodeObject;
		optimizationLevel = optLevel;
		final AvailObject type = code.functionType();
		final AvailObject tupleType = type.argsTupleType();
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			registerTypeAtPut(
				localOrArgumentRegister(i),
				tupleType.typeAtIndex(i));
		}
		nybbles = code.nybbles();
		pc = 1;
		stackp = code.maxStackDepth() + 1;
		// Just past end.  This is not the same offset it would have during
		// execution.
		contingentImpSets = new HashSet<AvailObject>();
		// The first instruction is a label that L1Ext_doPushLabel can always
		// find at the start of the list of instructions.
		addInstruction(newLabel());
		if (optLevel == 0)
		{
			code.invocationCount(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			addGenericInstruction(L2_doDecrementCounterAndReoptimizeOnZero);
		}
		for (int local = 1, end = code.numLocals(); local <= end; local++)
		{
			addGenericInstruction(
				L2_doCreateVariableTypeConstant_destObject_,
				new L2ConstantOperand(code.localTypeAt(local)),
				new L2WritePointerOperand(
					localOrArgumentRegister(code.numArgs() + local)));
		}
		for (
				int stackSlot = 1, end = code.maxStackDepth();
				stackSlot <= end;
				stackSlot++)
		{
			addGenericInstruction(
				L2_doMoveFromObject_destObject_,
				new L2ReadPointerOperand(nullRegister()),
				new L2WritePointerOperand(stackRegister(stackSlot)));
		}
		// Now translate all the instructions.  We already wrote a label as the
		// first instruction so that L1Ext_doPushLabel can always find it.
		// Since we only translate one method at a time, the first instruction
		// always represents the start of this compiledCode.
		while (pc <= nybbles.tupleSize())
		{
			final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.values()[nybble].dispatch(this);
		}
		// Translate the implicit L1_doReturn instruction that terminates the
		// instruction sequence.
		L1Operation.L1Implied_Return.dispatch(this);
		assert pc == nybbles.tupleSize() + 1;
		assert stackp == -666;
		optimize();
		final AvailObject newChunk = createChunk();
		assert code.startingChunk() == newChunk;
	}
}

/**
 * interpreter/levelTwo/L2Translator.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.VOID_TYPE;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.*;
import static java.lang.Math.max;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.instruction.*;
import com.avail.interpreter.levelTwo.register.*;

public class L2Translator implements L1OperationDispatcher
{
	List<L2Instruction> instructions;
	List<L2ObjectRegister> architecturalRegisters;
	List<L2RegisterVector> vectors;
	int pc;
	int stackp;
	int stackDepth;
	AvailObject code;
	AvailObject nybbles;
	int optimizationLevel;
	Map<L2RegisterIdentity, AvailObject> registerTypes;
	Map<L2RegisterIdentity, AvailObject> registerConstants;
	AvailObject contingentImpSets;
	L2Interpreter interpreter;


	// accessing-propagation

	public void clearRegisterConstants ()
	{
		registerConstants.clear();
	}

	public void clearRegisterTypes ()
	{
		registerTypes.clear();
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

	public boolean registerHasConstantAt (
		final L2Register register)
	{
		return registerConstants.containsKey(register.identity());
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

	public void removeConstantForRegister (
		final L2Register register)
	{
		registerConstants.remove(register.identity());
	}

	public void removeTypeForRegister (
		final L2Register register)
	{
		registerTypes.remove(register.identity());
	}

	public void restrictPropagationInformationToArchitecturalRegisters ()
	{
		//  Trim all type and constant information to those that are preserved in architectural registers.
		//  These are the caller, the closure, and all continuation slots.

		HashSet<L2RegisterIdentity> archRegs = new HashSet<L2RegisterIdentity>();
		archRegs.add(callerRegister().identity());
		archRegs.add(closureRegister().identity());
		for (int i = 1; i <= code.numArgsAndLocalsAndStack(); i++)
		{
			archRegs.add(continuationSlotRegister(i).identity());
		}
		Map<L2RegisterIdentity, AvailObject> oldRegisterTypes = registerTypes;
		registerTypes = new HashMap<L2RegisterIdentity, AvailObject>(oldRegisterTypes.size());
		for (Map.Entry<L2RegisterIdentity, AvailObject> entry : oldRegisterTypes.entrySet())
		{
			if (archRegs.contains(entry.getKey()))
			{
				registerTypes.put(entry.getKey(), entry.getValue());
			}
		}

		Map<L2RegisterIdentity, AvailObject> oldRegisterConstants = registerConstants;
		registerConstants = new HashMap<L2RegisterIdentity, AvailObject>(oldRegisterConstants.size());
		for (Map.Entry<L2RegisterIdentity, AvailObject> entry : oldRegisterConstants.entrySet())
		{
			if (archRegs.contains(entry.getKey()))
			{
				registerConstants.put(entry.getKey(), entry.getValue());
			}
		}
	}



	// private-helper

	void addInstruction (
		final L2Instruction anL2Instruction)
	{
		instructions.add(anL2Instruction);
		anL2Instruction.propagateTypeInfoFor(this);
	}

	L2ObjectRegister architecturalRegister (
		final int n)
	{
		//  Answer the nth architectural register.  These are registers with a fixed numbering.  Include the
		//  caller, the current closure, and the arguments.  Also include the locals and stack slots, but these
		//  won't be precolored.

		while (n >= architecturalRegisters.size())
		{
			architecturalRegisters.add(new L2ObjectRegister());
			architecturalRegisters.get(architecturalRegisters.size() - 1).identity().finalIndex(
				architecturalRegisters.size());
		}

		try
		{
			return architecturalRegisters.get(n - 1).clone();
		}
		catch (CloneNotSupportedException e)
		{
			error("Can't clone a register for some reason.");
			return null;
		}
	}

	L2ObjectRegister callerRegister ()
	{
		//  Answer the register holding the current context's caller context.

		return architecturalRegister(1);
	}

	L2ObjectRegister closureRegister ()
	{
		//  Answer the register holding the current context's closure.

		return architecturalRegister(2);
	}

	public AvailObject code ()
	{
		return code;
	}

	AvailObject contingentImpSets ()
	{
		return contingentImpSets;
	}

	L2ObjectRegister continuationSlotRegister (
		final int slotNumber)
	{
		//  Answer the register holding the specified continuation slot.  The slots are the arguments, then the locals,
		//  then the stack entries.  The first argument is in the 3rd architectural register.

		return architecturalRegister(2 + slotNumber);
	}

	L2RegisterVector createVector (
		final List<L2ObjectRegister> arrayOfRegisters)
	{
		//  Answer a vector of register numbers, registering a new one if necessary.


		final L2RegisterVector vector = new L2RegisterVector(arrayOfRegisters);
		return vector;
	}

	int getInteger ()
	{
		//  Answer an integer extracted at the current program counter.  The program
		//  counter will be adjusted to skip over the integer.

		final int tag = nybbles.extractNybbleFromTupleAt(pc);
		int value;
		if (tag < 10)
		{
			value = tag;
			pc++;
			return value;
		}
		if (tag <= 12)
		{
			value = tag * 16 - 150 + nybbles.extractNybbleFromTupleAt(pc + 1);
			pc += 2;
			return value;
		}
		if (tag == 13)
		{
			value = (nybbles.extractNybbleFromTupleAt(pc + 1) << 4) + nybbles.extractNybbleFromTupleAt(pc + 2) + 58;
			pc += 3;
			return value;
		}
		if (tag == 14)
		{
			value = 0;
			for (int _count1 = 1; _count1 <= 4; _count1++)
			{
				value = (value << 4) + nybbles.extractNybbleFromTupleAt(++pc);
			}
			//  making 5 nybbles total
			pc++;
			return value;
		}
		if (tag == 15)
		{
			value = 0;
			for (int _count2 = 1; _count2 <= 8; _count2++)
			{
				value = (value << 4) + nybbles.extractNybbleFromTupleAt(++pc);
			}
			//  making 9 nybbles total
			pc++;
			return value;
		}
		error("Impossible nybble");
		return 0;
	}

	L2ObjectRegister localOrArgumentRegister (
		final int argumentNumber)
	{
		//  Answer the register holding the specified argument/local number (the 1st argument is the 3rd architectural register).

		return continuationSlotRegister(argumentNumber);
	}

	L2LabelInstruction newLabel ()
	{
		return new L2LabelInstruction();
	}

	L2ObjectRegister newRegister ()
	{
		//  Answer a fresh register that nobody else has used yet.

		return new L2ObjectRegister();
	}

	/**
	 * Only inline effectively monomorphic messages for now -- i.e., method
	 * implementation sets where every possible method uses the same primitive
	 * number.  Return one of the method implementation bodies if it's
	 * unambiguous and can be inlined (or is a {@code
	 * Primitive.Flag#SpecialReturnConstant}), otherwise return null.
	 *
	 * @param impSet The {@link ImplementationSetDescriptor implementation set}
	 *               containing the method(s) that may be inlined or invoked.
	 * @param args A {@link List} of {@link L2ObjectRegister registers} holding
	 *             the actual constant values used to look up the implementation
	 *             for the call.
	 * @return A method body (a {@code ClosureDescriptor closure}) that
	 *         exemplifies the primitive that should be inlined.
	 */
	AvailObject primitiveToInlineForWithArgumentRegisters (
		final AvailObject impSet,
		final List<L2ObjectRegister> args)
	{
		List<AvailObject> argTypes = new ArrayList<AvailObject>(args.size());
		for (L2ObjectRegister arg : args)
		{
			AvailObject type;
			type = registerHasTypeAt(arg)
			? registerTypeAt(arg)
					: VOID_TYPE.o();
			argTypes.add(type);
		}
		return primitiveToInlineForWithArgumentTypes(impSet, argTypes);
	}

	/**
	 * Only inline effectively monomorphic messages for now -- i.e., method
	 * implementation sets where every possible method uses the same primitive
	 * number.  Return one of the method implementation bodies if it's
	 * unambiguous and can be inlined (or is a {@code
	 * Primitive.Flag#SpecialReturnConstant}), otherwise return null.
	 *
	 * @param impSet The {@link ImplementationSetDescriptor implementation set}
	 *               containing the method(s) that may be inlined or invoked.
	 * @param argTypeRegisters A {@link List} of {@link L2ObjectRegister
	 *                         registers} holding the types used to look up the
	 *                         implementation for the call.
	 * @return A method body (a {@code ClosureDescriptor closure}) that
	 *         exemplifies the primitive that should be inlined.
	 */
	AvailObject primitiveToInlineForWithArgumentTypeRegisters (
		final AvailObject impSet,
		final List<L2ObjectRegister> argTypeRegisters)
	{
		List<AvailObject> argTypes =
			new ArrayList<AvailObject>(argTypeRegisters.size());
		for (L2ObjectRegister argTypeRegister : argTypeRegisters)
		{
			// Map the list of argTypeRegisters to any bound constants,
			// which must be types.  It's probably an error if one isn't bound
			// to a type constant, but we'll allow it anyhow for the moment.
			AvailObject type;
			type = registerHasConstantAt(argTypeRegister)
			? registerConstantAt(argTypeRegister)
					: VOID_TYPE.o();
			argTypes.add(type);
		}
		return primitiveToInlineForWithArgumentTypes(impSet, argTypes);
	}


	/**
	 * Only inline effectively monomorphic messages for now -- i.e., method
	 * implementation sets where every possible method uses the same primitive
	 * number.  Return the primitive number if it's unambiguous and can be
	 * inlined, otherwise zero.
	 *
	 * @param impSet The {@link ImplementationSetDescriptor implementation set}
	 *               containing the method(s) that may be inlined or invoked.
	 * @param argTypes The types of the arguments to the call.
	 * @return One of the (equivalent) primitive method bodies, or null.
	 */
	AvailObject primitiveToInlineForWithArgumentTypes (
		final AvailObject impSet,
		final List<AvailObject> argTypes)
	{
		List<AvailObject> imps = impSet.implementationsAtOrBelow(argTypes);
		AvailObject firstBody = null;
		for (AvailObject bundle : imps)
		{
			// If a forward or abstract method is possible, don't inline.
			if (!bundle.isMethod())
			{
				return null;
			}

			AvailObject body = bundle.bodyBlock();
			if (body.code().primitiveNumber() == 0)
			{
				return null;
			}

			short primitiveNumber = body.code().primitiveNumber();
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
					if (!firstBody.literalAt(1).equals(
						body.literalAt(1)))
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
		Primitive primitive = Primitive.byPrimitiveNumber(
			firstBody.code().primitiveNumber());
		if (primitive.hasFlag(SpecialReturnConstant)
				|| primitive.hasFlag(CanInline))
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
	 * @return A {@link L2ObjectRegister register} representing the stack at the
	 *         given position.
	 */
	L2ObjectRegister stackRegister (
		final int stackIndex)
	{
		assert 1 <= stackIndex && stackIndex <= code.maxStackDepth();
		return continuationSlotRegister(code.numArgs() + code.numLocals() + stackIndex);
	}


	/**
	 * Answer the register representing the slot of the stack associated with
	 * the current value of stackp.
	 *
	 * @return A {@link L2ObjectRegister register} representing the top of the
	 *         stack right now.
	 */
	L2ObjectRegister topOfStackRegister ()
	{
		assert 1 <= stackp && stackp <= code.maxStackDepth();
		return stackRegister(stackp);
	}


	/**
	 * Inline the primitive.  Attempt to fold it (evaluate it right now) if the
	 * primitive says it's foldable and the arguments are all constants.  Answer
	 * the result if it was folded, otherwise null.  If it was folded, generate
	 * code to push the folded value.
	 * <p>
	 * Special case if the flag {@link Primitive.Flag#SpecialReturnConstant} is
	 * specified:  Always fold it, since it's just a constant.
	 *
	 * @param primitiveClosure A {@link ClosureDescriptor closure} for which
	 *                         its primitive might be inlined, or even folded if
	 *                         possible.
	 * @param impSet The implementation set containing the primitive to be
	 *               invoked.
	 * @param args The {@link List} of arguments.
	 * @param successLabel The label to jump to if the primitive is not folded
	 *                     and is inlined.
	 * @return The value if the primitive was folded, otherwise null.
	 */
	AvailObject emitInlinePrimitiveImpSetArgsOnSuccessJumpTo (
		final AvailObject primitiveClosure,
		final AvailObject impSet,
		final List<L2ObjectRegister> args,
		final L2LabelInstruction successLabel)
	{
		final short primitiveNumber = primitiveClosure.code().primitiveNumber();
		final Primitive primitive =
			Primitive.byPrimitiveNumber(primitiveNumber);
		contingentImpSets = contingentImpSets.setWithElementCanDestroy(
			impSet,
			true);
		if (primitive.hasFlag(SpecialReturnConstant))
		{
			// Don't attempt the primitive because it will fail.  Immediately
			// use the first literal as the return value.
			AvailObject value = primitiveClosure.code().literalAt(1);
			addInstruction(new L2LoadConstantInstruction().constantDestination(
				value,
				topOfStackRegister()));
			return value;
		}
		boolean allConstants = true;
		for (L2ObjectRegister arg : args)
		{
			if (!registerHasConstantAt(arg))
			{
				allConstants = false;
			}
		}
		boolean canFold;
		boolean hasInterpreter;
		if (allConstants)
		{
			canFold = primitive.hasFlag(CanFold);
			hasInterpreter = interpreter != null;
		}
		else
		{
			canFold = false;
			hasInterpreter = false;
		}
		if (allConstants && canFold && hasInterpreter)
		{
			List<AvailObject> argValues = new ArrayList<AvailObject>(args.size());
			for (L2Register argReg : args)
			{
				argValues.add(registerConstantAt(argReg));
			}
			Result success = interpreter.attemptPrimitive(primitiveNumber, argValues);
			if (success == SUCCESS)
			{
				AvailObject value = interpreter.primitiveResult();
				addInstruction(new L2LoadConstantInstruction().constantDestination(
					value,
					topOfStackRegister()));
				return value;
			}
			assert success != CONTINUATION_CHANGED
			: "This foldable primitive changed the continuation!";
			assert success == FAILURE;
		}
		final L2LabelInstruction postPrimitiveLabel = newLabel();
		addInstruction(new L2AttemptPrimitiveInstruction().primitiveArgumentsDestinationIfFail(
			primitiveNumber,
			createVector(args),
			topOfStackRegister(),
			postPrimitiveLabel));
		addInstruction(new L2JumpInstruction().target(successLabel));
		addInstruction(postPrimitiveLabel);
		return null;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final L2ObjectRegister originalTopOfStack = topOfStackRegister();
		addInstruction(new L2MakeImmutableInstruction(originalTopOfStack));
		stackp--;
		addInstruction(new L2MoveInstruction().sourceDestination(
			originalTopOfStack, topOfStackRegister()));
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		//  [n] - Push the value of the variable that's literal number n in the current compiledCode.

		final AvailObject constant = code.literalAt(getInteger());
		stackp--;
		addInstruction(new L2LoadConstantInstruction().constantDestination(constant, topOfStackRegister()));
		addInstruction(new L2GetInstruction().sourceVariableDestination(topOfStackRegister(), topOfStackRegister()));
	}

	@Override
	public void L1Ext_doGetType ()
	{
		//  [n] - Push the (n+1)st stack element's type.  This is only used by the supercast
		//  mechanism to produce types for arguments not being cast.  See #doSuperCall.
		//  This implies the type will be used for a lookup and then discarded.  We therefore
		//  don't treat the type as acquiring a new reference from the stack, so it doesn't
		//  have to become immutable.  This could be a sticky point with the garbage collector
		//  if it finds only one reference to the type, but I think it's ok still.

		final int index = getInteger();
		stackp--;
		addInstruction(new L2GetTypeInstruction().sourceDestination(stackRegister((stackp + 1 + index)), topOfStackRegister()));
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		//  Build a continuation which, when restarted, will be just like restarting the current continuation.

		stackp--;
		L2ObjectRegister destReg = topOfStackRegister();
		L2ObjectRegister voidReg = newRegister();
		addInstruction(new L2ClearObjectInstruction().destination(voidReg));
		L2LabelInstruction skipLabel = newLabel();
		L2LabelInstruction resumeLabel = newLabel();
		int numSlots = code.numArgsAndLocalsAndStack();
		List<L2ObjectRegister> vector = new ArrayList<L2ObjectRegister>(numSlots);
		List<L2ObjectRegister> vectorWithOnlyArgsPreserved = new ArrayList<L2ObjectRegister>(numSlots);
		for (int i = 1; i<= numSlots; i++)
		{
			vector.add(continuationSlotRegister(i));
			L2ObjectRegister voidRegClone;
			try
			{
				voidRegClone = voidReg.clone();
			}
			catch (CloneNotSupportedException e)
			{
				error("Unexpected failure cloning a register");
				voidRegClone = null;
			}
			vectorWithOnlyArgsPreserved.add(
				i <= code.numArgs()
				? continuationSlotRegister(i)
						: voidRegClone);
		}
		addInstruction(new L2CreateContinuationInstruction()
		.callerClosurePcStackpSizeSlotsVectorLabelDestination(
			callerRegister(),
			closureRegister(),
			1,
			code.maxStackDepth() + 1,
			numSlots,
			createVector(vectorWithOnlyArgsPreserved),
			resumeLabel,
			destReg));

		// Freeze all fields of the new object, including its caller, closure, and args.
		addInstruction(new L2MakeSubobjectsImmutableInstruction().register(destReg));
		addInstruction(new L2JumpInstruction().target(skipLabel));

		// This is where the continuation will start running if resumed.
		addInstruction(resumeLabel);

		// When (if) this new continuation resumes, the callerRegister will contain
		// the continuation to be exploded.  We can't just create a continuation that
		// will automatically jump to wordcode 1, since an explosion step has to happen
		// and it doesn't normally set up to do that.  We use an island of L2 code here
		// to say what to do on resume, which includes the explosion and a subsequent
		// jump to the start of the compiledCode.
		addInstruction(new L2ExplodeInstruction()
		.toExplodeDestSenderDestClosureDestVector(
			callerRegister(),
			callerRegister(),
			closureRegister(),
			createVector(vector)));
		// After exploding the continuation, jump back to the start of this method.
		// Assume inlining will uniformly adjust labels, otherwise nothing would work.
		// We insert a label just before dispatching nybblecodes, so the cast below is safe.
		addInstruction(new L2JumpInstruction().target(instructions.get(0)));

		// This is where the code jumps to after *creating* the continuation.
		addInstruction(skipLabel);
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

		AvailObject constant = code.literalAt(getInteger());
		L2ObjectRegister tempReg = newRegister();
		addInstruction(new L2LoadConstantInstruction()
		.constantDestination(constant, tempReg));
		addInstruction(new L2SetInstruction()
		.variableValue(tempReg, topOfStackRegister()));
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

		AvailObject impSet = code.literalAt(getInteger());
		AvailObject expectedType = code.literalAt(getInteger());
		int numSlots = code.numArgsAndLocalsAndStack();
		List<L2ObjectRegister> preSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		List<L2ObjectRegister> postSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
		{
			L2ObjectRegister register = continuationSlotRegister(slotIndex);
			preSlots.add(register);
			postSlots.add(register);
		}
		L2ObjectRegister voidReg = newRegister();
		L2ObjectRegister expectedTypeReg = newRegister();
		int nArgs = impSet.numArgs();
		List<L2ObjectRegister> argTypes =
			new ArrayList<L2ObjectRegister>(nArgs);
		for (int i = nArgs; i >= 1; i--)
		{
			argTypes.add(0, topOfStackRegister());
			preSlots.set(
				code.numArgs() + code.numLocals() + stackp - 1,
				voidReg);
			stackp++;
		}
		List<L2ObjectRegister> args =
			new ArrayList<L2ObjectRegister>(nArgs);
		for (int i = nArgs; i >= 1; i--)
		{
			args.add(0, topOfStackRegister());
			preSlots.set(
				code.numArgs() + code.numLocals() + stackp - 1,
				voidReg);
			stackp++;
		}
		stackp--;
		preSlots.set(
			code.numArgs() + code.numLocals() + stackp - 1,
			expectedTypeReg);
		L2LabelInstruction postExplodeLabel = newLabel();
		AvailObject primClosure = primitiveToInlineForWithArgumentTypeRegisters(
			impSet,
			argTypes);
		if (primClosure.code().primitiveNumber() > 0)
		{
			// Inline the primitive.  Attempt to fold it if the primitive says
			// it's foldable and the arguments are all constants.
			AvailObject folded = emitInlinePrimitiveImpSetArgsOnSuccessJumpTo(
				primClosure,
				impSet,
				args,
				postExplodeLabel);
			if (folded != null)
			{
				// It was folded to a constant.
				if (!folded.isInstanceOfSubtypeOf(expectedType))
				{
					error("Folded primitive did not yield the expected type");
				}
				return;
			}
		}
		addInstruction(new L2LoadConstantInstruction().constantDestination(
			expectedType,
			expectedTypeReg));
		addInstruction(new L2ClearObjectInstruction().destination(voidReg));
		List<AvailObject> savedSlotTypes = new ArrayList<AvailObject>(numSlots);
		List<AvailObject> savedSlotConstants = new ArrayList<AvailObject>(numSlots);
		for (L2ObjectRegister reg : preSlots)
		{
			savedSlotTypes.add(registerTypeAt(reg));
			savedSlotConstants.add(registerConstantAt(reg));
		}
		L2LabelInstruction postCallLabel = newLabel();
		addInstruction(new L2CreateContinuationInstruction()
		.callerClosurePcStackpSizeSlotsVectorLabelDestination(
			callerRegister(),
			closureRegister(),
			pc,
			stackp,
			numSlots,
			createVector(preSlots),
			postCallLabel,
			callerRegister()));
		addInstruction(new L2SuperCallInstruction()
		.selectorArgsVectorArgTypesVector(
			impSet,
			createVector(args),
			createVector(argTypes)));

		// The method being invoked will run until it returns, and the next
		// instruction will be here.
		addInstruction(postCallLabel);

		// And after the call returns, the callerRegister will contain the
		// continuation to be exploded.
		addInstruction(new L2ExplodeInstruction()
		.toExplodeDestSenderDestClosureDestVector(
			callerRegister(),
			callerRegister(),
			closureRegister(),
			createVector(postSlots)));
		for (int i = 0; i < postSlots.size(); i++)
		{
			AvailObject type = savedSlotTypes.get(i);
			if (type != null)
			{
				registerTypeAtPut(postSlots.get(i), type);
			}
			AvailObject constant = savedSlotConstants.get(i);
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
		AvailObject impSet = code.literalAt(getInteger());
		AvailObject expectedType = code.literalAt(getInteger());
		int numSlots = code.numArgsAndLocalsAndStack();
		List<L2ObjectRegister> preSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		List<L2ObjectRegister> postSlots =
			new ArrayList<L2ObjectRegister>(numSlots);
		for (int slotIndex = 1; slotIndex <= numSlots; slotIndex++)
		{
			L2ObjectRegister register = continuationSlotRegister(slotIndex);
			preSlots.add(register);
			postSlots.add(register);
		}
		L2ObjectRegister voidReg = newRegister();
		L2ObjectRegister expectedTypeReg = newRegister();
		int nArgs = impSet.numArgs();
		List<L2ObjectRegister> args =
			new ArrayList<L2ObjectRegister>(nArgs);
		for (int i = nArgs; i >= 1; i--)
		{
			args.add(0, topOfStackRegister());
			preSlots.set(
				code.numArgs() + code.numLocals() + stackp - 1,
				voidReg);
			stackp++;
		}
		stackp--;
		preSlots.set(
			code.numArgs() + code.numLocals() + stackp - 1,
			expectedTypeReg);
		L2LabelInstruction postExplodeLabel = newLabel();
		AvailObject primClosure = primitiveToInlineForWithArgumentRegisters(
			impSet,
			args);
		if (primClosure != null)
		{
			// Inline the primitive.  Attempt to fold it if the primitive says
			// it's foldable and the arguments are all constants.
			assert primClosure.code().primitiveNumber() != 0;
			AvailObject folded = emitInlinePrimitiveImpSetArgsOnSuccessJumpTo(
				primClosure,
				impSet,
				args,
				postExplodeLabel);
			if (folded != null)
			{
				// It was folded to a constant.
				if (!folded.isInstanceOfSubtypeOf(expectedType))
				{
					error("Folded primitive did not yield the expected type");
				}
				return;
			}
		}
		addInstruction(new L2LoadConstantInstruction().constantDestination(
			expectedType,
			expectedTypeReg));
		addInstruction(new L2ClearObjectInstruction().destination(voidReg));
		List<AvailObject> savedSlotTypes = new ArrayList<AvailObject>(numSlots);
		List<AvailObject> savedSlotConstants = new ArrayList<AvailObject>(numSlots);
		for (L2ObjectRegister reg : preSlots)
		{
			savedSlotTypes.add(registerTypeAt(reg));
			savedSlotConstants.add(registerConstantAt(reg));
		}
		L2LabelInstruction postCallLabel = newLabel();
		addInstruction(new L2CreateContinuationInstruction()
		.callerClosurePcStackpSizeSlotsVectorLabelDestination(
			callerRegister(),
			closureRegister(),
			pc,
			stackp,
			numSlots,
			createVector(preSlots),
			postCallLabel,
			callerRegister()));
		addInstruction(new L2CallInstruction()
		.selectorArgsVector(
			impSet,
			createVector(args)));
		// The method being invoked will run until it returns, and the next
		// instruction will be here.
		addInstruction(postCallLabel);
		// And after the call returns, the callerRegister will contain the
		// continuation to be exploded.
		addInstruction(new L2ExplodeInstruction()
		.toExplodeDestSenderDestClosureDestVector(
			callerRegister(),
			callerRegister(),
			closureRegister(),
			createVector(postSlots)));
		for (int i = 0; i < postSlots.size(); i++)
		{
			AvailObject type = savedSlotTypes.get(i);
			if (type != null)
			{
				registerTypeAtPut(postSlots.get(i), type);
			}
			AvailObject constant = savedSlotConstants.get(i);
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
		//  construction of a closure based on the compiledCode that's the literal at index m
		//  of the current compiledCode.

		int count = getInteger();
		AvailObject codeLiteral = code.literalAt(getInteger());
		List<L2ObjectRegister> outers = new ArrayList<L2ObjectRegister>(count);
		for (int i = count; i >= 1; i--)
		{
			outers.add(0, topOfStackRegister());
			stackp++;
		}
		stackp--;
		addInstruction(new L2CreateClosureInstruction().codeOutersVectorDestObject(
			codeLiteral,
			createVector(outers),
			topOfStackRegister()));

		// Now that the closure has been constructed, clear the slots that
		// were used for outer values (except the destination slot, which is
		// being overwritten with the resulting closure anyhow).
		for (int stackIndex = stackp + 1 - count; stackIndex <= stackp - 1; stackIndex++)
		{
			addInstruction(new L2ClearObjectInstruction().destination(
				stackRegister(stackIndex)));
		}
	}

	@Override
	public void L1_doExtension ()
	{
		//  The extension nybblecode was encountered.  Read another nybble and dispatch it through ExtendedSelectors.

		byte nybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		L1Operation.values()[nybble + 16].dispatch(this);
	}

	@Override
	public void L1_doGetLocal ()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).

		int index = getInteger();
		stackp--;
		addInstruction(new L2GetInstruction().sourceVariableDestination(
			localOrArgumentRegister(index),
			topOfStackRegister()));
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		//  [n] - Push the value of the local variable (not an argument) indexed by n (index 1 is first argument).

		int index = getInteger();
		stackp--;
		addInstruction(new L2GetClearingInstruction().sourceVariableDestination(
			localOrArgumentRegister(index),
			topOfStackRegister()));
	}

	@Override
	public void L1_doGetOuter ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.

		final int outerIndex = getInteger();
		stackp--;
		addInstruction(new L2ExtractOuterInstruction().closureRegisterOuterNumberDestination(
			closureRegister(),
			outerIndex,
			topOfStackRegister()));
		addInstruction(new L2GetInstruction().sourceVariableDestination(topOfStackRegister(), topOfStackRegister()));
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		//  [n] - Push the value of the outer variable indexed by n in the current closure.
		//  If the variable itself is mutable, clear it at this time - nobody will know.
		//  Actually, right now we don't optimize this in level two, for simplicity.

		int outerIndex = getInteger();
		stackp--;
		addInstruction(new L2ExtractOuterInstruction()
		.closureRegisterOuterNumberDestination(
			closureRegister(),
			outerIndex,
			topOfStackRegister()));
		addInstruction(new L2GetClearingInstruction()
		.sourceVariableDestination(
			topOfStackRegister(),
			topOfStackRegister()));
	}

	@Override
	public void L1_doMakeTuple ()
	{
		int count = getInteger();
		List<L2ObjectRegister> vector = new ArrayList<L2ObjectRegister>(count);
		for (int i = 1; i <= count; i++)
		{
			vector.add(stackRegister(stackp + count - i));
		}
		stackp += count - 1;
		addInstruction(new L2CreateTupleInstruction()
		.sourceVectorDestination(createVector(vector), topOfStackRegister()));
	}

	@Override
	public void L1_doPop ()
	{
		//  Remove the top item from the stack.

		assert stackp == code.maxStackDepth() : "Pop should only only occur at end of statement";
		addInstruction(new L2ClearObjectInstruction().destination(
			topOfStackRegister()));
		stackp++;
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.
		//  Since this is known to be the last use (nondebugger) of the argument or local, void that
		//  slot of the current continuation.

		int localIndex = getInteger();
		stackp--;
		addInstruction(new L2MoveInstruction().sourceDestination(
			localOrArgumentRegister(localIndex),
			topOfStackRegister()));
		addInstruction(new L2ClearObjectInstruction().destination(
			localOrArgumentRegister(localIndex)));
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		//  [n] - Push the outer variable indexed by n in the current closure.  If the variable is
		//  mutable, clear it (no one will know).  If the variable and closure are both mutable,
		//  remove the variable from the closure by voiding it.

		int outerIndex = getInteger();
		stackp--;
		addInstruction(new L2ExtractOuterInstruction()
		.closureRegisterOuterNumberDestination(
			closureRegister(),
			outerIndex,
			topOfStackRegister()));
		addInstruction(new L2MakeImmutableInstruction(
			topOfStackRegister()));
	}

	@Override
	public void L1_doPushLiteral ()
	{
		//  [n] - Push the literal indexed by n in the current compiledCode.

		AvailObject constant = code.literalAt(getInteger());
		stackp--;
		addInstruction(new L2LoadConstantInstruction().constantDestination(
			constant,
			topOfStackRegister()));
	}

	@Override
	public void L1_doPushLocal ()
	{
		//  [n] - Push the argument (actual value) or local variable (the variable itself) indexed by n.

		int localIndex = getInteger();
		stackp--;
		addInstruction(new L2MoveInstruction().sourceDestination(
			localOrArgumentRegister(localIndex),
			topOfStackRegister()));
		addInstruction(new L2MakeImmutableInstruction(
			topOfStackRegister()));
	}

	@Override
	public void L1_doPushOuter ()
	{
		//  [n] - Push the outer variable indexed by n in the current closure.

		int outerIndex = getInteger();
		stackp--;
		addInstruction(new L2ExtractOuterInstruction()
		.closureRegisterOuterNumberDestination(
			closureRegister(),
			outerIndex,
			topOfStackRegister()));
		addInstruction(new L2MakeImmutableInstruction(
			topOfStackRegister()));
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
		addInstruction(new L2ReturnInstruction().continuationValue(
			callerRegister(),
			topOfStackRegister()));
		assert stackp == code.maxStackDepth();
		stackp = -666;
	}

	@Override
	public void L1_doSetLocal ()
	{
		//  [n] - Pop the stack and assign this value to the local variable (not an argument) indexed by n (index 1 is first argument).

		int localIndex = getInteger();
		L2ObjectRegister local = localOrArgumentRegister(localIndex);
		addInstruction(new L2SetInstruction().variableValue(
			local,
			topOfStackRegister()));
		stackp++;
	}

	@Override
	public void L1_doSetOuter ()
	{
		//  [n] - Pop the stack and assign this value to the outer variable indexed by n in the current closure.

		int outerIndex = getInteger();
		L2ObjectRegister tempReg = newRegister();
		addInstruction(new L2MakeImmutableInstruction(
			topOfStackRegister()));
		addInstruction(new L2ExtractOuterInstruction()
		.closureRegisterOuterNumberDestination(
			closureRegister(),
			outerIndex,
			tempReg));
		addInstruction(new L2SetInstruction().variableValue(
			tempReg,
			topOfStackRegister()));
		stackp++;
	}

	AvailObject createChunk ()
	{
		final L2CodeGenerator codeGen = new L2CodeGenerator();
		codeGen.setInstructions(instructions);
		contingentImpSets.makeImmutable();
		codeGen.addContingentImplementationSets(contingentImpSets);
		final AvailObject chunk = codeGen.createChunkFor(code);
		chunk.moveToHead();
		return chunk;
	}

	public AvailObject createChunkForFirstInvocation ()
	{
		//  Create a chunk that will perform a naive translation of the current method to Level Two.  The
		//  naive translation creates a counter that is decremented each time the method is invoked.
		//  When the counter reaches zero, the method will be retranslated (with deeper optimization).

		if (true)
		{
			instructions = new ArrayList<L2Instruction>(10);
			architecturalRegisters = new ArrayList<L2ObjectRegister>(10);
			registerTypes = new HashMap<L2RegisterIdentity, AvailObject>(10);
			registerConstants = new HashMap<L2RegisterIdentity, AvailObject>(10);
			vectors = new ArrayList<L2RegisterVector>(10);
			code = null;
			nybbles = null;
		}
		contingentImpSets = SetDescriptor.empty();
		instructions.add(new L2DecrementToZeroThenOptimizeInstruction());
		instructions.add(new L2CreateSimpleContinuation().destination(callerRegister()));
		L2LabelInstruction label;
		instructions.add(label = new L2LabelInstruction());
		instructions.add(new L2InterpretOneInstruction());
		L2LabelInstruction pausePoint;
		instructions.add(pausePoint = new L2LabelInstruction());
		instructions.add(new L2JumpIfNotInterruptInstruction().target(label));
		instructions.add(new L2ProcessInterruptNowInstruction().continuation(callerRegister()));
		instructions.add(new L2JumpInstruction().target(label));
		optimize();
		final AvailObject chunk = createChunk();
		assert chunk.index() == 1;
		assert label.offset() == L2ChunkDescriptor.offsetToContinueUnoptimizedChunk();
		assert pausePoint.offset() == L2ChunkDescriptor.offsetToPauseUnoptimizedChunk();
		return chunk;
	}

	void optimize ()
	{
		//  Optimize the stream of instructions.

		simpleColorRegisters();
	}

	void simpleColorRegisters ()
	{
		//  Assign register numbers to every register.  Keep it simple for now.

		HashSet<L2RegisterIdentity> identities = new HashSet<L2RegisterIdentity>();
		for (L2Instruction instruction : instructions)
		{
			for (L2Register reg : instruction.sourceRegisters())
			{
				identities.add(reg.identity());
			}
			for (L2Register reg : instruction.destinationRegisters())
			{
				identities.add(reg.identity());
			}
		}
		int maxId = 0;
		for (L2RegisterIdentity identity : identities)
		{
			if (identity.finalIndex() != -1)
			{
				maxId = max(maxId, identity.finalIndex());
			}
		}
		for (L2RegisterIdentity identity : identities)
		{
			if (identity.finalIndex() == - 1)
			{
				identity.finalIndex(++maxId);
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
	 * @return An {@link L2ChunkDescriptor AvailObject}.
	 */
	AvailObject translateOptimizationFor (
		final @NotNull AvailObject aCompiledCodeObject,
		final int optLevel,
		final @NotNull L2Interpreter anL2Interpreter)
	{
		interpreter = anL2Interpreter;
		if (true)
		{
			instructions = new ArrayList<L2Instruction>(10);
			architecturalRegisters = new ArrayList<L2ObjectRegister>(10);
			registerTypes = new HashMap<L2RegisterIdentity, AvailObject>(10);
			registerConstants = new HashMap<L2RegisterIdentity, AvailObject>(10);
			vectors = new ArrayList<L2RegisterVector>(10);
		}
		code = aCompiledCodeObject;
		optimizationLevel = optLevel;
		final AvailObject type = code.closureType();
		for (int i = 1, end = type.numArgs(); i <= end; i++)
		{
			registerTypeAtPut(localOrArgumentRegister(i), type.argTypeAt(i));
		}
		nybbles = code.nybbles();
		pc = 1;
		stackp = code.maxStackDepth() + 1;
		// Just past end.  This is not the same offset it would have during
		// execution.
		contingentImpSets = SetDescriptor.empty();
		if (optLevel == 0)
		{
			code.invocationCount(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			addInstruction(new L2DecrementToZeroThenOptimizeInstruction());
		}
		for (int local = 1, end = code.numLocals(); local <= end; local++)
		{
			addInstruction(new L2CreateVariableInstruction().typeDestination(
				code.localTypeAt(local),
				localOrArgumentRegister(code.numArgs() + local)));
		}
		for (
				int stackSlot = 1, end = code.maxStackDepth();
				stackSlot <= end;
				stackSlot++)
		{
			addInstruction(new L2ClearObjectInstruction().destination(
				stackRegister(stackSlot)));
		}
		// Now translate all the instructions.  Start by writing a label that
		// L1Ext_doPushLabel can always find at the start of the list of
		// instructions. Since we only translate one method at a time, the first
		// instruction always represents the start of this compiledCode.
		addInstruction(newLabel());
		while (pc <= nybbles.tupleSize())
		{
			byte nybble = nybbles.extractNybbleFromTupleAt(pc);
			pc++;
			L1Operation.values()[nybble].dispatch(this);
		}
		// Translate the implicit L1_doReturn instruction that terminates the
		// instruction sequence.
		L1Operation.L1Implied_Return.dispatch(this);
		assert pc == nybbles.tupleSize() + 1;
		assert stackp == -0x29A;
		optimize();
		final AvailObject newChunk = createChunk();
		return newChunk;
	}
}

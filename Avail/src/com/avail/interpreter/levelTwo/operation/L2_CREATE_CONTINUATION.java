package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Create a continuation from scratch, using the specified caller, function,
 * constant level one program counter, constant stack pointer, continuation
 * slot values, and level two program counter.  Write the new continuation
 * into the specified register.
 */
public class L2_CREATE_CONTINUATION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_CREATE_CONTINUATION();

	static
	{
		instance.init(
			READ_POINTER.is("caller"),
			READ_POINTER.is("function"),
			IMMEDIATE.is("level one pc"),
			IMMEDIATE.is("stack pointer"),
			READ_VECTOR.is("slot values"),
			PC.is("level two pc"),
			WRITE_POINTER.is("destination"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int senderIndex = interpreter.nextWord();
		final int functionIndex = interpreter.nextWord();
		final int pcIndex = interpreter.nextWord();
		final int stackpIndex = interpreter.nextWord();
		final int slotsIndex = interpreter.nextWord();
		final int wordcodeOffset = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		final AvailObject function = interpreter.pointerAt(functionIndex);
		final AvailObject code = function.code();
		final int frameSize = code.numArgsAndLocalsAndStack();
		final AvailObject continuation =
			ContinuationDescriptor.mutable().create(frameSize);
		continuation.caller(interpreter.pointerAt(senderIndex));
		continuation.function(function);
		continuation.pc(pcIndex);
		continuation.stackp(frameSize - code.maxStackDepth() + stackpIndex);
		continuation.levelTwoChunkOffset(
			interpreter.chunk(),
			wordcodeOffset);
		final AvailObject slots = interpreter.vectorAt(slotsIndex);
		final int size = slots.tupleSize();
		for (int i = 1; i <= size; i++)
		{
			continuation.argOrLocalOrStackAtPut(
				i,
				interpreter.pointerAt(slots.tupleIntAt(i)));
		}
		interpreter.pointerAtPut(destIndex, continuation);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ReadPointerOperand functionOperand =
			(L2ReadPointerOperand) instruction.operands[1];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[6];
		final L2ObjectRegister destinationRegister =
			destinationOperand.register;
		final AvailObject functionType = registers.typeAt(
			functionOperand.register);
		assert functionType.isSubtypeOf(
			FunctionTypeDescriptor.mostGeneralType());
		registers.typeAtPut(
			destinationRegister,
			ContinuationTypeDescriptor.forFunctionType(functionType));
		registers.removeConstantAt(destinationRegister);
		registers.propagateWriteTo(destinationRegister);
	}
}
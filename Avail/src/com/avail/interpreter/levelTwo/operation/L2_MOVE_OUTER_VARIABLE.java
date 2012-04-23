package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.VariableDescriptor;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.RegisterSet;

/**
 * Extract a captured "outer" variable from a function.  If the outer
 * variable is an actual {@linkplain VariableDescriptor variable} then the
 * variable itself is what gets moved into the destination register.
 */
public class L2_MOVE_OUTER_VARIABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_MOVE_OUTER_VARIABLE();

	static
	{
		instance.init(
			IMMEDIATE.is("outer index"),
			READ_POINTER.is("function"),
			WRITE_POINTER.is("destination"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int outerIndex = interpreter.nextWord();
		final int fromIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		interpreter.pointerAtPut(
			destIndex,
			interpreter.pointerAt(fromIndex).outerVarAt(outerIndex));
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ImmediateOperand outerIndexOperand =
			(L2ImmediateOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[2];
		final L2Register destination = destinationOperand.register;
		registers.removeTypeAt(destination);
		registers.removeConstantAt(destination);
		registers.propagateWriteTo(destination);
		registers.typeAtPut(
			destination,
			registers.code().outerTypeAt(outerIndexOperand.value));
	}
}
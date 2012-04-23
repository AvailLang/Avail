package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.RegisterSet;

/**
 * Move a constant {@link AvailObject} into an object register.
 */
public class L2_MOVE_CONSTANT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_MOVE_CONSTANT();

	static
	{
		instance.init(
			CONSTANT.is("constant"),
			WRITE_POINTER.is("destination"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int fromIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		interpreter.pointerAtPut(
			destIndex,
			interpreter.chunk().literalAt(fromIndex));
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ConstantOperand constantOperand =
			(L2ConstantOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[1];
		registers.constantAtPut(
			destinationOperand.register,
			constantOperand.object);
	}
}
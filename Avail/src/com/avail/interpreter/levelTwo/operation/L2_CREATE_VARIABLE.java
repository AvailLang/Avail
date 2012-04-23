package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.RegisterSet;

/**
 * Create a new {@linkplain VariableDescriptor variable object} of the
 * specified {@link VariableTypeDescriptor variable type}.
 */
public class L2_CREATE_VARIABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_CREATE_VARIABLE();

	static
	{
		instance.init(
			CONSTANT.is("type"),
			WRITE_POINTER.is("variable"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int typeIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		interpreter.pointerAtPut(
			destIndex,
			VariableDescriptor.forOuterType(
				interpreter.chunk().literalAt(typeIndex)));
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
		// We know the type...
		registers.typeAtPut(
			destinationOperand.register,
			constantOperand.object);
		// ...but the instance is new so it can't be a constant.
		registers.removeConstantAt(destinationOperand.register);
		registers.propagateWriteTo(destinationOperand.register);
	}
}
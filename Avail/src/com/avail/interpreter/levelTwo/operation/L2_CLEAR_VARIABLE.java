package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.RegisterSet;

/**
 * Clear a variable; i.e., make it have no assigned value.
 */
public class L2_CLEAR_VARIABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_CLEAR_VARIABLE();

	static
	{
		instance.init(
			READ_POINTER.is("variable"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		@SuppressWarnings("unused")
		final int clearIndex = interpreter.nextWord();
		error("not implemented");
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ReadPointerOperand variableOperand =
			(L2ReadPointerOperand) instruction.operands[0];
		// If we haven't already guaranteed that this is a variable then we
		// are probably not doing things right.
		assert registers.hasTypeAt(variableOperand.register);
		final AvailObject varType = registers.typeAt(
			variableOperand.register);
		assert varType.isSubtypeOf(
			VariableTypeDescriptor.mostGeneralType());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}
package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_VECTOR;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.RegisterSet;

/**
 * This marks the entry point into optimized (level two) code.  At entry,
 * the arguments are expected to be in the specified architectural
 * registers.  This operation is a place-holder and is not actually emitted.
 */
public class L2_ENTER_L2_CHUNK extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_ENTER_L2_CHUNK();

	static
	{
		instance.init(
			WRITE_VECTOR.is("fixed and arguments"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		error("Enter chunk wordcode is not executable\n");
	}

	@Override
	public boolean shouldEmit ()
	{
		return false;
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// Don't wipe out my arguments.
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}
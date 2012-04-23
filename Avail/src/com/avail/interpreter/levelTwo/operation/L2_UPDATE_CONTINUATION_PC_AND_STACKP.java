package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * Update an existing continuation's level one program counter and stack
 * pointer to the provided immediate integers.  If the continuation is
 * mutable then change it in place, otherwise use a mutable copy.  Write
 * the resulting continuation back to the register that provided the
 * original.
 */
public class L2_UPDATE_CONTINUATION_PC_AND_STACKP extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_UPDATE_CONTINUATION_PC_AND_STACKP();

	static
	{
		instance.init(
			READWRITE_POINTER.is("continuation"),
			IMMEDIATE.is("new pc"),
			IMMEDIATE.is("new stack pointer"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		// TODO [MvG] Implement.
		@SuppressWarnings("unused")
		final int continuationIndex = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int pcIndex = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int stackpIndex = interpreter.nextWord();
		error("not implemented");
	}
}
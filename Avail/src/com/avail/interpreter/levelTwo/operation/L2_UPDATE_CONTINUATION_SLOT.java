package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * Update a slot of an existing continuation.  If the continuation is
 * mutable then change it in place, otherwise use a mutable copy.  Write
 * the resulting continuation back to the register that provided the
 * original.
 */
public class L2_UPDATE_CONTINUATION_SLOT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_UPDATE_CONTINUATION_SLOT();

	static
	{
		instance.init(
			READWRITE_POINTER.is("continuation"),
			IMMEDIATE.is("slot index"),
			READ_POINTER.is("replacement value"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		// TODO [MvG] Implement.
		@SuppressWarnings("unused")
		final int continuationIndex = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int indexIndex = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int valueIndex = interpreter.nextWord();
		error("not implemented");
	}
}
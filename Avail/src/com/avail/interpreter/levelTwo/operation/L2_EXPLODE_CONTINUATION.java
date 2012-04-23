package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;

/**
 * Given a continuation, extract its caller, function, and all of its slots
 * into the specified registers.  The level one program counter and stack
 * pointer are ignored, since they're always implicitly correlated with the
 * level two program counter.
 */
public class L2_EXPLODE_CONTINUATION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_EXPLODE_CONTINUATION();

	static
	{
		instance.init(
			READ_POINTER.is("continuation to explode"),
			WRITE_VECTOR.is("exploded continuation slots"),
			WRITE_POINTER.is("exploded caller"),
			WRITE_POINTER.is("exploded function"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		// Expand the current continuation's slots into the specified vector
		// of destination registers.  Also explode the level one pc, stack
		// pointer, the current function and the caller.
		final int continuationToExplodeIndex = interpreter.nextWord();
		final int explodedSlotsVectorIndex = interpreter.nextWord();
		final int explodedCallerIndex = interpreter.nextWord();
		final int explodedFunctionIndex = interpreter.nextWord();

		final AvailObject slots =
			interpreter.vectorAt(explodedSlotsVectorIndex);
		final int slotsCount = slots.tupleSize();
		final AvailObject continuation =
			interpreter.pointerAt(continuationToExplodeIndex);
		assert continuation.numArgsAndLocalsAndStack() == slotsCount;
		for (int i = 1; i <= slotsCount; i++)
		{
			final AvailObject slotValue =
				continuation.argOrLocalOrStackAt(i);
			interpreter.pointerAtPut(slots.tupleIntAt(i), slotValue);
		}
		interpreter.pointerAtPut(
			explodedCallerIndex,
			continuation.caller());
		interpreter.pointerAtPut(
			explodedFunctionIndex,
			continuation.function());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}
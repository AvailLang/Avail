package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.*;

/**
 * Mark as immutable all objects referred to from the specified object.
 * Copying a continuation as part of the {@link
 * L1Operation#L1Ext_doPushLabel} can make good use of this peculiar
 * instruction.
 */
public class L2_MAKE_SUBOBJECTS_IMMUTABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_MAKE_SUBOBJECTS_IMMUTABLE();

	static
	{
		instance.init(
			READ_POINTER.is("object"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int objectIndex = interpreter.nextWord();
		interpreter.pointerAt(objectIndex).makeSubobjectsImmutable();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Marking the object immutable is a side effect, but unfortunately
		// this could keep extra instructions around to create an object
		// that nobody wants.
		// [MvG] - maybe use a pseudo-copy operation from linear languages?
		return true;
	}
}
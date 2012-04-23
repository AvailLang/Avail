package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * Force the specified object to be immutable.  Maintenance of
 * conservative sticky-bit reference counts is mostly separated out into
 * this operation to allow code transformations to obviate the need for it
 * in certain non-obvious circumstances.
 */
public class L2_MAKE_IMMUTABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_MAKE_IMMUTABLE();

	static
	{
		instance.init(
			READ_POINTER.is("object"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int objectIndex = interpreter.nextWord();
		interpreter.pointerAt(objectIndex).makeImmutable();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Marking the object immutable is a side effect, but unfortunately
		// this could keep extra instructions around to create an object
		// that nobody wants.
		// TODO[MvG] - maybe a pseudo-copy operation from linear languages?
		return true;
	}
}
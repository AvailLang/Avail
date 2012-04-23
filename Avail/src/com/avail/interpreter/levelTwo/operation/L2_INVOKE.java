package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.RegisterSet;

/**
 * Send the specified method and arguments.  The calling continuation is
 * provided, which allows this operation to act more like a non-local jump
 * than a call.  The continuation has the arguments popped already, with the
 * expected return type pushed instead.
 *
 * <p>
 * The appropriate function is looked up and invoked.  The function may be a
 * primitive, and the primitive may succeed, fail, or change the current
 * continuation.
 * </p>
 */
public class L2_INVOKE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_INVOKE();

	static
	{
		instance.init(
			READ_POINTER.is("continuation"),
			READ_POINTER.is("function"),
			READ_VECTOR.is("arguments"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		// Assume the current continuation is already reified.
		final int callerIndex = interpreter.nextWord();
		final int functionIndex = interpreter.nextWord();
		final int argumentsIndex = interpreter.nextWord();
		final AvailObject caller = interpreter.pointerAt(callerIndex);
		final AvailObject function = interpreter.pointerAt(functionIndex);
		final AvailObject vect = interpreter.vectorAt(argumentsIndex);
		interpreter.argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(vect.tupleIntAt(i)));
		}
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			function,
			caller);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// Restriction happens elsewhere.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		// Returns to the pc saved in the continuation.
		return false;
	}
}
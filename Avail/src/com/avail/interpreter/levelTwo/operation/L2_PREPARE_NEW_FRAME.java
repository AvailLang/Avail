package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2Interpreter.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.RegisterSet;

/**
 * This operation is only used when entering a function that uses the
 * default chunk.  A new function has been set up for execution.  Its
 * arguments have been written to the architectural registers.  If this is a
 * primitive, then the primitive has already been attempted and failed,
 * writing the failure value into the failureValueRegister().  Set up the pc
 * and stackp, as well as local variables.  Also transfer the primitive
 * failure value into the first local variable if this is a primitive (and
 * therefore failed).
 */
public class L2_PREPARE_NEW_FRAME extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_PREPARE_NEW_FRAME();

	static
	{
		instance.init();
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final AvailObject function = interpreter.pointerAt(FUNCTION);
		final AvailObject code = function.code();
		final int numArgs = code.numArgs();
		final int numLocals = code.numLocals();
		final int numSlots = code.numArgsAndLocalsAndStack();
		// Create locals...
		int dest = argumentOrLocalRegister(numArgs + 1);
		for (int i = 1; i <= numLocals; i++)
		{
			interpreter.pointerAtPut(
				dest,
				VariableDescriptor.forOuterType(code.localTypeAt(i)));
			dest++;
		}
		// Write the null object into the remaining stack slots.  These
		// values should not encounter any kind of ordinary use, but they
		// must still be transferred into a continuation during reification.
		// Therefore don't use Java nulls here.
		for (int i = numArgs + numLocals + 1; i <= numSlots; i++)
		{
			interpreter.pointerAtPut(dest, NullDescriptor.nullObject());
			dest++;
		}
		interpreter.integerAtPut(pcRegister(), 1);
		interpreter.integerAtPut(
			stackpRegister(),
			argumentOrLocalRegister(numSlots + 1));
		if (code.primitiveNumber() != 0)
		{
			// A failed primitive.
			assert !Primitive.byPrimitiveNumber(code.primitiveNumber())
				.hasFlag(Flag.CannotFail);
			final AvailObject primitiveFailureValue =
				interpreter.pointerAt(PRIMITIVE_FAILURE);
			final AvailObject primitiveFailureVariable =
				interpreter.pointerAt(argumentOrLocalRegister(numArgs + 1));
			primitiveFailureVariable.setValue(primitiveFailureValue);
		}
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// No real optimization should ever be done near this wordcode.
		// Do nothing.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Keep this instruction from being removed, since it's only used
		// by the default chunk.
		return true;
	}
}
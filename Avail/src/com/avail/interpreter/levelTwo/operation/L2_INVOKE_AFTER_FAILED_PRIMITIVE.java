package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.PRIMITIVE_FAILURE;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Invoke the specified <em>primitive</em> function with the supplied
 * arguments, ignoring the primitive designation.  The calling continuation
 * is provided, which allows this operation to act more like a non-local
 * jump than a call.  The continuation has the arguments popped already,
 * with the expected return type pushed instead.
 *
 * <p>
 * The function must be a primitive which has already failed at this point,
 * so set up the failure code of the function without trying the primitive.
 * The failure value from the failed primitive attempt is provided and will
 * be saved in the architectural {@link FixedRegister#PRIMITIVE_FAILURE}
 * register for use by subsequent L1 or L2 code.
 * </p>
 */
public class L2_INVOKE_AFTER_FAILED_PRIMITIVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_INVOKE_AFTER_FAILED_PRIMITIVE();

	static
	{
		instance.init(
			READ_POINTER.is("continuation"),
			READ_POINTER.is("function"),
			READ_VECTOR.is("arguments"),
			READ_POINTER.is("primitive failure value"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		// The continuation is required to have already been reified.
		final int callerIndex = interpreter.nextWord();
		final int functionIndex = interpreter.nextWord();
		final int argumentsIndex = interpreter.nextWord();
		final int failureValueIndex = interpreter.nextWord();
		final AvailObject caller = interpreter.pointerAt(callerIndex);
		final AvailObject function = interpreter.pointerAt(functionIndex);
		final AvailObject failureValue =
			interpreter.pointerAt(failureValueIndex);
		final AvailObject vect = interpreter.vectorAt(argumentsIndex);
		interpreter.argsBuffer.clear();
		for (int i = 1; i <= vect.tupleSize(); i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject codeToCall = function.code();
		final int primNum = codeToCall.primitiveNumber();
		assert primNum != 0;
		assert !Primitive.byPrimitiveNumber(primNum).hasFlag(
			Flag.CannotFail);
		interpreter.invokeWithoutPrimitiveFunctionArguments(
			function,
			interpreter.argsBuffer,
			caller);
		// Put the primitive failure value somewhere that both L1 and L2
		// will find it.
		interpreter.pointerAtPut(PRIMITIVE_FAILURE, failureValue);
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
		// Never remove this send, since it's due to a failed primitive.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		// Returns to the pc saved in the continuation.
		return false;
	}
}
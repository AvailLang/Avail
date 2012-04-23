package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.RegisterSet;

/**
 * Execute a primitive with the provided arguments, writing the result into
 * the specified register.  The primitive must not fail.  Check that the
 * resulting object's type agrees with the provided expected type
 * (TODO [MvG] currently stopping the VM if not).
 *
 * <p>
 * Unlike for {@link L2_INVOKE} and related operations, we do not provide
 * the calling continuation here.  That's because by inlining the primitive
 * attempt we have avoided (or at worst postponed) construction of the
 * continuation that reifies the current function execution.  This is a Good
 * Thing, performance-wise.
 * </p>
 */
public class L2_RUN_INFALLIBLE_PRIMITIVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_RUN_INFALLIBLE_PRIMITIVE();

	static
	{
		instance.init(
			PRIMITIVE.is("primitive to run"),
			READ_VECTOR.is("arguments"),
			READ_POINTER.is("expected type"),
			WRITE_POINTER.is("primitive result"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int primNumber = interpreter.nextWord();
		final int argsVector = interpreter.nextWord();
		final int expectedTypeRegister = interpreter.nextWord();
		final int resultRegister = interpreter.nextWord();
		final AvailObject argsVect = interpreter.vectorAt(argsVector);
		interpreter.argsBuffer.clear();
		for (int i1 = 1; i1 <= argsVect.tupleSize(); i1++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(argsVect.tupleIntAt(i1)));
		}
		// Only primitive 340 needs the compiledCode argument, and it's
		// always folded.  In the case that primitive 340 is known to
		// produce the wrong type at some site (potentially dead code due to
		// inlining of an unreachable branch), it is converted to an
		// explicit failure instruction.  Thus we can pass null.
		final Result res = interpreter.attemptPrimitive(
			primNumber,
			null,
			interpreter.argsBuffer);
		assert res == SUCCESS;
		final AvailObject expectedType =
			interpreter.pointerAt(expectedTypeRegister);
		if (!interpreter.primitiveResult.isInstanceOf(expectedType))
		{
			// TODO [MvG] - This will have to be handled better some day.
			error(
				"primitive %s's result (%s) did not agree with"
				+ " semantic restriction's expected type (%s)",
				Primitive.byPrimitiveNumber(primNumber).name(),
				interpreter.primitiveResult,
				expectedType);
		}
		interpreter.pointerAtPut(
			resultRegister,
			interpreter.primitiveResult);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2PrimitiveOperand primitiveOperand =
			(L2PrimitiveOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[3];
		registers.removeTypeAt(destinationOperand.register);
		registers.removeConstantAt(destinationOperand.register);
		registers.propagateWriteTo(destinationOperand.register);

		// We can at least believe what the basic primitive signature says
		// it returns.
		registers.typeAtPut(
			destinationOperand.register,
			primitiveOperand.primitive.blockTypeRestriction().returnType());
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		// It depends on the primitive.
		assert instruction.operation == this;
		final L2PrimitiveOperand primitiveOperand =
			(L2PrimitiveOperand) instruction.operands[0];
		final Primitive primitive = primitiveOperand.primitive;
		assert primitive.hasFlag(Flag.CannotFail);
		final boolean mustKeep = primitive.hasFlag(Flag.HasSideEffect)
			|| primitive.hasFlag(Flag.CatchException)
			|| primitive.hasFlag(Flag.Invokes)
			|| primitive.hasFlag(Flag.SwitchesContinuation)
			|| primitive.hasFlag(Flag.Unknown);
		return mustKeep;
	}
}
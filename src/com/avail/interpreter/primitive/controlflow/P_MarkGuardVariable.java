/*
 * P_MarkGuardVariable.java
 * Copyright (c) 2016-2017, My Coverage Plan, Inc.
 * All rights reserved.
 */

package com.avail.interpreter.primitive.controlflow;

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.Unknown;

/**
 * <strong>Primitive:</strong> Mark a primitive failure variable from a nearby
 * invocation of {@link P_CatchException}.  The three states are (1) unmarked
 * and running the body, (2) handling an exception, and (3) running the final
 * ensure clause.  If the current state is 1, it can be marked as either 2 or 3.
 * If the state is 2, it can be marked as 3.  No other transitions are allowed.
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_MarkGuardVariable
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_MarkGuardVariable().init(
			2, CanInline, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Variable variable = args.get(0);
		final A_Number mark = args.get(1);
		return interpreter.markGuardVariable(variable, mark);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				variableTypeFor(
					enumerationWith(
						set(
							E_REQUIRED_FAILURE,
							E_INCORRECT_ARGUMENT_TYPE,
							E_HANDLER_SENTINEL,
							E_UNWIND_SENTINEL))),
				enumerationWith(
					set(
						E_HANDLER_SENTINEL,
						E_UNWIND_SENTINEL))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_CANNOT_MARK_HANDLER_FRAME));
	}
}

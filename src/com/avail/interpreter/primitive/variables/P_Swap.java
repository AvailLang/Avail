/**
 * P_Swap.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.avail.interpreter.primitive.variables;

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;

import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Swap the contents of two {@linkplain
 * VariableDescriptor variables}.
 */
public final class P_Swap extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_Swap().init(
			2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Variable var1 = args.get(0);
		final A_Variable var2 = args.get(1);
		if (!var1.kind().equals(var2.kind()))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES);
		}
		// This should work even on unassigned variables.
		final AvailObject value1 = var1.value();
		final AvailObject value2 = var2.value();
		// Record access specially, since we are using the "fast" variable
		// content accessor.
		if (interpreter.traceVariableReadsBeforeWrites())
		{
			final A_Fiber fiber = interpreter.fiber();
			fiber.recordVariableAccess(var1, true);
			fiber.recordVariableAccess(var2, true);
		}
		try
		{
			var1.setValue(value2);
			var2.setValue(value1);
			return interpreter.primitiveSuccess(NilDescriptor.nil());
		}
		catch (final VariableSetException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.functionType(
			TupleDescriptor.tuple(
				VariableTypeDescriptor.mostGeneralVariableType(),
				VariableTypeDescriptor.mostGeneralVariableType()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.enumerationWith(
			SetDescriptor.set(
				E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED));
	}
}

/**
 * P_AtomicFetchAndAdd.java
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Atomically read and update the specified
 * {@linkplain VariableDescriptor variable} by the specified {@linkplain
 * IntegerRangeTypeDescriptor amount}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_AtomicFetchAndAdd
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_AtomicFetchAndAdd().init(
			2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final AvailObject var = args.get(0);
		final AvailObject addend = args.get(1);
		try
		{
			return interpreter.primitiveSuccess(
				var.fetchAndAddValue(addend));
		}
		catch (
			final VariableGetException
				| VariableSetException e)
		{
			return interpreter.primitiveFailure(e);
		}
		catch (final ArithmeticException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				VariableTypeDescriptor.fromReadAndWriteTypes(
					IntegerRangeTypeDescriptor.extendedIntegers(),
					BottomTypeDescriptor.bottom()),
				IntegerRangeTypeDescriptor.extendedIntegers()),
			IntegerRangeTypeDescriptor.extendedIntegers());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type varType = argumentTypes.get(0);
		return varType.readType();
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_CANNOT_ADD_UNLIKE_INFINITIES,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED));
	}
}

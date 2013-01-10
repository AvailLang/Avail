/**
 * P_203_ExceptionStackDump.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.primitive;

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.exceptions.MapException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 202</strong>: Get the {@linkplain
 * ObjectTypeDescriptor#stackDumpAtom() stack dump} associated with the
 * specified {@linkplain ObjectTypeDescriptor#exceptionType() exception}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_203_ExceptionStackDump
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final @NotNull static Primitive instance =
		new P_203_ExceptionStackDump().init(1, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject exception = args.get(0);
		try
		{
			final AvailObject stackDump = exception.fieldMap().mapAt(
				ObjectTypeDescriptor.stackDumpAtom());
			return interpreter.primitiveSuccess(stackDump);
		}
		catch (final MapException e)
		{
			assert e.errorCode().equals(E_KEY_NOT_FOUND);
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ObjectTypeDescriptor.exceptionType()),
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.stringTupleType()));
	}

	@Override
	protected AvailObject privateFailureVariableType ()
	{
		return InstanceTypeDescriptor.on(
			E_INCORRECT_ARGUMENT_TYPE.numericCode());
	}
}

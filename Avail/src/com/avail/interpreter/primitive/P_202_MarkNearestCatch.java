/**
 * P_202_MarkNearestCatch.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 202</strong>: Mark the nearest frame corresponding to an
 * invocation of {@link P_200_CatchException} as ineligible to handle exceptions
 * any longer.
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
public final class P_202_MarkNearestCatch
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_202_MarkNearestCatch().init(1, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject code = args.get(0);
		return interpreter.markNearestGuard(code);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				AbstractEnumerationTypeDescriptor.withInstances(
					SetDescriptor.fromCollection(
						Arrays.asList(new A_Number[]
						{
							E_HANDLER_SENTINEL.numericCode(),
							E_UNWIND_SENTINEL.numericCode()
						})))),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(
				Arrays.asList(new A_Number[]
				{
					E_CANNOT_MARK_HANDLER_FRAME.numericCode(),
					E_NO_HANDLER_FRAME.numericCode()
				})));
	}
}

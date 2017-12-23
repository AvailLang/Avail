/**
 * P_AttemptJoinFiber.java
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

package com.avail.interpreter.primitive.fibers;

import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.utility.Mutable;
import com.avail.utility.MutableOrNull;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag
	.PERMIT_UNAVAILABLE;
import static com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_FIBER_CANNOT_JOIN_ITSELF;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> If the {@linkplain FiberDescriptor fiber} has
 * already {@linkplain ExecutionState#indicatesTermination() terminated}, then
 * answer right away; otherwise, record the current fiber as a joiner of the
 * specified fiber, and attempt to {@linkplain ExecutionState#PARKED park}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_AttemptJoinFiber
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_AttemptJoinFiber().init(
			1, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Fiber joinee = args.get(0);
		final Mutable<Boolean> shouldPark = new Mutable<>(false);
		final A_Fiber current = interpreter.fiber();
		// Forbid auto-joining.
		if (current.equals(joinee))
		{
			return interpreter.primitiveFailure(E_FIBER_CANNOT_JOIN_ITSELF);
		}
		joinee.lock(() ->
		{
			if (!joinee.executionState().indicatesTermination())
			{
				joinee.joiningFibers(
					joinee.joiningFibers().setWithElementCanDestroy(
						current, false));
				shouldPark.value = true;
			}
		});
		final MutableOrNull<Result> result = new MutableOrNull<>();
		if (shouldPark.value)
		{
			current.lock(() ->
			{
				// If permit is not available, then park this fiber.
				if (current.getAndSetSynchronizationFlag(
					PERMIT_UNAVAILABLE, true))
				{
					result.value = interpreter.primitivePark(
						stripNull(interpreter.function));
				}
				else
				{
					result.value = interpreter.primitiveSuccess(nil);
				}
			});
		}
		else
		{
			result.value = interpreter.primitiveSuccess(nil);
		}
		return result.value();
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(
				tuple(
					mostGeneralFiberType()),
				TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(E_FIBER_CANNOT_JOIN_ITSELF));
	}
}

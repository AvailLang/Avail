/**
 * P_UnparkFiber.java
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
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.FiberDescriptor.ExecutionState.PARKED;
import static com.avail.descriptor.FiberDescriptor.ExecutionState.SUSPENDED;
import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag
	.PERMIT_UNAVAILABLE;
import static com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Interpreter.resumeFromSuccessfulPrimitive;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Unpark the specified {@linkplain
 * FiberDescriptor fiber}. If the {@linkplain
 * SynchronizationFlag#PERMIT_UNAVAILABLE permit} associated with the fiber is
 * available, then simply continue. If the permit is not available, then restore
 * the permit and schedule {@linkplain Interpreter
 * #resumeFromSuccessfulPrimitive(AvailRuntime, A_Fiber, A_BasicObject, boolean)
 * resumption} of the fiber. A newly unparked fiber should always recheck the
 * basis for its having parked, to see if it should park again. Low-level
 * synchronization mechanisms may require the ability to spuriously unpark in
 * order to ensure correctness.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_UnparkFiber
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_UnparkFiber().init(
			1, CannotFail, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Fiber fiber = args.get(0);
		fiber.lock(() ->
		{
			// Restore the permit. If the fiber is parked, then unpark it.
			fiber.getAndSetSynchronizationFlag(PERMIT_UNAVAILABLE, false);
			if (fiber.executionState() == PARKED)
			{
				// Wake up the fiber.
				fiber.executionState(SUSPENDED);
				resumeFromSuccessfulPrimitive(
					currentRuntime(),
					fiber,
					nil,
					skipReturnCheck);
			}
			else
			{
				// Save the permit for next time.
				fiber.getAndSetSynchronizationFlag(
					PERMIT_UNAVAILABLE, false);
			}
		});
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(tuple(mostGeneralFiberType()), TOP.o());
	}
}

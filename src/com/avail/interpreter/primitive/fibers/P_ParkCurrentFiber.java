/*
 * P_ParkCurrentFiber.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.MutableOrNull;

import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag
	.PERMIT_UNAVAILABLE;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Attempt to acquire the {@linkplain
 * SynchronizationFlag#PERMIT_UNAVAILABLE permit} associated with the
 * {@linkplain FiberDescriptor#currentFiber() current} {@linkplain FiberDescriptor
 * fiber}. If the permit is available, then consume it and return immediately.
 * If the permit is not available, then {@linkplain ExecutionState#PARKED park}
 * the current fiber. A fiber suspended in this fashion may be resumed only by
 * calling {@link P_UnparkFiber}. A newly unparked fiber should always
 * recheck the basis for its having parked, to see if it should park again.
 * Low-level synchronization mechanisms may require the ability to spuriously
 * unpark in order to ensure correctness.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_ParkCurrentFiber
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ParkCurrentFiber().init(
			0, CannotFail, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(0);
		final A_Fiber fiber = interpreter.fiber();
		final MutableOrNull<Result> result = new MutableOrNull<>();
		fiber.lock(() ->
		{
			// If permit is not available, then park this fiber.
			result.value = fiber.getAndSetSynchronizationFlag(
					PERMIT_UNAVAILABLE, true)
				? interpreter.primitivePark(stripNull(interpreter.function))
				: interpreter.primitiveSuccess(nil);
		});
		return result.value();
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(emptyTuple(), TOP.o());
	}
}

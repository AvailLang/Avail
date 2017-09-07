/**
 * P_RaiseException.java
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
package com.avail.interpreter.primitive.controlflow;

import static com.avail.interpreter.Primitive.Flag.SwitchesContinuation;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Raise an exception. Scan the stack of
 * {@linkplain ContinuationDescriptor continuations} until one is found for
 * a {@linkplain FunctionDescriptor function} whose {@linkplain
 * CompiledCodeDescriptor code} is {@linkplain P_CatchException primitive
 * 200}. Get that continuation's second argument (a handler block of one
 * argument), and check if that handler block will accept {@code
 * exceptionValue}. If not, keep looking. If it will accept it, unwind the
 * stack so that the primitive 200 continuation is the top entry, and invoke
 * the handler block with {@code exceptionValue}. If there is no suitable
 * handler block, then fail this primitive (with the unhandled exception).
 */
public final class P_RaiseException
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_RaiseException().init(
			1, SwitchesContinuation);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_BasicObject exception = args.get(0);
		// Attach the current continuation to the exception, so that a stack
		// dump can be obtained later.
		final A_Map fieldMap = exception.fieldMap();
		final A_Map newFieldMap = fieldMap.mapAtPuttingCanDestroy(
			ObjectTypeDescriptor.stackDumpAtom(),
			interpreter.reifiedContinuation.makeImmutable(),
			false);
		final AvailObject newException =
			ObjectDescriptor.objectFromMap(newFieldMap);
		// Search for an applicable exception handler, and invoke it if found.
		return interpreter.searchForExceptionHandler(newException);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.functionType(
			TupleDescriptor.tuple(
				ObjectTypeDescriptor.exceptionType()),
			BottomTypeDescriptor.bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return ObjectTypeDescriptor.exceptionType();
	}
}

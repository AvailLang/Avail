/**
 * P_RemoveFiberVariable.java
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

import static com.avail.descriptor.AtomDescriptor.SpecialAtom.HERITABLE_KEY;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;

import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Disassociate the given {@linkplain
 * AtomDescriptor name} (key) from the variables of the current {@linkplain
 * FiberDescriptor fiber}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_RemoveFiberVariable
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_RemoveFiberVariable().init(
			1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Atom key = args.get(0);
		if (key.isAtomSpecial())
		{
			return interpreter.primitiveFailure(E_SPECIAL_ATOM);
		}
		final A_Fiber fiber = interpreter.fiber();
		// Choose the correct map based on the heritability of the key.
		final boolean heritable =
			!key.getAtomProperty(HERITABLE_KEY.atom).equalsNil();
		final A_Map globals =
			heritable
			? fiber.heritableFiberGlobals()
			: fiber.fiberGlobals();
		if (!globals.hasKey(key))
		{
			return interpreter.primitiveFailure(
				E_NO_SUCH_FIBER_VARIABLE);
		}
		if (heritable)
		{
			fiber.heritableFiberGlobals(
				globals.mapWithoutKeyCanDestroy(key, true));
		}
		else
		{
			fiber.fiberGlobals(
				globals.mapWithoutKeyCanDestroy(key, true));
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.functionType(
			TupleDescriptor.tuple(
				ATOM.o()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.enumerationWith(
			SetDescriptor.set(
				E_SPECIAL_ATOM,
				E_NO_SUCH_FIBER_VARIABLE));
	}
}

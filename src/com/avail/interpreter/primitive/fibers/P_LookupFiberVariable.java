/**
 * P_LookupFiberVariable.java
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

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.HERITABLE_KEY;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.E_NO_SUCH_FIBER_VARIABLE;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Lookup the given {@linkplain
 * AtomDescriptor name} (key) in the variables of the current
 * {@linkplain FiberDescriptor fiber}.
 */
public final class P_LookupFiberVariable
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_LookupFiberVariable().init(
			1, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final A_Atom key = args.get(0);
		final A_Fiber fiber = interpreter.fiber();
		// Choose the correct map based on the heritability of the key.
		final A_Map globals =
			key.getAtomProperty(HERITABLE_KEY.atom).equalsNil()
			? fiber.fiberGlobals()
			: fiber.heritableFiberGlobals();
		if (!globals.hasKey(key))
		{
			return interpreter.primitiveFailure(
				E_NO_SUCH_FIBER_VARIABLE);
		}
		return interpreter.primitiveSuccess(
			globals.mapAt(key).makeImmutable());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(tuple(ATOM.o()), ANY.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_NO_SUCH_FIBER_VARIABLE));
	}
}

/**
 * P_023_AddWriteReactor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import static com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Fallibility.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 23</strong>: Add a {@linkplain VariableAccessReactor
 * write reactor} to the specified {@linkplain VariableDescriptor variable}.
 * The supplied {@linkplain AtomDescriptor key} may be used subsequently to
 * remove the write reactor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_023_AddWriteReactor
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_023_AddWriteReactor().init(3, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Variable var = args.get(0);
		final A_Atom key = args.get(1);
		final A_Function reactorFunction = args.get(2);
		// Forbid special atoms.
		if (key.isAtomSpecial())
		{
			return interpreter.primitiveFailure(E_SPECIAL_ATOM);
		}
		final A_Function sharedFunction = reactorFunction.makeShared();
		final VariableAccessReactor writeReactor =
			new VariableAccessReactor(sharedFunction);
		var.addWriteReactor(key, writeReactor);
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				VariableTypeDescriptor.mostGeneralType(),
				ATOM.o(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					TOP.o())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_SPECIAL_ATOM.numericCode());
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		@SuppressWarnings("unused")
		final A_Type varType = argumentTypes.get(0);
		final A_Type keyType = argumentTypes.get(1);
		@SuppressWarnings("unused")
		final A_Type functionType = argumentTypes.get(2);
		if (keyType.isEnumeration())
		{
			boolean allSpecial = true;
			boolean noneSpecial = true;
			for (final A_Atom key : keyType.instances())
			{
				final boolean isSpecial = key.isAtomSpecial();
				allSpecial = allSpecial && isSpecial;
				noneSpecial = noneSpecial && !isSpecial;
			}
			// The aggregate booleans can only both be true in the degenerate
			// case that keyType is ⊥, which should be impossible.
			if (noneSpecial)
			{
				return CallSiteCannotFail;
			}
			if (allSpecial)
			{
				return CallSiteMustFail;
			}
		}
		return CallSiteCanFail;
	}
}

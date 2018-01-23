/**
 * P_AddWriteReactor.java
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

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VariableDescriptor;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor
	.mostGeneralVariableType;
import static com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM;
import static com.avail.interpreter.Primitive.Fallibility.*;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Add a {@linkplain VariableAccessReactor
 * write reactor} to the specified {@linkplain VariableDescriptor variable}.
 * The supplied {@linkplain AtomDescriptor key} may be used subsequently to
 * remove the write reactor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_AddWriteReactor
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_AddWriteReactor().init(
			3, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
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
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(
				tuple(
					mostGeneralVariableType(),
					ATOM.o(),
					functionType(
						emptyTuple(),
						TOP.o())),
				TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_SPECIAL_ATOM));
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
//		final A_Type varType = argumentTypes.get(0);
		final A_Type keyType = argumentTypes.get(1);
//		final A_Type functionType = argumentTypes.get(2);
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

/**
 * P_CreateFiberHeritableAtom.java
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
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.utility.MutableOrNull;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.HERITABLE_KEY;
import static com.avail.descriptor.AtomDescriptor.createAtom;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ModuleDescriptor.currentModule;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME;
import static com.avail.exceptions.AvailErrorCode.E_ATOM_ALREADY_EXISTS;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Create a new {@linkplain AtomDescriptor atom}
 * with the given name that represents a {@linkplain
 * SpecialAtom#HERITABLE_KEY heritable} {@linkplain FiberDescriptor fiber}
 * variable.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreateFiberHeritableAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CreateFiberHeritableAtom().init(
			1, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final A_String name = args.get(0);
		final A_Module module = currentModule();
		final MutableOrNull<A_Atom> trueName =
			new MutableOrNull<>();
		final MutableOrNull<AvailErrorCode> errorCode =
			new MutableOrNull<>();
		if (!module.equalsNil())
		{
			module.lock(
				() ->
				{
					final A_Set trueNames =
						module.trueNamesForStringName(name);
					if (trueNames.setSize() == 0)
					{
						final A_Atom newName = createAtom(name, module);
						newName.setAtomProperty(
							HERITABLE_KEY.atom, trueObject());
						module.addPrivateName(newName);
						trueName.value = newName;
					}
					else if (trueNames.setSize() == 1)
					{
						errorCode.value = E_ATOM_ALREADY_EXISTS;
					}
					else
					{
						errorCode.value = E_AMBIGUOUS_NAME;
					}
				});
		}
		else
		{
			final A_Atom newName = createAtom(name, nil);
			newName.setAtomProperty(HERITABLE_KEY.atom, trueObject());
			trueName.value = newName;
		}
		if (errorCode.value != null)
		{
			return interpreter.primitiveFailure(errorCode.value);
		}
		return interpreter.primitiveSuccess(trueName.value().makeShared());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(stringType()),
			ATOM.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_ATOM_ALREADY_EXISTS, E_AMBIGUOUS_NAME));
	}
}

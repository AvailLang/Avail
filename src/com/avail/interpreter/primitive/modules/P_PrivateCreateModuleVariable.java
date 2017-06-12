/**
 * P_PrivateCreateModuleVariable.java
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
package com.avail.interpreter.primitive.modules;

import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import java.util.List;

import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Create a {@link VariableSharedGlobalDescriptor
 * global variable or constant}, registering it with the given module.
 */
public final class P_PrivateCreateModuleVariable extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_PrivateCreateModuleVariable().init(
			5, CanFold, CanInline, Private, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		final A_Module module = args.get(0);
		final A_String name = args.get(1);
		final A_Type varType = args.get(2);
		final boolean isConstant = args.get(3).extractBoolean();
		final boolean stablyComputed = args.get(4).extractBoolean();

		assert isConstant || !stablyComputed;

		final A_Variable variable = VariableSharedGlobalDescriptor.createGlobal(
			varType, module, name, isConstant);
		if (stablyComputed)
		{
			variable.valueWasStablyComputed(true);
		}
		// The compiler should ensure this will always succeed.
		if (isConstant)
		{
			module.addConstantBinding(name, variable);
		}
		else
		{
			module.addVariableBinding(name, variable);
		}
		return interpreter.primitiveSuccess(variable);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				Types.MODULE.o(),
				TupleTypeDescriptor.stringType(),
				VariableTypeDescriptor.meta(),
				EnumerationTypeDescriptor.booleanObject(),
				EnumerationTypeDescriptor.booleanObject()),
			Types.TOP.o());
	}
}
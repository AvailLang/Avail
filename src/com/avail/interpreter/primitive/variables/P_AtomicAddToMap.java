/**
 * P_AtomicAddToMap.java
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

import com.avail.descriptor.*;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.effects.LoadingEffectToRunPrimitive;

import java.util.List;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Atomically read and update the map in the
 * specified {@linkplain VariableDescriptor variable} by adding the given key
 * and value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_AtomicAddToMap
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_AtomicAddToMap().init(
			3, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final AvailObject variable = args.get(0);
		final AvailObject key = args.get(1);
		final AvailObject value = args.get(2);
		final AvailLoader loader = interpreter.availLoaderOrNull();
		try
		{
			variable.atomicAddToMap(key, value);
		}
		catch (
			final VariableGetException
				| VariableSetException e)
		{
			return interpreter.primitiveFailure(e);
		}
		if (loader != null)
		{
			loader.recordEffect(
				new LoadingEffectToRunPrimitive(
					SpecialMethodAtom.ADD_TO_MAP_VARIABLE.bundle,
					variable,
					key,
					value));
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				VariableTypeDescriptor.fromReadAndWriteTypes(
					MapTypeDescriptor.mostGeneralType(),
					BottomTypeDescriptor.bottom()),
				ANY.o(),
				ANY.o()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_CANNOT_READ_UNASSIGNED_VARIABLE,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE));
	}
}

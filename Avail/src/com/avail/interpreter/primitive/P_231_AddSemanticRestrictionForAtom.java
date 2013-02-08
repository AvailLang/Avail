/**
 * P_231_AddSemanticRestrictionForAtom.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 231</strong>: Add a type restriction function.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_231_AddSemanticRestrictionForAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_231_AddSemanticRestrictionForAtom().init(2, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject atom = args.get(0);
		final AvailObject function = args.get(1);
		final AvailObject functionType = function.kind();
		final AvailObject tupleType = functionType.argsTupleType();
		for (int i = function.code().numArgs(); i >= 1; i--)
		{
			if (!tupleType.typeAtIndex(i).isInstanceOf(
				InstanceMetaDescriptor.on(InstanceMetaDescriptor.topMeta())))
			{
				return interpreter.primitiveFailure(
					E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES);
			}
		}
		final AvailLoader loader = FiberDescriptor.current().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		try
		{
			loader.addTypeRestriction(atom, function);
		}
		catch (final SignatureException e)
		{
			return interpreter.primitiveFailure(e);
		}
		function.code().setMethodName(
			StringDescriptor.from(
				String.format("Semantic restriction of %s", atom.name())));
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				FunctionTypeDescriptor.forReturnType(
					InstanceMetaDescriptor.topMeta())),
			TOP.o());
	}
}

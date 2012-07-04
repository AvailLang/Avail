/**
 * P_246_PublishName.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.exceptions.AvailErrorCode.E_COMPILATION_IS_OVER;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.exceptions.AmbiguousNameException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 246:</strong> Publish the {@linkplain AtomDescriptor atom}
 * associated with the specified {@linkplain StringDescriptor string} as a
 * public name of the current {@linkplain ModuleDescriptor module}. This has the
 * same effect as listing the string in the "Names" section of the current
 * module. Fails if called at runtime (rather than during compilation).
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class P_246_PublishName
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static @NotNull Primitive instance =
		new P_246_PublishName().init(1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject name = args.get(0);
		final AvailObject module = interpreter.module();
		if (module == null)
		{
			return interpreter.primitiveFailure(E_COMPILATION_IS_OVER);
		}
		try
		{
			final AvailObject trueName = interpreter.lookupName(name);
			module.atNewNamePut(name, trueName);
			module.atNameAdd(name, trueName);
			return interpreter.primitiveSuccess(NullDescriptor.nullObject());
		}
		catch (final AmbiguousNameException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringTupleType()),
			TOP.o());
	}
}

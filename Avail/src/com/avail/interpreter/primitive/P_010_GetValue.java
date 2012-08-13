/**
 * P_010_GetValue.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 10:</strong> There are two possibilities.  The
 * {@linkplain VariableDescriptor variable} is mutable, in which case we
 * want to destroy it, or the variable is immutable, in which case we want
 * to make sure the extracted value becomes immutable (in case the variable
 * is being held onto by something). Since the primitive invocation code is
 * going to erase it if it's mutable anyhow, only the second case requires
 * any real work.
 */
public class P_010_GetValue extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_010_GetValue().init(
		1, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject var = args.get(0);
		final AvailObject value = var.value();
		if (value.equalsNull())
		{
			return interpreter.primitiveFailure(
				E_CANNOT_READ_UNASSIGNED_VARIABLE);
		}
		if (!var.descriptor().isMutable())
		{
			value.makeImmutable();
		}
		return interpreter.primitiveSuccess(value);
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(VariableTypeDescriptor.mostGeneralType()),
			ANY.o());
	}

	@Override
	public AvailObject returnTypeGuaranteedByVMForArgumentTypes (
		final List<AvailObject> argumentTypes)
	{
		final AvailObject varType = argumentTypes.get(0);
		final AvailObject readType = varType.readType();
		return readType.equals(TOP.o()) ? ANY.o() : readType;
	}
}
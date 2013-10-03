/**
 * P_126_SpecialAtoms.java
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

import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 126:</strong> Answer the {@linkplain SetDescriptor
 * set} of {@linkplain AvailRuntime#specialAtoms() special atoms} known to
 * the {@linkplain AvailRuntime Avail runtime}.
 */
public final class P_126_SpecialAtoms extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_126_SpecialAtoms().init(
		0, CanFold, CannotFail);

	/** The cached set of special atoms defined by the Avail runtime. */
	private final static A_Set specialAtomsSet =
		SetDescriptor.fromCollection(AvailRuntime.specialAtoms());

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 0;
		return interpreter.primitiveSuccess(specialAtomsSet);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.empty(),
			SetTypeDescriptor.setTypeForSizesContentType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				ATOM.o()));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM(
		final List<? extends A_Type> argumentTypes)
	{
		return AbstractEnumerationTypeDescriptor.withInstance(specialAtomsSet);
	}
}

/**
 * P_264_GrammaticalRestrictionFromAtoms.java
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
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 264</strong>: Message precedence declaration with
 * {@linkplain TupleDescriptor tuple} of {@linkplain SetDescriptor sets} of
 * messages to exclude for each argument position. Note that the tuple's
 * elements should correspond with occurrences of underscore in the method
 * names, *not* with the (top-level) arguments of the method. This
 * distinction is only apparent when guillemet notation is used to accept
 * tuples of arguments.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_264_GrammaticalRestrictionFromAtoms
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final @NotNull static Primitive instance =
		new P_264_GrammaticalRestrictionFromAtoms().init(2, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject atomSet = args.get(0);
		final AvailObject exclusionsTuple = args.get(1);
		try
		{
			final AvailObject atomSetAsTuple = atomSet.asTuple();
			for (int i = atomSetAsTuple.tupleSize(); i >= 1; i--)
			{
				final AvailObject atom = atomSetAsTuple.tupleAt(i);
				interpreter.atDisallowArgumentMessages(
					atom,
					exclusionsTuple);
			}
		}
		catch (final SignatureException e)
		{
			return interpreter.primitiveFailure(e);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ATOM.o()),
				TupleTypeDescriptor.zeroOrMoreOf(
					SetTypeDescriptor.setTypeForSizesContentType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						ATOM.o()))),
			TOP.o());
	}
}

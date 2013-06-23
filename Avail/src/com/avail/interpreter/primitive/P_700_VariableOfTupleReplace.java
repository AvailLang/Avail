/**
 * P_700_VariableOfTupleReplace.java Copyright © 1993-2013, Mark van Gulik and
 * Todd L Smith. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 700</strong>: Replace the value at the index of the
 * {@linkplain TupleDescriptor tuple} {@linkplain A_Variable variable}
 * with a new value.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public final class P_700_VariableOfTupleReplace extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_700_VariableOfTupleReplace().init(
			3,
			CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		final A_Variable var = args.get(0);
		final A_Number index = args.get(1);
		final A_BasicObject newValue = args.get(2);
		final A_Tuple tuple;
		try
		{
			tuple = var.getValue();
			final int tupleIndex = index.extractInt();
			if (tupleIndex > tuple.tupleSize())
			{
				return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
			}
			final A_Tuple newTuple = tuple.tupleAtPuttingCanDestroy(
				tupleIndex, newValue, true);
			var.setValue(newTuple);
			return interpreter.primitiveSuccess(NilDescriptor.nil());
		}
		catch (final VariableGetException e)
		{
			return interpreter.primitiveFailure(e);
		}
		catch (final VariableSetException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				VariableTypeDescriptor.fromReadAndWriteTypes(
					TupleTypeDescriptor.mostGeneralType(),
					BottomTypeDescriptor.bottom()),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				ANY.o()),
			TOP.o());
	}
}

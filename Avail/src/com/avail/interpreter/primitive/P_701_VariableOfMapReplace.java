/**
 * P_701_VariableOfMapReplace.java Copyright © 1993-2013, Mark van Gulik and
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
 * <strong>Primitive 701</strong>: Replace the value at the key of the
 * {@linkplain MapDescriptor map} {@linkplain A_Variable variable}
 * with a new value.
 *
 * @author Rich &lt;rich@availlang.org&gt;
 */
public final class P_701_VariableOfMapReplace extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance = new P_701_VariableOfMapReplace()
		.init(3, CanInline,HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		final A_Variable var = args.get(0);
		final A_BasicObject key = args.get(1);
		final A_BasicObject newValue = args.get(2);
		final A_Map map;
		try
		{
			map = var.getValue();
			if (!map.hasKey(key))
			{
				return interpreter.primitiveFailure(E_KEY_NOT_FOUND);
			}
			final A_Map newMap = map.mapAtPuttingCanDestroy(
				key, newValue, true);
			var.setValue(newMap);
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
					MapTypeDescriptor.mostGeneralType(),
					BottomTypeDescriptor.bottom()),
				ANY.o(),
				ANY.o()),
			TOP.o());
	}
}

/**
 * P_082_MapAtKey.java
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
import static com.avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 82:</strong> Look up the key in the {@linkplain
 * MapDescriptor map}, answering the corresponding value.
 */
public class P_082_MapAtKey extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_082_MapAtKey().init(
		2, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject map = args.get(0);
		final AvailObject key = args.get(1);
		if (!map.hasKey(key))
		{
			return interpreter.primitiveFailure(E_KEY_NOT_FOUND);
		}
		return interpreter.primitiveSuccess(map.mapAt(key));
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				MapTypeDescriptor.mostGeneralType(),
				ANY.o()),
			ANY.o());
	}

	@Override
	public AvailObject returnTypeGuaranteedByVM (
		final List<AvailObject> argumentTypes)
	{
		final AvailObject mapType = argumentTypes.get(0);
		final AvailObject keyType = argumentTypes.get(1);
		if (mapType.isEnumeration() && keyType.isEnumeration())
		{
			AvailObject values = SetDescriptor.empty();
			final AvailObject keyTypeInstances = keyType.instances();
			for (final AvailObject mapInstance : mapType.instances())
			{
				for (final AvailObject keyInstance : keyTypeInstances)
				{
					if (mapInstance.hasKey(keyInstance))
					{
						values = values.setWithElementCanDestroy(
							mapInstance.mapAt(keyInstance),
							true);
					}
				}
			}
			return AbstractEnumerationTypeDescriptor.withInstances(values);
		}
		// Fall back on the map type's value type.
		return mapType.valueType();
	}
}
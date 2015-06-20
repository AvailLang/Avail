/**
 * P_278_EnvironmentMap.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
 * All rights reserved.
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

package com.avail.interpreter.primitive.general;

import static com.avail.interpreter.Primitive.Flag.*;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Map;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 278</strong>: Answer a {@linkplain A_Map map} that
 * represents the {@linkplain System#getenv() environment} of the Avail virtual
 * machine.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_278_EnvironmentMap
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance = new P_278_EnvironmentMap().init(
		0, CannotFail, CanInline, HasSideEffect);

	/**
	 * The cached {@linkplain System#getenv() environment} {@linkplain A_Map
	 * map}. The content may be {@code null} if memory pressure is high (or if
	 * the {@linkplain P_278_EnvironmentMap primitive} has never been called.
	 */
	private static SoftReference<A_Map> environmentMap =
		new SoftReference<A_Map>(null);

	/**
	 * Get the {@linkplain #environmentMap environment map}, creating a new one
	 * as necessary (either because it has never been created or because the
	 * garbage collector has discarded it).
	 *
	 * @return The environment map.
	 */
	private static A_Map getEnvironmentMap ()
	{
		// Don't bother to synchronize. If there's a race, then some redundant
		// work will be done. Big deal. This is likely to be cheaper in general
		// than repeatedly entering and leaving a critical section.
		A_Map result = environmentMap.get();
		if (result == null)
		{
			final Map<String, String> map = System.getenv();
			A_Tuple bindings = TupleDescriptor.empty();
			for (final Map.Entry<String, String> entry : map.entrySet())
			{
				bindings = bindings.appendCanDestroy(
					TupleDescriptor.from(
						StringDescriptor.from(entry.getKey()),
						StringDescriptor.from(entry.getValue())),
					true);
			}
			result = MapDescriptor.newWithBindings(bindings).makeShared();
			environmentMap = new SoftReference<A_Map>(result);
		}
		return result;
	}

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 0;
		return interpreter.primitiveSuccess(getEnvironmentMap());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.empty(),
			MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleTypeDescriptor.stringType(),
				TupleTypeDescriptor.stringType()));
	}
}

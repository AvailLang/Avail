/**
 * P_MapWithoutKey.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.maps;

import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Answer a new {@linkplain MapDescriptor
 * map}, but without the given key. Answer the original map if the key does
 * not occur in it.
 */
public final class P_MapWithoutKey extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_MapWithoutKey().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Map map = args.get(0);
		final A_BasicObject key = args.get(1);
		return interpreter.primitiveSuccess(
			map.mapWithoutKeyCanDestroy(key, true));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				MapTypeDescriptor.mostGeneralType(),
				ANY.o()),
			MapTypeDescriptor.mostGeneralType());
	}


	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type mapType = argumentTypes.get(0);
		final A_Type keyType = argumentTypes.get(1);

		final A_Type mapSizes = mapType.sizeRange();
		assert mapSizes.lowerInclusive();
		A_Number minSize = mapSizes.lowerBound();
		if (mapType.keyType().typeIntersection(keyType).isBottom())
		{
			// That key will not be found.
			return mapType;
		}
		// It's possible that the new map will be smaller by one.
		if (minSize.greaterThan(IntegerDescriptor.zero()))
		{
			minSize = minSize.minusCanDestroy(
				IntegerDescriptor.one(), false);
		}
		final A_Type newSizeRange = IntegerRangeTypeDescriptor.create(
			minSize, true, mapSizes.upperBound(), mapSizes.upperInclusive());
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			newSizeRange, mapType.keyType(), mapType.valueType());
	}
}

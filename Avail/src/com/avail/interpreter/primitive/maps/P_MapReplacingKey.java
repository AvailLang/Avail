/**
 * P_MapReplacingKey.java
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
 * map} like the given map, but also including the binding between {@code
 * key} and {@code value}. Overwrite any existing value if the key is
 * already present.
 */
public final class P_MapReplacingKey extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_MapReplacingKey().init(
			3, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Map map = args.get(0);
		final A_BasicObject key = args.get(1);
		final A_BasicObject value = args.get(2);
		return interpreter.primitiveSuccess(map.mapAtPuttingCanDestroy(
			key,
			value,
			true));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				MapTypeDescriptor.mostGeneralType(),
				ANY.o(),
				ANY.o()),
			MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
				IntegerRangeTypeDescriptor.naturalNumbers(),
				ANY.o(),
				ANY.o()));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type mapType = argumentTypes.get(0);
		final A_Type addedKeyType = argumentTypes.get(1);
		final A_Type addedValueType = argumentTypes.get(2);

		final A_Type oldMapKeyType = mapType.keyType();
		final A_Type newKeyType = oldMapKeyType.typeUnion(addedKeyType);
		final A_Type newValueType =
			mapType.valueType().typeUnion(addedValueType);
		final A_Type oldSizes = mapType.sizeRange();
		// Now there's at least one element.
		final A_Number newMin =
			oldMapKeyType.typeIntersection(newKeyType).isBottom()
				? oldSizes.lowerBound().plusCanDestroy(
					IntegerDescriptor.one(), false)
				: oldSizes.lowerBound();
		// ...and at most one more element.  We add two and make the bound
		// exclusive to accommodate positive infinity.
		final A_Number newMaxPlusOne =
			oldSizes.upperBound().plusCanDestroy(
				IntegerDescriptor.two(), false);
		final A_Type newSizes = IntegerRangeTypeDescriptor.create(
			newMin.makeImmutable(),
			true,
			newMaxPlusOne.makeImmutable(),
			false);

		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			newSizes,
			newKeyType.makeImmutable(),
			newValueType.makeImmutable());
	}
}
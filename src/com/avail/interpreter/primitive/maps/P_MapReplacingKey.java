/*
 * P_MapReplacingKey.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.MapDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.one;
import static com.avail.descriptor.IntegerDescriptor.two;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType;
import static com.avail.descriptor.MapTypeDescriptor.mostGeneralMapType;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;

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
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_MapReplacingKey().init(
			3, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Map map = interpreter.argument(0);
		final A_BasicObject key = interpreter.argument(1);
		final A_BasicObject value = interpreter.argument(2);
		return interpreter.primitiveSuccess(
			map.mapAtPuttingCanDestroy(key, value, true));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralMapType(),
				ANY.o(),
				ANY.o()),
			mapTypeForSizesKeyTypeValueType(
				naturalNumbers(),
				ANY.o(),
				ANY.o()));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
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
		A_Number newMin = oldSizes.lowerBound();
		if (oldMapKeyType.typeIntersection(newKeyType).isBottom()
			|| newMin.equalsInt(0))
		{
			newMin = newMin.plusCanDestroy(one(), false);
		}
		// ...and at most one more element.  We add two and make the bound
		// exclusive to accommodate positive infinity.
		final A_Number newMaxPlusOne =
			oldSizes.upperBound().plusCanDestroy(two(), false);
		final A_Type newSizes = integerRangeType(
			newMin, true, newMaxPlusOne, false);

		return mapTypeForSizesKeyTypeValueType(
			newSizes, newKeyType, newValueType);
	}
}

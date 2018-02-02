/*
 * P_TupleToObjectType.java
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

package com.avail.interpreter.primitive.objects;

import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.ObjectDescriptor.objectFromTuple;
import static com.avail.descriptor.ObjectTypeDescriptor.mostGeneralObjectType;
import static com.avail.descriptor.ObjectTypeDescriptor.objectTypeFromMap;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Convert a {@linkplain TupleDescriptor tuple}
 * of field assignment into an {@linkplain ObjectDescriptor object}. A field
 * assignment is a 2-tuple whose first element is an {@linkplain AtomDescriptor
 * atom} that represents the field and whose second element is its value.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_TupleToObject
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_TupleToObject().init(
			1, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final A_Tuple tuple = args.get(0);
		return interpreter.primitiveSuccess(objectFromTuple(tuple));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				zeroOrMoreOf(
					tupleTypeForTypes(
						ATOM.o(),
						ANY.o()))),
			mostGeneralObjectType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type tupleType = argumentTypes.get(0);
		final A_Type tupleSizes = tupleType.sizeRange();
		final A_Number tupleSizeLowerBound = tupleSizes.lowerBound();
		if (!tupleSizeLowerBound.equals(tupleSizes.upperBound())
			|| !tupleSizeLowerBound.isInt())
		{
			// Variable number of <key,value> pairs.  Give up.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
		}
		final int tupleSize = tupleSizeLowerBound.extractInt();
		A_Map fieldTypeMap = emptyMap();
		for (int i = 1; i <= tupleSize; i++)
		{
			final A_Type pairType = tupleType.typeAtIndex(i);
			assert pairType.sizeRange().lowerBound().extractInt() == 2;
			assert pairType.sizeRange().upperBound().extractInt() == 2;
			final A_Type keyType = pairType.typeAtIndex(1);
			if (!keyType.isEnumeration()
				|| !keyType.instanceCount().equalsInt(1))
			{
				// Can only strengthen if all key atoms are statically known.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes);
			}
			final A_Atom keyValue = keyType.instance();
			if (fieldTypeMap.hasKey(keyValue))
			{
				// In case the semantics of this situation change.  Give up.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes);
			}
			assert keyValue.isAtom();
			final A_Type valueType = pairType.typeAtIndex(2);
			fieldTypeMap = fieldTypeMap.mapAtPuttingCanDestroy(
				keyValue, valueType, true);
		}
		return objectTypeFromMap(fieldTypeMap);
	}
}

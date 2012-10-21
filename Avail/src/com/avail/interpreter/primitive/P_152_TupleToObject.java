/**
 * P_150_TupleToObjectType.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 152:</strong> Convert a {@linkplain TupleDescriptor tuple}
 * of field assignment into an {@linkplain ObjectDescriptor object}. A field
 * assignment is a 2-tuple whose first element is an {@linkplain AtomDescriptor
 * atom} that represents the field and whose second element is its value.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_152_TupleToObject
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_152_TupleToObject().init(
		1, CanFold, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject tuple = args.get(0);
		return interpreter.primitiveSuccess(
			ObjectDescriptor.objectFromTuple(tuple));
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.zeroOrMoreOf(
					TupleTypeDescriptor.forTypes(
						ATOM.o(),
						ANY.o()))),
			ObjectTypeDescriptor.mostGeneralType());
	}

	@Override
	public AvailObject returnTypeGuaranteedByVM (
		final List<AvailObject> argumentTypes)
	{
		final AvailObject tupleType = argumentTypes.get(0);
		final AvailObject tupleSizes = tupleType.sizeRange();
		final AvailObject tupleSizeLowerBound = tupleSizes.lowerBound();
		if (!tupleSizeLowerBound.equals(tupleSizes.upperBound())
			|| !tupleSizeLowerBound.isInt())
		{
			// Variable number of <key,value> pairs.  Give up.
			return super.returnTypeGuaranteedByVM(argumentTypes);
		}
		final int tupleSize = tupleSizeLowerBound.extractInt();
		AvailObject fieldTypeMap = MapDescriptor.empty();
		for (int i = 1; i <= tupleSize; i++)
		{
			final AvailObject pairType = tupleType.typeAtIndex(i);
			assert pairType.sizeRange().lowerBound().extractInt() == 2;
			assert pairType.sizeRange().upperBound().extractInt() == 2;
			final AvailObject keyType = pairType.typeAtIndex(1);
			if (!keyType.isEnumeration()
				|| !keyType.instanceCount().equals(IntegerDescriptor.one()))
			{
				// Can only strengthen if all key atoms are statically known.
				return super.returnTypeGuaranteedByVM(argumentTypes);
			}
			final AvailObject keyValue = keyType.instance();
			if (fieldTypeMap.hasKey(keyValue))
			{
				// In case the semantics of this situation change.  Give up.
				return super.returnTypeGuaranteedByVM(argumentTypes);
			}
			assert keyValue.isAtom();
			final AvailObject valueType = pairType.typeAtIndex(2);
			fieldTypeMap = fieldTypeMap.mapAtPuttingCanDestroy(
				keyValue,
				valueType,
				true);
		}
		return ObjectTypeDescriptor.objectTypeFromMap(fieldTypeMap);
	}
}

/**
 * P_188_CreateCompiledCode.java
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 188:</strong> Answer a {@linkplain
 * CompiledCodeDescriptor compiled code} with the given data.
 */
public class P_188_CreateCompiledCode extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_188_CreateCompiledCode().init(
		7, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 7;
		final AvailObject nybs = args.get(0);
		final AvailObject primitive = args.get(1);
		final AvailObject functionType = args.get(2);
		final AvailObject allLiterals = args.get(3);
		final AvailObject locals = args.get(4);
		final AvailObject outers = args.get(5);
		final AvailObject stack = args.get(6);

		final int nLocals = locals.extractInt();
		final int nOuters = outers.extractInt();
		final int primitiveInt = primitive.extractInt();
		final int nLiteralsTotal = allLiterals.tupleSize();

		if (primitiveInt != 0)
		{
			final Primitive prim = Primitive.byPrimitiveNumber(
				primitiveInt);
			if (prim == null || prim.hasFlag(Private))
			{
				return interpreter.primitiveFailure(
					E_INVALID_PRIMITIVE_NUMBER);
			}
			final AvailObject restrictionSignature =
				prim.blockTypeRestriction();
			if (!restrictionSignature.isSubtypeOf(functionType))
			{
				return interpreter.primitiveFailure(
					E_FUNCTION_DISAGREES_WITH_PRIMITIVE_RESTRICTION);
			}
		}

		final AvailObject localTypes =
			allLiterals.copyTupleFromToCanDestroy(
				nLiteralsTotal - nLocals + 1,
				nLiteralsTotal,
				false);
		for (int i = 1; i < nLocals; i++)
		{
			if (!localTypes.tupleAt(i).isType())
			{
				return interpreter.primitiveFailure(
					E_LOCAL_TYPE_LITERAL_IS_NOT_A_TYPE);
			}
		}

		final AvailObject outerTypes =
			allLiterals.copyTupleFromToCanDestroy(
				nLiteralsTotal - nLocals - nOuters + 1,
				nLiteralsTotal - nLocals,
				false);
		for (int i = 1; i < nOuters; i++)
		{
			if (!outerTypes.tupleAt(i).isType())
			{
				return interpreter.primitiveFailure(
					E_OUTER_TYPE_LITERAL_IS_NOT_A_TYPE);
			}
		}

		return interpreter.primitiveSuccess(
			CompiledCodeDescriptor.create(
				nybs,
				nLocals,
				stack.extractInt(),
				functionType,
				primitive.extractInt(),
				allLiterals.copyTupleFromToCanDestroy(
					1,
					nLiteralsTotal - nLocals - nOuters,
					false),
				localTypes,
				outerTypes,
				interpreter.module(),
				0));
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.nybbles()),
				IntegerRangeTypeDescriptor.unsignedShorts(),
				FunctionTypeDescriptor.meta(),
				TupleTypeDescriptor.mostGeneralType(),
				IntegerRangeTypeDescriptor.wholeNumbers(),
				IntegerRangeTypeDescriptor.wholeNumbers(),
				IntegerRangeTypeDescriptor.wholeNumbers()),
			CompiledCodeTypeDescriptor.mostGeneralType());
	}
}
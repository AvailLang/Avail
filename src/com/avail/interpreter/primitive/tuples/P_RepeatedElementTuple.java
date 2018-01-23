/**
 * P_RepeatedElementTuple.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.tuples;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.RepeatedElementTupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.RepeatedElementTupleDescriptor
	.createRepeatedElementTuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor
	.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.E_EXCEEDS_VM_LIMIT;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Create a {@linkplain
 * RepeatedElementTupleDescriptor repeated element tuple}.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public final class P_RepeatedElementTuple
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_RepeatedElementTuple().init(
			2, CanInline, CanFold);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;

		final A_Number size = args.get(0);
		final A_BasicObject element = args.get(1);

		if (!size.isInt())
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT);
		}
		final int sizeAsInt = size.extractInt();
		return interpreter.primitiveSuccess(
			createRepeatedElementTuple(
				sizeAsInt, element));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				wholeNumbers(),
				ANY.o()),
			mostGeneralTupleType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_EXCEEDS_VM_LIMIT));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		assert argumentTypes.size() == 2;

		final A_Type sizeType = argumentTypes.get(0);
		final A_Type elementType = argumentTypes.get(1);

		if (sizeType.instanceCount().equalsInt(1)
			&& elementType.instanceCount().equalsInt(1))
		{
			return instanceType(
				createRepeatedElementTuple(
					sizeType.instance().extractInt(), elementType.instance()));
		}
		return tupleTypeForSizesTypesDefaultType(
			sizeType, emptyTuple(), elementType);
	}
}

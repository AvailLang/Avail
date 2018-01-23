/**
 * P_CreateTupleType.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Construct a {@linkplain
 * TupleTypeDescriptor tuple type} with the given parameters. Canonize the
 * data if necessary.
 */
public final class P_CreateTupleType extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreateTupleType().init(
			3, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		final AvailObject typeTuple = args.get(0);
		final AvailObject defaultType = args.get(1);
		final AvailObject sizeRange = args.get(2);
		return interpreter.primitiveSuccess(
			tupleTypeForSizesTypesDefaultType(
				sizeRange, typeTuple, defaultType));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				zeroOrMoreOf(anyMeta()),
				anyMeta(),
				instanceMeta(wholeNumbers())),
			tupleMeta());
	}
}

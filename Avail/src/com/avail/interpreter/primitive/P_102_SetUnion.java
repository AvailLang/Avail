/**
 * P_102_SetUnion.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 102:</strong> Answer the union of two {@linkplain
 * SetDescriptor sets}.
 */
public final class P_102_SetUnion extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_102_SetUnion().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Set set1 = args.get(0);
		final A_Set set2 = args.get(1);

		return interpreter.primitiveSuccess(
			set1.setUnionCanDestroy(set2, true));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				SetTypeDescriptor.mostGeneralType(),
				SetTypeDescriptor.mostGeneralType()),
			SetTypeDescriptor.mostGeneralType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type setType1 = argumentTypes.get(0);
		final A_Type setType2 = argumentTypes.get(1);

		// Technically we can compute the exact minimum bound by building a
		// graph where the edges are the mutually disjoint element types, then
		// computing the minimum coloring via a Birkhoff chromatic polynomial.
		// Even the upper bound can be strengthened beyond the sum of the upper
		// bounds of the inputs through solution of a set of linear inequalities
		// and the pigeon-hole principle.  For now, just keep it simple.
		final A_Type sizes1 = setType1.sizeRange();
		final A_Type sizes2 = setType2.sizeRange();
		final A_Number min1 = sizes1.lowerBound();
		final A_Number min2 = sizes2.lowerBound();
		// Use the *max* of the lower bounds as the new min bound.
		final A_Number minSize = min1.numericCompare(min2).isMore()
			? min1 : min2;
		final A_Number maxSize = sizes1.upperBound().plusCanDestroy(
			sizes2.upperBound(),
			false);
		final A_Type unionSize = IntegerRangeTypeDescriptor.create(
			minSize,
			true,
			maxSize.plusCanDestroy(IntegerDescriptor.one(), false),
			false);
		final A_Type unionType = SetTypeDescriptor.setTypeForSizesContentType(
			unionSize,
			setType1.contentType().typeUnion(setType2.contentType()));
		return unionType.makeImmutable();
	}
}
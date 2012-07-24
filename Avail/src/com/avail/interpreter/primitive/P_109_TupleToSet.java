/**
 * P_109_TupleToSet.java
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

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 109:</strong> Convert a {@linkplain TupleDescriptor
 * tuple} into a {@linkplain SetDescriptor set}.
 */
public class P_109_TupleToSet extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_109_TupleToSet().init(
		1, CanFold, CannotFail);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject tuple = args.get(0);
		return interpreter.primitiveSuccess(tuple.asSet());
	}

	@Override
	protected @NotNull AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.mostGeneralType()),
			SetTypeDescriptor.mostGeneralType());
	}

	@Override
	public @NotNull AvailObject returnTypeGuaranteedByVMForArgumentTypes (
		final @NotNull List<AvailObject> argumentTypes)
	{
		final AvailObject tupleType = argumentTypes.get(0);

		final AvailObject unionType = tupleType.unionOfTypesAtThrough(
			1,
			Integer.MAX_VALUE);
		unionType.makeImmutable();
		final AvailObject tupleSizes = tupleType.sizeRange();
		// Technically, if two tuple entries have disjoint types then the
		// minimum set size is two.  Generalizing this leads to computing the
		// Birkhoff chromatic polynomial of the graph whose vertices are the
		// tuple subscripts and which has edges when two tuple subscript types
		// are disjoint (their intersection is bottom).  This is the optimum
		// bound for the minimum size of the resulting set.  The maximum can be
		// improved by a not yet worked out pigeon hole principle when there are
		// element types with a small number of possible instances (e.g.,
		// enumerations) and those sets of instances overlap between many tuple
		// elements.  We do neither optimization here, but we do note that only
		// the empty tuple can produce the empty set, and the set size is never
		// greater than the tuple size.
		final AvailObject minSize =
			tupleSizes.lowerBound().equals(IntegerDescriptor.zero())
				? IntegerDescriptor.zero()
				: IntegerDescriptor.one();
		final AvailObject setSizes = IntegerRangeTypeDescriptor.create(
			minSize,
			true,
			tupleSizes.upperBound(),
			tupleSizes.upperInclusive());
		final AvailObject setType =
			SetTypeDescriptor.setTypeForSizesContentType(
				setSizes,
				unionType);
		return setType;
	}
}
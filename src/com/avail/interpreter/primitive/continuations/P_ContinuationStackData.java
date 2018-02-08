/*
 * P_ContinuationStackData.java
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
package com.avail.interpreter.primitive.continuations;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.VariableDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.ContinuationDescriptor.nilSubstitute;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor
	.generateObjectTupleFrom;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Answer a {@linkplain TupleDescriptor
 * tuple} containing the {@linkplain ContinuationDescriptor continuation}'s
 * stack data. Substitute an unassigned {@linkplain BottomTypeDescriptor
 * bottom}-typed {@linkplain VariableDescriptor variable} (unconstructible
 * from Avail) for any {@linkplain NilDescriptor#nil null} values.
 */
public final class P_ContinuationStackData extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ContinuationStackData().init(
			1, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Continuation con = interpreter.argument(0);
		final A_Tuple tuple = generateObjectTupleFrom(
			con.function().code().numSlots(),
			index ->
			{
				final A_BasicObject entry = con.argOrLocalOrStackAt(index);
				return entry.equalsNil()
					? nilSubstitute()
					: entry;
			});
		tuple.makeSubobjectsImmutable();
		return interpreter.primitiveSuccess(tuple);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(mostGeneralContinuationType()),
			mostGeneralTupleType());
	}
}

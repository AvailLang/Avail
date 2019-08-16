/*
 * P_RejectParsing.java
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
package com.avail.interpreter.primitive.phrases;

import com.avail.compiler.AvailRejectedParseException;
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FiberDescriptor.GeneralFlag.CAN_REJECT_PARSE;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.exceptions.AvailErrorCode.E_UNTIMELY_PARSE_REJECTION;
import static com.avail.interpreter.Primitive.Flag.Unknown;

/**
 * <strong>Primitive:</strong> Reject current macro substitution with the
 * specified one-based error level [1..4] and error string.  The levels
 * correspond to the instances of {@link ParseNotificationLevel}, although the
 * integer has to be adjusted to zero-based to use {@link
 * ParseNotificationLevel#levelFromInt(int)}.
 */
public final class P_RejectParsing
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_RejectParsing().init(
			2, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		if (!interpreter.fiber().generalFlag(CAN_REJECT_PARSE))
		{
			return interpreter.primitiveFailure(E_UNTIMELY_PARSE_REJECTION);
		}
		final A_Number oneBasedRejectionLevel = interpreter.argument(0);
		final A_String rejectionString = interpreter.argument(1);
		throw new AvailRejectedParseException(
			ParseNotificationLevel.levelFromInt(
				oneBasedRejectionLevel.extractInt() - 1),
			rejectionString);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				inclusive(1L, 4L),
				stringType()),
			bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_UNTIMELY_PARSE_REJECTION));
	}
}

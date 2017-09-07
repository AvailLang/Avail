/**
 * P_DescribeNoncanonicalMessage.java
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
package com.avail.interpreter.primitive.methods;

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Answer a string describing why the given
 * string is unsuitable as a {@linkplain MessageBundleDescriptor message} name.
 * If the given string is actually suitable, answer the empty string.
 */
public final class P_DescribeNoncanonicalMessage extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_DescribeNoncanonicalMessage().init(
			1, CanInline, CanFold, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_String messageName = args.get(0);
		try
		{
			@SuppressWarnings("unused")
			final MessageSplitter splitter = new MessageSplitter(messageName);
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveSuccess(
				StringDescriptor.stringFrom(e.describeProblem()));
		}
		return interpreter.primitiveSuccess(TupleDescriptor.emptyTuple());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.functionType(
			TupleDescriptor.tuple(
				TupleTypeDescriptor.stringType()),
			TupleTypeDescriptor.stringType());
	}
}

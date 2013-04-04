/**
 * P_227_AbstractMethodDeclarationForAtom.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Result.*;
import java.util.ArrayList;
import java.util.List;
import com.avail.*;
import com.avail.descriptor.*;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.*;
import com.avail.utility.Continuation0;

/**
 * <strong>Primitive 227</strong>: Declare method as {@linkplain
 * AbstractDefinitionDescriptor abstract}. This identifies responsibility for
 * definitions that want to be concrete.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_227_AbstractMethodDeclarationForAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_227_AbstractMethodDeclarationForAtom().init(
			2, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final A_Atom atom = args.get(0);
		final A_Type blockSignature = args.get(1);
		final A_Fiber fiber = FiberDescriptor.current();
		final AvailLoader loader = fiber.availLoader();
		if (loader == null || loader.module().equalsNil())
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Function failureFunction =
			interpreter.primitiveFunctionBeingAttempted();
		final List<AvailObject> copiedArgs = new ArrayList<>(args);
		assert failureFunction.code().primitiveNumber() == primitiveNumber;
		interpreter.primitiveSuspend();
		AvailRuntime.current().whenLevelOneSafeDo(
			AvailTask.forUnboundFiber(
				fiber,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						try
						{
							loader.addAbstractSignature(
								atom,
								blockSignature);
							Interpreter.resumeFromSuccessfulPrimitive(
								AvailRuntime.current(),
								fiber,
								NilDescriptor.nil());
						}
						catch (final SignatureException e)
						{
							Interpreter.resumeFromFailedPrimitive(
								AvailRuntime.current(),
								fiber,
								e.numericCode(),
								failureFunction,
								copiedArgs);
						}
					}
				}));
		return FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				FunctionTypeDescriptor.meta()),
			TOP.o());
	}

//	@Override
//	protected A_Type privateFailureVariableType ()
//	{
//		return AbstractEnumerationTypeDescriptor.withInstances(
//			TupleDescriptor.from(
//				E_LOADING_IS_OVER.numericCode()
//			).asSet());
//	}
}

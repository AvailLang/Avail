/*
 * P_InvokeCallback.java
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
package com.avail.interpreter.primitive.pojos;

import com.avail.AvailRuntime;
import com.avail.CallbackSystem;
import com.avail.CallbackSystem.Callback;
import com.avail.CallbackSystem.CallbackCompletion;
import com.avail.CallbackSystem.CallbackFailure;
import com.avail.descriptor.*;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PojoDescriptor.newPojo;
import static com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Private;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Given zero or more arguments, invoke the {@link
 * Callback} that's in a {@link PojoDescriptor} stored in the sole outer
 * variable.
 *
 * <p>If a Java {@link Throwable} is thrown while executing the {@link
 * Callback}, or if the specific callback indicates failure of some other form,
 * invoke TODO the handler for Java exceptions in callbacks.  Otherwise, answer
 * the result of successfully executing the callback.  The callback body runs in
 * the {@link CallbackSystem}'s thread pool.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_InvokeCallback extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_InvokeCallback().init(
			-1, Private, CanSuspend);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader != null)
		{
			// Callback invocations shouldn't be summarized.
			loader.statementCanBeSummarized(false);
		}
		final AvailRuntime runtime = interpreter.runtime();
		final A_Function primitiveFunction = stripNull(interpreter.function);
		assert primitiveFunction.code().primitive() == this;
		final A_BasicObject callbackPojo = primitiveFunction.outerVarAt(1);
		final List<AvailObject> copiedArgs =
			new ArrayList<>(interpreter.argsBuffer);
		final A_Tuple argumentsTuple = tupleFromList(copiedArgs).makeShared();
		final A_Fiber fiber = interpreter.fiber();
		final Callback callback = callbackPojo.javaObjectNotNull();
		final AtomicBoolean ranCompletion = new AtomicBoolean(false);
		final CallbackCompletion completion = result ->
		{
			final boolean alreadyCompleted = ranCompletion.getAndSet(true);
			// TODO - Report this situation a better way within Java.
			// For now, just ignore calls after the first one.
			if (!alreadyCompleted)
			{
				Interpreter.resumeFromSuccessfulPrimitive(
					runtime,
					fiber,
					P_InvokeCallback.this,
					result.makeShared());
			}
		};
		final CallbackFailure failure = throwable ->
		{
			final boolean alreadyCompleted = ranCompletion.getAndSet(true);
			// TODO - Report this situation a better way within Java.
			// For now, just ignore calls after the first one.
			if (!alreadyCompleted)
			{
				Interpreter.resumeFromFailedPrimitive(
					runtime,
					fiber,
					newPojo(
						identityPojo(throwable),
						pojoTypeForClass(throwable.getClass())
					).makeShared(),
					primitiveFunction,
					copiedArgs);
			}
		};
		runtime.callbackSystem().executeCallbackTask(
			callback, argumentsTuple, completion, failure);
		return interpreter.primitiveSuspend(primitiveFunction);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		// This primitive is suitable for any block signature.
		return bottom();
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return pojoTypeForClass(Throwable.class);
	}

	@Override
	public void writeDefaultFailureCode (
		final int lineNumber,
		final L1InstructionWriter writer,
		final int numArgs)
	{
		// Raw functions using this primitive should not be constructed through
		// this default mechanism.  See CallbackSystem for details.
		throw new UnsupportedOperationException(
			this.getClass().getSimpleName()
				+ " must not create a function through the bootstrap "
				+ "mechanism");
	}
}

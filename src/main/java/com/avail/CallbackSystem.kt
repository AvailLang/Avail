/*
 * CallbackSystem.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail;

import com.avail.descriptor.fiber.A_Fiber;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.pojos.PojoDescriptor;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.NilDescriptor;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.interpreter.primitive.pojos.P_InvokeCallback;
import com.avail.interpreter.primitive.pojos.PrimitiveHelper;
import com.avail.utility.SimpleThreadFactory;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.avail.AvailRuntimeConfiguration.availableProcessors;
import static com.avail.descriptor.functions.FunctionDescriptor.createWithOuters1;
import static com.avail.descriptor.maps.MapDescriptor.emptyMap;
import static com.avail.descriptor.pojos.PojoDescriptor.newPojo;
import static com.avail.descriptor.pojos.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.types.PojoTypeDescriptor.resolvePojoType;
import static java.util.Collections.synchronizedMap;

/**
 * A mechanism for adapting a Java {@link Function} into an Avail {@link
 * A_Function}.  The Java function must take an {@link A_Tuple} and return an
 * {@link AvailObject}.  The Avail function's signature is provided, and will
 * pack its arguments into a tuple for the Java function.  The Java function's
 * returned value will be checked at runtime before being returned into Avail.
 *
 * <p>If a {@link Throwable} is thrown by the Java code, it will be caught,
 * wrapped as a {@linkplain PojoDescriptor pojo} inside an Avail exception, and
 * rethrown within Avail.</p>
 *
 * <p>Note that the {@link AvailRuntime} that's responsible for the {@link
 * A_Fiber} doing the callback contains a {@code CallbackSystem} whose {@link
 * #callbackExecutor}'s worker threads are responsible for performing the
 * callback to the Java lambda.  Performing a long computation, significant
 * wait, or unbounded blocking may negatively affect execution of other
 * callbacks, so it's recommended that exceptionally long or blocking
 * computations be performed in some other way.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class CallbackSystem
{
	/** A mechanism for invoking Java lambdas from Avail. */
	@FunctionalInterface
	public interface Callback
	{
		/**
		 * Invoke this Java callback, whose actual behavior is specified by
		 * (anonymous) subclasses / lambda expressions.  This should be invoked
		 * via {@link CallbackSystem#executeCallbackTask(Callback, A_Tuple,
		 * CallbackCompletion, CallbackFailure)}.
		 *
		 * @param argumentsTuple
		 *        The {@link A_Tuple} of arguments that Avail code has provided.
		 * @param completion
		 *        What to invoke when this callback is considered complete, if
		 *        ever, passing the callback's output value to it.  This must be
		 *        invoked at most once by the callback.
		 * @param failure
		 *        What to invoke when this callback is considered to have
		 *        completed unsuccessfully, passing the {@link Throwable} that
		 *        was caught.  This must be invoked at most once by the
		 *        callback.
		 */
		void call (
			final A_Tuple argumentsTuple,
			final CallbackCompletion completion,
			final CallbackFailure failure);
	}

	/**
	 * A mechanism for indicating when a callback Java lambda has completed
	 * successfully.  By having a {@link Callback} invoke this when it's deemed
	 * complete instead of coupling it to the time that the lambda function
	 * invocation returns, the Java client is free to delegate the
	 * responsibility for completing the callback to other Java {@link Thread}s,
	 * such as thread pools, completion mechanisms, coroutines, etc.  Each
	 * {@code CallbackCompletion} must be invoked at most once, and mutually
	 * exclusively of the associated {@link CallbackFailure}.
	 *
	 * <p>It's safe to invoke neither the completion nor failure handler for a
	 * callback, and the corresponding fiber will eventually be subject to
	 * garbage collection.</p>
	 *
	 * <p>Invoking both the completion and failure, or either of them more than
	 * once, currently causes all but the first invocation to be ignored.</p>
	 */
	@FunctionalInterface
	public interface CallbackCompletion
	{
		/**
		 * Invoke this callback success handler.
		 *
		 * @param result
		 *        The {@link AvailObject} that was produced by the callback.
		 *        This should be {@link NilDescriptor#nil} if Avail is not
		 *        expecting a value to be produced.
		 */
		void complete (final AvailObject result);
	}

	/**
	 * A mechanism for indicating when a Java lambda has completed
	 * <em>unsuccessfully</em>.  By having a {@link Callback} invoke this when
	 * it's deemed to have failed instead of coupling it to the time that the
	 * lambda function invocation returns or throws, the Java client is free to
	 * pass the responsibility for completing the callback to other Java {@link
	 * Thread}s, such as thread pools, completion mechanisms, coroutines, etc.
	 * Each {@code CallbackCompletion} must be invoked at most once, and
	 * mutually exclusively of the associated {@link CallbackFailure}.
	 *
	 * <p>It's safe to invoke neither the completion nor failure handler for a
	 * callback, and the corresponding fiber will eventually be subject to
	 * garbage collection.</p>
	 *
	 * <p>Invoking both the completion and failure, or either of them more than
	 * once, currently causes all but the first invocation to be ignored.</p>
	 */
	@FunctionalInterface
	public interface CallbackFailure
	{
		/**
		 * Invoke this callback failure handler.
		 *
		 * @param throwable
		 *        The {@link Throwable} that was caught during execution of the
		 *        {@link Callback}.
		 */
		void failed (final Throwable throwable);
	}

	/**
	 * Cache generated {@link A_RawFunction}s, keyed by signature {@link
	 * A_Type}s.
	 */
	private static final Map<A_Type, A_RawFunction> rawFunctionCache =
		synchronizedMap(new WeakHashMap<>());

	/**
	 * The Pojo type for a Java {@link Callback} object.
	 */
	private static final A_Type callbackTypePojo =
		resolvePojoType(Callback.class, emptyMap()).makeShared();

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for performing
	 * asynchronous callbacks on behalf of this {@link AvailRuntime}.
	 */
	private final ThreadPoolExecutor callbackExecutor =
		new ThreadPoolExecutor(
			availableProcessors,
			availableProcessors << 2,
			10L,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(),
			new SimpleThreadFactory("AvailCallback"),
			new CallerRunsPolicy());

	/**
	 * Schedule a {@link Runnable} task for eventual execution by the
	 * {@linkplain ThreadPoolExecutor thread pool executor} for callback
	 * operations. The implementation is free to run the task immediately or
	 * delay its execution arbitrarily. The task will not execute on an {@link
	 * AvailThread}.
	 *
	 * @param callback
	 *        The {@link Callback} to invoke.
	 * @param argumentsTuple
	 *        The arguments {@link A_Tuple} to supply the callback.
	 * @param completion
	 *        What to invoke when the callback semantically succeeds, whether in
	 *        the same thread that the callback started in or not.  It's passed
	 *        an {@link AvailObject} which should be consistent with the
	 *        expected result type of the {@link Callback}.
	 * @param failure
	 *        What to invoke when it's determined that the callback has failed,
	 *        whether in the same thread that the callback started in or not.
	 *        It's passed a Throwable.
	 *
	 */
	public void executeCallbackTask (
		final Callback callback,
		final A_Tuple argumentsTuple,
		final CallbackCompletion completion,
		final CallbackFailure failure)
	{
		callbackExecutor.execute(
			() ->
			{
				// As a nicety, catch throwables that happen directly in the
				// execution of the task within the callback thread.  It's
				// the specific callback's responsibility to catch other
				// throwables in other Threads that effect the callback, and
				// invoke 'failure' itself.
				try
				{
					callback.call(argumentsTuple, completion, failure);
				}
				catch (final Throwable t)
				{
					failure.failed(t);
				}
			});
	}

	/**
	 * Destroy all data structures used by this {@code AvailRuntime}.  Also
	 * disassociate it from the current {@link Thread}'s local storage.
	 */
	public void destroy ()
	{
		callbackExecutor.shutdownNow();
		try
		{
			callbackExecutor.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (final InterruptedException e)
		{
			// Ignore.
		}
	}

	/**
	 * Create an {@link A_Function} from the given {@link Callback} and function
	 * {@link A_Type}.
	 *
	 * @param functionType
	 *        The signature of the {@link A_Function} to create.
	 * @param callback
	 *        The {@link Callback} to invoke when the corresponding Avail
	 *        function is invoked.
	 * @return The Avail {@link A_Function}.
	 */
	public static A_Function createCallbackFunction (
		final A_Type functionType,
		final Callback callback)
	{
		final AvailObject callbackPojo =
			newPojo(identityPojo(callback), callbackTypePojo);
		final A_RawFunction rawFunction =
			rawFunctionCache.computeIfAbsent(
				functionType,
				fType -> PrimitiveHelper.INSTANCE
					.rawPojoInvokerFunctionFromFunctionType(
						P_InvokeCallback.INSTANCE,
						fType,
						callbackTypePojo));
		return createWithOuters1(rawFunction, callbackPojo);
	}
}

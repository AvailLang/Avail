/*
 * CallbackSystem.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail

import com.avail.AvailRuntimeConfiguration.availableProcessors
import com.avail.descriptor.fiber.A_Fiber
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters1
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.pojos.PojoDescriptor
import com.avail.descriptor.pojos.PojoDescriptor.Companion.newPojo
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.resolvePojoType
import com.avail.interpreter.primitive.pojos.P_InvokeCallback
import com.avail.interpreter.primitive.pojos.PrimitiveHelper.rawPojoInvokerFunctionFromFunctionType
import com.avail.utility.SimpleThreadFactory
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Function
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A mechanism for adapting a Java [Function] into an Avail [A_Function].  The
 * Java function must take an [A_Tuple] and return an [AvailObject].  The Avail
 * function's signature is provided, and will pack its arguments into a tuple
 * for the Java function.  The Java function's returned value will be checked at
 * runtime before being returned into Avail.
 *
 * If a [Throwable] is thrown by the Java code, it will be caught, wrapped as a
 * [pojo][PojoDescriptor] inside an Avail exception, and rethrown within Avail.
 *
 * Note that the [AvailRuntime] that's responsible for the [A_Fiber] doing the
 * callback contains a `CallbackSystem` whose [.callbackExecutor]'s worker
 * threads are responsible for performing the callback to the Java lambda.
 * Performing a long computation, significant wait, or unbounded blocking may
 * negatively affect execution of other callbacks, so it's recommended that
 * exceptionally long or blocking computations be performed in some other way.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
class CallbackSystem
{
	/** A mechanism for invoking Java lambdas from Avail.  */
	@FunctionalInterface
	interface Callback
	{
		/**
		 * Invoke this Java callback, whose actual behavior is specified by
		 * (anonymous) subclasses / lambda expressions.  This should be invoked
		 * via [CallbackSystem.executeCallbackTask].
		 *
		 * @param argumentsTuple
		 *   The [A_Tuple] of arguments that Avail code has provided.
		 * @param completion
		 *   What to invoke when this callback is considered complete, if ever,
		 *   passing the callback's output value to it.  This must be invoked at
		 *   most once by the callback.
		 * @param failure
		 *   What to invoke when this callback is considered to have completed
		 *   unsuccessfully, passing the [Throwable] that was caught.  This must
		 *   be invoked at most once by the callback.
		 */
		fun call(
			argumentsTuple: A_Tuple,
			completion: CallbackCompletion,
			failure: CallbackFailure)
	}

	/**
	 * A mechanism for indicating when a callback Java lambda has completed
	 * successfully.  By having a [Callback] invoke this when it's deemed
	 * complete instead of coupling it to the time that the lambda function
	 * invocation returns, the Java client is free to delegate the
	 * responsibility for completing the callback to other Java [Thread]s, such
	 * as thread pools, completion mechanisms, coroutines, etc.  Each
	 * `CallbackCompletion` must be invoked at most once, and mutually
	 * exclusively of the associated [CallbackFailure].
	 *
	 * It's safe to invoke neither the completion nor failure handler for a
	 * callback, and the corresponding fiber will eventually be subject to
	 * garbage collection.
	 *
	 * Invoking both the completion and failure, or either of them more than
	 * once, currently causes all but the first invocation to be ignored.
	 */
	@FunctionalInterface
	interface CallbackCompletion
	{
		/**
		 * Invoke this callback success handler.
		 *
		 * @param result
		 *   The [AvailObject] that was produced by the callback. This should be
		 *   [NilDescriptor.nil] if Avail is not expecting a value to be
		 *   produced.
		 */
		fun complete(result: A_BasicObject)
	}

	/**
	 * A mechanism for indicating when a Java lambda has completed
	 * *unsuccessfully*.  By having a [Callback] invoke this when it's deemed to
	 * have failed instead of coupling it to the time that the lambda function
	 * invocation returns or throws, the Java client is free to pass the
	 * responsibility for completing the callback to other Java [Thread]s, such
	 * as thread pools, completion mechanisms, coroutines, etc. Each
	 * `CallbackCompletion` must be invoked at most once, and mutually
	 * exclusively of the associated [CallbackFailure].
	 *
	 * It's safe to invoke neither the completion nor failure handler for a
	 * callback, and the corresponding fiber will eventually be subject to
	 * garbage collection.
	 *
	 * Invoking both the completion and failure, or either of them more than
	 * once, currently causes all but the first invocation to be ignored.
	 */
	@FunctionalInterface
	interface CallbackFailure
	{
		/**
		 * Invoke this callback failure handler.
		 *
		 * @param throwable
		 *   The [Throwable] that was caught during execution of the [Callback].
		 */
		fun failed(throwable: Throwable)
	}

	/**
	 * The [thread pool executor][ThreadPoolExecutor] for performing
	 * asynchronous callbacks on behalf of this [AvailRuntime].
	 */
	private val callbackExecutor = ThreadPoolExecutor(
		availableProcessors,
		availableProcessors shl 2,
		10L,
		TimeUnit.SECONDS,
		LinkedBlockingQueue(),
		SimpleThreadFactory("AvailCallback"),
		CallerRunsPolicy())

	/**
	 * Schedule a [Runnable] task for eventual execution by the
	 * [thread&#32;pool&#32;executor][ThreadPoolExecutor] for callback
	 * operations. The implementation is free to run the task immediately or
	 * delay its execution arbitrarily. The task will not execute on an
	 * [AvailThread].
	 *
	 * @param callback
	 *   The [Callback] to invoke.
	 * @param argumentsTuple
	 *   The arguments [A_Tuple] to supply the callback.
	 * @param completion
	 *   What to invoke when the callback semantically succeeds, whether in the
	 *   same thread that the callback started in or not.  It's passed an
	 *   [AvailObject] which should be consistent with the expected result type
	 *   of the [Callback].
	 * @param failure
	 *   What to invoke when it's determined that the callback has failed,
	 *   whether in the same thread that the callback started in or not. It's
	 *   passed a Throwable.
	 */
	fun executeCallbackTask(
		callback: Callback,
		argumentsTuple: A_Tuple,
		completion: CallbackCompletion,
		failure: CallbackFailure)
	{
		callbackExecutor.execute {
			// As a nicety, catch throwables that happen directly in the
			// execution of the task within the callback thread.  It's
			// the specific callback's responsibility to catch other
			// throwables in other Threads that effect the callback, and
			// invoke 'failure' itself.
			try
			{
				callback.call(argumentsTuple, completion, failure)
			}
			catch (t: Throwable)
			{
				failure.failed(t)
			}
		}
	}

	/**
	 * Destroy all data structures used by this `AvailRuntime`.  Also
	 * disassociate it from the current [Thread]'s local storage.
	 */
	fun destroy()
	{
		callbackExecutor.shutdownNow()
		try
		{
			callbackExecutor.awaitTermination(10, TimeUnit.SECONDS)
		}
		catch (e: InterruptedException)
		{
			// Ignore.
		}
	}

	companion object
	{
		/**
		 * Cache generated [A_RawFunction]s, keyed by signature [A_Type]s.
		 */
		@GuardedBy("rawFunctionCacheLock")
		private val rawFunctionCache = WeakHashMap<A_Type, A_RawFunction>()

		/** The lock that protects the [rawFunctionCache]. */
		private val rawFunctionCacheLock = ReentrantReadWriteLock()

		/**
		 * The Pojo type for a Java [Callback] object.
		 */
		private val callbackTypePojo: A_Type =
			resolvePojoType(Callback::class.java, emptyMap).makeShared()

		/**
		 * Create an [A_Function] from the given [Callback] and function
		 * [A_Type].
		 *
		 * @param functionType
		 *   The signature of the [A_Function] to create.
		 * @param callbackFunction
		 *   The Kotlin function from which to build a [Callback] to invoke when
		 *   the corresponding Avail function is invoked.
		 * @return
		 *   The Avail [A_Function].
		 */
		@JvmStatic
		fun createCallbackFunction(
			functionType: A_Type,
			callbackFunction: (
				argumentsTuple: A_Tuple,
				completion: CallbackCompletion,
				failure: CallbackFailure
			) -> Unit
		): A_Function
		{
			val callback = object : Callback {
				override fun call(
					argumentsTuple: A_Tuple,
					completion: CallbackCompletion,
					failure: CallbackFailure
				) = callbackFunction(argumentsTuple, completion, failure)
			}
			val callbackPojo = newPojo(identityPojo(callback), callbackTypePojo)
			val rawFunction = rawFunctionCacheLock.write {
				rawFunctionCache.computeIfAbsent(functionType) { fType ->
					rawPojoInvokerFunctionFromFunctionType(
						P_InvokeCallback, fType, callbackTypePojo)
				}
			}
			return createWithOuters1(rawFunction, callbackPojo)
		}

		/**
		 * Create an [A_Function] from the given [Callback] and function
		 * [A_Type].  Since Kotlin is actively hostile toward SAM (single
		 * abstract method) lambda conversions, this method is targeted toward
		 * use from Java callers that can simply use the lambda notation.
		 *
		 * @param functionType
		 *   The signature of the [A_Function] to create.
		 * @param callback
		 *   The [Callback] to invoke when the corresponding Avail function is
		 *   invoked.
		 * @return
		 *   The Avail [A_Function].
		 */
		@Suppress("unused")
		@JvmStatic
		fun createCallbackFunctionInJava(
			functionType: A_Type,
			callback: Callback
		): A_Function
		{
			val callbackPojo = newPojo(identityPojo(callback), callbackTypePojo)
			val rawFunction = rawFunctionCacheLock.read {
				rawFunctionCache[functionType]
			} ?: rawFunctionCacheLock.write {
				rawFunctionCache.computeIfAbsent(functionType) { fType ->
					rawPojoInvokerFunctionFromFunctionType(
						P_InvokeCallback, fType, callbackTypePojo)
				}
			}
			return createWithOuters1(rawFunction!!, callbackPojo)
		}
	}
}

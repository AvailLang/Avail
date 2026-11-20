/*
 * A_Label.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package avail.descriptor.representation

import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.LabelDescriptor
import avail.descriptor.representation.A_BasicObject.Companion.dispatch

/**
 * [A_Label] is an interface that specifies the operations specific to
 * [labels][LabelDescriptor] in Avail.  A label names an invocation of a
 * [function][FunctionDescriptor] that can be restarted with new arguments, or
 * exited with a value, both of which return control to the label's caller.
 * It's a sub-interface of [A_BasicObject], the interface that defines the
 * behavior that all AvailObjects are required to support.
 */
interface A_Label : A_BasicObject
{
	companion object
	{
		/**
		 * The [continuation][ContinuationDescriptor] to which control passes
		 * when this label is exited, and which becomes the caller of the new
		 * invocation when this label is restarted.  It is [nil][NilDescriptor]
		 * when the label's function was invoked as the outermost frame of its
		 * fiber.
		 *
		 * @return
		 *   The label's calling continuation, or nil.
		 */
		val A_Label.caller: A_Continuation
			get() = dispatch { o_Caller(it) }

		/**
		 * The [function][FunctionDescriptor] that restarting this label
		 * invokes again.
		 *
		 * @return
		 *   The label's function.
		 */
		val A_Label.function: A_Function
			get() = dispatch { o_Function(it) }
	}
}

/*
 * A_Function.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.functions

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor
import avail.interpreter.execution.Interpreter
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `A_Function` is an interface that specifies the operations specific to
 * Avail functions.  It's a sub-interface of [A_BasicObject], the interface
 * that defines the behavior that all [AvailObject]s are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Function : A_BasicObject
{
	/**
	 * Answer the [raw&#32;function][A_RawFunction] (also known as compiled
	 * code) on which this function is based.  The raw function holds the
	 * information that is common to all functions, and each function
	 * additionally holds zero or more captured variables and values from its
	 * lexical context.
	 *
	 * @return
	 *   This function's [A_RawFunction].
	 */
	@ReferencedInGeneratedCode
	fun code(): A_RawFunction = dispatch { o_Code(it) }

	/**
	 * Answer this function's lexically captured variable or constant value that
	 * has the specified index.
	 *
	 * @param index
	 *   Which outer variable or constant is of interest.
	 * @return
	 *   The specified outer variable or constant of this function.
	 */
	@ReferencedInGeneratedCode
	fun outerVarAt(index: Int): AvailObject =
		dispatch { o_OuterVarAt(it, index) }

	/**
	 * Set the specified captured variable/constant slot to the given variable
	 * or constant value.
	 *
	 * @param index
	 *   Which variable or constant to initialize.
	 * @param value
	 *   The value to write into this function.
	 */
	@ReferencedInGeneratedCode
	fun outerVarAtPut(index: Int, value: AvailObject) =
		dispatch { o_OuterVarAtPut(it, index, value) }

	companion object
	{
		/**
		 * Answer the number of lexically captured variables or constants held
		 * by this function.
		 *
		 * @return
		 *   The number of outer variables captured by this function.
		 */
		val A_Function.numOuterVars: Int get() = dispatch { o_NumOuterVars(it) }

		/**
		 * An outer variable or constant of this function has been used for the
		 * last time.  Replace it with [nil][NilDescriptor.nil] if the function
		 * is mutable, and answer true.  If the function is immutable then
		 * something besides the [Interpreter] or a fiber's chain of
		 * [A_Continuation]s might be referring to it, so answer false.
		 *
		 * @param index
		 *   Which outer variable or constant is no longer needed.
		 * @return
		 *   Whether the outer variable or constant was actually nilled.
		 */
		fun A_Function.optionallyNilOuterVar(index: Int): Boolean =
			dispatch { o_OptionallyNilOuterVar(it, index) }
	}
}

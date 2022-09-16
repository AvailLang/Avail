/*
 * P_SetStylerFunction.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.style

import avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.functions.A_Function
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_STYLER_ALREADY_SET_BY_THIS_MODULE
import avail.exceptions.AvailException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Create and install a [styler][A_Styler], from the given
 * [function][A_Function] and for the method associated with the given
 * [A_Bundle]. Fail if the method already has a styling function for the current
 * module.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_SetStylerFunction : Primitive(2, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val atom: A_Atom = interpreter.argument(0)
		val function: A_Function = interpreter.argument(1)
		val loader = interpreter.fiber().availLoader
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase.isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val bundle = try
		{
			atom.bundleOrCreate()
		}
		catch (e: AvailException)
		{
			// MalformedMessageException
			// SignatureException
			// AmbiguousNameException
			return interpreter.primitiveFailure(e)
		}
		return interpreter.suspendInSafePointThen {
			try
			{
				loader.addStyler(bundle, function)
				succeed(nil)
			}
			catch (e: AvailException)
			{
				assert(e.errorCode == E_STYLER_ALREADY_SET_BY_THIS_MODULE)
				fail(e.errorCode)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM.o,
				stylerFunctionType),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_STYLER_ALREADY_SET_BY_THIS_MODULE
			).setUnionCanDestroy(possibleErrors, true))

	override fun bootstrapStyler() = P_BootstrapDefinitionStyler
}

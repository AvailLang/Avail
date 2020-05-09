/*
 * P_PublishName.kt
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

package com.avail.interpreter.primitive.modules

import com.avail.descriptor.ModuleDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.PUBLISH_NEW_NAME
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AmbiguousNameException
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.effects.LoadingEffectToRunPrimitive

/**
 * **Primitive:** Publish the [atom][AtomDescriptor] associated with the
 * specified [string][StringDescriptor] as a public name of the current
 * [module][ModuleDescriptor]. This has the same effect as listing the string in
 * the "Names" section of the current module. Fails if called at runtime.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_PublishName : Primitive(
	1, CanInline, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val name = interpreter.argument(0)
		val loader = interpreter.fiber().availLoader() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		val module = loader.module()
		if (module.equalsNil())
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		return try
		{
			val trueName = loader.lookupName(name)
			module.introduceNewName(trueName)
			module.addImportedName(trueName)
			if (loader.phase() == EXECUTING_FOR_COMPILE)
			{
				// Record the publication.
				loader.recordEffect(
					LoadingEffectToRunPrimitive(
						PUBLISH_NEW_NAME.bundle,
						name))

			}
			interpreter.primitiveSuccess(nil)
		}
		catch (e: AmbiguousNameException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType()), TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_LOADING_IS_OVER,
			E_CANNOT_DEFINE_DURING_COMPILATION,
		    E_AMBIGUOUS_NAME))
}

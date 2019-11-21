/*
 * P_CreateAnonymousModule.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.modules

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ModuleDescriptor
import com.avail.descriptor.ModuleDescriptor.currentModule
import com.avail.descriptor.ModuleDescriptor.newModule
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TupleTypeDescriptor.*
import com.avail.descriptor.TypeDescriptor.Types.MODULE
import com.avail.descriptor.tuples.A_Tuple
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Answer the [module][ModuleDescriptor] currently undergoing
 * compilation. Fails at runtime (if compilation is over).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateAnonymousModule : Primitive(1, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val allUses: A_Tuple = interpreter.argument(0)

		val loader = interpreter.availLoaderOrNull() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		val currentModule = currentModule()
		val newModule = newModule(TupleDescriptor.emptyTuple())
		for ((moduleName, importsForModule) in allUses) {
			val importedModule = moduleName
			//TODO finish this
		}

		if (currentModule.equalsNil())
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		return interpreter.primitiveSuccess(newModule)
	}

	override fun privateBlockTypeRestriction(): A_Type = functionType(
		tuple(
			// Entire 'uses' argument
			oneOrMoreOf(
				// One imported module.
				tupleTypeForTypes(
					// Imported module name. Must be imported in current module.
					nonemptyStringType(),
					// Optional imported names list section.
					zeroOrOneOf(
						// Imported names list section.
						tupleTypeForTypes(
							// Imported names list.
							zeroOrMoreOf(
								// Single imported name.
								tupleTypeForTypes(
									// Negated import.
									booleanType(),
									// Name being imported.
									nonemptyStringType(),
									// Optional rename string.
									zeroOrOneOf(
										// Rename string.
										nonemptyStringType()))),
							// Wildcard for imported names.
							booleanType()))))),
		MODULE.o())

	override fun privateFailureVariableType(): A_Type = enumerationWith(
		set(
			E_LOADING_IS_OVER))
}

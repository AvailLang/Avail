/*
 * P_ModuleHeaderPrefixCheckModuleName.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.builder.ModuleName
import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.PhraseTypeDescriptor.Constants.stringLiteralType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.Private
import com.avail.interpreter.execution.Interpreter

/**
 * This is the prefix function for [P_ModuleHeaderPseudoMacro] associated with
 * having just read the module name.  It allows early checking that the module
 * name agrees with the file name.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_ModuleHeaderPrefixCheckModuleName : Primitive(1, Private, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val moduleNameLiteral = interpreter.argument(0)

		val loader = interpreter.availLoader()
		val module = loader.module()
		val qualifiedName = module.moduleName()
		val moduleNameInstance = ModuleName(qualifiedName.asNativeString())
		val localName = moduleNameInstance.localName

		val declaredModuleName =
			moduleNameLiteral.token().literal().literal().asNativeString()
		if (localName != declaredModuleName)
		{
			throw AvailRejectedParseException(
				STRONG,
				"module name (%s) to agree with file's name (%s)",
				declaredModuleName,
				localName)
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			/* Module name */
			tupleFromArray(stringLiteralType),
			TOP.o
		)
}

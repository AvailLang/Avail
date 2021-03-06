/*
 * P_CreateAnonymousModule.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.modules

import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.A_Module.Companion.addPrivateNames
import com.avail.descriptor.module.ModuleDescriptor.Companion.newModule
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.MODULE
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Create an anonymous [module][A_Module] that privately
 * imports only and exactly the supplied [set][A_Set] of
 * [atoms][AtomDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateAnonymousModule : Primitive(1, CanInline, CannotFail)
{
	override fun attempt (interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val allUses: A_Set = interpreter.argument(0)

		val newModule = newModule(emptyTuple)
		newModule.addPrivateNames(allUses)
		return interpreter.primitiveSuccess(newModule)
	}

	override fun privateBlockTypeRestriction () =
		functionType(
			// All atoms that should be imported privately.
			tuple(setTypeForSizesContentType(wholeNumbers, ATOM.o)),
			MODULE.o
		)
}

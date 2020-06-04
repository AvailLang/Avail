/*
 * P_BootstrapPrefixStartOfBlock.kt
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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_STACK_KEY
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapPrefixStartOfBlock` primitive is triggered at the start of
 * parsing a block.  It pushes the current scope onto the scope stack so that it
 * can be popped again by the [P_BootstrapBlockMacro] when the block parsing
 * completes.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapPrefixStartOfBlock : Primitive(0, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)

		interpreter.fiber().availLoader() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		val clientDataGlobalKey = CLIENT_DATA_GLOBAL_KEY.atom
		val compilerScopeMapKey = COMPILER_SCOPE_MAP_KEY.atom
		val compilerScopeStackKey = COMPILER_SCOPE_STACK_KEY.atom
		val fiber = interpreter.fiber()
		var fiberGlobals = fiber.fiberGlobals()
		var clientData: A_Map = fiberGlobals.mapAt(clientDataGlobalKey)
		val bindings = clientData.mapAt(compilerScopeMapKey)
		val stack: A_Tuple =
			if (clientData.hasKey(compilerScopeStackKey))
			{
				clientData.mapAt(compilerScopeStackKey)
			}
			else { emptyTuple() }.appendCanDestroy(bindings, false)
		clientData =
			clientData.mapAtPuttingCanDestroy(
				compilerScopeStackKey, stack, true)
		fiberGlobals =
			fiberGlobals.mapAtPuttingCanDestroy(
				clientDataGlobalKey, clientData, true)
		fiber.setFiberGlobals(fiberGlobals.makeShared())
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(emptyTuple(), TOP.o())
}

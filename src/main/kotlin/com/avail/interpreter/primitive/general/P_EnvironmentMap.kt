/*
 * P_EnvironmentMap.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

package com.avail.interpreter.primitive.general

import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import java.lang.ref.SoftReference

/**
 * **Primitive:** Answer a [map][A_Map] that represents the
 * [environment][System.getenv] of the Avail virtual machine.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_EnvironmentMap : Primitive(0, CannotFail, CanInline, HasSideEffect)
{
	/**
	 * The cached [environment][System.getenv] [map][A_Map]. The content may be
	 * `null` if memory pressure is high (or if the
	 * [primitive][P_EnvironmentMap] has never been called.
	 */
	private var environmentMap = SoftReference<A_Map>(null)

	/**
	 * Get the [environment&#32;map][environmentMap], creating a new one as
	 * necessary (either because it has never been created or because the
	 * garbage collector has discarded it).
	 *
	 * @return The environment map.
	 */
	private fun getEnvironmentMap(): A_Map
	{
		// Don't bother to synchronize. If there's a race, then some redundant
		// work will be done. Big deal. This is likely to be cheaper in general
		// than repeatedly entering and leaving a critical section.
		var result = environmentMap.get()
		if (result === null)
		{
			result = emptyMap()
			val map = System.getenv()
			for ((key, value) in map)
			{
				result = result!!.mapAtPuttingCanDestroy(
					stringFrom(key),
					stringFrom(value),
					true)
			}
			environmentMap = SoftReference(result!!.makeShared())
		}
		return result
	}

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)
		return interpreter.primitiveSuccess(getEnvironmentMap())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			emptyTuple(),
			mapTypeForSizesKeyTypeValueType(
				wholeNumbers(), stringType(), stringType()))
}

/*
 * P_GrammaticalRestrictionFromAtoms.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.methods

import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.MalformedMessageException
import com.avail.exceptions.SignatureException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Unknown

/**
 * **Primitive:** Message precedence declaration with
 * [tuple][TupleDescriptor] of [sets][SetDescriptor] of
 * messages to exclude for each argument position. Note that the tuple's
 * elements should correspond with occurrences of underscore in the method
 * names, *not* with the (top-level) arguments of the method. This
 * distinction is only apparent when guillemet notation is used to accept
 * tuples of arguments.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_GrammaticalRestrictionFromAtoms : Primitive(2, Unknown)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val atomSet = interpreter.argument(0)
		val exclusionsTuple = interpreter.argument(1)
		val loader = interpreter.fiber().availLoader()
		             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		try
		{
			loader.addGrammaticalRestrictions(atomSet, exclusionsTuple)
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: SignatureException)
		{
			return interpreter.primitiveFailure(e)
		}

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(setTypeForSizesContentType(
			naturalNumbers(),
			ATOM.o()), zeroOrMoreOf(
			setTypeForSizesContentType(
				wholeNumbers(),
				ATOM.o()))), TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(E_LOADING_IS_OVER, E_CANNOT_DEFINE_DURING_COMPILATION,
			    E_INCORRECT_NUMBER_OF_ARGUMENTS)
				.setUnionCanDestroy(possibleErrors, true))
	}

}
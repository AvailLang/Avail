/*
 * P_CreateSendExpression.java
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

package com.avail.interpreter.primitive.phrases

import com.avail.descriptor.A_Bundle
import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.ListPhraseDescriptor
import com.avail.descriptor.MessageBundleDescriptor
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.SendPhraseDescriptor
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive

import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.topMeta
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import com.avail.descriptor.SendPhraseDescriptor.newSendNode
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Create a [send][SendPhraseDescriptor] from the specified [message][MessageBundleDescriptor], [list phrase][ListPhraseDescriptor] of [ ][PhraseKind.EXPRESSION_PHRASE], and [ ].  Do not apply semantic restrictions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateSendExpression : Primitive(3, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(3)
		val messageName = interpreter.argument(0)
		val argsListNode = interpreter.argument(1)
		val returnType = interpreter.argument(2)

		val argExpressions = argsListNode.expressionsTuple()
		val argsCount = argExpressions.tupleSize()
		val bundle: A_Bundle
		try
		{
			bundle = messageName.bundleOrCreate()
			val splitter = bundle.messageSplitter()
			if (splitter.numberOfArguments != argsCount)
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
			}
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e.errorCode)
		}

		return interpreter.primitiveSuccess(
			newSendNode(emptyTuple(), bundle, argsListNode, returnType))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				ATOM.o(),
				LIST_PHRASE.mostGeneralType(),
				topMeta()),
			SEND_PHRASE.mostGeneralType())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 3)
		//		final A_Type messageNameType = argumentTypes.get(0);
		//		final A_Type argsListNodeType = argumentTypes.get(1);
		val returnTypeType = argumentTypes[2]

		val returnType = returnTypeType.instance()
		return SEND_PHRASE.create(returnType)
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS
			).setUnionCanDestroy(possibleErrors, true))
	}

}
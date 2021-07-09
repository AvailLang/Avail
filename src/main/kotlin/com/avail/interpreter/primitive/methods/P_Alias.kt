/*
 * P_Alias.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.compiler.splitter.MessageSplitter
import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import com.avail.descriptor.atoms.A_Atom.Companion.setAtomBundle
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import com.avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
import com.avail.descriptor.bundles.MessageBundleDescriptor.Companion.newBundle
import com.avail.descriptor.maps.A_Map.Companion.forEach
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AmbiguousNameException
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import com.avail.exceptions.AvailErrorCode.E_ATOM_ALREADY_EXISTS
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.effects.LoadingEffectToRunPrimitive
import com.avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Alias a [name][A_String] to another [name][A_Atom].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_Alias : Primitive(2, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val newString: A_String = interpreter.argument(0)
		val oldAtom: A_Atom = interpreter.argument(1)

		val loader = interpreter.availLoaderOrNull()
		loader ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		if (oldAtom.isAtomSpecial)
		{
			return interpreter.primitiveFailure(E_SPECIAL_ATOM)
		}
		val newAtom =
			try
			{
				loader.lookupName(newString)
			}
			catch (e: AmbiguousNameException)
			{
				return interpreter.primitiveFailure(e)
			}

		if (newAtom.bundleOrNil.notNil)
		{
			return interpreter.primitiveFailure(E_ATOM_ALREADY_EXISTS)
		}
		val newBundle: A_Bundle = try
		{
			val oldBundle = oldAtom.bundleOrCreate()
			val method = oldBundle.bundleMethod
			loader.recordEffect(
				LoadingEffectToRunPrimitive(
					SpecialMethodAtom.ALIAS.bundle, newString, oldAtom))
			newBundle(newAtom, method, MessageSplitter(newString))
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e.errorCode)
		}

		newAtom.setAtomBundle(newBundle)
		if (loader.phase() == EXECUTING_FOR_COMPILE)
		{
			val root = loader.rootBundleTree()
			loader.module().lock {
				newBundle.definitionParsingPlans.forEach { _, value ->
					root.addPlanInProgress(newPlanInProgress(value, 1))
				}
			}
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				stringType,
				ATOM.o),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
					E_LOADING_IS_OVER,
					E_CANNOT_DEFINE_DURING_COMPILATION,
					E_SPECIAL_ATOM,
					E_AMBIGUOUS_NAME,
					E_ATOM_ALREADY_EXISTS)
				.setUnionCanDestroy(possibleErrors, true))
}

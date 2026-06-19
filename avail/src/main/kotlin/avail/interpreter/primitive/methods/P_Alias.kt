/*
 * P_Alias.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.methods

import avail.compiler.splitter.MessageSplitter
import avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import avail.descriptor.bundles.MessageBundleDescriptor.Companion.newBundle
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import avail.descriptor.representation.A_Atom
import avail.descriptor.representation.A_Atom.Companion.bundleOrCreate
import avail.descriptor.representation.A_Atom.Companion.bundleOrNil
import avail.descriptor.representation.A_Atom.Companion.isAtomSpecial
import avail.descriptor.representation.A_Atom.Companion.setAtomBundle
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Bundle
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_Bundle.Companion.definitionParsingPlans
import avail.descriptor.representation.A_BundleTree.Companion.addPlanInProgress
import avail.descriptor.representation.A_Map.Companion.forEachInMap
import avail.descriptor.representation.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.representation.A_String
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.exceptions.AmbiguousNameException
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import avail.exceptions.AvailErrorCode.E_ATOM_ALREADY_EXISTS
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.exceptions.MalformedMessageException
import avail.interpreter.effects.LoadingEffectToRunPrimitive
import avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** Alias a [name][A_String] to another [name][A_Atom].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_Alias : Primitive2(CanInline, HasSideEffect)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val newString: A_String = arg1
		val oldAtom: A_Atom = arg2

		val loader = availLoaderOrNull()
		loader ?: return fail(E_LOADING_IS_OVER)
		if (!loader.phase.isExecuting)
		{
			return fail(E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		if (oldAtom.isAtomSpecial)
		{
			return fail(E_SPECIAL_ATOM)
		}
		val newAtom =
			try
			{
				loader.lookupName(newString)
			}
			catch (e: AmbiguousNameException)
			{
				return fail(e.errorCode)
			}

		if (newAtom.bundleOrNil.notNil)
		{
			return fail(E_ATOM_ALREADY_EXISTS)
		}
		val newBundle: A_Bundle = try
		{
			val oldBundle = oldAtom.bundleOrCreate()
			val method = oldBundle.bundleMethod
			loader.recordEarlyEffect(
				LoadingEffectToRunPrimitive(
					SpecialMethodAtom.ALIAS, newString, oldAtom))
			newBundle(newAtom, method, MessageSplitter.split(newString))
		}
		catch (e: MalformedMessageException)
		{
			return fail(e.errorCode)
		}

		newAtom.setAtomBundle(newBundle)
		if (loader.phase == EXECUTING_FOR_COMPILE)
		{
			val root = loader.rootBundleTree
			loader.module.lock {
				newBundle.definitionParsingPlans.forEachInMap { _, value ->
					root.addPlanInProgress(newPlanInProgress(value, 1))
				}
			}
		}
		return nil
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				stringType,
				ATOM()),
			TOP())

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

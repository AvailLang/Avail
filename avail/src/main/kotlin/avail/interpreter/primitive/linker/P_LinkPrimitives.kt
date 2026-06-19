/*
 * P_LinkPrimitives.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.linker

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Module.Companion.moduleName
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.A_Variable.Companion.setValue
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_INVALID_PATH
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_LIBRARY_ALREADY_LINKED
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_NO_FILE
import avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive2
import avail.interpreter.primitive.PrimitiveClassLoader
import java.io.IOException
import java.net.MalformedURLException
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * **Primitive:** Link the [Primitive]s from the indicated jar file, in the same
 * module root as the loading module, using a [PrimitiveClassLoader]. The jar
 * must be specified using an Avail root-relative qualified path.
 *
 * @author Richard Arriaga
 */
@Suppress("unused")
object P_LinkPrimitives : Primitive2(CanInline, HasSideEffect)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val jarPath = arg1.asNativeString()
		val oldModuleOut: A_Variable = arg2
		val loader = availLoaderOrNull() ?: return fail(E_LOADING_IS_OVER)
		loader.statementCanBeSummarized(false)
		if (!loader.phase.isExecuting)
		{
			return fail(E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val module = module()
		val moduleName = module.moduleName.asNativeString()
		val currentRoot = runtime.moduleRoots().firstOrNull {
			it.resolver.getResolverReference(moduleName) != null
		}!!
		val jarReference = currentRoot.resolver.getResolverReference(jarPath)
			?: return fail(E_NO_FILE)
		val jarFile = Paths.get(jarReference.uri).toFile()
		if (!jarFile.exists())
		{
			return fail(E_NO_FILE)
		}
		mutex.withLock {
			PrimitiveClassLoader.jarLinked(jarFile.path)?.let { origLinker ->
				oldModuleOut.setValue(origLinker)
				return fail(E_LIBRARY_ALREADY_LINKED)
			}
			try
			{
				PrimitiveClassLoader(jarFile, module().moduleName)
			}
			catch (e: IOException)
			{
				return fail(E_IO_ERROR)
			}
			catch (e: SecurityException)
			{
				return fail(E_PERMISSION_DENIED)
			}
			catch (e: MalformedURLException)
			{
				return fail(E_INVALID_PATH)
			}
			return nil
		}
	}

	/**
	 * The lock that must be held when examining linking the JAR to guard
	 * against link race conditions.
	 */
	private val mutex = ReentrantLock()

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(nonemptyStringType, variableTypeFor(stringType)),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_PERMISSION_DENIED,
				E_INVALID_PATH,
				E_IO_ERROR,
				E_NO_FILE,
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_LIBRARY_ALREADY_LINKED
			))
}

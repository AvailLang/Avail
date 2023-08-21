/*
 * P_Hash.kt
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

package avail.interpreter.primitive.linker

import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_INVALID_PATH
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_NO_FILE
import avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.PrimitiveClassLoader
import avail.interpreter.PrimitiveClassLoader.Companion.PRIMITIVE_NAME_PREFIX
import avail.interpreter.execution.Interpreter
import java.io.IOException
import java.net.MalformedURLException
import java.nio.file.Paths
import java.util.jar.JarFile

/**
 * **Primitive:** Link the [Primitive]s from the indicated jar file, in the same
 * module root as the loading module, using a [PrimitiveClassLoader]. The jar
 * must be specified using an Avail root-relative qualified path.
 *
 * @author Richard Arriaga
 */
@Suppress("unused")
object P_LinkPrimitives : Primitive(1, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val jarPath = interpreter.argument(0).asNativeString()
		val loader = interpreter.availLoaderOrNull()
			?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		loader.statementCanBeSummarized(false)
		if (!loader.phase.isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val module = interpreter.module()
		val moduleName = module.moduleName.asNativeString()
		val currentRoot = interpreter.runtime.moduleRoots().firstOrNull {
			it.resolver.getResolverReference(moduleName) != null
		}!!
		val jarReference = currentRoot.resolver.getResolverReference(jarPath)
			?: return interpreter.primitiveFailure(E_NO_FILE)
		val jarFile = Paths.get(jarReference.uri).toFile()
		if (!jarFile.exists())
		{
			return interpreter.primitiveFailure(E_NO_FILE)
		}
		val primitives = mutableSetOf<String>()
		try
		{
			JarFile(jarFile).use { jar ->
				jar.entries().asIterator().forEach { entry ->
					if (!entry.name.endsWith(".class")) return@forEach
					val last = entry.name.split("/").last()
					if(last.startsWith(PRIMITIVE_NAME_PREFIX))
					{
						entry.name.replace(".class", "").let {
							primitives.add(it.replace("/", "."))
						}
					}
				}
			}
		}
		catch (e: IOException)
		{
			return interpreter.primitiveFailure(E_IO_ERROR)
		}
		catch (e: SecurityException)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED)
		}

		try
		{
			PrimitiveClassLoader(
				jarFile, interpreter.module().moduleName, primitives)
		}
		catch (e: SecurityException)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED)
		}
		catch (e: MalformedURLException)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH)
		}
		return interpreter.primitiveSuccess(emptySet)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(nonemptyStringType),
			setTypeForSizesContentType(wholeNumbers, nonemptyStringType))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_PERMISSION_DENIED,
				E_INVALID_PATH,
				E_IO_ERROR,
				E_NO_FILE,
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION
			))
}

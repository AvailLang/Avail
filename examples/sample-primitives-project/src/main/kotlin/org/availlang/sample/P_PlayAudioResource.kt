/*
 * P_AdditionTest.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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
@file:Suppress("ClassName")

package org.availlang.sample

import avail.builder.ModuleRoot
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor
import avail.descriptor.types.TupleTypeDescriptor
import avail.descriptor.variables.A_Variable
import avail.exceptions.AvailErrorCode
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.Semaphore
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * **Primitive:** Play an audio resource stored inside the current [ModuleRoot].
 *
 * @author Richard Arriaga
 */
@Suppress("unused")
object P_PlayAudioResource : Primitive(2, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		// This primitive expects two inputs
		interpreter.checkArgumentCount(2)
		// Parameter 1 is the root-relative fully qualified name of the audio
		// resource file to play
		val resourcePath = interpreter.argument(0).asNativeString()
		// Parameter 2 is the error message variable that is populated by this
		// primitive if the audio file format is not supported.
		val unsupportedFormatMessage: A_Variable =
			interpreter.argument(1)
		// The current A_Module where this resource is being played from
		val module = interpreter.module()
		// Transform the module name from an A_String to a native JVM string
		val moduleName = module.moduleName.asNativeString()
		val currentRoot = interpreter.runtime.moduleRoots().firstOrNull {
			it.resolver.getResolverReference(moduleName) != null
		}!!
		val soundReference =
			currentRoot.resolver.getResolverReference(resourcePath)
				?: return interpreter.primitiveFailure(AvailErrorCode.E_NO_FILE)
		val resourceFile = Paths.get(soundReference.uri).toFile()
		if (!resourceFile.exists())
		{
			return interpreter.primitiveFailure(AvailErrorCode.E_NO_FILE)
		}
		return try
		{
			// TODO run on a fiber?
			playAudio(resourceFile)
			interpreter.primitiveSuccess(nil)
		}
		catch (e: IOException)
		{
			interpreter.primitiveFailure(AvailErrorCode.E_IO_ERROR)
		}
		catch (e: UnsupportedAudioFileException)
		{
			unsupportedFormatMessage.setValue(stringFrom(e.localizedMessage))
			interpreter.primitiveFailure(
				AvailErrorCode.E_OPERATION_NOT_SUPPORTED)
		}
	}

	/**
	 * Play the audio file.
	 *
	 * @param audioFile
	 *   The audio resource [File] to play.
	 */
	private fun playAudio (audioFile: File) {
		val semaphore = Semaphore(0)
		AudioSystem.getAudioInputStream(audioFile).use {
			AudioSystem.getClip().apply {
				open(it)
				start()
				addLineListener { event ->
					if (event === LineEvent.Type.STOP)
					{
						semaphore.release()
					}
				}
			}
		}
		semaphore.acquire()
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(TupleTypeDescriptor.nonemptyStringType),
			PrimitiveTypeDescriptor.Types.TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				AvailErrorCode.E_NO_FILE,
				AvailErrorCode.E_IO_ERROR,
				AvailErrorCode.E_OPERATION_NOT_SUPPORTED))
}

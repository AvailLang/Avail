/*
 * JVMChunk.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived set this software
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
package com.avail.optimizer.jvm

import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.optimizer.ExecutableChunk
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L2Generator
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * A `JVMChunk` is an [ExecutableChunk] for the Java Virtual Machine. It is
 * produced by a [JVMTranslator] on behalf of an [L2Generator] that has just
 * completed a
 * [translation&#32;or&#32;optimization][L1Translator.translateToLevelTwo].
 *
 * In the initial cheesy version of JVM translation, the generated subclasses of
 * `JVMChunk` simply embed the reified [L2Instruction]s directly and execute
 * them without the [interpreter&#39;s][Interpreter] loop overhead. This
 * mechanism is a feel-good milestone, and is not intended to survive very long.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *   Construct a `JVMChunk`.
 */
abstract class JVMChunk @ReferencedInGeneratedCode constructor()
	: ExecutableChunk
{
	/**
	 * The L1 source code, if any is available; `null` otherwise. Primarily
	 * intended for debugging.
	 */
	@Suppress("unused")
	val l1Source: String?
		get () = try
		{
			val cl: Class<out JVMChunk?> = javaClass
			val m = cl.getMethod("runChunk", Interpreter::class.java)
			val an = m.getAnnotation(JVMChunkL1Source::class.java)
			val bytes = Files.readAllBytes(Paths.get(an.sourcePath))
			val buffer =
				StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
			buffer.toString()
		}
		catch (e: Throwable)
		{
			null
		}

	/**
	 * The L2 source code, if any is available; `null` otherwise. Primarily
	 * intended for debugging.
	 */
	@Suppress("unused")
	val l2Source: String?
		get () = try
		{
			val cl: Class<out JVMChunk?> = javaClass
			val m = cl.getMethod("runChunk", Interpreter::class.java)
			val an = m.getAnnotation(JVMChunkL2Source::class.java)
			val bytes = Files.readAllBytes(Paths.get(an.sourcePath))
			val buffer =
				StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
			buffer.toString()
		}
		catch (e: Throwable)
		{
			null
		}

	companion object
	{
		/**
		 * The [CheckedMethod] for the default constructor.
		 */
		@JvmField
		val chunkConstructor : CheckedConstructor =
			CheckedConstructor.constructorMethod(JVMChunk::class.java)

		/** An empty `long` array.  */
		@ReferencedInGeneratedCode
		@JvmField
		val noLongs = LongArray(0)

		/** Access to the field [noLongs]  */
		var noLongsField : CheckedField = CheckedField.staticField(
			JVMChunk::class.java,
			"noLongs",
			LongArray::class.java)

		/** An empty [AvailObject] array.  */
		@ReferencedInGeneratedCode
		@JvmField
		val noObjects = arrayOf<AvailObject>()

		/** Access to the field [noObjects]  */
		@JvmField
		var noObjectsField : CheckedField = CheckedField.staticField(
			JVMChunk::class.java,
			"noObjects",
			Array<AvailObject>::class.java)

		/**
		 * Throw a [RuntimeException] on account of a bad offset into the
		 * calling generated `JVMChunk` subclass's [runChunk][runChunk].
		 *
		 * @param offset
		 *   The illegal offset into the caller.
		 * @return
		 *   Pretends to return a [RuntimeException], but actually throws it
		 *   instead. This is for the convenience of the caller.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun badOffset(offset: Int): RuntimeException
		{
			throw RuntimeException("bad offset $offset")
		}

		/** The [CheckedMethod] for [badOffset].  */
		@JvmField
		val badOffsetMethod: CheckedMethod = CheckedMethod.staticMethod(
			JVMChunk::class.java,
			"badOffset",
			RuntimeException::class.java,
			Int::class.javaPrimitiveType!!)
	}
}

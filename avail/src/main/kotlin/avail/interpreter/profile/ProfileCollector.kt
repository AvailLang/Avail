/*
 * ProfileCollector.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.profile;

import avail.AvailRuntimeSupport.captureNanos
import avail.descriptor.representation.A_Module
import avail.descriptor.representation.A_Module.Companion.moduleName
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_RawFunction.Companion.module
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Styler.Companion.function
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.primitive.controlflow.P_ExitContinuationIf
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/**
 * A [ProfileCollector] captures trace information from a running [Interpreter].
 * It records calls to functions, exits from functions, and various special tags
 * like switching continuations and switching fibers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ProfileCollector(
	val interpreter: Interpreter,
	val file: File
) : Closeable
{
	val bufferedWrite = BufferedOutputStream(FileOutputStream(file), 8192)

	var latestTime = 0L

	val rawFunctions = mutableListOf<A_RawFunction>()

	val rawFunctionToIndex = mutableMapOf<A_RawFunction, Int>()

	val modules = mutableListOf<A_Module>()

	val moduleToIndex = mutableMapOf<A_Module, Int>()

	/**
	 * A fiber has just started running, perhaps resuming after an earlier
	 * suspension.  Record the current stack and the time of the event.
	 */
	fun startFiber()
	{
		emitEventTagAndTime(Event.StartFiber)
		writeStack()
	}

	/**
	 * A fiber has stopped running for some reason, perhaps due to completion or
	 * suspension.  Record the time of the event.  If the fiber starts up again,
	 * a [startFiber] event will record the stack.
	 */
	fun stopFiber()
	{
		emitEventTagAndTime(Event.StopFiber)
	}

	/**
	 * A function has been called, and it's using the [DefaultL1Chunk].  Record
	 * the time of the event, and information about what [A_RawFunction] was
	 * invoked.
	 */
	fun startCallL1(code: A_RawFunction)
	{
		emitEventTagAndTime(Event.StartCallL1)
		writeCode(code)
	}

	/**
	 * A function has been called, and its chunk is an [L2SimpleChunk].  Record
	 * the time of the event, and information about what [A_RawFunction] was
	 * invoked.
	 */
	fun startCallL2Simple(code: A_RawFunction)
	{
		emitEventTagAndTime(Event.StartCallL2Simple)
		writeCode(code)
	}

	/**
	 * A function has been called, and its chunk is an [L2Chunk].  Record the
	 * time of the event, and information about what [A_RawFunction] was
	 * invoked.
	 */
	fun startCallL2(code: A_RawFunction)
	{
		emitEventTagAndTime(Event.StartCallL2)
		writeCode(code)
	}

	/**
	 * A function call has either completed or been reified.  Which function can
	 * be reconstructed from an earlier [startCallL1], [startCallL2Simple],
	 * or [startCallL2].
	 */
	fun endCall()
	{
		emitEventTagAndTime(Event.EndCall)
	}

	/**
	 * The current continuation has been changed.  This is not a function call
	 * or return, but a consequence of a [P_ExitContinuationIf] or
	 * [P_RestartContinuation] or something of that nature.
	 */
	fun switchContinuation()
	{
		emitEventTagAndTime(Event.SwitchContinuation)
		writeStack()
	}

	private fun writeStack()
	{
		val stack = buildList {
			var continuation = interpreter.getReifiedContinuation()!!
			while (continuation.notNil)
			{
				add(continuation.function.code())
			}
		}
		writeULong(stack.size.toULong())
		stack.asReversed().forEach(::writeCode)
	}

	private fun writeDeltaTime()
	{
		val newTime = captureNanos(interpreter)
		val delta = newTime - latestTime
		latestTime = newTime
		writeULong(delta.toULong())
	}

	private fun writeCode(code: A_RawFunction)
	{
		var codeIndex = rawFunctionToIndex[code]
		if (codeIndex !== null)
		{
			// We've seen this code before, so just output its index + 1.
			writeULong((codeIndex + 1).toULong())
			return
		}
		codeIndex = rawFunctions.size
		rawFunctions.add(code)
		rawFunctionToIndex[code] = codeIndex
		// We haven't seen this code before, so output a zero, the code's name
		// string, the line number, and the module information.
		writeULong(0UL)
		writeString(code.methodName.asNativeString())
		writeULong(code.codeStartingLineNumber.toULong())
		// Now write the module information.
		val module = code.module
		var moduleIndex = moduleToIndex[module]
		if (moduleIndex !== null)
		{
			writeULong((moduleIndex + 1).toULong())
		}
		else
		{
			writeULong(0UL)
			val moduleName = when
			{
				module.isNil -> ""
				else -> module.moduleName.asNativeString()
			}
			writeString(moduleName)
			moduleIndex = modules.size
			modules.add(module)
			moduleToIndex[module] = moduleIndex
		}
	}

	private fun emitEventTagAndTime(event: Event)
	{
		emitByte(event.ordinal)
		writeDeltaTime()
	}

	private fun writeULong(value: ULong)
	{
		// 0..127 are written as a single byte.
		if (value <= 0x7F_UL)
		{
			emitByte(value.toInt())
		}
		else if (value <= 0x3FFF_UL)
		{
			// 128..16383 are written with six bits of the first byte
			// used for the high byte (first byte is 128..191).  The
			// second byte is the low byte.
			// Note that the two-byte sequences 80,00 through 80,7F are
			// an encoding hole that is not produced by this mechanism.
			emitByte((value.toInt() shr 8) + 0x80)
			emitByte(value.toInt() and 0xFF)
		}
		else if (value <= 0x003D_FFFF_UL)
		{
			// The first byte is 192..253, or almost six bits (after
			// dealing with the 192 bias).  The middle and low bytes
			// follow.  That allows up to 0x003D_FFFF to be written in
			// only three bytes. The middle and low bytes follow.
			// Note that three-byte sequences C0,00,00 through C0,3F,FF
			// are an encoding hole not produced by this mechanism.
			emitByte((value.toInt() shr 16) + 0xC0)
			emitByte(value.toInt() shr 8)
			emitByte(value.toInt())
		}
		else if (value <= 0xFFFF_FFFF_UL)
		{
			// Write an 0xFE byte, then four more bytes containing the
			// 32-bit unsigned value.
			// Note that five-byte sequences FE,00,00,00,00 through
			// FE,00,3D,FF,FF will be written with a shorter form, and
			// the long form is an encoding hole.
			emitByte(0xFE)
			emitByte(value.toInt() shr 24)
			emitByte(value.toInt() shr 16)
			emitByte(value.toInt() shr 8)
			emitByte(value.toInt())
		}
		else
		{
			// All the way up to 2^64-1.
			// Note that nine-byte sequences FF,00,00,00,00,00,00,00,00,00
			// through FF,00,00,00,00,FF,FF,FF,FF will be written with a
			// shorter form, and the long form is an encoding hole.
			emitByte(0xFF)
			emitByte((value shr 56).toInt())
			emitByte((value shr 48).toInt())
			emitByte((value shr 40).toInt())
			emitByte((value shr 32).toInt())
			emitByte((value shr 24).toInt())
			emitByte((value shr 16).toInt())
			emitByte((value shr 8).toInt())
			emitByte(value.toInt())
		}
	}

	/** Output the low byte of the given [Int]. */
	private fun emitByte(int: Int)
	{
		bufferedWrite.write(int)
	}

	private fun writeString(string: String)
	{
		writeULong(string.length.toULong())
		string.forEach { writeULong(it.code.toULong()) }
	}

	/**
	 * Write the [Event.EndOfFile] tag, to indicate the file is complete, then
	 * flush and close it.
	 */
	override fun close()
	{
		emitEventTagAndTime(Event.EndOfFile)
		bufferedWrite.flush()
		bufferedWrite.close()
	}

	enum class Event
	{
		StartFiber,
		StopFiber,
		StartCallL1,
		StartCallL2Simple,
		StartCallL2,
		EndCall,
		SwitchContinuation,
		EndOfFile
	}
}

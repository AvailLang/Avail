/*
 * AvailRejectedParseException.kt
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

package com.avail.compiler

import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.exceptions.PrimitiveThrownException
import com.avail.interpreter.primitive.compiler.P_RejectParsing
import java.lang.String.format
import java.util.function.Supplier

/**
 * An `AvailRejectedParseException` is thrown by primitive [P_RejectParsing] to
 * indicate the fiber running a semantic restriction (or macro body or prefix
 * function) has rejected the argument types or phrases for the reason specified
 * in the exception's constructor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class AvailRejectedParseException : PrimitiveThrownException
{
	/**
	 * The [ParseNotificationLevel] that indicates the priority of the parse
	 * theory that failed.
	 */
	val level: ParseNotificationLevel

	/**
	 * The [error&#32;message][StringDescriptor] indicating why a particular
	 * parse was rejected.
	 */
	val rejectionString: A_String by lazy { rejectionSupplier() }

	/**
	 * A [Supplier] that will produce the rejectionString if needed.
	 */
	internal val rejectionSupplier: () -> A_String

	/**
	 * Construct a new instance.  If this diagnostic is deemed relevant, the
	 * string will be presented after the word `"Expected..."`.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param rejectionString
	 *   The Avail [string][A_String] indicating why a particular parse was
	 *   rejected.
	 */
	constructor(level: ParseNotificationLevel, rejectionString: A_String)
	{
		this.level = level
		this.rejectionSupplier = { rejectionString }
	}

	/**
	 * Construct a new instance with a Java [String] as the pattern for the
	 * explanation, and arguments to be substituted into the pattern.  If this
	 * diagnostic is deemed relevant, the string will be presented after the
	 * word `"Expected..."`.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param rejectionPattern
	 *   The String to use as a pattern in [String.format].  The arguments with
	 *   which to instantiate the pattern are also supplied.
	 * @param rejectionArguments
	 *   The arguments that should be substituted into the pattern.
	 */
	constructor(
		level: ParseNotificationLevel,
		rejectionPattern: String,
		vararg rejectionArguments: Any)
	{
		this.level = level
		this.rejectionSupplier = {
			stringFrom(format(rejectionPattern, *rejectionArguments))
		}
	}

	/**
	 * Construct a new instance the most general way, with a function to produce
	 * an [Avail&#32;string][A_String] as needed.  If this diagnostic is deemed
	 * relevant, the string will be presented after the word `"Expected..."`.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param supplier
	 *   The function that produces a diagnostic [Avail&#32;string][A_String]
	 *   upon first request.
	 */
	constructor(level: ParseNotificationLevel, supplier: ()-> A_String)
	{
		this.level = level
		this.rejectionSupplier = supplier
	}
}

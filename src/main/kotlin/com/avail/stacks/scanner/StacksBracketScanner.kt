/*
 * StacksBracketScanner.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.stacks.scanner

import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.stacks.exceptions.StacksScannerException
import com.avail.stacks.tokens.AbstractStacksToken
import com.avail.stacks.tokens.BracketedStacksToken

/**
 * A Stacks scanner that tokenizes the lexeme of a [BracketedStacksToken].
 *
 * @author Richard Arriaga &lt;Rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new `StacksBracketScanner`.
 *
 * @param bracketToken A [BracketedStacksToken].
 */
class StacksBracketScanner private constructor(bracketToken: BracketedStacksToken)
	: AbstractStacksScanner(bracketToken.moduleName)
{
	/** The module file name without the path. */
	val moduleLeafName: String =
		moduleName.substring(moduleName.lastIndexOf(' ') + 1)

	init
	{
		this.tokenString(bracketToken.lexeme)
		this.lineNumber(bracketToken.lineNumber)
		this.filePosition(bracketToken.position)
		this.position(1)
	}

	/**
	 * Answer whether we have exhausted the comment string.
	 *
	 * @return Whether we are finished scanning.
	 */
	override fun atEnd(): Boolean= position == tokenString.length - 1

	/**
	 * Scan the already-specified [String] to produce [tokens][outputTokens].
	 *
	 * @throws StacksScannerException If a scanning exception happens.
	 */
	@Throws(StacksScannerException::class)
	private fun scan()
	{
		while (!atEnd())
		{
			startOfToken(position)
			ScannerAction.forCodePoint(next()).scan(this)
		}
	}

	companion object
	{
		/**
		 * Answer the [list][List] of [tokens][TokenDescriptor] that comprise a
		 * [Avail&#32;comment][CommentTokenDescriptor].
		 *
		 * @param bracketToken
		 *   A [comment&#32;bracket][BracketedStacksToken] to be tokenized.
		 * @return A [list][List] of all tokenized words in the
		 *   [AvailComment][CommentTokenDescriptor].
		 * @throws StacksScannerException If scanning fails.
		 */
		@Throws(StacksScannerException::class)
		fun scanBracketString(bracketToken: BracketedStacksToken)
			: List<AbstractStacksToken>
		{
			val scanner =
				StacksBracketScanner(bracketToken)
			scanner.scan()
			return scanner.outputTokens
		}
	}
}

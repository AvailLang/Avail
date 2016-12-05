/**
 * ArgumentForMacroOnly.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
package com.avail.compiler.splitter;
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.ListNodeDescriptor;

import static com.avail.compiler.ParsingOperation.*;

/**
 * An {@linkplain ArgumentForMacroOnly} is the translation of an {@linkplain
 * Metacharacter#UNDERSCORE underscore} (_) in a message name, followed
 * immediately by an {@linkplain Metacharacter#EXCLAMATION_MARK
 * exclamation mark} (!).  It indicates where an argument is expected – but
 * the argument is allowed to be ⊤-valued or ⊥-valued.  Functions (and
 * therefore method definitions) may not take arguments of type ⊤ or ⊥, so
 * this mechanism is restricted to use by macros, where the phrases
 * themselves (including phrases yielding ⊤ or ⊥) are what get passed to
 * the macro body.
 *
 * <p>Because {@link ListNodeDescriptor list phrases} have an {@linkplain
 * A_Phrase#expressionType()} that depends on the types of the expressionType
 * of each subexpression, and because ⊥ as an element in a tuple type makes
 * the entire resulting tuple type also be ⊥, we can't just directly accept
 * an expression that produces ⊤ or ⊥ (e.g., the resulting list's apparent
 * cardinality would be lost, as ⊥ is a subtype of every tuple type.</p>
 */
final class ArgumentForMacroOnly
extends Argument
{
	/**
	 * Construct a new {@link ArgumentForMacroOnly}.
	 *
	 * @param startTokenIndex The one-based token index of this argument.
	 */
	ArgumentForMacroOnly (
		final MessageSplitter splitter,
		final int startTokenIndex)
	{
		super(splitter, startTokenIndex);
	}

	/**
	 * Parse an argument expression which might be top-valued.
	 */
	@Override
	void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		generator.emit(this, PARSE_TOP_VALUED_ARGUMENT);
		generator.emitDelayed(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			MessageSplitter.indexForConstant(phraseType));
	}
}

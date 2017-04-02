/**
 * AvailCompilerResult.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.compiler;

import java.util.Collections;
import java.util.List;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.ModuleDescriptor;

/**
 * A {@code AvailCompilerResult} holds the results of {@linkplain
 * AvailCompiler compiling} a {@linkplain ModuleDescriptor module}. This
 * includes not only the module itself, but any ancillary artifacts produced as
 * side-effects of compilation, e.g., {@linkplain CommentTokenDescriptor Stacks
 * comments}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public final class AvailCompilerResult
{
	/**
	 * The {@linkplain AvailCompiler compiled} {@linkplain
	 * ModuleDescriptor module}.
	 */
	private final A_Module module;

	/**
	 * Answer the {@linkplain AvailCompiler compiled} {@linkplain
	 * ModuleDescriptor module}.
	 *
	 * @return The compiled module.
	 */
	public A_Module module ()
	{
		return module;
	}

	/**
	 * The complete collection {@linkplain Collections#unmodifiableList(List)
	 * unmodifiable} of {@linkplain CommentTokenDescriptor comment tokens} that
	 * were produced by the {@linkplain AvailScanner scanner}.
	 */
	private final List<A_Token> commentTokens;

	/**
	 * Answer the complete {@linkplain Collections#unmodifiableList(List)
	 * unmodifiable} collection of {@linkplain CommentTokenDescriptor tokens}
	 * that were produced by the {@linkplain AvailScanner scanner}.
	 *
	 * @return Some comment tokens.
	 */
	public List<A_Token> commentTokens ()
	{
		return commentTokens;
	}

	/**
	 * Construct a new {@link AvailCompilerResult}.
	 *
	 * @param module
	 *        The {@linkplain AvailCompiler compiled} {@linkplain
	 *        ModuleDescriptor module}.
	 * @param commentTokens
	 *        The complete {@linkplain Collections#unmodifiableList(List)
	 *        unmodifiable} collection of {@linkplain CommentTokenDescriptor
	 *        comment tokens} that were produced by the {@linkplain AvailScanner
	 *        scanner}.
	 */
	AvailCompilerResult (
		final A_Module module,
		final List<A_Token> commentTokens)
	{
		this.module = module;
		this.commentTokens = commentTokens;
	}
}

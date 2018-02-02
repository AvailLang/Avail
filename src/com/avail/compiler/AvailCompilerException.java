/*
 * AvailCompilerException.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.builder.ModuleName;
import com.avail.descriptor.ModuleDescriptor;

import java.io.File;

/**
 * An {@code AvailCompilerException} is thrown by the {@linkplain
 * AvailCompiler Avail compiler} when compilation fails for any reason.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class AvailCompilerException
extends Exception
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 486558432544374634L;

	/**
	 * The {@linkplain ModuleName fully-qualified name} of the {@linkplain
	 * ModuleDescriptor module} undergoing {@linkplain AvailCompiler
	 * compilation}.
	 */
	private final ModuleName moduleName;

	/**
	 * Answer the {@linkplain ModuleName fully-qualified name} of the
	 * {@linkplain ModuleDescriptor module} undergoing {@linkplain
	 * AvailCompiler compilation}.
	 *
	 * @return A {@linkplain ModuleName module name}.
	 */
	public ModuleName moduleName ()
	{
		return moduleName;
	}

	/**
	 * The position within the {@linkplain ModuleDescriptor module} undergoing
	 * {@linkplain AvailCompiler compilation} at which the error was
	 * detected.
	 */
	private final long position;

	/**
	 * Answer the position within the {@linkplain ModuleDescriptor module}
	 * undergoing {@linkplain AvailCompiler compilation} at which the
	 * error was detected.
	 *
	 * @return A {@linkplain File file} position.
	 */
	public long position ()
	{
		return position;
	}

	/**
	 * The position within the {@linkplain ModuleDescriptor module} undergoing
	 * {@linkplain AvailCompiler compilation} of the first line break
	 * after the position at which the error was detected.
	 */
	private final long endOfErrorLine;

	/**
	 * Answer the position within the {@linkplain ModuleDescriptor module}
	 * undergoing {@linkplain AvailCompiler compilation} of the first
	 * line break after the position at which the error was detected.
	 *
	 * @return A {@linkplain File file} position.
	 */
	public long endOfErrorLine ()
	{
		return endOfErrorLine;
	}

	/**
	 * Construct a new {@link AvailCompilerException}.
	 *
	 * @param moduleName
	 *        The {@linkplain ModuleName fully-qualified name} of the
	 *        {@linkplain ModuleDescriptor module} undergoing {@linkplain
	 *        AvailCompiler compilation}.
	 * @param position
	 *        The position within the {@linkplain ModuleDescriptor module}
	 *        undergoing {@linkplain AvailCompiler compilation} at which
	 *        the error was detected.
	 * @param endOfErrorLine
	 *        The position within the {@linkplain ModuleDescriptor module}'s
	 *        source of the line break following the error.  Useful for
	 *        displaying the error in context.
	 * @param errorText
	 *        The text of the error message, intended for display at the
	 *        encapsulated position.
	 */
	AvailCompilerException (
		final ModuleName moduleName,
		final long position,
		final long endOfErrorLine,
		final String errorText)
	{
		super(errorText);
		this.moduleName     = moduleName;
		this.position       = position;
		this.endOfErrorLine = endOfErrorLine;
	}
}

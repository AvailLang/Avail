/**
 * P_CurrentWorkingDirectory.java
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

package com.avail.interpreter.primitive.files;

import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Answer the {@linkplain
 * Path#toRealPath(LinkOption...) real path} of the current working directory.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CurrentWorkingDirectory
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CurrentWorkingDirectory().init(
			0, CannotFail, CanInline, CanFold);

	/**
	 * The current working directory of the Avail virtual machine. Because Java
	 * does not permit the current working directory to be changed, it is safe
	 * to cache the answer at class-loading time.
	 */
	private static final A_String currentWorkingDirectory;

	// Obtain the current working directory. Try to resolve this location to its
	// real path. If resolution fails, then just use the value of the "user.dir"
	// system property.
	static
	{
		final String userDir = System.getProperty("user.dir");
		final FileSystem fileSystem = FileSystems.getDefault();
		final Path path = fileSystem.getPath(userDir);
		String realPathString;
		try
		{
			realPathString = path.toRealPath().toString();
		}
		catch (final IOException|SecurityException e)
		{
			realPathString = userDir;
		}
		currentWorkingDirectory = stringFrom(realPathString).makeShared();
	}

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 0;
		return interpreter.primitiveSuccess(currentWorkingDirectory);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(emptyTuple(), stringType());
	}
}

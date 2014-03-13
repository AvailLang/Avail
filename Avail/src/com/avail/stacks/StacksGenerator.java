/**
 * StacksGenerator.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.stacks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map.Entry;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.compiler.AbstractAvailCompiler.ModuleImport;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.TupleDescriptor;

/**
 * An Avail documentation generator.  It takes tokenized method/class comments
 * in .avail files and creates navigable documentation from them.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksGenerator
{
	/**
	 * The default path to the output directory for module-oriented
	 * documentation and data files.
	 */
	public static final Path defaultDocumentationPath = Paths.get("stacks");

	/**
	 * The path for documentation storage as provided by the user.
	 */
	Path providedDocumentPath;

	/**
	 *  The location useds for storing any log files such as error-logs.
	 */
	public final Path logPath;

	/**
	 * A {@linkplain ModuleNameResolver} to resolve {@linkplain
	 * 			ModuleImport}
	 */
	final ModuleNameResolver resolver;

	/**
	 * The error log file for the malformed comments.
	 */
	StacksErrorLog errorLog;

	/**
	 * A map of {@linkplain ModuleName module names} to a list of all the method
	 * names exported from said module
	 */
	HashMap<A_String,A_Set> moduleToExportedMethodsMap;

	/**
	 * A map of {@linkplain ModuleName module names} to a list of all the method
	 * names exported from said module
	 */
	HashMap<A_String,StacksCommentsModule> moduleToComments;

	/**
	 * Construct a new {@link StacksGenerator}.
	 *
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param resolver
	 * 			A {@linkplain ModuleNameResolver} to resolve {@linkplain
	 * 			ModuleImport}
	 * @throws IllegalArgumentException
	 *         If the output path exists but does not specify a directory.
	 */
	public StacksGenerator(final Path outputPath,
		final ModuleNameResolver resolver)
		throws IllegalArgumentException
	{
		if (Files.exists(outputPath) && !Files.isDirectory(outputPath))
		{
			throw new IllegalArgumentException(
				outputPath + " exists and is not a directory");
		}

		this.resolver = resolver;

		this.logPath = outputPath
			.resolve("logs");
		this.errorLog = new StacksErrorLog(logPath);


		this.moduleToComments =
			new HashMap<A_String,StacksCommentsModule>();

		this.moduleToExportedMethodsMap =
			new HashMap<A_String,A_Set>();
	}

	/**
	 * Inform the {@linkplain StacksGenerator generator} about the documentation
	 * and linkage of a {@linkplain ModuleDescriptor module}.
	 *
	 * @param header
	 *        The {@linkplain ModuleHeader header} of the module.
	 * @param commentTokens
	 *        The complete {@linkplain TupleDescriptor collection} of
	 *        {@linkplain CommentTokenDescriptor comments} produced for the
	 *        given module.
	 */
	public synchronized void add (
		final ModuleHeader header,
		final A_Tuple commentTokens)
	{


		System.out.println("Starting scanning of comments in "
			+ header.moduleName.qualifiedName());

		StacksCommentsModule commentsModule = null;

		commentsModule = new StacksCommentsModule(
			header,commentTokens,moduleToExportedMethodsMap,errorLog, resolver);
		updateModuleToComments(commentsModule);
	}

	/**
	 * Update moduleToComments with a new {@linkplain StacksCommentsModule}.
	 * @param commentModule
	 * 		A new {@linkplain StacksCommentsModule} to add to moduleToComments;
	 */
	private void updateModuleToComments (
		final StacksCommentsModule commentModule)
	{

		moduleToComments.put(commentModule.moduleName(), commentModule);
	}

	/**
	 * Generate complete Stacks documentation.
	 *
	 * @param outermostModule
	 *        The outermost {@linkplain ModuleDescriptor module} for the
	 *        generation request.
	 */
	public synchronized void generate (final ModuleName outermostModule)
	{
		System.out.println("In generate()");
		final ByteBuffer closeHTML = ByteBuffer.wrap(String.format(
			"</ol>\n<h4>Error Count: %d</h4>\n</body>\n</html>"
				,errorLog.errorCount())
			.getBytes(StandardCharsets.UTF_8));
		errorLog.addLogEntry(closeHTML,0);

		final StringBuilder stringBuilder = new StringBuilder();
		for (final Entry<A_String, A_Set> entry :
			moduleToExportedMethodsMap.entrySet())
		{
			stringBuilder.append("<h3>").append(entry.getKey().toString())
				.append("</h3>\n");

		    final A_Tuple value = entry.getValue().asTuple();
			if (value.tupleSize() == 0)
			{
				//Do nothing
			}
			else
			{
				stringBuilder.append("<ol>\n");
				for (int i = 1, limit = value.tupleSize(); i <= limit; i++)
				{
					stringBuilder.append("<li>");
					stringBuilder.append(value.tupleAt(i).asNativeString());
					stringBuilder.append("</li>\n");
				}
				stringBuilder.append("</ol>\n");
			}
		}

		final StringBuilder stringBuilderImplementations = new StringBuilder();
		for (final Entry<A_String, StacksCommentsModule> entry :
			moduleToComments.entrySet())
		{
			stringBuilderImplementations.append("<h3>")
				.append(entry.getKey().toString())
				.append("</h3>\n")
				.append(entry.getValue().toString());
		}

		final StacksOutputFile myMapFile = new StacksOutputFile(
			logPath,
			"Header Map.html",
			("<!DOCTYPE html>\n<head><style>h3 "
				+ "{text-decoration:underline;}\n "
				+ "strong, em {color:blue;}</style>\n"
				+ "</head>\n<body>\n" + stringBuilder.toString()
				+ "</body>\n</html>"));

		final StacksOutputFile myMethodsFile = new StacksOutputFile(
			logPath,
			"Methods Map.html",
			("<!DOCTYPE html>\n<head><style>h3 "
				+ "{text-decoration:underline;}\n "
				+ "strong, em {color:blue;}</style>\n"
				+ "</head>\n<body>\n" + stringBuilderImplementations.toString()
				+ "</body>\n</html>"));
		try
		{
			//do nothing
		}
		finally
		{
			try
			{
				errorLog.file().close();
				myMapFile.file().close();
				myMethodsFile.file().close();
			}
			catch (final IOException e)
			{
				// TODO [RAA] Remove in favor of other convention
				e.printStackTrace();
			}
		}
		clear();

	}

	/**
	 * Clear all internal data structures and reinitialize the {@linkplain
	 * StacksGenerator generator} for subsequent usage.
	 */
	public synchronized void clear ()
	{
		moduleToComments.clear();
		moduleToExportedMethodsMap.clear();
	}
}

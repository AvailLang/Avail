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
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import com.avail.AvailRuntime;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.compiler.AbstractAvailCompiler.ModuleImport;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.utility.IO;
import com.avail.utility.Pair;

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
	 * The original incoming base path.
	 */
	final Path outputPath;

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
	 * The {@linkplain HTMLFileMap} is a map for all html files in
	 * stacks
	 */
	private final HTMLFileMap htmlFileMap;

	/**
	 * A map of {@linkplain ModuleName module names} to a list of all the method
	 * names exported from said module
	 */
	HashMap<String,StacksCommentsModule> moduleToComments;

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
		this.outputPath = outputPath;
		this.htmlFileMap = new HTMLFileMap();
		this.resolver = resolver;

		this.logPath = outputPath.resolve("logs");
		this.errorLog = new StacksErrorLog(logPath);

		this.providedDocumentPath = outputPath.resolve("library-documentation");

		this.moduleToComments =
			new HashMap<String,StacksCommentsModule>(50);
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
		StacksCommentsModule commentsModule = null;
		commentsModule = new StacksCommentsModule(
			header,commentTokens,errorLog, resolver,
			moduleToComments,htmlFileMap);
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
	 * @param runtime
	 *        An {@linkplain AvailRuntime runtime}.
	 * @param outermostModule
	 *        The outermost {@linkplain ModuleDescriptor module} for the
	 *        generation request.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public synchronized void generate (
			final AvailRuntime runtime,
			final ModuleName outermostModule)
		throws IOException
	{
		final ByteBuffer closeHTML = ByteBuffer.wrap(String.format(
			"</ol>\n<h4>Error Count: %d</h4>\n</body>\n</html>"
				,errorLog.errorCount())
			.getBytes(StandardCharsets.UTF_8));

		errorLog.addLogEntry(closeHTML,0);

		final StacksCommentsModule outerMost = moduleToComments
			.get(outermostModule.qualifiedName());

		try
		{
			Files.createDirectories(outputPath);
			Files.createDirectories(providedDocumentPath);
		}
		catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//Identify the location of the templates.
		final Path templatePackageName = Paths.get(
			"src/"
			+ StacksGenerator.class.getPackage().getName().replace('.', '/')
			+ "/configuration");

		final Path implementationWrapperTemplate =
			templatePackageName.resolve("implementation wrapper.html.template");

		final Path implementationProperties =
			templatePackageName.resolve("implementation html.properties");

		final int fileToOutPutCount =
			outerMost.calculateFinalImplementationGroupsMap(htmlFileMap,
				outputPath, implementationWrapperTemplate,
				implementationProperties, runtime,
				"/about-avail/documentation/stacks"
					+ "/library-documentation");

		createJSFiles(templatePackageName);

		//Create the main HTML landing page
		createMainHTML(templatePackageName);

		if (fileToOutPutCount > 0)
		{
			final StacksSynchronizer synchronizer =
				new StacksSynchronizer(fileToOutPutCount);

			moduleToComments
				.get(outermostModule.qualifiedName())
					.writeMethodsToHTMLFiles(providedDocumentPath,synchronizer,
						runtime,htmlFileMap,implementationWrapperTemplate,
						implementationProperties, errorLog);

			synchronizer.waitForWorkUnitsToComplete();
		}

		htmlFileMap.writeInternalLinksToJSON(
			providedDocumentPath.resolve("internalLink.json"));
		IO.close(errorLog.file());

		clear();

	}

	/**
	 * Clear all internal data structures and reinitialize the {@linkplain
	 * StacksGenerator generator} for subsequent usage.
	 */
	public synchronized void clear ()
	{
		moduleToComments.clear();
		htmlFileMap.clear();
	}

	/**
	 * Create all necessary JS files.
	 * @param templatePackageName
	 * 		The path of the package where all the templates reside
	 */
	private void createJSFiles(final Path templatePackageName)
	{
		final Path stacksAppFilePath =
			providedDocumentPath.resolve("stacksApp.js");

		final Path stacksAppTemplatePath =
			templatePackageName.resolve("stacksApp.js.template");

		final ArrayList<Pair<CharSequence,CharSequence>> swapPairs =
			new ArrayList<Pair<CharSequence,CharSequence>>();

		swapPairs.add(new Pair<CharSequence,CharSequence>(
			"{[{CATEGORY-CONTENT}]}",
			htmlFileMap.categoryMethodsToJson()));

		createFileFromTemplate(stacksAppTemplatePath,stacksAppFilePath,
			swapPairs);
	}

	/**
	 * Create the main HTML landing page for Stacks.
	 * @param templatePackageName
	 * 		The path of the package where all the templates reside
	 */
	private void createMainHTML(final Path templatePackageName)
	{
		final Path stacksTemplate =
			templatePackageName.resolve("stacks.html.template");

		final Path stacksHTML =
			providedDocumentPath.resolve("index.html");

		try
		{
			IO.close(FileChannel.open(stacksHTML,
				EnumSet.of(StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING)));

		}
		catch (final IOException e1)
		{

			e1.printStackTrace();
		}

		//Copy stacks.html.template to target destination
		try
		{
			Files.copy(
				stacksTemplate,
				stacksHTML,
				StandardCopyOption.REPLACE_EXISTING);
		}
		catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//Create new landing-detail.html from template
		final Path landingPath =
			providedDocumentPath.resolve("landing-detail.html");

		final Path landingTemplatePath =
			templatePackageName.resolve("landing-detail.html.template");

		final ArrayList<Pair<CharSequence,CharSequence>> swapPairs =
			new ArrayList<Pair<CharSequence,CharSequence>>();

		swapPairs.add(new Pair<CharSequence,CharSequence>(
			"{[{GENERATED-CONTENT}]}",
			htmlFileMap.categoryDescriptionTable()));

		createFileFromTemplate(landingTemplatePath, landingPath,swapPairs);
	}

	/**
	 * Given a template file path, create a new file with the provided new file
	 * path and replace given template place holders with replacement content.
	 * @param templateFilePath
	 * 		The {@linkplain Path path} to the template file.
	 * @param newFilePath
	 * 		The {@linkplain Path path} to the new file.
	 * @param replacementPairs
	 * 		The {@linkplain Pair pairs} of <template text, replacement text>
	 */
	public static void createFileFromTemplate(
		final Path templateFilePath,
		final Path newFilePath,
		final ArrayList<Pair<CharSequence,CharSequence>>replacementPairs)
	{
		FileChannel newFile;
		try
		{
			newFile =
				FileChannel.open(newFilePath,
					EnumSet.of(StandardOpenOption.CREATE,
						StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING));
			try
			{
				final String decodedTemplate =
					HTMLBuilder.getOuterHTMLTemplate(templateFilePath);
				String newFileContent = decodedTemplate;
				for (final Pair<CharSequence,CharSequence> pair :
					replacementPairs)
				{
					newFileContent =
						newFileContent.replace(pair.first(), pair.second());
				}
				newFile.write(ByteBuffer
					.wrap(newFileContent.getBytes(StandardCharsets.UTF_8)));
			}
			catch (final IOException e)
			{
				e.printStackTrace();
			}

			IO.close(newFile);
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
	}
}

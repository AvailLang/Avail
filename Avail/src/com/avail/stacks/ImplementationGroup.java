/**
 * ImplementationGroup.java
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

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import com.avail.AvailRuntime;
import com.avail.descriptor.A_String;

/**
 * A grouping of {@linkplain AbstractCommentImplementation implementations}
 * consisting of {@linkplain MethodCommentImplementation methods},
 * {@linkplain SemanticRestrictionCommentImplementation semantic restrictions},
 * {@linkplain GrammaticalRestrictionCommentImplementation grammatical
 * restrictions}, {@linkplain GlobalCommentImplementation globals}, and
 * {@linkplain ClassCommentImplementation classes}.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class ImplementationGroup
{
	/**
	 * The name of the implementation.  It is not final because it can be
	 * renamed.
	 */
	private final A_String name;

	/**
	 * @return the name
	 */
	public A_String name ()
	{
		return name;
	}

	/**
	 * The set of all names referring to this implementation
	 */
	private HashSet<String> aliases;

	/**
	 * The relative file path and file name
	 */
	private String filepath;

	/**
	 * A list of {@linkplain MethodCommentImplementation methods}
	 */
	private ArrayList<MethodCommentImplementation> methods;

	/**
	 * @return the {@linkplain MethodCommentImplementation methods}
	 */
	public ArrayList<MethodCommentImplementation> methods ()
	{
		return methods;
	}

	/**
	 * @param newMethod the {@linkplain MethodCommentImplementation method}
	 * to add
	 */
	public void addMethod (final MethodCommentImplementation newMethod)
	{
		methods.add(newMethod);
		addAlias(newMethod.signature().name());
	}

	/**
	 * A list of {@linkplain SemanticRestrictionCommentImplementation
	 * semantic restrictions}
	 */
	private ArrayList<SemanticRestrictionCommentImplementation>
		semanticRestrictions;

	/**
	 * @return the {@linkplain SemanticRestrictionCommentImplementation
	 * semantic restrictions}
	 */
	public ArrayList<SemanticRestrictionCommentImplementation>
		semanticRestrictions ()
	{
		return semanticRestrictions;
	}

	/**
	 * @param newSemanticRestriction the new {@linkplain
	 * SemanticRestrictionCommentImplementation semantic restriction} to add
	 */
	public void addSemanticRestriction (
		final SemanticRestrictionCommentImplementation newSemanticRestriction)
	{
		semanticRestrictions.add(newSemanticRestriction);
		addAlias(newSemanticRestriction.signature().name());
	}

	/**
	 * A list of {@linkplain GrammaticalRestrictionCommentImplementation
	 * grammatical restrictions}
	 */
	private ArrayList<GrammaticalRestrictionCommentImplementation>
		grammaticalRestrictions;

	/**
	 * @return the {@linkplain GrammaticalRestrictionCommentImplementation
	 * grammatical restrictions}
	 */
	public ArrayList<GrammaticalRestrictionCommentImplementation>
		grammaticalRestrictions ()
	{
		return grammaticalRestrictions;
	}

	/**
	 * @param newGrammaticalRestriction the {@linkplain
	 * GrammaticalRestrictionCommentImplementation grammatical restrictions}
	 * to add
	 */
	public void addGrammaticalRestriction (
		final GrammaticalRestrictionCommentImplementation
			newGrammaticalRestriction)
	{
		grammaticalRestrictions.add(newGrammaticalRestriction);
		addAlias(newGrammaticalRestriction.signature().name());
	}

	/**
	 * A {@linkplain ClassCommentImplementation class comment}
	 */
	private ClassCommentImplementation classImplementation;

	/**
	 * @return the classImplementation
	 */
	public ClassCommentImplementation classImplementation ()
	{
		return classImplementation;
	}
	/**
	 * @param aClassImplemenataion the classImplementation to set
	 */
	public void classImplemenataion (
		final ClassCommentImplementation aClassImplemenataion)
	{
		this.classImplementation = aClassImplemenataion;
		addAlias(aClassImplemenataion.signature().name());
	}

	/**
	 * A module {@linkplain GlobalCommentImplementation global}.
	 */
	private GlobalCommentImplementation global;

	/**
	 * @return the global
	 */
	public GlobalCommentImplementation global ()
	{
		return global;
	}
	/**
	 * @param aGlobal the global to set
	 */
	public void global (final GlobalCommentImplementation aGlobal)
	{
		this.global = aGlobal;
		addAlias(aGlobal.signature().name());
	}

	/**
	 * Construct a new {@link ImplementationGroup}.
	 *
	 * @param name The name of the implementation
	 */
	public ImplementationGroup (final A_String name)
	{
		this.name = name;
		this.methods = new ArrayList<MethodCommentImplementation>();
		this.semanticRestrictions =
			new ArrayList<SemanticRestrictionCommentImplementation>();
		this.grammaticalRestrictions =
			new ArrayList<GrammaticalRestrictionCommentImplementation>();
		this.aliases = new HashSet<String>();
	}

	/**
	 * Construct a new {@link ImplementationGroup} holding a {@linkplain
	 * ClassCommentImplementation class comment}.
	 *
	 * @param name The name of the implementation
	 * @param classImplementation the {@linkplain ClassCommentImplementation
	 * class comment}
	 */
	public ImplementationGroup (final A_String name,
		final ClassCommentImplementation classImplementation)
	{
		this.name = name;
		this.classImplementation = classImplementation;
	}

	/**
	 * Construct a new {@link ImplementationGroup} holding a {@linkplain
	 * GlobalCommentImplementation global comment}.
	 *
	 * @param name The name of the implementation
	 * @param global the {@linkplain GlobalCommentImplementation global comment}
	 */
	public ImplementationGroup (final A_String name,
		final GlobalCommentImplementation global)
	{
		this.name = name;
		this.global = global;
	}

	/**
	 * Create HTML file from implementation
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param qualifiedMethodName
	 * 		The full name of the method, module path and method name
	 * @param htmlOpenContent
	 * 		HTML document opening tags (e.g. header etc)
	 * @param htmlCloseContent
	 * 		HTML document closing the document tags
	 * @param synchronizer
	 * 		The {@linkplain StacksSynchronizer} used to control the creation
	 * 		of Stacks documentation
	 * @param runtime
	 *      An {@linkplain AvailRuntime runtime}.
	 * @param htmlFileMap
	 * 		A mapping object for all html files in stacks
	 */
	public void toHTML(final Path outputPath,
		final String qualifiedMethodName,
		final String htmlOpenContent, final String htmlCloseContent,
		final StacksSynchronizer synchronizer,
		final AvailRuntime runtime,
		final HTMLFileMap htmlFileMap)
	{
		final StringBuilder stringBuilder = new StringBuilder()
			.append(htmlOpenContent)
			.append(tabs(1) + "<h2 "
				+ HTMLBuilder.tagClass(HTMLClass.classMethodHeading) + ">")
			.append(name.asNativeString())
			.append("</h2>\n");

		if (!methods.isEmpty())
		{
			if (!grammaticalRestrictions.isEmpty())
			{
				final int listSize = grammaticalRestrictions.size();
				if (listSize > 1)
				{
					for (int i = 1; i < listSize; i++)
					{
						grammaticalRestrictions.get(0)
							.mergeGrammaticalRestrictionImplementations(
								grammaticalRestrictions.get(i));
					}

				}
				stringBuilder
					.append(grammaticalRestrictions.get(0).toHTML(htmlFileMap));
			}

			stringBuilder.append(tabs(1) + "<h4 "
					+ HTMLBuilder.tagClass(HTMLClass.classMethodSectionHeader)
					+ ">Implementations:</h4>\n")
				.append(tabs(1)
					+ "<div "
					+ HTMLBuilder
						.tagClass(HTMLClass.classMethodSectionContent) + ">\n");

			for (final MethodCommentImplementation implementation : methods)
			{
				stringBuilder.append(implementation.toHTML(htmlFileMap));
			}

			stringBuilder.append(tabs(1) + "</div>\n");

			if (!semanticRestrictions.isEmpty())
			{
				stringBuilder
					.append(tabs(1) + "<h4 "
						+ HTMLBuilder
							.tagClass(HTMLClass.classMethodSectionHeader)
						+ ">Semantic restrictions:</h4>\n")
					.append(tabs(1) + "<div "
						+ HTMLBuilder
							.tagClass(HTMLClass.classMethodSectionContent)
						+ ">\n");

				for (final SemanticRestrictionCommentImplementation
					implementation : semanticRestrictions)
				{
					stringBuilder.append(implementation.toHTML(htmlFileMap));
				}

				stringBuilder.append(tabs(1) + "</div>\n");
			}
			final String localPath = qualifiedMethodName
				.substring(1, qualifiedMethodName.lastIndexOf('/') + 1);

			final String hashedFileName = String.valueOf(name.hash()) + ".html";

			final StacksOutputFile htmlFile = new StacksOutputFile(
				outputPath.resolve(localPath), synchronizer, hashedFileName,
				runtime);

			htmlFile.write(stringBuilder.append(htmlCloseContent).toString());
		}
		else if (!(global == null))
		{
			stringBuilder.append(global().toHTML(htmlFileMap));
			final int leafFileNameStart =
				qualifiedMethodName.lastIndexOf('/') + 1;
			final String localPath = qualifiedMethodName
				.substring(1, leafFileNameStart);

			final String hashedFileName =
				qualifiedMethodName.substring(leafFileNameStart);

			final StacksOutputFile htmlFile = new StacksOutputFile(
				outputPath.resolve(localPath), synchronizer, hashedFileName,
				runtime);

			htmlFile.write(stringBuilder.append(htmlCloseContent).toString());
		}
		else if (!(classImplementation == null))
		{
			stringBuilder.append(classImplementation.toHTML(htmlFileMap));

			final String localPath = qualifiedMethodName
				.substring(1, qualifiedMethodName.lastIndexOf('/') + 1);

			final String hashedFileName = String.valueOf(name.hash()) + ".html";

			final StacksOutputFile htmlFile = new StacksOutputFile(
				outputPath.resolve(localPath), synchronizer, hashedFileName,
				runtime);

			htmlFile.write(stringBuilder.append(htmlCloseContent).toString());
		}
	}

	/**
	 * Determine if the implementation is populated.
	 * @return
	 */
	public boolean isPopulated()
	{
		return (!methods.isEmpty() ||
			!(global == null) ||
			!(classImplementation == null));
	}

	/**
	 * @return A set of category String names for this implementation.
	 */
	public HashSet<String> getCategorySet()
	{
		final HashSet<String> categorySet = new HashSet<String>();

		for (final MethodCommentImplementation implementation : methods)
		{
			categorySet.addAll(implementation.getCategorySet());
		}

		for (final GrammaticalRestrictionCommentImplementation implementation :
			grammaticalRestrictions)
		{
			categorySet.addAll(implementation.getCategorySet());
		}

		for (final SemanticRestrictionCommentImplementation implementation :
			semanticRestrictions)
		{
			categorySet.addAll(implementation.getCategorySet());
		}

		if (!(classImplementation == null))
		{
			categorySet.addAll(classImplementation.getCategorySet());
		}
		if (!(global == null))
		{
			categorySet.addAll(global.getCategorySet());
		}

		return categorySet;
	}

	/**
	 * @param numberOfTabs
	 * 		the number of tabs to insert into the string.
	 * @return
	 * 		a String consisting of the number of tabs requested in
	 * 		in numberOfTabs.
	 */
	public String tabs(final int numberOfTabs)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = 1; i <= numberOfTabs; i++)
		{
			stringBuilder.append('\t');
		}
		return stringBuilder.toString();
	}

	/**
	 * @return the aliases
	 */
	public HashSet<String> aliases ()
	{
		return aliases;
	}

	/**
	 * @param alias the alias to add to the set
	 */
	public void addAlias (final String alias)
	{
		aliases.add(alias);
	}

	/**
	 * @param newAliases the alias to add to the set
	 */
	public void addAliases (final String newAliases)
	{
		aliases.addAll(aliases);
	}

	/**
	 * @return the filepath
	 */
	public String filepath ()
	{
		return filepath;
	}

	/**
	 * @param newFilepath the filepath to set
	 */
	public void filepath (final String newFilepath)
	{
		this.filepath = newFilepath;
	}

	/**
	 * Generate json objects of alias names
	 * @return
	 */
	public ArrayList<String> aliasFilePathsJSON()
	{
		final ArrayList<String> jsonFilePaths = new ArrayList<String>();
		for (final String alias : aliases)
		{
			jsonFilePaths
				.add("\"" + alias + "\" : " + "\"" + filepath() + "\"");
		}
		return jsonFilePaths;
	}
}

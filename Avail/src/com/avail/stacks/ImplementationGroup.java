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
import java.util.HashMap;
import java.util.HashSet;
import com.avail.AvailRuntime;
import com.avail.descriptor.StringDescriptor;

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
	private final String name;

	/**
	 * @return the name
	 */
	public String name ()
	{
		return name;
	}

	/**
	 * The set of all names referring to this implementation
	 */
	private final HashSet<String> aliases;

	/**
	 * The relative file path and file name
	 */
	private String filepath;

	/**
	 * A map keyed by a unique identifier to {@linkplain
	 * MethodCommentImplementation methods}
	 */
	private final HashMap<String,MethodCommentImplementation> methods;

	/**
	 * @return the {@linkplain MethodCommentImplementation methods}
	 */
	public HashMap<String,MethodCommentImplementation> methods ()
	{
		return methods;
	}

	/**
	 * @param newMethod the {@linkplain MethodCommentImplementation method}
	 * to add
	 */
	public void addMethod (final MethodCommentImplementation newMethod)
	{
		methods.put(newMethod.identityCheck(),newMethod);
		addAlias(newMethod.signature().name());
	}

	/**
	 * A list of {@linkplain SemanticRestrictionCommentImplementation
	 * semantic restrictions}
	 */
	private final HashMap<String,SemanticRestrictionCommentImplementation>
		semanticRestrictions;

	/**
	 * @return the {@linkplain SemanticRestrictionCommentImplementation
	 * semantic restrictions}
	 */
	public HashMap<String,SemanticRestrictionCommentImplementation>
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
		semanticRestrictions.put(newSemanticRestriction.identityCheck(),
			newSemanticRestriction);
		addAlias(newSemanticRestriction.signature().name());
	}

	/**
	 * A list of {@linkplain GrammaticalRestrictionCommentImplementation
	 * grammatical restrictions}
	 */
	private final HashMap<String,GrammaticalRestrictionCommentImplementation>
		grammaticalRestrictions;

	/**
	 * @return the {@linkplain GrammaticalRestrictionCommentImplementation
	 * grammatical restrictions}
	 */
	public HashMap<String,GrammaticalRestrictionCommentImplementation>
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
		grammaticalRestrictions.put(newGrammaticalRestriction.identityCheck(),
			newGrammaticalRestriction);
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
	public ImplementationGroup (final String name)
	{
		this.name = name;
		this.methods = new HashMap<String,MethodCommentImplementation>();
		this.semanticRestrictions =
			new HashMap<String,SemanticRestrictionCommentImplementation>();
		this.grammaticalRestrictions =
			new HashMap<String,GrammaticalRestrictionCommentImplementation>();
		this.aliases = new HashSet<String>();
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
	 * @param implementationProperties
	 * 		The file path location of the HTML properties used to generate
	 * 		the bulk of the inner html of the implementations.
	 * @param startingTabCount
	 * 		The number of tabs in to start with.
	 * @param nameOfGroup
	 * 		The name of the implementation as it is to be displayed.
	 */
	public void toHTML(final Path outputPath,
		final String qualifiedMethodName,
		final String htmlOpenContent, final String htmlCloseContent,
		final StacksSynchronizer synchronizer,
		final AvailRuntime runtime,
		final HTMLFileMap htmlFileMap,
		final Path implementationProperties,
		final int startingTabCount,
		final String nameOfGroup)
	{
		final StringBuilder stringBuilder = new StringBuilder()
			.append(htmlOpenContent)
			.append(tabs(1) + "<h2 "
				+ HTMLBuilder.tagClass(HTMLClass.classMethodHeading) + ">")
			.append(nameOfGroup)
			.append("</h2>\n");

		if (!methods.isEmpty())
		{
			if (!grammaticalRestrictions.isEmpty())
			{
				final int listSize = grammaticalRestrictions.size();
				final ArrayList<GrammaticalRestrictionCommentImplementation>
					restrictions = new ArrayList
						<GrammaticalRestrictionCommentImplementation>();
				restrictions.addAll(grammaticalRestrictions.values());
				if (listSize > 1)
				{
					for (int i = 1; i < listSize; i++)
					{
						restrictions.get(0)
							.mergeGrammaticalRestrictionImplementations(
								restrictions.get(i));
					}

				}
				stringBuilder
					.append(restrictions.get(0)
						.toHTML(htmlFileMap,nameOfGroup));
			}

			stringBuilder.append(tabs(1) + "<h4 "
					+ HTMLBuilder.tagClass(HTMLClass.classMethodSectionHeader)
					+ ">Implementations:</h4>\n")
				.append(tabs(1)
					+ "<div "
					+ HTMLBuilder
						.tagClass(HTMLClass.classMethodSectionContent) + ">\n");

			for (final MethodCommentImplementation implementation :
				methods.values())
			{
				stringBuilder.append(implementation.toHTML(htmlFileMap,
					nameOfGroup));
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
					implementation : semanticRestrictions.values())
				{
					stringBuilder.append(implementation.toHTML(htmlFileMap,
						nameOfGroup));
				}

				stringBuilder.append(tabs(1) + "</div>\n");
			}
			final String localPath = qualifiedMethodName
				.substring(1, qualifiedMethodName.lastIndexOf('/') + 1);

			long hashedName = StringDescriptor.from(name).hash();
			hashedName = hashedName & 0xFFFFFFFFL;

			final String hashedFileName = String.valueOf(hashedName) + ".html";

			final StacksOutputFile htmlFile = new StacksOutputFile(
				outputPath.resolve(localPath), synchronizer, hashedFileName,
				runtime, name);

			htmlFile.write(stringBuilder.append(htmlCloseContent).toString());
		}
		else if (!(global == null))
		{
			stringBuilder.append(global().toHTML(htmlFileMap, nameOfGroup));
			final int leafFileNameStart =
				qualifiedMethodName.lastIndexOf('/') + 1;
			final String localPath = qualifiedMethodName
				.substring(1, leafFileNameStart);

			final String hashedFileName =
				qualifiedMethodName.substring(leafFileNameStart);

			final StacksOutputFile htmlFile = new StacksOutputFile(
				outputPath.resolve(localPath), synchronizer, hashedFileName,
				runtime,name);

			htmlFile.write(stringBuilder.append(htmlCloseContent).toString());
		}
		else if (!(classImplementation == null))
		{
			stringBuilder.append(classImplementation
				.toHTML(htmlFileMap, nameOfGroup));

			final String localPath = qualifiedMethodName
				.substring(1, qualifiedMethodName.lastIndexOf('/') + 1);

			long hashedName = StringDescriptor.from(name).hash();
			hashedName = hashedName & 0xFFFFFFFF;

			final String hashedFileName = String.valueOf(hashedName) + ".html";

			final StacksOutputFile htmlFile = new StacksOutputFile(
				outputPath.resolve(localPath), synchronizer, hashedFileName,
				runtime,name);

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

		for (final MethodCommentImplementation implementation :
			methods.values())
		{
			categorySet.addAll(implementation.getCategorySet());
		}

		for (final GrammaticalRestrictionCommentImplementation implementation :
			grammaticalRestrictions.values())
		{
			categorySet.addAll(implementation.getCategorySet());
		}

		for (final SemanticRestrictionCommentImplementation implementation :
			semanticRestrictions.values())
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

	/**
	 * Merge the input {@linkplain ImplementationGroup group} with this group
	 * @param group
	 * 		The {@linkplain ImplementationGroup} to merge into this group.
	 */
	public void mergeWith (final ImplementationGroup group)
	{
		classImplementation = group.classImplementation();
		global = group.global();
		methods.putAll(group.methods());
		semanticRestrictions.putAll(group.semanticRestrictions());
		grammaticalRestrictions.putAll(group.grammaticalRestrictions());
	}
}

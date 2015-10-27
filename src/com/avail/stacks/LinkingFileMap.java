/**
 * LinkingFileMap.java
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

package com.avail.stacks;

import static com.avail.utility.Strings.*;
import static java.nio.file.StandardOpenOption.*;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import com.avail.utility.Pair;
import com.avail.utility.json.JSONWriter;

/**
 * A holder for all categories in stacks
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class LinkingFileMap
{
	/**
	 * The map containing categories.  Keyed by name to description.
	 */
	private final HashMap<String,StacksDescription> categoryToDescription;

	/**
	 * The map containing categories.  Keyed by name to description.
	 */
	private final HashMap<String,ArrayList<Pair<String,String>>>
		categoryMethodList;

	/**
	 * A map of aliases to file links.
	 */
	private final HashMap<String,HashSet<String>> aliasesToFileLink;

	/**
	 * @return aliasesToFileLink
	 */
	public HashMap<String,HashSet<String>> aliasesToFileLink()
	{
		return aliasesToFileLink;
	}

	/**
	 * @param alias the alias to add to the map
	 * @param fileLink the file that the alias links to
	 */
	public void addAlias (final String alias, final String fileLink)
	{
		if (aliasesToFileLink.containsKey(alias))
		{
			aliasesToFileLink.get(alias).add(fileLink);
		}
		else
		{
			final HashSet<String> newLinks = new HashSet<String>();
			newLinks.add(fileLink);
			aliasesToFileLink.put(alias,newLinks);
		}
	}

	/**
	 * A map of aliases to file links.
	 */
	private final HashMap<String,String> namedFileLinks;

	/**
	 * A map of aliases to file links.
	 * @return namedFileLinks
	 */
	public HashMap<String,String> namedFileLinks()
	{
		return namedFileLinks;
	}

	/**
	 * @param alias the alias to add to the map
	 * @param fileLink the file that the alias links to
	 */
	public void addNamedFileLinks (final String alias, final String fileLink)
	{
		namedFileLinks.put(alias, fileLink);
	}

	/**
	 * A map of aliases to file links.
	 */
	private HashMap<String,String> internalLinks;

	/**
	 * @param links final link map to set.
	 *
	 */
	public void internalLinks (final HashMap<String,String> links)
	{
		this.internalLinks = links;
	}

	/**
	 * @return the internalLinks
	 */
	public HashMap<String,String> internalLinks ()
	{
		return internalLinks;
	}

	/**
	 * Construct a new {@link LinkingFileMap}.
	 *
	 */
	public LinkingFileMap ()
	{
		categoryToDescription = new HashMap<String,StacksDescription>();
		categoryMethodList =
			new HashMap<String,ArrayList<Pair<String,String>>>();
		aliasesToFileLink = new HashMap<String,HashSet<String>>();
		namedFileLinks = new HashMap<String,String>();
	}

	/**
	 * @return the categoryToDescription
	 */
	public HashMap<String,StacksDescription> categoryToDescription ()
	{
		return categoryToDescription;
	}

	/**
	 * Add a new category
	 * @param name
	 * 		The category name
	 * @param description
	 * 		The category description
	 */
	public void addCategoryToDescription (
		final String name,
		final StacksDescription description)
	{
		categoryToDescription.put(name,description);
	}

	/**
	 * Check to see if key is a listed category.
	 * @param key
	 * 		The key to search
	 * @return
	 * 		Whether or not the name is a category
	 */
	public boolean isCategory(final String key)
	{
		return categoryToDescription.containsKey(key);
	}

	/**
	 * Add a new method to the category method map, categoryMethodList,
	 * HashMap<String,ArrayList<Pair<String,String>>>
	 * @param categoryName
	 * 		The category Name
	 * @param methodLeafName
	 * 		The non-qualified method name
	 * @param methodAndMethodLink
	 * 		The link to the file.
	 *
	 */
	public void addCategoryMethodPair (final String categoryName,
		final String methodLeafName, final String methodAndMethodLink)
	{
		final Pair<String,String> methodPair =
			new Pair<String,String>(methodLeafName,
				methodAndMethodLink);
		if (categoryMethodList.containsKey(categoryName))
		{
			categoryMethodList.get(categoryName).add(methodPair);
		}
		else
		{
			final ArrayList <Pair<String,String>> aList =
				new ArrayList <Pair<String,String>>();
			aList.add(methodPair);
			categoryMethodList.put(categoryName, aList);
		}
	}

	/**
	 * A method that writes a JSON file of all the internal linking of Stacks
	 * files
	 * @param path
	 */
	public void writeCategoryLinksToJSON (final Path path)
	{
		final StandardOpenOption[] options = new StandardOpenOption[]
			{CREATE, TRUNCATE_EXISTING, WRITE};
		Writer writer;
		try
		{
			writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				options);

			final JSONWriter jsonWriter = new JSONWriter(writer);

			jsonWriter.startObject();
			jsonWriter.write("categories");
			jsonWriter.startArray();

			for (final Map.Entry<String, ArrayList<Pair<String, String>>> entry
				: categoryMethodList.entrySet())
			{
				jsonWriter.startObject();
				jsonWriter.write("selected");
				jsonWriter.write(false);
				jsonWriter.write("category");
				jsonWriter.write(entry.getKey());
				jsonWriter.write("methods");
				final ArrayList<Pair<String,String>> pairs = entry.getValue();
				jsonWriter.startArray();
				for (final Pair<String,String> pair : pairs)
				{
					final String distinct = (
						new StringBuilder().append(pair.first())
						.append(pair.second())).toString();
					final String relativeLink = pair.second().substring(1);
					jsonWriter.startObject();
					jsonWriter.write("methodName");
					jsonWriter.write(pair.first());
					jsonWriter.write("link");
					jsonWriter.write(relativeLink);
					jsonWriter.write("distinct");
					jsonWriter.write(distinct);
					jsonWriter.endObject();
				}
				jsonWriter.endArray();
				jsonWriter.endObject();
			}
			jsonWriter.endArray();
			jsonWriter.endObject();
			jsonWriter.close();
		}
		catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Create a json file that has the the categories' methods
	 * links.
	 * @return
	 */
	public String categoryMethodsToJson()
	{
		final StringBuilder stringBuilder = new StringBuilder().append("[\n");

		final ArrayList<String> categorySet =
			new ArrayList<String>(categoryMethodList.keySet());

		final int setSize = categorySet.size();

		if (setSize > 0)
		{
			for (int j = 0; j < setSize - 1; j++)
			{
				stringBuilder.append(tabs(1) + "{\n")
					.append(tabs(2) + "\"selected\" : false,\n")
					.append(tabs(2) + "\"category\" : \"")
					.append(categorySet.get(j))
					.append("\",\n" + tabs(2) +"\"methods\" : [\n");

				final ArrayList<Pair<String,String>> methodList =
					categoryMethodList.get(categorySet.get(j));

				final int listSize = methodList.size();

				if (listSize > 0)
				{
					for (int i = 0; i < listSize - 1; i++)
					{
						final Pair<String,String> pair = methodList.get(i);
						stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
							.append(pair.first()).append("\", \"link\" : \"")
							.append(pair.second().substring(1))
							.append("\", \"distinct\" : \"")
							.append(pair.first()).append(pair.second())
							.append("\"},\n");
					}

					final Pair<String,String> lastPair =
						methodList.get(listSize - 1);

					stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
						.append(lastPair.first()).append("\", \"link\" : \"")
						.append(lastPair.second().substring(1))
						.append("\", \"distinct\" : \"")
						.append(lastPair.first()).append(lastPair.second())
						.append("\"}\n" + tabs(2)+ "]\n" + tabs(1)+ "},\n");
				}
			}

			stringBuilder.append(tabs(1) + "{\n" + tabs(2) + "\"selected\" : "
					+ "false,\n")
				.append(tabs(2) + "\"category\" : \"")
				.append(categorySet.get(setSize - 1))
				.append("\",\n" + tabs(2) + "\"methods\" : [\n");

			final ArrayList<Pair<String,String>> methodList =
				categoryMethodList.get(categorySet.get(setSize - 1));

			final int listSize = methodList.size();

			if (listSize > 0)
			{
				for (int i = 0; i < listSize - 1; i++)
				{
					final Pair<String,String> pair = methodList.get(i);
					stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
						.append(pair.first()).append("\", \"link\" : \"")
						.append(pair.second().substring(1))
						.append("\", \"distinct\" : \"")
						.append(pair.first()).append(pair.second())
						.append("\"},\n");
				}

				final Pair<String,String> lastPair =
					methodList.get(listSize - 1);

				stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
					.append(lastPair.first()).append("\", \"link\" : \"")
					.append(lastPair.second().substring(1))
					.append("\", \"distinct\" : \"")
					.append(lastPair.first()).append(lastPair.second())
					.append("\"}\n" + tabs(2) + "]\n" + tabs(1) + "}\n]");
			}
		}

		return stringBuilder.toString();
	}

	/**
	 * Create category description html table
	 * @return
	 * 		html table text
	 */
	public String categoryDescriptionTable()
	{
		final StringBuilder stringBuilder = new StringBuilder()
		.append(tabs(1) + "<h4 "
			+ HTMLBuilder
				.tagClass(HTMLClass.classMethodSectionHeader)
			+ ">Stacks Categories</h4>\n")
		.append(tabs(1) + "<div "
			+ HTMLBuilder.tagClass(HTMLClass.classMethodSectionContent)
			+ ">\n")
	    .append(tabs(2) + "<table "
	    	+ HTMLBuilder.tagClass(HTMLClass.classStacks)
	    	+ ">\n")
	    .append(tabs(3) + "<thead>\n")
	    .append(tabs(4) + "<tr>\n")
	    .append(tabs(5) + "<th style=\"white-space:nowrap\" "
	    	+ HTMLBuilder.tagClass(
	    		HTMLClass.classStacks, HTMLClass.classGColLabelNarrow)
	    	+ " scope=\"col\">Category</th>\n")
	    .append(tabs(5) + "<th "
	    	+ HTMLBuilder.tagClass(
	    		HTMLClass.classStacks, HTMLClass.classGColLabelWide)
	    	+ " scope=\"col\">Description</th>\n")
	    .append(tabs(4) + "</tr>\n")
	    .append(tabs(3) + "</thead>\n")
	    .append(tabs(3) + "<tbody>\n");

		final ArrayList<String> sortedKeys = new ArrayList<String>();
		sortedKeys.addAll(categoryToDescription.keySet());

		Collections.sort(sortedKeys);

		for (final String category : sortedKeys)
		{
			stringBuilder
				.append(tabs(4)).append("<tr>\n")
				.append(tabs(5) + "<td "
					+ HTMLBuilder
						.tagClass(HTMLClass.classStacks, HTMLClass.classGCode)
					+ ">")
				.append(category)
				.append("</td>\n")
				.append(tabs(5) + "<td "
					+ HTMLBuilder
						.tagClass(HTMLClass.classStacks, HTMLClass.classIDesc)
					+ ">")
				.append(categoryToDescription.get(category).toHTML(this, 0,
					null))
				.append("</td>\n")
				.append(tabs(4) + "</tr>\n");
		}

		stringBuilder.append(tabs(3) + "</tbody>\n")
			.append(tabs(2) + "</table>\n")
			.append(tabs(1) + "</div>\n");
		return stringBuilder.toString();

	}

	/**
	 * Clear the field maps.
	 */
	public void clear()
	{
		categoryMethodList.clear();
		categoryToDescription.clear();
	}

	/**
	 * A method that writes a JSON file of all the internal linking of Stacks
	 * files
	 * @param path
	 */
	public void writeInternalLinksToJSON (final Path path)
	{
		final StandardOpenOption[] options = new StandardOpenOption[]
			{CREATE, TRUNCATE_EXISTING, WRITE};
		Writer writer;
		try
		{
			writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				options);

			final JSONWriter jsonWriter = new JSONWriter(writer);

			jsonWriter.startObject();

			for (final String key : internalLinks.keySet())
			{
				jsonWriter.write(key);
				jsonWriter.write(internalLinks.get(key));
			}
			jsonWriter.endObject();

			jsonWriter.close();
		}
		catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}

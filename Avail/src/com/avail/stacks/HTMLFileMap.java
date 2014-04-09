/**
 * HTMLFileMap.java
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

import java.util.ArrayList;
import java.util.HashMap;
import com.avail.utility.Pair;

/**
 * A holder for all categories in stacks
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class HTMLFileMap
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
	private final HashMap<String,String> aliasesToFileLink;

	/**
	 * @param alias the alias to add to the map
	 * @param fileLink the file that the alias links to
	 */
	public void addAlias (final String alias, final String fileLink)
	{
		aliasesToFileLink.put(alias,fileLink);
	}

	/**
	 * Construct a new {@link HTMLFileMap}.
	 *
	 */
	public HTMLFileMap ()
	{
		categoryToDescription = new HashMap<String,StacksDescription>();
		categoryMethodList =
			new HashMap<String,ArrayList<Pair<String,String>>>();
		aliasesToFileLink = new HashMap<String,String>();
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
	 * 		The link to the HTML file.
	 *
	 */
	public void addCategoryMethodPair (final String categoryName,
		final String methodLeafName, final String methodAndMethodLink)
	{
		final Pair<String,String> methodPair =
			new Pair<String,String>(methodLeafName,methodAndMethodLink);
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
							.append("\"},\n");
					}

					final Pair<String,String> lastPair =
						methodList.get(listSize - 1);

					stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
						.append(lastPair.first()).append("\", \"link\" : \"")
						.append(lastPair.second().substring(1))
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
						.append(pair.second().substring(1)).append("\"},\n");
				}

				final Pair<String,String> lastPair =
					methodList.get(listSize - 1);

				stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
					.append(lastPair.first()).append("\", \"link\" : \"")
					.append(lastPair.second().substring(1))
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
	    .append(tabs(3) + "<tbody>\n")
	    .append(tabs(4) + "<tr>\n");

		for (final String category : categoryToDescription.keySet())
		{
			stringBuilder
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
				.append(categoryToDescription.get(category).toHTML(this))
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
	 * @param numberOfTabs
	 * 		the number of tabs to insert into the string.
	 * @return
	 * 		a String consisting of the number of tabs requested in
	 * 		in numberOfTabs.
	 */
	private String tabs(final int numberOfTabs)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = 1; i <= numberOfTabs; i++)
		{
			stringBuilder.append('\t');
		}
		return stringBuilder.toString();
	}
}

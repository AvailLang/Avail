/**
 * StacksCategories.java
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
public class StacksCategories
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
	 * Construct a new {@link StacksCategories}.
	 *
	 */
	public StacksCategories ()
	{
		categoryToDescription = new HashMap<String,StacksDescription>();
		categoryMethodList =
			new HashMap<String,ArrayList<Pair<String,String>>>();
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
	public String toJson()
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
							.append(pair.second()).append("\"},\n");
					}

					final Pair<String,String> lastPair =
						methodList.get(listSize - 1);

					stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
						.append(lastPair.first()).append("\", \"link\" : \"")
						.append(lastPair.second())
						.append("\"}\n" + tabs(2)+ "]\n" + tabs(1)+ "},");
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
						.append(pair.second()).append("\"},\n");
				}

				final Pair<String,String> lastPair =
					methodList.get(listSize - 1);

				stringBuilder.append(tabs(3) + "{\"methodName\" : \"")
					.append(lastPair.first()).append("\", \"link\" : \"")
					.append(lastPair.second())
					.append("\"}\n" + tabs(2) + "]\n" + tabs(1) + "}\n]");
			}
		}

		return stringBuilder.toString();
	}

	/**
	 * Create the Angular JS file content that provides the category linking
	 * capability to the file index.html
	 * @return
	 * 		The string content of the Angular JS file.
	 */
	public String toAngularJS()
	{
		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder
			.append("var stacksApp = angular.module('stacksApp',[]);\n");
		stringBuilder
			.append("stacksApp.factory('Categories', function () {\n"
			+ tabs(1) + "var Categories = {};\n"
			+ tabs(1) + "Categories.content = ");
		stringBuilder.append(toJson());
		stringBuilder.append(";\n"
			+ tabs(1) + "return Categories;\n"
			+ "})\n");

		stringBuilder.append("function CategoriesCntrl($scope,Categories) {\n"
			+ tabs(1) + "$scope.categories = Categories;\n")
			.append(tabs(1) + "$scope.methodList = function()\n")
			.append(tabs(1) + "{\n")
			.append(tabs(2) + "var allCategories = $scope.categories;\n")
			.append(tabs(2) + "var filteredMethods = {};\n")
			.append(tabs(2) + "var finalList = {content : []};\n")
			.append(tabs(2) + "var selectedCount = 0;\n")
			.append(tabs(2) + "for (var i=0; i < allCategories.content.length;"
				+ "i++)\n")
			.append(tabs(2) + "{\n")
			.append(tabs(3) + "if (allCategories.content[i].selected)\n")
			.append(tabs(3) + "{\n")
			.append(tabs(4) + "selectedCount++;\n")
			.append(tabs(4) + "for  (var j = 0; j "
				+ "< allCategories.content[i].methods.length; j++)\n")
			.append(tabs(4) + "{\n")
			.append(tabs(5) + "var method = allCategories.content[i]"
				+ ".methods[j].methodName;\n")
			.append(tabs(5) + "try\n")
			.append(tabs(5) + "{\n")
			.append(tabs(6) + "var newCount = "
				+ "filteredMethods[method].count + 1;\n")
			.append(tabs(6) + "filteredMethods[method].count = newCount;\n")
			.append(tabs(5) + "}\n")
			.append(tabs(5) + "catch(err)\n")
			.append(tabs(5) + "{\n")
			.append(tabs(6) + "filteredMethods[method] =\n")
			.append(tabs(7) + "{\"name\" : method.toLowerCase(),\n")
			.append(tabs(7) + "\"link\" : allCategories.content[i]"
				+ ".methods[j].link,\n")
			.append(tabs(7) + "\"count\" : 1};\n")
			.append(tabs(5) + "}\n")
			.append(tabs(4) + "}\n")
			.append(tabs(3) + "}\n")
			.append(tabs(2) + "}\n")
			.append(tabs(2) + "for (var key in filteredMethods)\n")
			.append(tabs(2) + "{\n")
			.append(tabs(3) + "if (filteredMethods[key].count == "
				+ "selectedCount)\n")
			.append(tabs(3) + "{\n")
			.append(tabs(4) + "finalList.content.push({\"name\" : key, \"link\" : filteredMethods[key].link})\n")
			.append(tabs(3) + "}\n")
			.append(tabs(2) + "}\n")
			.append(tabs(2) + "return finalList;\n")
			.append(tabs(1) + "}\n")
			.append(tabs(1) + "$scope.linkValue = {};\n")
			.append(tabs(1) + "$scope.changeLinkValue = function(method) {\n")
			.append(tabs(2) + "$scope.linkValue = method.link;\n")
			.append(tabs(1) + "}\n")
			.append("}");


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
	public String tabs(final int numberOfTabs)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = 1; i <= numberOfTabs; i++)
		{
			stringBuilder.append('\t');
		}
		return stringBuilder.toString();
	}
}
/*
 * LinkingFileMap.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.stacks

import com.avail.stacks.comment.ModuleComment
import com.avail.utility.Strings.tabs
import com.avail.utility.json.JSONWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * A holder for all categories in stacks
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class LinkingFileMap
{
	/**
	 * The map containing categories.  Keyed by name to description.
	 */
	val categoryToDescription = mutableMapOf<String, StacksDescription>()

	/**
	 * The list of [ModuleComment]s for this compilation
	 */
	val moduleComments = mutableSetOf<ModuleComment>()

	/**
	 * The map containing categories.  Keyed by name to description.
	 */
	val categoryMethodList = mutableMapOf<String, MutableList<Pair<String, String>>>()

	/**
	 * A map of aliases to file links.
	 */
	val aliasesToFileLink = mutableMapOf<String, MutableSet<String>>()

	/**
	 * A map of aliases to file links.
	 */
	val namedFileLinks = mutableMapOf<String, String>()

	/**
	 * A map of aliases to file links.
	 */
	var internalLinks: MutableMap<String, String>? = null
		private set

	/**
	 * @param alias the alias to add to the map
	 * @param fileLink the file that the alias links to
	 */
	@Suppress("unused")
	fun addAlias(alias: String, fileLink: String)
	{
		if (aliasesToFileLink.containsKey(alias))
		{
			aliasesToFileLink[alias]!!.add(fileLink)
		}
		else
		{
			val newLinks = mutableSetOf<String>()
			newLinks.add(fileLink)
			aliasesToFileLink[alias] = newLinks
		}
	}

	/**
	 * @param alias the alias to add to the map
	 * @param fileLink the file that the alias links to
	 */
	fun addNamedFileLinks(alias: String, fileLink: String)
	{
		namedFileLinks[alias] = fileLink
	}

	/**
	 * @param links final link map to set.
	 */
	fun internalLinks(links: MutableMap<String, String>)
	{
		this.internalLinks = links
	}

	/**
	 * Add a new category
	 * @param name
	 *   The category name
	 * @param description
	 *   The category description
	 */
	fun addCategoryToDescription(name: String, description: StacksDescription)
	{
		categoryToDescription[name] = description
	}

	/**
	 * Check to see if key is a listed category.
	 *
	 * @param key
	 *   The key to search
	 * @return Whether or not the name is a category
	 */
	@Suppress("unused")
	fun isCategory(key: String): Boolean= categoryToDescription.containsKey(key)

	/**
	 * Add a new method to the category method map, categoryMethodList,
	 * Map<String></String>,List<Pair></Pair><String></String>,String>>>
	 *
	 * @param categoryName
	 *   The category Name
	 * @param methodLeafName
	 *   The non-qualified method name
	 * @param methodAndMethodLink
	 *   The link to the file.
	 */
	fun addCategoryMethodPair(
		categoryName: String,
		methodLeafName: String,
		methodAndMethodLink: String)
	{
		val methodPair = Pair(
			methodLeafName,
			methodAndMethodLink)
		if (categoryMethodList.containsKey(categoryName))
		{
			categoryMethodList[categoryName]!!.add(methodPair)
		}
		else
		{
			categoryMethodList[categoryName] = mutableListOf(methodPair)
		}
	}

	/**
	 * A method that writes a JSON file of all the internal linking of Stacks
	 * files
	 * @param path
	 */
	fun writeCategoryLinksToJSON(path: Path)
	{
		try
		{
			val options = arrayOf(CREATE, TRUNCATE_EXISTING, WRITE)
			val writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				*options)

			val jsonWriter = JSONWriter(writer)

			jsonWriter.startObject()
			jsonWriter.write("categories")
			jsonWriter.startArray()

			for ((key, pairs) in categoryMethodList)
			{
				jsonWriter.startObject()
				jsonWriter.write("selected")
				jsonWriter.write(false)
				jsonWriter.write("category")
				jsonWriter.write(key)
				jsonWriter.write("methods")
				jsonWriter.startArray()
				for (pair in pairs)
				{
					val distinct = pair.first + pair.second
					val relativeLink = pair.second.substring(1)
					jsonWriter.startObject()
					jsonWriter.write("methodName")
					jsonWriter.write(pair.first)
					jsonWriter.write("link")
					jsonWriter.write(relativeLink)
					jsonWriter.write("distinct")
					jsonWriter.write(distinct)
					jsonWriter.endObject()
				}
				jsonWriter.endArray()
				jsonWriter.endObject()
			}
			jsonWriter.endArray()
			jsonWriter.endObject()
			jsonWriter.close()
		}
		catch (e: IOException)
		{
			// TODO Auto-generated catch block
			e.printStackTrace()
		}

	}

	/**
	 * Create a json file that has the the categories' methods
	 * links.
	 * @return
	 */
	@Suppress("unused")
	fun categoryMethodsToJson(): String
	{
		val stringBuilder = StringBuilder().append("[\n")

		val categorySet = categoryMethodList.keys.toList()

		val setSize = categorySet.size

		if (setSize > 0)
		{
			for (j in 0 until setSize - 1)
			{
				stringBuilder
					.append(tabs(1))
					.append("{\n")
					.append(tabs(2))
					.append("\"selected\" : false,\n")
					.append(tabs(2))
					.append("\"category\" : \"")
					.append(categorySet[j])
					.append("\",\n")
					.append(tabs(2))
					.append("\"methods\" : [\n")

				val methodList = categoryMethodList[categorySet[j]]

				val listSize = methodList!!.size

				if (listSize > 0)
				{
					for (i in 0 until listSize - 1)
					{
						val pair = methodList[i]
						stringBuilder
							.append(tabs(3))
							.append("{\"methodName\" : \"")
							.append(pair.first)
							.append("\", \"link\" : \"")
							.append(pair.second.substring(1))
							.append("\", \"distinct\" : \"")
							.append(pair.first)
							.append(pair.second)
							.append("\"},\n")
					}

					val lastPair = methodList[listSize - 1]

					stringBuilder
						.append(tabs(3))
						.append("{\"methodName\" : \"")
						.append(lastPair.first)
						.append("\", \"link\" : \"")
						.append(lastPair.second.substring(1))
						.append("\", \"distinct\" : \"")
						.append(lastPair.first)
						.append(lastPair.second)
						.append("\"}\n")
						.append(tabs(2))
						.append("]\n")
						.append(tabs(1))
						.append("},\n")
				}
			}

			stringBuilder
				.append(tabs(1))
				.append("{\n")
				.append(tabs(2))
				.append("\"selected\" : ")
				.append("false,\n")
				.append(tabs(2))
				.append("\"category\" : \"")
				.append(categorySet[setSize - 1])
				.append("\",\n")
				.append(tabs(2))
				.append("\"methods\" : [\n")

			val methodList = categoryMethodList[categorySet[setSize - 1]]

			val listSize = methodList!!.size

			if (listSize > 0)
			{
				for (i in 0 until listSize - 1)
				{
					val pair = methodList[i]
					stringBuilder
						.append(tabs(3))
						.append("{\"methodName\" : \"")
						.append(pair.first)
						.append("\", \"link\" : \"")
						.append(pair.second.substring(1))
						.append("\", \"distinct\" : \"")
						.append(pair.first)
						.append(pair.second)
						.append("\"},\n")
				}

				val lastPair = methodList[listSize - 1]

				stringBuilder
					.append(tabs(3))
					.append("{\"methodName\" : \"")
					.append(lastPair.first)
					.append("\", \"link\" : \"")
					.append(lastPair.second.substring(1))
					.append("\", \"distinct\" : \"")
					.append(lastPair.first)
					.append(lastPair.second)
					.append("\"}\n")
					.append(tabs(2))
					.append("]\n")
					.append(tabs(1))
					.append("}\n]")
			}
		}

		return stringBuilder.toString()
	}

	/**
	 * Clear the field maps.
	 */
	fun clear()
	{
		categoryMethodList.clear()
		categoryToDescription.clear()
	}

	/**
	 * A method that writes a JSON file of all the internal linking of Stacks
	 * files
	 * @param path
	 */
	fun writeInternalLinksToJSON(path: Path)
	{
		try
		{
			val options = arrayOf(CREATE, TRUNCATE_EXISTING, WRITE)
			val writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				*options)

			val jsonWriter = JSONWriter(writer)

			jsonWriter.startObject()

			for ((key, value) in internalLinks!!)
			{
				jsonWriter.write(key)
				jsonWriter.write(value)
			}
			jsonWriter.endObject()

			jsonWriter.close()
		}
		catch (e: IOException)
		{
			// TODO Auto-generated catch block
			e.printStackTrace()
		}
	}

	/**
	 * Add a [ModuleComment].
	 *
	 * @param comment
	 *   The comment to add.
	 */
	fun addModuleComment(comment: ModuleComment)
	{
		moduleComments.add(comment)
	}

	/**
	 * A method that writes all the category comments discovered during parsing
	 * @param path
	 */
	fun writeCategoryDescriptionToJSON(path: Path, errorLog: StacksErrorLog)
	{
		try
		{
			val options = arrayOf(CREATE, TRUNCATE_EXISTING, WRITE)
			val writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				*options)

			val jsonWriter = JSONWriter(writer)

			jsonWriter.startObject()
			jsonWriter.write("categories")
			jsonWriter.startArray()

			for ((key, value) in categoryToDescription)
			{
				jsonWriter.startObject()
				jsonWriter.write("category")
				jsonWriter.write(key)
				jsonWriter.write("description")
				value.toJSON(this, 0, errorLog, jsonWriter)
				jsonWriter.endObject()
			}
			jsonWriter.endArray()
			jsonWriter.endObject()
			jsonWriter.close()
		}
		catch (e: IOException)
		{
			// TODO Auto-generated catch block
			e.printStackTrace()
		}

	}

	/**
	 * A method that writes all the module comments discovered during parsing
	 * to a file
	 * @param path
	 */
	fun writeModuleCommentsToJSON(path: Path, errorLog: StacksErrorLog)
	{
		try
		{
			val options = arrayOf(CREATE, TRUNCATE_EXISTING, WRITE)
			val writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				*options)

			val jsonWriter = JSONWriter(writer)

			jsonWriter.startObject()
			jsonWriter.write("modules")
			jsonWriter.startArray()

			for (comment in moduleComments)
			{
				comment.toJSON(this, "", errorLog, jsonWriter)
			}
			jsonWriter.endArray()
			jsonWriter.endObject()
			jsonWriter.close()
		}
		catch (e: IOException)
		{
			// TODO Auto-generated catch block
			e.printStackTrace()
		}
	}
}

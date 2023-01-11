/*
 * CommentGroup.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.stacks

import avail.AvailRuntime
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.stacks.comment.AvailComment
import avail.stacks.comment.ClassComment
import avail.stacks.comment.GlobalComment
import avail.stacks.comment.GrammaticalRestrictionComment
import avail.stacks.comment.MacroComment
import avail.stacks.comment.MethodComment
import avail.stacks.comment.SemanticRestrictionComment
import org.availlang.json.JSONWriter
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * A grouping of [AvailComment]s from an Avail module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class CommentGroup
{
	/**
	 * The name of the implementation.  It is not final because it can be
	 * renamed.
	 */
	val name: A_String

	/**
	 * The name of the module where this implementation is named and exported
	 * from.
	 */
	val namingModule: String

	/**
	 * Is this entire implementation for a private method?
	 */
	/**
	 * @return whether or not the method/class/macro is private
	 */
	val isPrivate: Boolean

	/**
	 * Indicates the implementation group has the
	 * [sticky][AvailComment.isSticky] trait
	 */
	var hasStickyComment: Boolean = false
		private set

	/**
	 * All the categories this group belongs to.
	 */
	val categories: MutableSet<String>

	/**
	 * The set of all names referring to this implementation
	 */
	val aliases: MutableSet<String>

	/**
	 * The relative file path and file name first, fileName second.
	 */
	var filepath: StacksFilename? = null
		private set

	/**
	 * A map keyed by a unique identifier to [MethodComment].
	 */
	val methods: MutableMap<String, MethodComment>

	/**
	 * A map keyed by a unique identifier to [MacroComment].
	 */
	val macros: MutableMap<String, MacroComment>

	/**
	 * A list of [semantic&#32;restrictions][SemanticRestrictionComment]
	 */
	val semanticRestrictions: MutableMap<String, SemanticRestrictionComment>

	/**
	 * A list of [grammatical restrictions][GrammaticalRestrictionComment]
	 */
	val grammaticalRestrictions: MutableMap<String, GrammaticalRestrictionComment>

	/**
	 * A [class&#32;comment][ClassComment]
	 */
	var classImplementation: ClassComment? = null
		private set

	/**
	 * A module [global][GlobalComment].
	 */
	var global: GlobalComment? = null
		private set

	/**
	 * Determine if the implementation is populated.
	 */
	val isPopulated: Boolean
		get() = methods.isNotEmpty() ||
			macros.isNotEmpty() ||
			global !== null ||
			classImplementation !== null

	/**
	 * set the boolean, [hasStickyComment], presumably to true as the default is
	 * false.
	 *
	 * @param aSwitch
	 *   The boolean value to set
	 */
	fun hasStickyComment(aSwitch: Boolean)
	{
		hasStickyComment = aSwitch
	}

	/**
	 * @return the name of all the categories this group belongs to.
	 */
	fun categories(): MutableSet<String>
	{
		if (categories.size > 1)
		{
			categories.remove("Unclassified")
		}
		return categories
	}

	/**
	 * @param newMethod
	 *   the [method][MethodComment] to add
	 */
	fun addMethod(newMethod: MethodComment)
	{
		methods[newMethod.identityCheck()] = newMethod
		aliases.add(newMethod.signature.name)

		//Will only ever be one category tag in categories
		for (category in newMethod.categories[0].categories())
		{
			categories.add(category.lexeme)
		}
	}

	/**
	 * @param newMacro
	 * the [method][MethodComment] to add
	 */
	fun addMacro(newMacro: MacroComment)
	{
		macros[newMacro.identityCheck()] = newMacro
		aliases.add(newMacro.signature.name)

		//Will only ever be one category tag in categories
		for (category in newMacro.categories[0].categories())
		{
			categories.add(category.lexeme)
		}
	}

	/**
	 * @param newSemanticRestriction
	 *   the new [SemanticRestrictionComment] to add
	 */
	fun addSemanticRestriction(
		newSemanticRestriction: SemanticRestrictionComment)
	{
		semanticRestrictions[newSemanticRestriction.identityCheck()] =
			newSemanticRestriction
		aliases.add(newSemanticRestriction.signature.name)
		//Will only ever be one category tag in categories
		for (category in newSemanticRestriction.categories[0].categories())
		{
			categories.add(category.lexeme)
		}
	}

	/**
	 * @param newGrammaticalRestriction
	 *   the [GrammaticalRestrictionComment] to add.
	 */
	fun addGrammaticalRestriction(
		newGrammaticalRestriction: GrammaticalRestrictionComment)
	{
		grammaticalRestrictions[newGrammaticalRestriction.identityCheck()] =
			newGrammaticalRestriction
		aliases.add(newGrammaticalRestriction.signature.name)

		//Will only ever be one category tag in categories
		for (category in newGrammaticalRestriction.categories[0].categories())
		{
			categories.add(category.lexeme)
		}
	}

	/**
	 * @param aClassImplementation the classImplementation to set
	 */
	fun classImplementation(
		aClassImplementation: ClassComment)
	{
		this.classImplementation = aClassImplementation
		aliases.add(aClassImplementation.signature.name)

		for (category in aClassImplementation.categories[0].categories())
		{
			categories.add(category.lexeme)
		}
	}

	/**
	 * @param aGlobal the global to set
	 */
	fun global(aGlobal: GlobalComment)
	{
		this.global = aGlobal
		aliases.add(aGlobal.signature.name)
		for (category in aGlobal.categories[0].categories())
		{
			categories.add(category.lexeme)
		}
	}

	/**
	 * Construct a new [CommentGroup].
	 *
	 * @param name
	 *   The name of the implementation
	 * @param namingModule
	 *   The name of the module where the implementation was first named and
	 *   exported from.
	 * @param filename
	 *   The name of the file that will represent this group.
	 * @param isPrivate
	 *   Indicates whether or not this is a public method
	 */
	constructor(
		name: A_String,
		namingModule: String, filename: StacksFilename,
		isPrivate: Boolean)
	{
		this.name = name
		this.methods = mutableMapOf()
		this.macros = mutableMapOf()
		this.semanticRestrictions = mutableMapOf()
		this.grammaticalRestrictions = mutableMapOf()
		this.aliases = mutableSetOf()
		this.namingModule = namingModule
		this.categories = mutableSetOf()
		this.filepath = filename
		this.isPrivate = isPrivate
		this.hasStickyComment = false
	}

	/**
	 * Construct a new [CommentGroup].
	 *
	 * @param group
	 *   The [CommentGroup] to copy.
	 * @param fileName
	 *   A pair with the fileName and path first and just the name of the file
	 *   that will represent this group second.
	 * @param namingModule
	 *   The name of the module where the implementation was first named and
	 *   exported from.
	 * @param name
	 *   The name of the implementation
	 */
	constructor(
		group: CommentGroup,
		fileName: StacksFilename,
		namingModule: String,
		name: A_String)
	{
		this.name = name
		this.methods = group.methods
		this.macros = group.macros
		this.semanticRestrictions = group.semanticRestrictions
		this.grammaticalRestrictions = group.grammaticalRestrictions
		this.aliases = group.aliases
		this.global = group.global
		this.classImplementation = group.classImplementation
		this.namingModule = namingModule
		this.categories = group.categories()
		this.filepath = fileName
		this.isPrivate = false
		this.hasStickyComment = false
	}

	/**
	 * Create JSON file from implementation.
	 *
	 * @param outputPath
	 *   The [path][Path] to the output [path][BasicFileAttributes.isDirectory]
	 *   for documentation and data files.
	 * @param synchronizer
	 *   The [StacksSynchronizer] used to control the creation of Stacks
	 *   documentation
	 * @param runtime
	 *   An [runtime][AvailRuntime].
	 * @param linkingFileMap
	 *   A mapping object for all files in stacks
	 * @param nameOfGroup
	 *   The name of the implementation as it is to be displayed.
	 * @param errorLog
	 *   The accumulating [StacksErrorLog]
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	fun toJSON(
		outputPath: Path,
		synchronizer: StacksSynchronizer,
		runtime: AvailRuntime,
		linkingFileMap: LinkingFileMap,
		nameOfGroup: String,
		errorLog: StacksErrorLog)
	{
		val jsonWriter = JSONWriter()
		jsonWriter.startObject()
		if (categories.size > 1)
		{
			categories.remove("Unclassified")
		}

		if (methods.isNotEmpty())
		{
			jsonWriter.write("type")
			jsonWriter.write("method")
			jsonWriter.write("name")
			jsonWriter.write(nameOfGroup)
			if (grammaticalRestrictions.isNotEmpty())
			{
				val listSize = grammaticalRestrictions.size
				val restrictions = grammaticalRestrictions.values.toList()
				if (listSize > 1)
				{
					for (i in 1 until listSize)
					{
						restrictions[0]
							.mergeGrammaticalRestrictionImplementations(
								restrictions[i])
					}
				}
				restrictions[0].toJSON(
					linkingFileMap, nameOfGroup, errorLog, jsonWriter)

			}
			jsonWriter.write("definitions")
			jsonWriter.startArray()
			for (implementation in methods.values)
			{
				jsonWriter.startObject()
				implementation
					.toJSON(linkingFileMap, nameOfGroup, errorLog, jsonWriter)
				jsonWriter.endObject()
			}
			jsonWriter.endArray()

			if (semanticRestrictions.isNotEmpty())
			{
				jsonWriter.write("semanticRestrictions")
				jsonWriter.startArray()
				for (implementation in semanticRestrictions.values)
				{
					jsonWriter.startObject()
					implementation
						.toJSON(
							linkingFileMap, nameOfGroup, errorLog,
							jsonWriter)
					jsonWriter.endObject()
				}
				jsonWriter.endArray()
			}
		}
		else if (macros.isNotEmpty())
		{
			jsonWriter.write("type")
			jsonWriter.write("macro")
			jsonWriter.write("name")
			jsonWriter.write(nameOfGroup)
			if (grammaticalRestrictions.isNotEmpty())
			{
				val listSize = grammaticalRestrictions.size
				val restrictions = grammaticalRestrictions.values.toList()
				if (listSize > 1)
				{
					for (i in 1 until listSize)
					{
						restrictions[0]
							.mergeGrammaticalRestrictionImplementations(
								restrictions[i])
					}
				}
				restrictions[0].toJSON(
					linkingFileMap, nameOfGroup, errorLog, jsonWriter)
			}
			jsonWriter.write("definitions")
			jsonWriter.startArray()
			for (implementation in macros.values)
			{
				jsonWriter.startObject()
				implementation.toJSON(
					linkingFileMap, nameOfGroup, errorLog,
					jsonWriter)
				jsonWriter.endObject()
			}
			jsonWriter.endArray()

			if (semanticRestrictions.isNotEmpty())
			{
				jsonWriter.write("semanticRestrictions")
				jsonWriter.startArray()
				for (implementation in semanticRestrictions.values)
				{
					jsonWriter.startObject()
					implementation
						.toJSON(
							linkingFileMap, nameOfGroup, errorLog,
							jsonWriter)
					jsonWriter.endObject()
				}
				jsonWriter.endArray()
			}
		}
		else if (global !== null)
		{
			global!!.toJSON(linkingFileMap, nameOfGroup, errorLog, jsonWriter)
		}
		else if (classImplementation !== null)
		{
			jsonWriter.write("type")
			jsonWriter.write("class")
			jsonWriter.write("name")
			jsonWriter.write(nameOfGroup)
			classImplementation!!.toJSON(
				linkingFileMap, nameOfGroup, errorLog,
				jsonWriter)
		}

		var fullFilePath = outputPath
		val directoryTree = filepath!!.pathName.split("/".toRegex())
			.dropLastWhile { it.isEmpty() }.toTypedArray()

		for (directory in directoryTree)
		{
			fullFilePath = fullFilePath.resolve(directory)
		}

		val jsonFile = StacksOutputFile(
			fullFilePath, synchronizer,
			filepath!!.leafFilename,
			runtime, name.asNativeString())

		jsonWriter.endObject()
		jsonFile.write(jsonWriter.toString())
		jsonWriter.close()
	}

	/**
	 * Merge the input [group][CommentGroup] with this group
	 *
	 * @param group
	 *   The [CommentGroup] to merge into this group.
	 */
	fun mergeWith(group: CommentGroup)
	{
		classImplementation = group.classImplementation
		global = group.global
		methods.putAll(group.methods)
		macros.putAll(group.macros)
		semanticRestrictions.putAll(group.semanticRestrictions)
		grammaticalRestrictions.putAll(group.grammaticalRestrictions)
	}
}

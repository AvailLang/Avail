/*
 * Command.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.server.messages

import com.avail.AvailRuntime
import com.avail.builder.ModuleName
import com.avail.builder.ModuleRoot
import com.avail.builder.ModuleRoots
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.A_Module
import com.avail.persistence.IndexedRepositoryManager
import com.avail.server.AvailServer
import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.Command.TrieNode
import com.avail.server.messages.Command.TrieNode.Companion.trie
import com.avail.utility.Nulls.stripNull
import java.lang.String.format
import java.util.*

/**
 * To direct the activities of an [Avail server][AvailServer], a client sends
 * [command messages][CommandMessage] that encode `Command`s. The `Command`
 * `enum` codifies the set of possible commands, and each member specifies the
 * decoding logic.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Command`. If it doesn't [require special
 * parsing][requiresSpecialParsing], then add it to the parsing [TrieNode.trie]
 * (treating the tokenization of its [name][name] on underscore boundaries as
 * its syntax).
 */
enum class Command
{
	/**
	 * Negotiate a protocol version.
	 */
	VERSION
	{
		override val requiresSpecialParsing get() = true
		override val syntaxHelp get() = "VERSION <version: INTEGER>"

		override fun parse(source: String): CommandMessage?
		{
			val tokens = source.split("\\s+".toRegex(), 2)
			if (tokens.size < 2
				|| !tokens[0].equals("version", ignoreCase = true))
			{
				return null
			}
			return try
			{
				VersionCommandMessage(Integer.parseInt(tokens[1]))
			}
			catch (e: NumberFormatException)
			{
				null
			}

		}
	},

	/**
	 * Upgrade the receiving [channel][AvailServerChannel] using a server-forged
	 * [UUID].
	 */
	UPGRADE
	{
		override val requiresSpecialParsing get() = true
		override val syntaxHelp get() = "UPGRADE <dashed: UUID>"

		override fun parse(source: String): CommandMessage?
		{
			val tokens = source.split("\\s+".toRegex(), 2)
			if (tokens.size < 2
				|| !tokens[0].equals("upgrade", ignoreCase = true))
			{
				return null
			}
			return try
			{
				UpgradeCommandMessage(UUID.fromString(tokens[1]))
			}
			catch (e: IllegalArgumentException)
			{
				null
			}

		}
	},

	/**
	 * List all [commands][Command].
	 */
	COMMANDS,

	/**
	 * List all [module roots][ModuleRoot].
	 */
	MODULE_ROOTS,

	/**
	 * List all [module root paths][ModuleRoots.writePathsOn].
	 */
	MODULE_ROOT_PATHS,

	/**
	 * Answer the [module roots path][ModuleRoots.modulePath].
	 */
	MODULE_ROOTS_PATH,

	/**
	 * List all source modules reachable from the [module roots][ModuleRoot].
	 */
	SOURCE_MODULES,

	/**
	 * List all entry points.
	 */
	ENTRY_POINTS,

	/**
	 * Clear all [binary module repositories][IndexedRepositoryManager].
	 */
	CLEAR_REPOSITORIES,

	/**
	 * Load the [module][A_Module] whose source is given by the specified
	 * fully-qualified path.
	 */
	LOAD_MODULE
	{
		override val requiresSpecialParsing get() = true
		override val syntaxHelp get() = "LOAD MODULE <fully-qualified: MODULE>"

		override fun parse(source: String): LoadModuleCommandMessage?
		{
			val tokens = source.split("\\s+".toRegex(), 3)
			if (tokens.size < 3
				|| !tokens[0].equals("load", ignoreCase = true)
				|| !tokens[1].equals("module", ignoreCase = true))
			{
				return null
			}
			return try
			{
				LoadModuleCommandMessage(ModuleName(tokens[2]))
			}
			catch (e: IllegalArgumentException)
			{
				null
			}
		}
	},

	/**
	 * Unload the [module][A_Module] whose source is given by the specified
	 * fully-qualified path.
	 */
	UNLOAD_MODULE
	{
		override val requiresSpecialParsing get() = true
		override val syntaxHelp get() =
			"UNLOAD MODULE <fully-qualified: MODULE>"

		override fun parse(source: String): CommandMessage?
		{
			val tokens = source.split("\\s+".toRegex(), 3)
			if (tokens.size < 3
				|| !tokens[0].equals("unload", ignoreCase = true)
				|| !tokens[1].equals("module", ignoreCase = true))
			{
				return null
			}
			return try
			{
				UnloadModuleCommandMessage(ModuleName(tokens[2]))
			}
			catch (e: IllegalArgumentException)
			{
				null
			}
		}
	},

	/**
	 * Unload all loaded modules.
	 */
	UNLOAD_ALL_MODULES,

	/**
	 * Run the specified entry point.
	 */
	RUN_ENTRY_POINT
	{
		override val requiresSpecialParsing get() = true
		override val syntaxHelp get() = "RUN <entry-point-command: EXPRESSION>"

		override fun parse(source: String): RunEntryPointCommandMessage?
		{
			val tokens = source.split("\\s+".toRegex(), 2)
			return (
				if (tokens.size < 2
					|| !tokens[0].equals("run", ignoreCase = true))
				{
					null
				}
				else RunEntryPointCommandMessage(tokens[1]))
		}
	},

	/**
	 * View all [fibers][A_Fiber] associated with the [server][AvailServer]'s
	 * [runtime][AvailRuntime].
	 */
	ALL_FIBERS;

	/**
	 * The tokenized syntax of the [command][Command], or `null` if the command
	 * does not have fixed syntax.
	 */
	private val syntax: Array<String>?

	/**
	 * `true` iff [command messages][CommandMessage] of this [form][Command]
	 * require special parsing, `false` otherwise.
	 */
	internal open val requiresSpecialParsing get() = false

	/**
	 * Apply special parsing logic to produce a [command
	 * message][CommandMessage] for this form of [command][Command].
	 *
	 * @param source
	 *   The source of the command.
	 * @return
	 *   A command message, or `null` if the tokens could not be understood as a
	 *   command of this kind.
	 */
	open fun parse(source: String): CommandMessage?
	{
		throw UnsupportedOperationException()
	}

	/**
	 * A [description][String] of the syntax of the [command][Command]. This
	 * description should be helpful to a human.
	 */
	open val syntaxHelp: String
		get()
		{
			// This method should be overridden by any member that requires
			// special parsing.
			assert(!requiresSpecialParsing)
			val tokens = stripNull(syntax)
			val builder = StringBuilder()
			var first = true
			for (token in tokens)
			{
				if (!first)
				{
					builder.append(' ')
				}
				builder.append(token)
				first = false
			}
			return builder.toString()
		}

	/**
	 * A `TrieNode` represents a [command][Command] prefix within the [trie].
	 */
	internal class TrieNode
	{
		/**
		 * The [command][Command] indicated by the prefix leading up to this
		 * [node][TrieNode], or `null` if no command is indicated.
		 */
		var command: Command? = null

		/**
		 * The transition [map][Map], indexed by following [token][String].
		 */
		val nextNodes: MutableMap<String, TrieNode> = HashMap()

		companion object
		{
			/** The root of the trie for parsing [commands][Command]. */
			private val trie = TrieNode()

			/**
			 * Add the specified [command][Command] to the parse [trie], using
			 * the given array of [String]s as the tokenized syntax of the
			 * command. All tokens are added as minuscule.
			 *
			 * @param command
			 *   A command.
			 * @param syntax
			 *   The tokenized syntax of the command.
			 */
			fun addCommand(command: Command, vararg syntax: String)
			{
				var node = trie
				for (token in syntax)
				{
					val lowercase = token.toLowerCase()
					node = node.nextNodes.computeIfAbsent(lowercase) {
						TrieNode()
					}
				}
				val existingCommand = node.command
				assert(existingCommand == null) {
					format(
						"Commands %s and %s have the same syntax!",
						existingCommand!!.name,
						command.name)
				}
				node.command = command
			}

			/**
			 * The [commands][Command] that [require special
			 * parsing][requiresSpecialParsing].
			 */
			private val speciallyParsedCommands = ArrayList<Command>(10)

			/**
			 * Add the specified [command][Command] to the
			 * [list][speciallyParsedCommands] of [specially parsed
			 * commands][requiresSpecialParsing].
			 *
			 * @param command
			 *   A specially parsed command.
			 */
			fun addSpeciallyParsedCommand(command: Command)
			{
				assert(command.requiresSpecialParsing)
				speciallyParsedCommands.add(command)
			}

			/**
			 * Parse a [command][Command] using the parsing [trie].
			 *
			 * @param source
			 *   The source of the command.
			 * @return
			 *   A command, or `null` if no command satisfied the given syntax.
			 */
			private fun parseSimpleCommand(source: String): Command?
			{
				var node = trie
				val tokens = source.split("\\s+".toRegex())
				for (token in tokens)
				{
					val nextNode = node.nextNodes[token.toLowerCase()]
						?: return null
					node = nextNode
				}
				return node.command
			}

			/**
			 * Parse one or more [command messages][CommandMessage] from the
			 * specified [source][String].
			 *
			 * @param source
			 *   The source of the command.
			 * @return
			 *   A [list][List] of command messages.
			 */
			fun parseCommands(source: String): List<CommandMessage>
			{
				val parsedCommands = ArrayList<CommandMessage>()
				val simpleCommand = parseSimpleCommand(source)
				if (simpleCommand != null)
				{
					parsedCommands.add(SimpleCommandMessage(simpleCommand))
				}
				for (command in speciallyParsedCommands)
				{
					try
					{
						val commandMessage = command.parse(source)
						if (commandMessage != null)
						{
							parsedCommands.add(commandMessage)
						}
					}
					catch (e: UnsupportedOperationException)
					{
						assert(false) {
							"Attempted to specially parse a simple command!"
						}
						throw e
					}

				}
				return parsedCommands
			}
		}
	}

	init
	{
		@Suppress("LeakingThis")
		if (!requiresSpecialParsing)
		{
			val tokens = name.split("_").toTypedArray()
			this.syntax = tokens
			TrieNode.addCommand(this, *tokens)
		}
		else
		{
			this.syntax = null
			TrieNode.addSpeciallyParsedCommand(this)
		}
	}

	companion object
	{
		/** An array of all [Command] enumeration values.  */
		val all = values()

		/**
		 * Parse an unambiguous [command message][CommandMessage] from the
		 * supplied raw [message][Message].
		 *
		 * @param message
		 *   A raw message, comprising command source.
		 * @return
		 *   An unambiguous command message.
		 * @throws CommandParseException
		 *   If an unambiguous command could not be parsed.
		 */
		@Throws(CommandParseException::class)
		fun parse(message: Message): CommandMessage
		{
			val source = message.content
			val parsedCommands = TrieNode.parseCommands(source)
			if (parsedCommands.isEmpty())
			{
				throw CommandParseException("unrecognized command")
			}
			if (parsedCommands.size > 1)
			{
				val formatter = Formatter()
				formatter.format(
					"ambiguous command: could be %s",
					if (parsedCommands.size == 2) "either" else "any of")
				var i = 0
				val size = parsedCommands.size
				while (i < size)
				{
					val command = parsedCommands[i]
					if (i > 0 && i < size - 1)
					{
						formatter.format(",")
					}
					else if (i == size - 1)
					{
						if (size > 2)
						{
							formatter.format(",")
						}
						formatter.format(" or")
					}
					formatter.format(" %s", command.command.name)
					i++
				}
				throw CommandParseException(formatter.toString())
			}
			// assert parsedCommands.size() == 1;
			return parsedCommands[0]
		}
	}
}

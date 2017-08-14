/**
 * Command.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.server.messages;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.avail.AvailRuntime;
import org.jetbrains.annotations.Nullable;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleRoot;
import com.avail.builder.ModuleRoots;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Module;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.server.AvailServer;
import com.avail.server.io.AvailServerChannel;
import com.avail.utility.json.JSONWriter;

/**
 * To direct the activities of an {@linkplain AvailServer Avail server}, a
 * client sends {@linkplain CommandMessage command messages} that encode {@code
 * Command}s. The {@code Command} {@code enum} codifies the set of possible
 * commands, and each member specifies the decoding logic.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public enum Command
{
	/**
	 * Negotiate a protocol version.
	 */
	VERSION
	{
		@Override
		boolean requiresSpecialParsing ()
		{
			return true;
		}

		@Override
		public String syntaxHelp ()
		{
			return "VERSION <version: INTEGER>";
		}

		@Override
		public @Nullable CommandMessage parse (final String source)
		{
			final String[] tokens = source.split("\\s+", 2);
			if (tokens.length < 2 || !tokens[0].equalsIgnoreCase("version"))
			{
				return null;
			}
			final int version;
			try
			{
				version = Integer.parseInt(tokens[1]);
			}
			catch (final NumberFormatException e)
			{
				return null;
			}
			return new VersionCommandMessage(version);
		}
	},

	/**
	 * Upgrade the receiving {@linkplain AvailServerChannel channel} using a
	 * server-forged {@link UUID}.
	 */
	UPGRADE
	{
		@Override
		boolean requiresSpecialParsing ()
		{
			return true;
		}

		@Override
		public String syntaxHelp ()
		{
			return "UPGRADE <dashed: UUID>";
		}

		@Override
		public @Nullable CommandMessage parse (final String source)
		{
			final String[] tokens = source.split("\\s+", 2);
			if (tokens.length < 2 || !tokens[0].equalsIgnoreCase("upgrade"))
			{
				return null;
			}
			final UUID uuid;
			try
			{
				uuid = UUID.fromString(tokens[1]);
			}
			catch (final IllegalArgumentException e)
			{
				return null;
			}
			return new UpgradeCommandMessage(uuid);
		}
	},

	/**
	 * List all {@linkplain Command commands}.
	 */
	COMMANDS,

	/**
	 * List all {@linkplain ModuleRoot module roots}.
	 */
	MODULE_ROOTS,

	/**
	 * List all {@linkplain ModuleRoots#writePathsOn(JSONWriter) module root
	 * paths}.
	 */
	MODULE_ROOT_PATHS,

	/**
	 * Answer the {@linkplain ModuleRoots#modulePath() module roots path}.
	 */
	MODULE_ROOTS_PATH,

	/**
	 * List all source modules reachable from the {@linkplain ModuleRoot module
	 * roots}.
	 */
	SOURCE_MODULES,

	/**
	 * List all entry points.
	 */
	ENTRY_POINTS,

	/**
	 * Clear all {@linkplain IndexedRepositoryManager binary module
	 * repositories}.
	 */
	CLEAR_REPOSITORIES,

	/**
	 * Load the {@linkplain A_Module module} whose source is given by the
	 * specified fully-qualified path.
	 */
	LOAD_MODULE
	{
		@Override
		boolean requiresSpecialParsing ()
		{
			return true;
		}

		@Override
		public String syntaxHelp ()
		{
			return "LOAD MODULE <fully-qualified: MODULE>";
		}

		@Override
		public @Nullable LoadModuleCommandMessage parse (final String source)
		{
			final String[] tokens = source.split("\\s+", 3);
			if (tokens.length < 3
				|| !tokens[0].equalsIgnoreCase("load")
				|| !tokens[1].equalsIgnoreCase("module"))
			{
				return null;
			}
			final ModuleName name;
			try
			{
				name = new ModuleName(tokens[2]);
			}
			catch (final IllegalArgumentException e)
			{
				return null;
			}
			return new LoadModuleCommandMessage(name);
		}
	},

	/**
	 * Unload the {@linkplain A_Module module} whose source is given by the
	 * specified fully-qualified path.
	 */
	UNLOAD_MODULE
	{
		@Override
		boolean requiresSpecialParsing ()
		{
			return true;
		}

		@Override
		public String syntaxHelp ()
		{
			return "UNLOAD MODULE <fully-qualified: MODULE>";
		}

		@Override
		public @Nullable CommandMessage parse (final String source)
		{
			final String[] tokens = source.split("\\s+", 3);
			if (tokens.length < 3
				|| !tokens[0].equalsIgnoreCase("unload")
				|| !tokens[1].equalsIgnoreCase("module"))
			{
				return null;
			}
			final ModuleName name;
			try
			{
				name = new ModuleName(tokens[2]);
			}
			catch (final IllegalArgumentException e)
			{
				return null;
			}
			return new UnloadModuleCommandMessage(name);
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
		@Override
		boolean requiresSpecialParsing ()
		{
			return true;
		}

		@Override
		public String syntaxHelp ()
		{
			return "RUN <entry-point-command: EXPRESSION>";
		}

		@Override
		public @Nullable RunEntryPointCommandMessage parse (final String source)
		{
			final String[] tokens = source.split("\\s+", 2);
			if (tokens.length < 2 || !tokens[0].equalsIgnoreCase("run"))
			{
				return null;
			}
			return new RunEntryPointCommandMessage(tokens[1]);
		}
	},

	/**
	 * View all {@linkplain A_Fiber fibers} associated with the {@linkplain
	 * AvailServer server}'s {@linkplain AvailRuntime runtime}.
	 */
	ALL_FIBERS;

	/** An array of all {@link Command} enumeration values. */
	private static final Command[] all = values();

	/**
	 * Answer an array of all {@link Command} enumeration values.
	 *
	 * @return An array of all {@link Command} enum values.  Do not
	 *         modify the array.
	 */
	public static Command[] all ()
	{
		return all;
	}

	/**
	 * Do {@linkplain CommandMessage command messages} of this {@linkplain
	 * Command form} require special parsing?
	 *
	 * @return {@code true} if special parsing is required, {@code false}
	 *         otherwise.
	 */
	boolean requiresSpecialParsing ()
	{
		return false;
	}

	/**
	 * Apply special parsing logic to produce a {@linkplain CommandMessage
	 * command message} for this form of {@linkplain Command command}.
	 *
	 * @param source
	 *        The source of the command.
	 * @return A command message, or {@code null} if the tokens could not be
	 *         understood as a command of this kind.
	 */
	public @Nullable CommandMessage parse (final String source)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * The tokenized syntax of the {@linkplain Command command}, or {@code null}
	 * if the command does not have fixed syntax.
	 */
	private final @Nullable String[] syntax;

	/**
	 * Answer a {@linkplain String description} of the syntax of the {@linkplain
	 * Command command}. This description should be helpful to a human.
	 *
	 * @return A concise syntax guide.
	 */
	public String syntaxHelp ()
	{
		// This method should be overridden by any member that requires special
		// parsing.
		assert !requiresSpecialParsing();
		final String[] tokens = syntax;
		assert tokens != null;
		final StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (final String token : tokens)
		{
			if (!first)
			{
				builder.append(' ');
			}
			builder.append(token);
			first = false;
		}
		return builder.toString();
	}

	/**
	 * A {@code TrieNode} represents a {@linkplain Command command} prefix
	 * within the {@linkplain #trie}.
	 */
	static final class TrieNode
	{
		/**
		 * The {@linkplain Command command} indicated by the prefix
		 * leading up to this {@linkplain TrieNode node}, or {@code null} if no
		 * command is indicated.
		 */
		@Nullable Command command;

		/**
		 * The transition {@linkplain Map map}, indexed by following {@linkplain
		 * String token}.
		 */
		final Map<String, TrieNode> nextNodes = new HashMap<>();

		/**
		 * The root of the trie for parsing {@linkplain Command commands}.
		 */
		private static final TrieNode trie = new TrieNode();

		/**
		 * Add the specified {@linkplain Command command} to the parse
		 * {@linkplain #trie}, using the given array of {@link String}s as the
		 * tokenized syntax of the command. All tokens are added as minuscule.
		 *
		 * @param command
		 *        A command.
		 * @param syntax
		 *        The tokenized syntax of the command.
		 */
		static void addCommand (
			final Command command,
			final String... syntax)
		{
			TrieNode node = trie;
			for (final String token : syntax)
			{
				final String lowercase = token.toLowerCase();
				node = node.nextNodes.computeIfAbsent(
					lowercase, k -> new TrieNode());
			}
			final Command existingCommand = node.command;
			assert existingCommand == null : String.format(
				"Commands %s and %s have the same syntax!",
				existingCommand.name(),
				command.name());
			node.command = command;
		}

		/**
		 * The {@linkplain Command commands} that {@linkplain
		 * #requiresSpecialParsing() require special parsing}.
		 */
		private static final List<Command> speciallyParsedCommands =
			new ArrayList<>(10);

		/**
		 * Add the specified {@linkplain Command command} to the {@linkplain
		 * #speciallyParsedCommands list} of {@linkplain
		 * #requiresSpecialParsing() specially parsed commands}.
		 *
		 * @param command
		 *        A specially parsed command.
		 */
		static void addSpeciallyParsedCommand (final Command command)
		{
			assert command.requiresSpecialParsing();
			speciallyParsedCommands.add(command);
		}

		/**
		 * Parse a {@linkplain Command command} using the parsing {@linkplain
		 * #trie}.
		 *
		 * @param source
		 *        The source of the command.
		 * @return A command, or {@code null} if no command satisfied the
		 *         given syntax.
		 */
		private static @Nullable Command parseSimpleCommand (
			final String source)
		{
			TrieNode node = trie;
			final String[] tokens = source.split("\\s+");
			for (final String token : tokens)
			{
				final TrieNode nextNode = node.nextNodes.get(
					token.toLowerCase());
				if (nextNode == null)
				{
					return null;
				}
				node = nextNode;
			}
			return node.command;
		}

		/**
		 * Parse one or more {@linkplain CommandMessage command messages} from
		 * the specified {@linkplain String source}.
		 *
		 * @param source
		 *        The source of the command.
		 * @return A {@linkplain List list} of command messages.
		 */
		static List<CommandMessage> parseCommands (final String source)
		{
			final List<CommandMessage> parsedCommands = new ArrayList<>();
			final Command simpleCommand = parseSimpleCommand(source);
			if (simpleCommand != null)
			{
				parsedCommands.add(new SimpleCommandMessage(simpleCommand));
			}
			for (final Command command : speciallyParsedCommands)
			{
				try
				{
					final CommandMessage commandMessage = command.parse(source);
					if (commandMessage != null)
					{
						parsedCommands.add(commandMessage);
					}
				}
				catch (final UnsupportedOperationException e)
				{
					assert false :
						"Attempted to specially parse a simple command!";
					throw e;
				}
			}
			return parsedCommands;
		}
	}

	/**
	 * Construct a new {@link Command}. If it doesn't {@linkplain
	 * #requiresSpecialParsing() require special parsing}, then add it to the
	 * parsing {@linkplain TrieNode#trie} (treating the tokenization of its
	 * {@linkplain #name() name} on underscore boundaries as its syntax).
	 */
	Command ()
	{
		if (!requiresSpecialParsing())
		{
			final String[] tokens = name().split("_");
			this.syntax = tokens;
			TrieNode.addCommand(this, tokens);
		}
		else
		{
			this.syntax = null;
			TrieNode.addSpeciallyParsedCommand(this);
		}
	}

	/**
	 * Construct a new {@link Command} and add it to the parsing {@linkplain
	 * TrieNode#trie}.
	 *
	 * @param syntax
	 *        The tokenized syntax of the command.
	 */
	Command (final String... syntax)
	{
		assert !requiresSpecialParsing();
		this.syntax = syntax;
		TrieNode.addCommand(this, syntax);
	}

	/**
	 * Parse an unambiguous {@linkplain CommandMessage command message} from the
	 * supplied raw {@linkplain Message message}.
	 *
	 * @param message
	 *        A raw message, comprising command source.
	 * @return An unambiguous command message.
	 * @throws CommandParseException
	 *         If an unambiguous command could not be parsed.
	 */
	public static CommandMessage parse (final Message message)
		throws CommandParseException
	{
		final String source = message.content();
		final List<CommandMessage> parsedCommands =
			TrieNode.parseCommands(source);
		if (parsedCommands.isEmpty())
		{
			throw new CommandParseException("unrecognized command");
		}
		if (parsedCommands.size() > 1)
		{
			@SuppressWarnings("resource")
			final Formatter formatter = new Formatter();
			formatter.format(
				"ambiguous command: could be %s",
				parsedCommands.size() == 2 ? "either" : "any of");
			for (int i = 0, size = parsedCommands.size(); i < size; i++)
			{
				final CommandMessage command = parsedCommands.get(i);
				if (i > 0 && i < size - 1)
				{
					formatter.format(",");
				}
				else if (i == size - 1)
				{
					if (size > 2)
					{
						formatter.format(",");
					}
					formatter.format(" or");
				}
				formatter.format(" %s", command.command().name());
			}
			throw new CommandParseException(formatter.toString());
		}
		// assert parsedCommands.size() == 1;
		return parsedCommands.get(0);
	}
}

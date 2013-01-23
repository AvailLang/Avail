/**
 * AbstractAvailCompiler.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith. All rights reserved.
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

package com.avail.compiler;

import static com.avail.compiler.AbstractAvailCompiler.ExpectedToken.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.utility.PrefixSharingList.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.compiler.scanning.*;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.serialization.*;
import com.avail.utility.*;

/**
 * The abstract compiler for Avail code.  Subclasses may wish to implement, oh,
 * say, a system version with a hard-coded basic syntax and a non-system version
 * with no hard-coded syntax but macro capability.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AbstractAvailCompiler
{
	/**
	 * A module's header information.
	 */
	public static class ModuleHeader
	{
		/**
		 * The {@link ModuleName} of the module undergoing compilation.
		 */
		public final ModuleName moduleName;

		/**
		 * Whether this is the header of a system module.
		 */
		public boolean isSystemModule;

		/**
		 * The versions for which the module undergoing compilation guarantees
		 * support.
		 */
		public final List<AvailObject> versions =
			new ArrayList<AvailObject>();

		/**
		 * The {@linkplain ModuleDescriptor modules} extended by the module
		 * undergoing compilation. Each element is a {@linkplain TupleDescriptor
		 * 3-tuple} whose first element is a module {@linkplain StringDescriptor
		 * name}, whose second element is the {@linkplain SetDescriptor set} of
		 * {@linkplain MethodDefinitionDescriptor method} names to import
		 * (and re-export), and whose third element is the set of conformant
		 * versions.
		 */
		public final List<AvailObject> extendedModules =
			new ArrayList<AvailObject>();

		/**
		 * The {@linkplain ModuleDescriptor modules} used by the module
		 * undergoing compilation. Each element is a {@linkplain TupleDescriptor
		 * 3-tuple} whose first element is a module {@linkplain StringDescriptor
		 * name}, whose second element is the {@linkplain SetDescriptor set} of
		 * {@linkplain MethodDefinitionDescriptor method} names to import,
		 * and whose third element is the set of conformant versions.
		 */
		public final List<AvailObject> usedModules =
			new ArrayList<AvailObject>();

		/**
		 * The {@linkplain AtomDescriptor names} defined and exported by the
		 * {@linkplain ModuleDescriptor module} undergoing compilation.
		 */
		public final List<AvailObject> exportedNames =
			new ArrayList<AvailObject>();

		/**
		 * The {@linkplain String pragma strings}.
		 */
		public final List<AvailObject> pragmas =
			new ArrayList<AvailObject>();

		/**
		 * Construct a new {@link AbstractAvailCompiler.ModuleHeader}.
		 *
		 * @param moduleName The {@link ModuleName} of the module.
		 */
		public ModuleHeader (final ModuleName moduleName)
		{
			this.moduleName = moduleName;
		}

		/**
		 * @param serializer
		 */
		public void serializeHeaderOn (final Serializer serializer)
		{
			serializer.serialize(
				StringDescriptor.from(moduleName.qualifiedName()));
			serializer.serialize(
				AtomDescriptor.objectFromBoolean(isSystemModule));
			serializer.serialize(
				TupleDescriptor.fromList(versions));
			serializer.serialize(
				TupleDescriptor.fromList(extendedModules));
			serializer.serialize(
				TupleDescriptor.fromList(usedModules));
			serializer.serialize(
				TupleDescriptor.fromList(exportedNames));
			serializer.serialize(
				TupleDescriptor.fromList(pragmas));
		}

		/**
		 * Extract the module's header information from the {@link
		 * Deserializer}.
		 *
		 * @param deserializer The source of the header information.
		 * @throws MalformedSerialStreamException if malformed.
		 */
		public void deserializeHeaderFrom (final Deserializer deserializer)
		throws MalformedSerialStreamException
		{
			AvailObject object = deserializer.deserialize();
			assert object != null;
			if (!object.asNativeString().equals(moduleName.qualifiedName()))
			{
				throw new RuntimeException("Incorrect module name");
			}
			object = deserializer.deserialize();
			assert object != null;
			isSystemModule = object.extractBoolean();
			object = deserializer.deserialize();
			assert object != null;
			versions.clear();
			versions.addAll(toList(object));
			object = deserializer.deserialize();
			assert object != null;
			extendedModules.clear();
			extendedModules.addAll(toList(object));
			object = deserializer.deserialize();
			assert object != null;
			usedModules.clear();
			usedModules.addAll(toList(object));
			object = deserializer.deserialize();
			assert object != null;
			exportedNames.clear();
			exportedNames.addAll(toList(object));
			object = deserializer.deserialize();
			assert object != null;
			pragmas.clear();
			pragmas.addAll(toList(object));
		}

		/**
		 * Update the given module to correspond with information that has been
		 * accumulated in this {@link ModuleHeader}.
		 *
		 * @param module The module to update.
		 * @param runtime The current {@link AvailRuntime}.
		 * @return An error message {@link String} if there was a problem, or
		 *         {@code null} if no problems were encountered.
		 */
		public @Nullable String applyToModule (
			final AvailObject module,
			final AvailRuntime runtime)
		{
			final ModuleNameResolver resolver = runtime.moduleNameResolver();
			final ResolvedModuleName qualifiedName =
				resolver.resolve(moduleName);
			assert qualifiedName != null;
			module.versions(SetDescriptor.fromCollection(versions));
			for (final AvailObject modImport : extendedModules)
			{
				assert modImport.isTuple();
				assert modImport.tupleSize() == 3;

				final ResolvedModuleName ref = resolver.resolve(
					qualifiedName.asSibling(
						modImport.tupleAt(1).asNativeString()));
				assert ref != null;
				final AvailObject availRef = StringDescriptor.from(
					ref.qualifiedName());
				if (!runtime.includesModuleNamed(availRef))
				{
					return
						"module \"" + ref.qualifiedName()
						+ "\" to be loaded already";
				}

				final AvailObject mod = runtime.moduleAt(availRef);
				final AvailObject reqVersions = modImport.tupleAt(3);
				if (reqVersions.setSize() > 0)
				{
					final AvailObject modVersions = mod.versions();
					final AvailObject intersection =
						modVersions.setIntersectionCanDestroy(
							reqVersions, false);
					if (intersection.setSize() == 0)
					{
						return
							"version compatibility; module \"" + ref.localName()
							+ "\" guarantees versions " + modVersions
							+ " but current module requires " + reqVersions;
					}
				}

				final AvailObject modNames = modImport.tupleAt(2).setSize() > 0
					? modImport.tupleAt(2)
					: mod.names().keysAsSet();
				for (final AvailObject strName : modNames)
				{
					if (!mod.names().hasKey(strName))
					{
						return
							"module \"" + ref.qualifiedName()
							+ "\" to export " + strName;
					}
					final AvailObject trueNames = mod.names().mapAt(strName);
					for (final AvailObject trueName : trueNames)
					{
						module.atNameAdd(strName, trueName);
					}
				}
			}
			for (final AvailObject modImport : usedModules)
			{
				assert modImport.isTuple();
				assert modImport.tupleSize() == 3;

				final ResolvedModuleName ref = resolver.resolve(
					qualifiedName.asSibling(
						modImport.tupleAt(1).asNativeString()));
				assert ref != null;
				final AvailObject availRef = StringDescriptor.from(
					ref.qualifiedName());
				if (!runtime.includesModuleNamed(availRef))
				{
					return
						"module \"" + ref.qualifiedName()
						+ "\" to be loaded already";
				}

				final AvailObject mod = runtime.moduleAt(availRef);
				final AvailObject reqVersions = modImport.tupleAt(3);
				if (reqVersions.setSize() > 0)
				{
					final AvailObject modVersions = mod.versions();
					final AvailObject intersection =
						modVersions.setIntersectionCanDestroy(
							reqVersions, false);
					if (intersection.setSize() == 0)
					{
						return
							"version compatibility; module \"" + ref.localName()
							+ "\" guarantees versions " + modVersions
							+ " but current module requires " + reqVersions;
					}
				}

				final AvailObject modNames = modImport.tupleAt(2).setSize() > 0
					? modImport.tupleAt(2)
					: mod.names().keysAsSet();
				for (final AvailObject strName : modNames)
				{
					if (!mod.names().hasKey(strName))
					{
						return
							"module \"" + ref.qualifiedName()
							+ "\" to export " + strName;
					}
					final AvailObject trueNames = mod.names().mapAt(strName);
					for (final AvailObject trueName : trueNames)
					{
						module.atPrivateNameAdd(strName, trueName);
					}
				}
			}

			for (final AvailObject name : exportedNames)
			{
				assert name.isString();
				final AvailObject trueName = AtomDescriptor.create(
					name,
					module);
				module.atNewNamePut(name, trueName);
				module.atNameAdd(name, trueName);
			}

			return null;
		}
	}

	/**
	 * The header information for the current module being parsed.
	 */
	public final ModuleHeader moduleHeader;

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	AvailObject module;

	/**
	 * The {@linkplain L2Interpreter interpreter} to use when evaluating
	 * top-level expressions.
	 */
	@InnerAccess final L2Interpreter interpreter;

	/**
	 * The source text of the Avail {@linkplain ModuleDescriptor module}
	 * undergoing compilation.
	 */
	private String source;

	/**
	 * The complete {@linkplain List list} of {@linkplain TokenDescriptor
	 * tokens} parsed from the source text.
	 */
	@InnerAccess List<AvailObject> tokens;

	/**
	 * The position of the rightmost {@linkplain TokenDescriptor token} reached
	 * by any parsing attempt.
	 */
	@InnerAccess int greatestGuess;

	/**
	 * The {@linkplain List list} of {@linkplain String} {@linkplain Generator
	 * generators} that describe what was expected (but not found) at the
	 * {@linkplain #greatestGuess rightmost reached position}.
	 */
	@InnerAccess final List<Generator<String>> greatExpectations =
		new ArrayList<Generator<String>>();

	/** The memoization of results of previous parsing attempts. */
	@InnerAccess AvailCompilerFragmentCache fragmentCache;

	/**
	 * The {@linkplain Continuation3 action} that should be performed repeatedly
	 * by the {@linkplain AbstractAvailCompiler compiler} to report compilation
	 * progress.
	 */
	Continuation4<ModuleName, Long, Long, Long> progressBlock;

	/**
	 * A flag to indicate that the tasks in the {@link #workPool} are being
	 * canceled.
	 */
	@InnerAccess volatile boolean canceling = false;

	/**
	 * The original reason, if any, that a cancellation is taking place.
	 */
	@InnerAccess @Nullable volatile Throwable causeOfCancellation = null;

	/**
	 * Answer whether this is a {@linkplain AvailSystemCompiler system
	 * compiler}.  A system compiler is used for modules that start with the
	 * keyword "{@linkplain ExpectedToken#SYSTEM System}".  Such modules use a
	 * predefined syntax.
	 *
	 * @return Whether this is a system compiler.
	 */
	boolean isSystemCompiler ()
	{
		return false;
	}

	/**
	 * These are the tokens that are understood by the Avail compilers. Most of
	 * these tokens exist to support the {@linkplain AvailSystemCompiler
	 * system compiler}, though a few (related to module headers) are needed
	 * also by the {@linkplain AvailCompiler standard compiler}.
	 */
	public enum ExpectedToken
	{
		/** Module header token. Must be the first token of a system module. */
		SYSTEM("System", KEYWORD),

		/** Module header token: Precedes the name of the defined module. */
		MODULE("Module", KEYWORD),

		/**
		 * Module header token: Precedes the list of versions for which the
		 * defined module guarantees compatibility.
		 */
		VERSIONS("Versions", KEYWORD),

		/** Module header token: Precedes the list of pragma strings. */
		PRAGMA("Pragma", KEYWORD),

		/**
		 * Module header token: Precedes the list of imported modules whose
		 * (filtered) names should be re-exported to clients of the defined
		 * module.
		 */
		EXTENDS("Extends", KEYWORD),

		/**
		 * Module header token: Precedes the list of imported modules whose
		 * (filtered) names are imported only for the private use of the
		 * defined module.
		 */
		USES("Uses", KEYWORD),

		/**
		 * Module header token: Precedes the list of names exported for use by
		 * clients of the defined module.
		 */
		NAMES("Names", KEYWORD),

		/** Module header token: Precedes the contents of the defined module. */
		BODY("Body", KEYWORD),

		/** Leads a primitive binding. */
		PRIMITIVE("Primitive", KEYWORD),

		/** Leads a label. */
		DOLLAR_SIGN("$", OPERATOR),

		/** Leads a reference. */
		UP_ARROW("↑", OPERATOR),

		/** Module header token: Separates tokens. */
		COMMA(",", OPERATOR),

		/** Uses related to declaration and assignment. */
		COLON(":", OPERATOR),

		/** Uses related to declaration and assignment. */
		EQUALS("=", OPERATOR),

		/** Leads a lexical block. */
		OPEN_SQUARE("[", OPERATOR),

		/** Ends a lexical block. */
		CLOSE_SQUARE("]", OPERATOR),

		/** Leads a function body. */
		VERTICAL_BAR("|", OPERATOR),

		/** Leads an exception set. */
		CARET("^", OPERATOR),

		/** Module header token: Uses related to grouping. */
		OPEN_PARENTHESIS("(", OPERATOR),

		/** Module header token: Uses related to grouping. */
		CLOSE_PARENTHESIS(")", OPERATOR),

		/** End of statement. */
		SEMICOLON(";", OPERATOR);

		/** The {@linkplain String Java string} form of the lexeme. */
		private final String lexemeString;

		/**
		 * The {@linkplain StringDescriptor Avail string} form of the
		 * lexeme.
		 */
		private AvailObject lexeme;

		/** The {@linkplain TokenType token type}. */
		private final TokenType tokenType;

		/**
		 * Answer the {@linkplain StringDescriptor lexeme}.
		 *
		 * @return The lexeme.
		 */
		public AvailObject lexeme ()
		{
			assert lexeme != null;
			return lexeme;
		}

		/**
		 * Answer the {@linkplain TokenType token type}.
		 *
		 * @return The token type.
		 */
		TokenType tokenType ()
		{
			return tokenType;
		}

		/**
		 * Construct a new {@link ExpectedToken}.
		 *
		 * @param lexemeString
		 *        The {@linkplain StringDescriptor lexeme string}, i.e. the text
		 *        of the token.
		 * @param tokenType
		 *        The {@linkplain TokenType token type}.
		 */
		ExpectedToken (
			final String lexemeString,
			final TokenType tokenType)
		{
			this.lexemeString = lexemeString;
			this.tokenType = tokenType;
		}

		/**
		 * Release any AvailObjects held statically by this class.
		 */
		public static void clearWellKnownObjects ()
		{
			for (final ExpectedToken value : values())
			{
				value.lexeme = null;
			}
		}

		/**
		 * Create AvailObjects to hold statically by this class.
		 */
		public static void createWellKnownObjects ()
		{
			for (final ExpectedToken value : values())
			{
				assert value.lexeme == null;
				value.lexeme =
					StringDescriptor.from(value.lexemeString).makeShared();
			}
		}
	}

	/**
	 * Construct a suitable {@linkplain AbstractAvailCompiler compiler} to
	 * parse the specified {@linkplain ModuleName module name}, using the given
	 * {@linkplain L2Interpreter interpreter}.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor module} being defined.
	 * @param interpreter
	 *        The {@link Interpreter} used to execute code during compilation.
	 * @param stopAfterBodyToken
	 *        Whether to stop parsing at the occurrence of the BODY token. This
	 *        is an optimization for faster build analysis.
	 * @return The new {@linkplain AbstractAvailCompiler compiler}.
	 */
	public static AbstractAvailCompiler create (
		final ModuleName qualifiedName,
		final L2Interpreter interpreter,
		final boolean stopAfterBodyToken)
	{
		final AvailRuntime runtime = interpreter.runtime();
		final ResolvedModuleName resolvedName =
			runtime.moduleNameResolver().resolve(qualifiedName);
		if (resolvedName == null)
		{
			throw new AvailCompilerException(
				qualifiedName,
				0,
				0,
				"Unable to resolve fully-qualified module name \""
					+ qualifiedName.qualifiedName()
					+ "\" to an existing file");
		}
		final String source = extractSource(qualifiedName, resolvedName);
		final List<AvailObject> tokens = tokenize(
			source,
			stopAfterBodyToken);
		AbstractAvailCompiler compiler;
		if (!tokens.isEmpty()
			&& tokens.get(0).string().equals(SYSTEM.lexeme()))
		{
			compiler = new AvailSystemCompiler(
				interpreter,
				qualifiedName,
				source,
				tokens);
		}
		else
		{
			compiler = new AvailCompiler(
				interpreter,
				qualifiedName,
				source,
				tokens);
		}
		return compiler;
	}

	/**
	 * Construct a new {@link AbstractAvailCompiler} which will use the given
	 * {@link L2Interpreter} to evaluate expressions.
	 *
	 * @param interpreter
	 *        The interpreter to be used for evaluating expressions.
	 * @param moduleName
	 *        The {@link ModuleName} of the module being compiled.
	 * @param source
	 *        The source code {@linkplain StringDescriptor string}.
	 * @param tokens
	 *        The list of {@linkplain TokenDescriptor tokens}.
	 */
	public AbstractAvailCompiler (
		final L2Interpreter interpreter,
		final ModuleName moduleName,
		final String source,
		final List<AvailObject> tokens)
	{
		this.interpreter = interpreter;
		this.moduleHeader = new ModuleHeader(moduleName);
		this.source = source;
		this.tokens = tokens;
	}

	/**
	 * This is actually a two-argument continuation, but it has only a single
	 * type parameter because the first one is always the {@linkplain
	 * ParserState parser state} that indicates where the continuation should
	 * continue parsing.
	 *
	 * @param <AnswerType>
	 *        The type of the second parameter of the {@linkplain
	 *        Con#value(ParserState, Object)} method.
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	abstract class Con<AnswerType>
	implements Continuation2<ParserState, AnswerType>
	{
		/**
		 * A debugging description of this continuation.
		 */
		final String description;

		/**
		 * Construct a new {@link AvailCompiler.Con} with the provided
		 * description.
		 *
		 * @param description
		 *            The provided description.
		 */
		Con (final String description)
		{
			this.description = description;
		}

		@Override
		public String toString ()
		{
			return "Con(" + description + ")";
		}

		@Override
		public abstract void value (
			@Nullable ParserState state,
			@Nullable AnswerType answer);
	}


	/**
	 * A {@link Runnable} which supports a natural ordering (via the {@link
	 * Comparable} interface) which will favor processing of the leftmost
	 * available tasks first.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	abstract class ParsingTask implements Runnable, Comparable<ParsingTask>
	{
		/**
		 * The description associated with this task.  Only used for debugging.
		 */
		final String description;

		/**
		 * The parsing position (token index) at which this task will operate.
		 */
		final int position;

		/**
		 * Construct a new {@link AbstractAvailCompiler.ParsingTask}.
		 *
		 * @param description What this task will do.
		 * @param position Where in the parse stream this task operates.
		 */
		public ParsingTask (
			final String description,
			final int position)
		{
			this.description = description;
			this.position = position;
		}

		@Override
		public String toString()
		{
			return description + "@pos(" + position + ")";
		}

		@Override
		public int compareTo (final @Nullable ParsingTask o)
		{
			assert o != null;
			return position - o.position;
		}
	}

	/**
	 * The maximum number of entries that may be in the work pool.
	 */
	private final static int maxWorkPoolSize = 10000;

	/**
	 * A collection of {@link Runnable} parsing tasks that need to be explored
	 * at some point.  The contents of this collection should only be accessed
	 * through synchronized methods.
	 */
	private final static BlockingQueue<Runnable> workPool =
		new PriorityBlockingQueue<Runnable>(
			maxWorkPoolSize);

	/**
	 * A static flag indicating whether parallel parsing should be allowed.
	 * As of 2011.05.01 there is not yet correct support for multiple Avail
	 * threads manipulating AvailObjects safely, but it will be forthcoming.
	 */
	private final static boolean enableParallelParsing = false;

	/**
	 * A {@link ThreadPoolExecutor} with which to execute parsing tasks.
	 */
	private final static ThreadPoolExecutor workPoolExecutor =
		enableParallelParsing
			?
				new ThreadPoolExecutor(
					1,
					Runtime.getRuntime().availableProcessors(),
					2,
					TimeUnit.MINUTES,
					workPool)
			:
				new ThreadPoolExecutor(
					1,
					1,
					2,
					TimeUnit.MINUTES,
					workPool);

	/**
	 * The number of work units that have been queued.  This should only be
	 * accessed through synchronized methods.
	 */
	long workUnitsQueued = 0;

	/**
	 * The number of work units that have been started.  This should only be
	 * accessed through synchronized methods.
	 */
	long workUnitsStarted = 0;

	/**
	 * The number of work units that have been completed.  This should only be
	 * accessed through synchronized methods.
	 */
	long workUnitsCompleted = 0;

	/**
	 * The output stream on which the serializer writes.
	 */
	public final ByteArrayOutputStream serializerOutputStream =
		new ByteArrayOutputStream(1000);

	/**
	 * The serializer that captures the sequence of bytes representing the
	 * module during compilation.
	 */
	final Serializer serializer = new Serializer(serializerOutputStream);

	/**
	 * Execute the block, passing a continuation that it should run upon finding
	 * a local solution. If exactly one solution is found, unwrap the stack (but
	 * not the token stream position or scopeMap), and pass the result to the
	 * continuation. Otherwise report that an unambiguous statement was
	 * expected.
	 *
	 * @param start
	 *        Where to start parsing
	 * @param tryBlock
	 *        The block to attempt.
	 * @param supplyAnswer
	 *        What to do if exactly one result was produced.
	 */
	void tryIfUnambiguousThen (
		final ParserState start,
		final Con<Con<AvailObject>> tryBlock,
		final Con<AvailObject> supplyAnswer)
	{
		assert workPool.isEmpty();
		final Mutable<Integer> count = new Mutable<Integer>(0);
		final Mutable<AvailObject> solution = new Mutable<AvailObject>();
		final Mutable<AvailObject> another = new Mutable<AvailObject>();
		final Mutable<ParserState> where = new Mutable<ParserState>();
		attempt(
			start,
			tryBlock,
			new Con<AvailObject>("Unambiguous statement")
			{
				@Override
				public void value (
					final @Nullable ParserState afterSolution,
					final @Nullable AvailObject aSolution)
				{
					synchronized (AbstractAvailCompiler.this)
					{
						if (count.value == 0)
						{
							solution.value = aSolution;
							where.value = afterSolution;
						}
						else
						{
							assert aSolution != solution.value
								: "Same solution was presented twice!";
							another.value = aSolution;
							canceling = true;
							AbstractAvailCompiler.this.notifyAll();
						}
						count.value++;
					}
				}
			});
		synchronized (this)
		{
			try
			{
				while (workUnitsCompleted < workUnitsQueued)
				{
					wait();
				}
				// Note: count.value increases monotonically.
				assert workUnitsStarted == workUnitsQueued;
				assert workUnitsCompleted == workUnitsQueued;
			}
			catch (final InterruptedException e)
			{
				// Treat an interrupt of this thread as a failure.
				throw new RuntimeException(e);
			}
		}
		if (count.value == 0)
		{
			return;
		}
		if (count.value > 1)
		{
			// Indicate the problem on the last token of the ambiguous
			// expression.
			reportAmbiguousInterpretations(
				new ParserState(
					where.value.position - 1,
					where.value.clientDataMap),
				solution.value,
				another.value);
			return;
		}
		// We found exactly one solution. Advance the token stream just past it,
		// and commit its side-effects to the scope, then invoke the
		// continuation with the solution.
		assert count.value == 1;
		supplyAnswer.value(where.value, solution.value);
	}

	/**
	 * {@link ParserState} instances are immutable and keep track of a current
	 * {@link #position} and {@link #clientDataMap} during parsing.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public class ParserState
	{
		/**
		 * The position represented by this {@link ParserState}. In particular,
		 * it's the (zero-based) index of the current token.
		 */
		final int position;

		/**
		 * A {@linkplain MapDescriptor map} of interesting information used by
		 * the compiler.
		 */
		public final AvailObject clientDataMap;

		/**
		 * Construct a new immutable {@link ParserState}.
		 *
		 * @param position
		 *            The index of the current token.
		 * @param clientDataMap
		 *            The {@link MapDescriptor map} of data used by macros while
		 *            parsing Avail code.
		 */
		ParserState (
			final int position,
			final AvailObject clientDataMap)
		{
			this.position = position;
			this.clientDataMap = clientDataMap;
		}

		@Override
		public int hashCode ()
		{
			return position * 473897843 ^ clientDataMap.hashCode();
		}

		@Override
		public boolean equals (final @Nullable Object another)
		{
			if (!(another instanceof ParserState))
			{
				return false;
			}
			final ParserState anotherState = (ParserState) another;
			return position == anotherState.position
				&& clientDataMap.equals(anotherState.clientDataMap);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"%s%n\tPOSITION = %d%n\tTOKENS = %s ☞ %s%n\tCLIENT_DATA = %s",
				getClass().getSimpleName(),
				position,
				(position > 0
					? tokens.get(position - 1).string()
					: "(start)"),
				(position < tokens.size()
					? tokens.get(position).string()
					: "(end)"),
				clientDataMap);
		}

		/**
		 * Determine if this state represents the end of the file. If so, one
		 * should not invoke {@link #peekToken()} or {@link #afterToken()}
		 * again.
		 *
		 * @return Whether this state represents the end of the file.
		 */
		boolean atEnd ()
		{
			return this.position == tokens.size() - 1;
		}

		/**
		 * Answer the {@linkplain TokenDescriptor token} at the current
		 * position.
		 *
		 * @return The token.
		 */
		AvailObject peekToken ()
		{
			return tokens.get(position);
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @return Whether the specified token was found.
		 */
		boolean peekToken (final ExpectedToken expectedToken)
		{
			if (atEnd())
			{
				return false;
			}
			final AvailObject token = peekToken();
			return token.tokenType() == expectedToken.tokenType()
				&& token.string().equals(expectedToken.lexeme());
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @param expected
		 *        A {@linkplain Generator generator} of a message to record if
		 *        the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final ExpectedToken expectedToken,
			final Generator<String> expected)
		{
			final AvailObject token = peekToken();
			final boolean found = token.tokenType() == expectedToken.tokenType()
					&& token.string().equals(expectedToken.lexeme());
			if (!found)
			{
				expected(expected);
			}
			return found;
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @param expected
		 *        A message to record if the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final ExpectedToken expectedToken,
			final String expected)
		{
			return peekToken(expectedToken, generate(expected));
		}

		/**
		 * Return a new {@link ParserState} like this one, but advanced by one
		 * token.
		 *
		 * @return A new parser state.
		 */
		ParserState afterToken ()
		{
			assert !atEnd();
			return new ParserState(
				position + 1,
				clientDataMap);
		}

		/**
		 * Parse a string literal. Answer the {@linkplain LiteralTokenDescriptor
		 * string literal token} if found, otherwise answer {@code null}.
		 *
		 * @return The actual {@linkplain LiteralTokenDescriptor literal token}
		 *         or {@code null}.
		 */
		@Nullable AvailObject peekStringLiteral ()
		{
			final AvailObject token = peekToken();
			if (token.isInstanceOfKind(
				LiteralTokenTypeDescriptor.create(
					TupleTypeDescriptor.stringTupleType())))
			{
				return token;
			}
			return null;
		}

		/**
		 * Record an expectation at the current parse position. The expectations
		 * captured at the rightmost parse position constitute the error message
		 * in case the parse fails.
		 *
		 * <p>
		 * The expectation is a {@linkplain Generator Generator<String>}, in
		 * case constructing a {@link String} would be prohibitive. There is
		 * also {@link #expected(String) another} version of this method that
		 * accepts a String directly.
		 * </p>
		 *
		 * @param stringGenerator
		 *        The {@code Generator<String>} to capture.
		 */
		void expected (final Generator<String> stringGenerator)
		{
			// System.out.println(Integer.toString(position) + " expected " +
			// stringBlock.value());
			if (position == greatestGuess)
			{
				greatExpectations.add(stringGenerator);
			}
			if (position > greatestGuess)
			{
				greatestGuess = position;
				greatExpectations.clear();
				greatExpectations.add(stringGenerator);
			}
		}

		/**
		 * Record an indication of what was expected at this parse position.
		 *
		 * @param aString
		 *        The string to look up.
		 */
		void expected (final String aString)
		{
			expected(generate(aString));
		}

		/**
		 * Return the {@linkplain ModuleDescriptor module} under compilation for
		 * this {@linkplain ParserState}.
		 *
		 * @return The current module being compiled.
		 */
		public AvailObject currentModule ()
		{
			return AbstractAvailCompiler.this.module;
		}

		/**
		 * Evaluate the given {@linkplain ParseNodeDescriptor phrase}, answering
		 * its result.  Note that the phrase might throw an {@link
		 * AvailRejectedParseException}.  The phrase is considered to start
		 * lexically at the token position carried by the receiver.
		 *
		 * @param phrase The phrase to execute.
		 * @return The value returned by the phrase.
		 */
		public AvailObject evaluate (final AvailObject phrase)
		{
			final int line = peekToken().lineNumber();
			return AbstractAvailCompiler.this.evaluate(phrase, line);
		}
	}

	/**
	 * Parse one or more string literals separated by commas. This parse isn't
	 * backtracking like the rest of the grammar - it's greedy. It considers a
	 * comma followed by something other than a string literal to be an
	 * unrecoverable parsing error (not a backtrack).
	 *
	 * <p>
	 * Return the {@link ParserState} after the strings if successful, otherwise
	 * null. Populate the passed {@link List} with the
	 * {@linkplain StringDescriptor actual Avail strings}.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param stringTokens
	 *        The initially empty list of strings to populate.
	 * @return The parser state after the list of strings, or {@code null} if
	 *         the list of strings is malformed.
	 */
	private static @Nullable ParserState parseStringLiterals (
		final ParserState start,
		final List<AvailObject> stringTokens)
	{
		assert stringTokens.isEmpty();

		AvailObject token = start.peekStringLiteral();
		if (token == null)
		{
			return start;
		}
		stringTokens.add(token.literal());
		ParserState state = start.afterToken();
		while (state.peekToken(COMMA))
		{
			state = state.afterToken();
			token = state.peekStringLiteral();
			if (token == null)
			{
				state.expected("another string literal after comma");
				return null;
			}
			state = state.afterToken();
			stringTokens.add(token.literal());
		}
		return state;
	}

	/**
	 * Parse one or more {@linkplain ModuleDescriptor module} imports separated
	 * by commas. This parse isn't backtracking like the rest of the grammar -
	 * it's greedy. It considers any parse error to be unrecoverable (not a
	 * backtrack).
	 *
	 * <p>
	 * Return the {@link ParserState} after the imports if successful, otherwise
	 * {@code null}. Populate the passed {@linkplain List list} with {@linkplain
	 * TupleDescriptor 2-tuples}. Each tuple's first element is a module
	 * {@linkplain StringDescriptor name} and second element is the
	 * collection of {@linkplain MethodDefinitionDescriptor method} names to
	 * import.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param imports
	 *        The initially empty list of imports to populate.
	 * @return The parser state after the list of imports, or {@code null} if
	 *         the list of imports is malformed.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private static @Nullable ParserState parseImports (
		final ParserState start,
		final List<AvailObject> imports)
	{
		assert imports.isEmpty();

		ParserState state = start;
		do
		{
			final AvailObject token = state.peekStringLiteral();
			if (token == null)
			{
				state.expected("another module name after comma");
				return imports.isEmpty() ? state : null;
			}

			final AvailObject moduleName = token.literal();
			state = state.afterToken();

			final List<AvailObject> versions = new ArrayList<AvailObject>();
			if (state.peekToken(OPEN_PARENTHESIS))
			{
				state = state.afterToken();
				state = parseStringLiterals(state, versions);
				if (state == null)
				{
					return null;
				}
				if (!state.peekToken(
					CLOSE_PARENTHESIS,
					"a close parenthesis following acceptable versions"))
				{
					return null;
				}
				state = state.afterToken();
			}

			final List<AvailObject> names = new ArrayList<AvailObject>();
			if (state.peekToken(EQUALS))
			{
				state = state.afterToken();
				if (!state.peekToken(
					OPEN_PARENTHESIS,
					"an open parenthesis preceding import list"))
				{
					return null;
				}
				state = state.afterToken();
				state = parseStringLiterals(state, names);
				if (state == null)
				{
					return null;
				}
				if (!state.peekToken(
					CLOSE_PARENTHESIS,
					"a close parenthesis following import list"))
				{
					return null;
				}
				state = state.afterToken();
			}

			imports.add(TupleDescriptor.from(
				moduleName,
				TupleDescriptor.fromList(names).asSet(),
				TupleDescriptor.fromList(versions).asSet()));
			if (state.peekToken(COMMA))
			{
				state = state.afterToken();
			}
			else
			{
				return state;
			}
		}
		while (true);
	}

	/**
	 * Read the source string for the {@linkplain ModuleDescriptor module}
	 * specified by the fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @param resolvedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module.
	 * @return The module's {@linkplain String source code}.
	 * @throws AvailCompilerException
	 *         If source extraction failed for any reason.
	 */
	private static String extractSource (
			final ModuleName qualifiedName,
			final ResolvedModuleName resolvedName)
		throws AvailCompilerException
	{
		String source;

		try
		{
			final StringBuilder sourceBuilder = new StringBuilder(4096);
			final char[] buffer = new char[4096];
			final Reader reader = new BufferedReader(
				new FileReader(resolvedName.fileReference()));
			int charsRead;
			while ((charsRead = reader.read(buffer)) > 0)
			{
				sourceBuilder.append(buffer, 0, charsRead);
			}
			source = sourceBuilder.toString();
			reader.close();
		}
		catch (final IOException e)
		{
			throw new AvailCompilerException(
				qualifiedName,
				0,
				0,
				"Encountered an I/O exception while reading source module \""
					+ qualifiedName.qualifiedName() + "\" (resolved to \""
					+ resolvedName.fileReference().getAbsolutePath()
					+ "\")");
		}
		return source;
	}

	/**
	 * Tokenize the {@linkplain ModuleDescriptor module} specified by the
	 * fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param source
	 *        The {@linkplain String string} containing the module's source
	 *        code.
	 * @param stopAfterBodyToken
	 *        Stop scanning after encountering the BODY token?
	 * @return The {@linkplain ResolvedModuleName resolved module name}.
	 * @throws AvailCompilerException
	 *         If tokenization failed for any reason.
	 */
	static List<AvailObject> tokenize (
			final String source,
			final boolean stopAfterBodyToken)
		throws AvailCompilerException
	{
		return new AvailScanner().scanString(source, stopAfterBodyToken);
	}

	/**
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes. Answer the
	 * resulting tree.
	 *
	 * @param object
	 *        The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock
	 *        What to do with each descendant.
	 * @param parentNode
	 *        This node's parent.
	 * @param outerNodes
	 *        The list of {@linkplain BlockNodeDescriptor blocks} surrounding
	 *        this node, from outermost to innermost.
	 * @param nodeMap
	 *        The {@link Map} from old {@linkplain ParseNodeDescriptor parse
	 *        nodes} to newly copied, mutable parse nodes.  This should ensure
	 *        the consistency of declaration references.
	 * @return A replacement for this node, possibly this node itself.
	 */
	static AvailObject treeMapWithParent (
		final AvailObject object,
		final Transformer3<
				AvailObject,
				AvailObject,
				List<AvailObject>,
				AvailObject>
			aBlock,
		final AvailObject parentNode,
		final List<AvailObject> outerNodes,
		final Map<AvailObject, AvailObject> nodeMap)
	{
		if (nodeMap.containsKey(object))
		{
			return object;
		}
		final AvailObject objectCopy = object.copyMutableParseNode();
		objectCopy.childrenMap(
			new Transformer1<AvailObject, AvailObject>()
			{
				@Override
				public AvailObject value (final AvailObject child)
				{
					assert child.isInstanceOfKind(PARSE_NODE.mostGeneralType());
					return treeMapWithParent(
						child,
						aBlock,
						objectCopy,
						outerNodes,
						nodeMap);
				}
			});
		final AvailObject transformed = aBlock.value(
			objectCopy,
			parentNode,
			outerNodes);
		transformed.makeImmutable();
		nodeMap.put(object, transformed);
		return transformed;
	}

	/**
	 * Answer a {@linkplain Generator generator} that will produce the given
	 * string.
	 *
	 * @param string
	 *        The exact string to generate.
	 * @return A generator that produces the string that was provided.
	 */
	Generator<String> generate (final String string)
	{
		return new Generator<String>()
		{
			@Override
			public String value ()
			{
				return string;
			}
		};
	}

	/**
	 * Report an error by throwing an {@link AvailCompilerException}. The
	 * exception encapsulates the {@linkplain ModuleName module name} of the
	 * {@linkplain ModuleDescriptor module} undergoing compilation, the error
	 * string, and the text position. This position is the rightmost position
	 * encountered during the parse, and the error strings in
	 * {@link #greatExpectations} are the things that were expected but not
	 * found at that position. This seems to work very well in practice.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *        Always thrown.
	 */
	void reportError (final ModuleName qualifiedName)
		throws AvailCompilerException
	{
		reportError(
			qualifiedName,
			tokens.get(greatestGuess),
			"Expected...",
			greatExpectations);
	}

	/**
	 * A bunch of dash characters, wide enough to catch the eye.
	 */
	static final String rowOfDashes;

	static
	{
		final char[] chars = new char[70];
		Arrays.fill(chars, '-');
		rowOfDashes = new String(chars);
	}

	/**
	 * Report an error by throwing an {@link AvailCompilerException}. The
	 * exception encapsulates the {@linkplain ModuleName module name} of the
	 * {@linkplain ModuleDescriptor module} undergoing compilation, the error
	 * string, and the text position. This position is the rightmost position
	 * encountered during the parse, and the error strings in
	 * {@link #greatExpectations} are the things that were expected but not
	 * found at that position. This seems to work very well in practice.
	 *
	 * @param token
	 *        Where the error occurred.
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @param banner
	 *        The string that introduces the problem text.
	 * @param problems
	 *        A list of {@linkplain Generator generators} that may be
	 *        invoked to produce problem strings.
	 * @throws AvailCompilerException
	 *         Always thrown.
	 */
	void reportError (
			final ModuleName qualifiedName,
			final AvailObject token,
			final String banner,
			final List<Generator<String>> problems)
		throws AvailCompilerException
	{
		assert problems.size() > 0 : "Bug - empty problem list";
		final long charPos = token.start();
		final String sourceUpToError = source.substring(0, (int) charPos);
		final int startOfPreviousLine = sourceUpToError.lastIndexOf('\n') + 1;
		final Formatter text = new Formatter();
		text.format("%n");
		int wedges = 3;
		for (int i = startOfPreviousLine; i < charPos; i++)
		{
			if (source.codePointAt(i) == '\t')
			{
				while (wedges > 0)
				{
					text.format(">");
					wedges--;
				}
				text.format("\t");
			}
			else
			{
				if (wedges > 0)
				{
					text.format(">");
					wedges--;
				}
				else
				{
					text.format(" ");
				}
			}
		}
		text.format("^-- %s", banner);
		text.format("%n>>>%s", rowOfDashes);
		final Set<String> alreadySeen = new HashSet<String>(problems.size());
		for (final Generator<String> generator : problems)
		{
			final String str = generator.value();
			if (!alreadySeen.contains(str))
			{
				alreadySeen.add(str);
				text.format("\n>>>\t%s", str.replace("\n", "\n>>>\t"));
			}
		}
		text.format(
			"%n(file=\"%s\", line=%d)",
			moduleHeader.moduleName.qualifiedName(),
			token.lineNumber());
		text.format("%n>>>%s", rowOfDashes);
		int endOfLine = source.indexOf('\n', (int) charPos);
		if (endOfLine == -1)
		{
			source = source + "\n";
			endOfLine = source.length() - 1;
		}
		final String textString = text.toString();
		text.close();
		throw new AvailCompilerException(
			qualifiedName,
			charPos,
			endOfLine,
			textString);
	}

	/**
	 * A statement was parsed correctly in two different ways. There may be more
	 * ways, but we stop after two as it's already an error. Report the error.
	 *
	 * @param where
	 *        Where the expressions were parsed from.
	 * @param interpretation1
	 *        The first interpretation as a {@linkplain ParseNodeDescriptor
	 *        parse node}.
	 * @param interpretation2
	 *        The second interpretation as a {@linkplain ParseNodeDescriptor
	 *        parse node}.
	 */
	private void reportAmbiguousInterpretations (
		final ParserState where,
		final AvailObject interpretation1,
		final AvailObject interpretation2)
	{
		final Mutable<AvailObject> node1 =
			new Mutable<AvailObject>(interpretation1);
		final Mutable<AvailObject> node2 =
			new Mutable<AvailObject>(interpretation2);
		findParseTreeDiscriminants(node1, node2);

		where.expected(
			new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringBuilder builder = new StringBuilder(200);
					builder.append("unambiguous interpretation.  ");
					builder.append("Here are two possible parsings...\n");
					builder.append("\t");
					builder.append(node1.value.toString());
					builder.append("\n\t");
					builder.append(node2.value.toString());
					return builder.toString();
				}
			});
	}

	/**
	 * Given two unequal parse trees, find the smallest descendant nodes that
	 * still contain all the differences.  The given {@link Mutable} objects
	 * initially contain references to the root nodes, but are updated to refer
	 * to the most specific pair of nodes that contain all the differences.
	 *
	 * @param node1
	 *            A {@code Mutable} reference to a {@linkplain
	 *            ParseNodeDescriptor parse tree}.  Updated to hold the most
	 *            specific difference.
	 * @param node2
	 *            The {@code Mutable} reference to the other parse tree.
	 *            Updated to hold the most specific difference.
	 */
	private void findParseTreeDiscriminants (
		final Mutable<AvailObject> node1,
		final Mutable<AvailObject> node2)
	{
		while (true)
		{
			assert !node1.value.equals(node2.value);
			if (!node1.value.kind().parseNodeKind().equals(
				node2.value.kind().parseNodeKind()))
			{
				// The nodes are different kinds, so present them as what's
				// different.
				return;
			}
			if (node1.value.kind().parseNodeKindIsUnder(SEND_NODE)
				&& node1.value.method() != node2.value.method())
			{
				// They're sends of different messages, so don't go any deeper.
				return;
			}
			final List<AvailObject> parts1 = new ArrayList<AvailObject>();
			node1.value.childrenDo(new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject part)
				{
					parts1.add(part);
				}
			});
			final List<AvailObject> parts2 = new ArrayList<AvailObject>();
			node2.value.childrenDo(new Continuation1<AvailObject>()
				{
					@Override
					public void value (final @Nullable AvailObject part)
					{
						parts2.add(part);
					}
				});
			if (parts1.size() != parts2.size())
			{
				// Different structure at this level.
				return;
			}
			final List<Integer> differentIndices =
				new ArrayList<Integer>();
			for (int i = 0; i < parts1.size(); i++)
			{
				if (!parts1.get(i).equals(parts2.get(i)))
				{
					differentIndices.add(i);
				}
			}
			if (differentIndices.size() != 1)
			{
				// More than one part differs, so we can't drill deeper.
				return;
			}
			// Drill into the only part that differs.
			node1.value = parts1.get(differentIndices.get(0));
			node2.value = parts2.get(differentIndices.get(0));
		}
	}

	/**
	 * Attempt the zero-argument continuation. The implementation is free to
	 * execute it now or to put it in a bag of continuations to run later <em>in
	 * an arbitrary order</em>.  There may be performance and/or scale benefits
	 * to processing entries in FIFO, LIFO, or some hybrid order, but the
	 * correctness is not affected by a choice of order.  Parallel execution is
	 * even expected to be implemented at some point.
	 *
	 * @param continuation
	 *        What to do at some point in the future.
	 * @param description
	 *        Debugging information about what is to be parsed.
	 * @param position
	 *        Debugging information about where the parse is happening.
	 */
	synchronized void eventuallyDo (
		final Continuation0 continuation,
		final String description,
		final int position)
	{
		if (canceling)
		{
			// Don't add any new tasks if canceling.
			return;
		}
		final AbstractAvailCompiler thisCompiler = this;
		try
		{
			workUnitsQueued++;
			workPoolExecutor.execute(new ParsingTask(description, position)
			{
				@Override
				public void run ()
				{
					synchronized (thisCompiler)
					{
						workUnitsStarted++;
					}
					try
					{
						// Don't actually run tasks if canceling.
						if (!canceling)
						{
//							System.out.println(task.value);  //TODO[MvG] Remove
							continuation.value();
						}
					}
					catch (final Throwable e)
					{
						synchronized (thisCompiler)
						{
							if (!canceling)
							{
								causeOfCancellation = e;
								canceling = true;
							}
						}
					}
					finally
					{
						synchronized (thisCompiler)
						{
							workUnitsCompleted++;
							if (workUnitsCompleted == workUnitsQueued)
							{
								thisCompiler.notifyAll();
							}
						}
					}
				}
			});
		}
		catch (final RejectedExecutionException e)
		{
			throw new RuntimeException("Probably recursive parse error", e);
		}
	}

	/**
	 * Wrap the {@linkplain Continuation1 continuation of one argument} inside a
	 * {@linkplain Continuation0 continuation of zero arguments} and record that
	 * as per {@linkplain #eventuallyDo(Continuation0, String, int)}.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param here
	 *        Where to start parsing when the continuation runs.
	 * @param continuation
	 *        What to execute with the passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@code Continuation1
	 *        one-argument continuation}.
	 */
	<ArgType> void attempt (
		final ParserState here,
		final Con<ArgType> continuation,
		final ArgType argument)
	{
		eventuallyDo(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					continuation.value(here, argument);
				}
			},
			continuation.description,
			here.position);
	}

	/**
	 * Clear the fragment cache.
	 */
	private void privateClearFrags ()
	{
		fragmentCache.clear();
	}

	/**
	 * Start definition of a {@linkplain ModuleDescriptor module}. The entire
	 * definition can be rolled back because the {@linkplain L2Interpreter
	 * interpreter}'s context module will contain all methods and precedence
	 * rules defined between the transaction start and the rollback (or commit).
	 * Committing simply clears this information.
	 *
	 * @param moduleNameString
	 *        The name of the {@linkplain ModuleDescriptor module}.
	 */
	void startModuleTransaction (final AvailObject moduleNameString)
	{
		assert module == null;
		module = ModuleDescriptor.newModule(moduleNameString);
		module.isSystemModule(isSystemCompiler());
		interpreter.setModule(module);
	}

	/**
	 * Rollback the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction(AvailObject)}.
	 */
	void rollbackModuleTransaction ()
	{
		assert module != null;
		module.removeFrom(interpreter);
		module = null;
		interpreter.setModule(null);
	}

	/**
	 * Commit the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction(AvailObject)}. Simply
	 * clear the {@linkplain #module} field.
	 */
	void commitModuleTransaction ()
	{
		assert module != null;
		interpreter.runtime().addModule(module);
		module.cleanUpAfterCompile();
		module = null;
		interpreter.setModule(null);
	}

	/**
	 * Convert a {@link ParseNodeDescriptor parse node} into a zero-argument
	 * {@link FunctionDescriptor function}.
	 *
	 * @param expressionNode The parse tree to compile to a function.
	 * @param lineNumber The line number to attach to the new function.
	 * @return A zero-argument function.
	 */
	AvailObject createFunctionToRun (
		final AvailObject expressionNode,
		final int lineNumber)
	{
		final AvailObject block = BlockNodeDescriptor.newBlockNode(
			Collections.<AvailObject>emptyList(),
			(short) 0,
			Collections.singletonList(expressionNode),
			TOP.o(),
			SetDescriptor.empty(),
			lineNumber);
		BlockNodeDescriptor.recursivelyValidate(block);
		final AvailCodeGenerator codeGenerator = new AvailCodeGenerator(
			module);
		final AvailObject compiledBlock = block.generate(codeGenerator);
		// The block is guaranteed context-free (because imported
		// variables/values are embedded directly as constants in the generated
		// code), so build a function with no copied data.
		assert compiledBlock.numOuters() == 0;
		final AvailObject function = FunctionDescriptor.create(
			compiledBlock,
			TupleDescriptor.empty());
		function.makeImmutable();
		return function;
	}

	/**
	 * Evaluate a {@linkplain ParseNodeDescriptor parse node} in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param expressionNode
	 *            A {@linkplain ParseNodeDescriptor parse node}.
	 * @param lineNumber
	 *            The line number on which the expression starts.
	 * @return The result of generating a {@linkplain FunctionDescriptor
	 *         function} from the argument and evaluating it.
	 */
	AvailObject evaluate (
		final AvailObject expressionNode,
		final int lineNumber)
	{
		final AvailObject function = createFunctionToRun(
			expressionNode,
			lineNumber);
		final AvailObject result = interpreter.runFunctionArguments(
			function,
			Collections.<AvailObject>emptyList());
		return result;
	}

	/**
	 * Evaluate a parse tree node. It's a top-level statement in a module.
	 * Declarations are handled differently - they cause a variable to be
	 * declared in the module's scope.
	 *
	 * @param expressionOrMacro
	 *        The expression to compile and evaluate as a top-level statement in
	 *        the module.
	 */
	void evaluateModuleStatement (final AvailObject expressionOrMacro)
	{
		final AvailObject expression = expressionOrMacro.stripMacro();
		if (!expression.isInstanceOfKind(DECLARATION_NODE.mostGeneralType()))
		{
			// Only record module statements that aren't declarations.  Users of
			// the module don't care if a module variable or constant is only
			// reachable from the module's methods.
			final AvailObject function = createFunctionToRun(expression, 0);
			serializer.serialize(function);
			interpreter.runFunctionArguments(
				function,
				Collections.<AvailObject>emptyList());
			return;
		}
		// It's a declaration (but the parser couldn't previously tell that it
		// was at module scope)...
		final AvailObject name = expression.token().string();
		switch (expression.declarationKind())
		{
			case LOCAL_CONSTANT:
			{
				final AvailObject val = evaluate(
					expression.initializationExpression(),
					expression.token().lineNumber());
				final AvailObject var = VariableDescriptor.forInnerType(
					AbstractEnumerationTypeDescriptor.withInstance(val));
				module.addConstantBinding(
					name,
					var.makeImmutable());
				// Create a module variable declaration (i.e., cheat) JUST for
				// this initializing assignment.
				final AvailObject decl =
					DeclarationNodeDescriptor.newModuleVariable(
						expression.token(),
						var,
						expression.initializationExpression());
				final AvailObject assign = AssignmentNodeDescriptor.from(
					VariableUseNodeDescriptor.newUse(expression.token(), decl),
					LiteralNodeDescriptor.syntheticFrom(val),
					false);
				final AvailObject function = createFunctionToRun(
					assign,
					expression.token().lineNumber());
				serializer.serialize(function);
				var.setValue(val);
				break;
			}
			case LOCAL_VARIABLE:
			{
				final AvailObject var = VariableDescriptor.forInnerType(
					expression.declaredType());
				module.addVariableBinding(
					name,
					var.makeImmutable());
				if (!expression.initializationExpression().equalsNil())
				{
					final AvailObject decl =
						DeclarationNodeDescriptor.newModuleVariable(
							expression.token(),
							var,
							expression.initializationExpression());
					final AvailObject assign = AssignmentNodeDescriptor.from(
						VariableUseNodeDescriptor.newUse(
							expression.token(),
							decl),
						expression.initializationExpression(),
						false);
					final AvailObject function = createFunctionToRun(
						assign,
						expression.token().lineNumber());
					serializer.serialize(function);
					var.setValue(
						evaluate(
							expression.initializationExpression(),
							expression.token().lineNumber()));
				}
				break;
			}
			default:
				assert false
				: "Expected top-level declaration to be parsed as local";
		}
	}

	/**
	 * Report that the parser was expecting one of several keywords. The
	 * keywords are keys of the {@linkplain MapDescriptor map} argument
	 * {@code incomplete}.
	 *
	 * @param where
	 *        Where the keywords were expected.
	 * @param incomplete
	 *        A map of partially parsed keywords, where the keys are the strings
	 *        that were expected at this position.
	 * @param caseInsensitive
	 *        {@code true} if the parsed keywords are case-insensitive, {@code
	 *        false} otherwise.
	 */
	void expectedKeywordsOf (
		final ParserState where,
		final AvailObject incomplete,
		final boolean caseInsensitive)
	{
		where.expected(
			new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringBuilder builder = new StringBuilder(200);
					if (caseInsensitive)
					{
						builder.append(
							"one of the following case-insensitive internal "
							+ "keywords:");
					}
					else
					{
						builder.append(
							"one of the following internal keywords:");
					}
					final List<String> sorted = new ArrayList<String>(
						incomplete.mapSize());
					final boolean detail = incomplete.mapSize() < 10;
					for (final MapDescriptor.Entry entry
						: incomplete.mapIterable())
					{
						final String string = entry.key.asNativeString();
						if (detail)
						{
							final StringBuilder buffer = new StringBuilder();
							buffer.append(string);
							buffer.append(" (");
							boolean first = true;
							for (final MapDescriptor.Entry successorBundleEntry
								: entry.value.allBundles().mapIterable())
							{
								if (first)
								{
									first = false;
								}
								else
								{
									buffer.append(", ");
								}
								buffer.append(
									successorBundleEntry.key.toString());
							}
							buffer.append(")");
							sorted.add(buffer.toString());
						}
						else
						{
							sorted.add(string);
						}
					}
					Collections.sort(sorted);
					boolean startOfLine = true;
					builder.append("\n\t");
					final int leftColumn = 4 + 4; // ">>> " and a tab.
					int column = leftColumn;
					for (final String s : sorted)
					{
						if (!startOfLine)
						{
							builder.append("  ");
							column += 2;
						}
						startOfLine = false;
						final int lengthBefore = builder.length();
						builder.append(s);
						column += builder.length() - lengthBefore;
						if (detail || column + 2 + s.length() > 80)
						{
							builder.append("\n\t");
							column = leftColumn;
							startOfLine = true;
						}
					}
					return builder.toString();
				}
			});
	}

	/**
	 * Parse a send node. To prevent infinite left-recursion and false
	 * ambiguity, we only allow a send with a leading keyword to be parsed from
	 * here, since leading underscore sends are dealt with iteratively
	 * afterward.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do after parsing a complete send node.
	 */
	protected void parseLeadingKeywordSendThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		parseRestOfSendNode(
			start,
			interpreter.rootBundleTree(),
			null,
			start,
			false,  // Nothing consumed yet.
			Collections.<AvailObject> emptyList(),
			continuation);
	}

	/**
	 * Parse a send node whose leading argument has already been parsed.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param leadingArgument
	 *            The argument that was already parsed.
	 * @param initialTokenPosition
	 *            Where the leading argument started.
	 * @param continuation
	 *            What to do after parsing a send node.
	 */
	void parseLeadingArgumentSendAfterThen (
		final ParserState start,
		final AvailObject leadingArgument,
		final ParserState initialTokenPosition,
		final Con<AvailObject> continuation)
	{
		assert start.position != initialTokenPosition.position;
		assert leadingArgument != null;
		parseRestOfSendNode(
			start,
			interpreter.rootBundleTree(),
			leadingArgument,
			initialTokenPosition,
			false,  // Leading argument does not yet count as something parsed.
			Collections.<AvailObject> emptyList(),
			continuation);
	}

	/**
	 * Parse an expression with an optional lead-argument message send around
	 * it. Backtracking will find all valid interpretations.
	 *
	 * @param startOfLeadingArgument
	 *            Where the leading argument started.
	 * @param afterLeadingArgument
	 *            Just after the leading argument.
	 * @param node
	 *            An expression that acts as the first argument for a potential
	 *            leading-argument message send, or possibly a chain of them.
	 * @param continuation
	 *            What to do with either the passed node, or the node wrapped in
	 *            suitable leading-argument message sends.
	 */
	void parseOptionalLeadingArgumentSendAfterThen (
		final ParserState startOfLeadingArgument,
		final ParserState afterLeadingArgument,
		final AvailObject node,
		final Con<AvailObject> continuation)
	{
		// It's optional, so try it with no wrapping.
		attempt(afterLeadingArgument, continuation, node);

		// Don't wrap it if its type is top.
		if (node.expressionType().equals(TOP.o()))
		{
			return;
		}

		// Try to wrap it in a leading-argument message send.
		attempt(
			afterLeadingArgument,
			new Con<AvailObject>("Possible leading argument send")
			{
				@Override
				public void value (
					final @Nullable ParserState afterLeadingArgument2,
					final @Nullable AvailObject node2)
				{
					assert afterLeadingArgument2 != null;
					assert node2 != null;
					parseLeadingArgumentSendAfterThen(
						afterLeadingArgument2,
						node2,
						startOfLeadingArgument,
						new Con<AvailObject>("Leading argument send")
						{
							@Override
							public void value (
								final @Nullable ParserState afterSend,
								final @Nullable AvailObject leadingSend)
							{
								assert afterSend != null;
								assert leadingSend != null;
								parseOptionalLeadingArgumentSendAfterThen(
									startOfLeadingArgument,
									afterSend,
									leadingSend,
									continuation);
							}
						});
				}
			},
			node);
	}

	/**
	 * We've parsed part of a send. Try to finish the job.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param bundleTree
	 *            The bundle tree used to parse at this position.
	 * @param firstArgOrNull
	 *            Either null or an argument that must be consumed before any
	 *            keywords (or completion of a send).
	 * @param consumedAnything
	 *            Whether any actual tokens have been consumed so far for this
	 *            send node.  That includes any leading argument.
	 * @param initialTokenPosition
	 *            The parse position where the send node started to be
	 *            processed. Does not count the position of the first argument
	 *            if there are no leading keywords.
	 * @param argsSoFar
	 *            The list of arguments parsed so far. I do not modify it. This
	 *            is a stack of expressions that the parsing instructions will
	 *            assemble into a list that correlates with the top-level
	 *            non-backquoted underscores and guillemet groups in the message
	 *            name.
	 * @param continuation
	 *            What to do with a fully parsed send node.
	 */
	void parseRestOfSendNode (
		final ParserState start,
		final AvailObject bundleTree,
		final @Nullable AvailObject firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<AvailObject> argsSoFar,
		final Con<AvailObject> continuation)
	{
		bundleTree.expand();
		final AvailObject complete = bundleTree.lazyComplete();
		final AvailObject incomplete = bundleTree.lazyIncomplete();
		final AvailObject caseInsensitive =
			bundleTree.lazyIncompleteCaseInsensitive();
		final AvailObject actions = bundleTree.lazyActions();
		final AvailObject prefilter = bundleTree.lazyPrefilterMap();
		final boolean anyComplete = complete.mapSize() > 0;
		final boolean anyIncomplete = incomplete.mapSize() > 0;
		final boolean anyCaseInsensitive = caseInsensitive.mapSize() > 0;
		final boolean anyActions = actions.mapSize() > 0;
		final boolean anyPrefilter = prefilter.mapSize() > 0;

		if (!(anyComplete
			|| anyIncomplete
			|| anyCaseInsensitive
			|| anyActions
			|| anyPrefilter))
		{
			return;
		}
		if (anyComplete && consumedAnything && firstArgOrNull == null)
		{
			// There are complete messages, we didn't leave a leading argument
			// stranded, and we made progress in the file (i.e., the message
			// send does not consist of exactly zero tokens).  It *should* be
			// powerful enough to parse calls of "_" (i.e., the implicit
			// conversion operation), but only if there is a grammatical
			// restriction to prevent run-away left-recursion.  A type
			// restriction won't be checked soon enough to prevent the
			// recursion.
			for (final MapDescriptor.Entry entry : complete.mapIterable())
			{
				assert interpreter.runtime().hasMethodAt(entry.key);
				completedSendNode(
					initialTokenPosition,
					start,
					argsSoFar,
					entry.value,
					continuation);
			}
		}
		if (anyIncomplete
			&& firstArgOrNull == null
			&& !start.atEnd())
		{
			final AvailObject keywordToken = start.peekToken();
			if (keywordToken.tokenType() == KEYWORD
				|| keywordToken.tokenType() == OPERATOR)
			{
				final AvailObject keywordString = keywordToken.string();
				if (incomplete.hasKey(keywordString))
				{
					final AvailObject subtree = incomplete.mapAt(keywordString);
					eventuallyParseRestOfSendNode(
						"Continue send after a keyword: " + keywordString,
						start.afterToken(),
						subtree,
						null,
						initialTokenPosition,
						true,  // Just consumed a token.
						argsSoFar,
						continuation);
				}
				else
				{
					expectedKeywordsOf(start, incomplete, false);
				}
			}
			else
			{
				expectedKeywordsOf(start, incomplete, false);
			}
		}
		if (anyCaseInsensitive
			&& firstArgOrNull == null
			&& !start.atEnd())
		{
			final AvailObject keywordToken = start.peekToken();
			if (keywordToken.tokenType() == KEYWORD
				|| keywordToken.tokenType() == OPERATOR)
			{
				final AvailObject keywordString =
					keywordToken.lowerCaseString();
				if (caseInsensitive.hasKey(keywordString))
				{
					final AvailObject subtree =
						caseInsensitive.mapAt(keywordString);
					eventuallyParseRestOfSendNode(
						"Continue send after a case-insensitive keyword",
						start.afterToken(),
						subtree,
						null,
						initialTokenPosition,
						true,  // Just consumed a token.
						argsSoFar,
						continuation);
				}
				else
				{
					expectedKeywordsOf(start, caseInsensitive, true);
				}
			}
			else
			{
				expectedKeywordsOf(start, caseInsensitive, true);
			}
		}
		if (anyPrefilter)
		{
			System.out.println("PREFILTER ENCOUNTERED: " + prefilter);
			final AvailObject latestArgument = last(argsSoFar);
			if (latestArgument.isInstanceOfKind(SEND_NODE.mostGeneralType()))
			{
				final AvailObject methodName = latestArgument.method().name();
				if (prefilter.hasKey(methodName))
				{
					eventuallyParseRestOfSendNode(
						"Continue send after productive grammatical "
							+ "restriction",
						start,
						prefilter.mapAt(methodName),
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						continuation);
					// Don't allow normal action processing, as it would ignore
					// the restriction which we've been so careful to prefilter.
					assert actions.mapSize() == 1;
					assert ParsingOperation.decode(
							actions.mapIterable().next().key.extractInt())
						== ParsingOperation.CHECK_ARGUMENT;
					return;
				}
			}
		}
		if (anyActions)
		{
			for (final MapDescriptor.Entry entry : actions.mapIterable())
			{
				final AvailObject key = entry.key;
				final AvailObject value = entry.value;
				eventuallyDo(
					new Continuation0()
					{
						@Override
						public void value ()
						{
							runParsingInstructionThen(
								start,
								key.extractInt(),
								firstArgOrNull,
								argsSoFar,
								initialTokenPosition,
								consumedAnything,
								value,
								continuation);
						}
					},
					//TODO[MvG]: Reduce back to a string constant at some point.
					"Continue with instruction: "
						+ ParsingOperation.decode(entry.key.extractInt()),
					start.position);
			}
		}
	}

	/**
	 * Answer the {@linkplain SetDescriptor set} of {@linkplain
	 * DeclarationNodeDescriptor declaration nodes} which are used by this
	 * parse tree but are locally declared (i.e., not at global module scope).
	 *
	 * @param parseTree
	 *            The parse tree to examine.
	 * @return
	 *            The set of the local declarations that were used in the parse
	 *            tree.
	 */
	AvailObject usesWhichLocalVariables (
		final AvailObject parseTree)
	{
		final Mutable<AvailObject> usedDeclarations =
			new Mutable<AvailObject>(SetDescriptor.empty());
		parseTree.childrenDo(new Continuation1<AvailObject>()
		{
			@Override
			public void value (final @Nullable AvailObject node)
			{
				assert node != null;
				if (node.isInstanceOfKind(VARIABLE_USE_NODE.mostGeneralType()))
				{
					final AvailObject declaration = node.declaration();
					if (!declaration.declarationKind().isModuleScoped())
					{
						usedDeclarations.value =
							usedDeclarations.value.setWithElementCanDestroy(
								declaration,
								true);
					}
				}
			}
		});
		return usedDeclarations.value;
	}

	/**
	 * Execute one non-keyword-parsing instruction, then run the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param instruction
	 *            The {@linkplain MessageSplitter instruction} to execute.
	 * @param firstArgOrNull
	 *            Either the already-parsed first argument or null. If we're
	 *            looking for leading-argument message sends to wrap an
	 *            expression then this is not-null before the first argument
	 *            position is encountered, otherwise it's null and we should
	 *            reject attempts to start with an argument (before a keyword).
	 * @param argsSoFar
	 *            The message arguments that have been parsed so far.
	 * @param initialTokenPosition
	 *            The position at which parsing of this message started. If it
	 *            was parsed as a leading argument send (i.e., firstArgOrNull
	 *            started out non-null) then the position is of the token
	 *            following the first argument.
	 * @param consumedAnything
	 *            Whether any tokens or arguments have been consumed yet.
	 * @param successorTrees
	 *            The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            MessageBundleTreeDescriptor bundle trees} at which to continue
	 *            parsing.
	 * @param continuation
	 *            What to do with a complete {@linkplain SendNodeDescriptor
	 *            message send}.
	 */
	void runParsingInstructionThen (
		final ParserState start,
		final int instruction,
		final @Nullable AvailObject firstArgOrNull,
		final List<AvailObject> argsSoFar,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final AvailObject successorTrees,
		final Con<AvailObject> continuation)
	{
		final ParsingOperation op = ParsingOperation.decode(instruction);
//		System.out.format(
//			"OP=%s%s [%s ☞ %s]%n",
//			op.name(),
//			instruction < ParsingOperation.distinctInstructions
//				? ""
//				: " (operand=" + op.operand(instruction) + ")",
//			tokens.get(start.position - 1).string(),
//			tokens.get(start.position).string());
		switch (op)
		{
			case PARSE_ARGUMENT:
			{
				// Parse an argument and continue.
				assert successorTrees.tupleSize() == 1;
				parseSendArgumentWithExplanationThen(
					start,
					" (an argument of some message)",
					firstArgOrNull,
					firstArgOrNull == null
						&& initialTokenPosition.position != start.position,
					new Con<AvailObject>("Argument of message send")
					{
						@Override
						public void value (
							final @Nullable ParserState afterArg,
							final @Nullable AvailObject newArg)
						{
							assert afterArg != null;
							assert newArg != null;
							final List<AvailObject> newArgsSoFar =
								append(argsSoFar, newArg);
							eventuallyParseRestOfSendNode(
								"Continue send after argument",
								afterArg,
								successorTrees.tupleAt(1),
								null,
								initialTokenPosition,
								// The argument counts as something that was
								// consumed if it's not a leading argument...
								firstArgOrNull == null,
								newArgsSoFar,
								continuation);
						}
					});
				break;
			}
			case NEW_LIST:
			{
				// Push an empty list node and continue.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					append(argsSoFar, ListNodeDescriptor.empty());
				eventuallyParseRestOfSendNode(
					"Continue send after push empty",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case APPEND_ARGUMENT:
			{
				// Append the item that's the last thing to the list that's the
				// second last thing. Pop both and push the new list (the
				// original list must not change), then continue.
				assert successorTrees.tupleSize() == 1;
				final AvailObject value = last(argsSoFar);
				final List<AvailObject> poppedOnce = withoutLast(argsSoFar);
				final AvailObject oldNode = last(poppedOnce);
				final AvailObject listNode = oldNode.copyWith(value);
				final List<AvailObject> newArgsSoFar =
					append(withoutLast(poppedOnce), listNode);
				eventuallyParseRestOfSendNode(
					"Continue send after append",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case SAVE_PARSE_POSITION:
			{
				// Push current parse position.
				assert successorTrees.tupleSize() == 1;
				final AvailObject marker = MarkerNodeDescriptor.create(
					IntegerDescriptor.fromInt(
						firstArgOrNull == null
							? start.position
							: initialTokenPosition.position));
				final List<AvailObject> newArgsSoFar =
					PrefixSharingList.append(argsSoFar, marker);
				eventuallyParseRestOfSendNode(
					"Continue send after push parse position",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case DISCARD_SAVED_PARSE_POSITION:
			{
				// Under-pop saved parse position (from 2nd-to-top of stack).
				assert successorTrees.tupleSize() == 1;
				final AvailObject oldTop = last(argsSoFar);
				final List<AvailObject> poppedOnce = withoutLast(argsSoFar);
				assert last(poppedOnce).isMarkerNode();
				final List<AvailObject> newArgsSoFar =
					append(withoutLast(poppedOnce), oldTop);
				eventuallyParseRestOfSendNode(
					"Continue send after underpop saved position",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case ENSURE_PARSE_PROGRESS:
			{
				// Check parse progress (abort if parse position is still equal
				// to value at 2nd-to-top of stack). Also update the entry to
				// be the new parse position.
				assert successorTrees.tupleSize() == 1;
				final AvailObject top = last(argsSoFar);
				final List<AvailObject> poppedOnce = withoutLast(argsSoFar);
				final AvailObject oldMarker = last(poppedOnce);
				if (oldMarker.markerValue().extractInt() == start.position)
				{
					// No progress has been made.  Reject this path.
					return;
				}
				final AvailObject newMarker = MarkerNodeDescriptor.create(
					IntegerDescriptor.fromInt(start.position));
				final List<AvailObject> newArgsSoFar =
					append(
						append(withoutLast(poppedOnce), newMarker),
						top);
				eventuallyParseRestOfSendNode(
					"Continue send after check parse progress",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case PARSE_RAW_TOKEN:
				// Parse a raw token and continue.
				assert successorTrees.tupleSize() == 1;
				if (firstArgOrNull != null)
				{
					// Starting with a parseRawToken can't cause unbounded
					// left-recursion, so treat it more like reading an expected
					// token than like parseArgument.  Thus, if a firstArgument
					// has been provided (i.e., we're attempting to parse a
					// leading-argument message to wrap a leading expression),
					// then reject the parse.
					break;
				}
				final AvailObject newToken = parseRawTokenOrNull(start);
				if (newToken != null)
				{
					final ParserState afterToken = start.afterToken();
					final AvailObject syntheticToken =
						LiteralTokenDescriptor.create(
							newToken.string(),
							newToken.start(),
							newToken.lineNumber(),
							SYNTHETIC_LITERAL,
							newToken);
					final AvailObject literalNode =
						LiteralNodeDescriptor.fromToken(syntheticToken);
					final List<AvailObject> newArgsSoFar =
						append(argsSoFar, literalNode);
					eventuallyParseRestOfSendNode(
						"Continue send after raw token for ellipsis",
						afterToken,
						successorTrees.tupleAt(1),
						null,
						initialTokenPosition,
						true,
						newArgsSoFar,
						continuation);
				}
				break;
			case POP:
			{
				// Pop the parse stack.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar = withoutLast(argsSoFar);
				eventuallyParseRestOfSendNode(
					"Continue send after pop",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case PARSE_ARGUMENT_IN_MODULE_SCOPE:
			{
				// Parse an argument in the outermost (module) scope and
				// continue.
				assert successorTrees.tupleSize() == 1;
				final AvailObject clientDataInGlobalScope =
					start.clientDataMap.mapAtPuttingCanDestroy(
						AtomDescriptor.compilerScopeMapKey(),
						MapDescriptor.empty(),
						false);
				final ParserState startInGlobalScope = new ParserState(
					start.position,
					clientDataInGlobalScope);
				parseSendArgumentWithExplanationThen(
					startInGlobalScope,
					" (a global-scoped argument of some message)",
					firstArgOrNull,
					firstArgOrNull == null
						&& initialTokenPosition.position != start.position,
					new Con<AvailObject>("Global-scoped argument of message")
					{
						@Override
						public void value (
							final @Nullable ParserState afterArg,
							final @Nullable AvailObject newArg)
						{
							assert afterArg != null;
							assert newArg != null;
							if (firstArgOrNull != null)
							{
								// A leading argument was already supplied.  We
								// couldn't prevent it from referring to
								// variables that were in scope during its
								// parsing, but we can reject it if the leading
								// argument is supposed to be parsed in global
								// scope, which is the case here, and there are
								// references to local variables within the
								// argument's parse tree.
								final AvailObject usedLocals =
									usesWhichLocalVariables(newArg);
								if (usedLocals.setSize() > 0)
								{
									// A leading argument was supplied which
									// used at least one local.  It shouldn't
									// have.
									afterArg.expected(new Generator<String>()
									{
										@Override
										public String value ()
										{
											final List<String> localNames =
												new ArrayList<String>();
											for (final AvailObject usedLocal
												: usedLocals)
											{
												final AvailObject name =
													usedLocal.token().string();
												localNames.add(
													name.asNativeString());
											}
											return
												"A leading argument which " +
												"was supposed to be parsed in" +
												"module scope actually " +
												"referred to some local " +
												"variables: " +
												localNames.toString();
										}
									});
									return;
								}
							}
							final List<AvailObject> newArgsSoFar =
								append(argsSoFar, newArg);
							final ParserState afterArgButInScope =
								new ParserState(
									afterArg.position,
									start.clientDataMap);
							eventuallyParseRestOfSendNode(
								"Continue send after argument in module scope",
								afterArgButInScope,
								successorTrees.tupleAt(1),
								null,
								initialTokenPosition,
								// The argument counts as something that was
								// consumed if it's not a leading argument...
								firstArgOrNull == null,
								newArgsSoFar,
								continuation);
						}
					});
				break;
			}
			case RESERVED_9:
			case RESERVED_10:
			case RESERVED_11:
			case RESERVED_12:
			case RESERVED_13:
			case RESERVED_14:
			case RESERVED_15:
			{
				AvailObject.error("Invalid parse instruction: " + op);
				break;
			}
			case BRANCH:
				// $FALL-THROUGH$
				// Fall through.  The successorTrees will be different
				// for the jump versus parallel-branch.
			case JUMP:
				for (int i = successorTrees.tupleSize(); i >= 1; i--)
				{
					final AvailObject successorTree = successorTrees.tupleAt(i);
					eventuallyParseRestOfSendNode(
						"Continue send after branch or jump (" +
							(i == 1 ? "not taken)" : "taken)"),
						start,
						successorTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						continuation);
				}
				break;
			case PARSE_PART:
				// $FALL-THROUGH$
			case PARSE_PART_CASE_INSENSITIVELY:
				assert false
				: op.name() + " instruction should not be dispatched";
				break;
			case CHECK_ARGUMENT:
			{
				// CheckArgument.  An actual argument has just been parsed (and
				// pushed).  Make sure it satisfies any grammatical
				// restrictions.  The message bundle tree's lazy prefilter map
				// deals with that efficiently.
				assert successorTrees.tupleSize() == 1;
				assert firstArgOrNull == null;
				eventuallyParseRestOfSendNode(
					"Continue send after checkArgument",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					continuation);
				break;
			}
			case CONVERT:
			{
				// Convert the argument.
				assert successorTrees.tupleSize() == 1;
				final AvailObject input = last(argsSoFar);
				final AvailObject replacement =
					op.conversionRule(instruction).convert(
						input,
						initialTokenPosition);
				final List<AvailObject> newArgsSoFar =
					append(withoutLast(argsSoFar), replacement);
				eventuallyParseRestOfSendNode(
					"Continue send after conversion",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case PUSH_INTEGER_LITERAL:
			{
				final AvailObject integerValue = IntegerDescriptor.fromInt(
					op.integerToPush(instruction));
				final AvailObject token =
					LiteralTokenDescriptor.create(
						StringDescriptor.from(integerValue.toString()),
						initialTokenPosition.peekToken().start(),
						initialTokenPosition.peekToken().lineNumber(),
						LITERAL,
						integerValue);
				final AvailObject literalNode =
					LiteralNodeDescriptor.fromToken(token);
				final List<AvailObject> newArgsSoFar =
					append(argsSoFar, literalNode);
				eventuallyParseRestOfSendNode(
					"Continue send after conversion",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					continuation);
				break;
			}
			case PREPARE_TO_RUN_PREFIX_FUNCTION:
			{
				List<AvailObject> stackCopy = Collections.emptyList();
				for (final AvailObject arg : argsSoFar)
				{
					if (!arg.isMarkerNode())
					{
						stackCopy = append(stackCopy, arg);
					}
				}
				for (int i = op.fixupDepth(instruction); i > 0; i--)
				{
					// Pop the last element and append it to the second last.
					final AvailObject value = last(stackCopy);
					final List<AvailObject> poppedOnce = withoutLast(stackCopy);
					final AvailObject oldNode = last(poppedOnce);
					final AvailObject listNode = oldNode.copyWith(value);
					stackCopy = append(withoutLast(poppedOnce), listNode);
				}
				// Convert the List to an Avail list node.
				final AvailObject newListNode =
					ListNodeDescriptor.newExpressions(
						TupleDescriptor.fromList(stackCopy));
				assert successorTrees.tupleSize() == 1;
				eventuallyParseRestOfSendNode(
					"Continue send after preparing to run prefix function (§)",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					append(argsSoFar, newListNode),
					continuation);
				break;
			}
			case RUN_PREFIX_FUNCTION:
			{
				// Extract the list node pushed by the
				// PREPARE_TO_RUN_PREFIX_FUNCTION instruction that should have
				// just run.
				final AvailObject listNodeOfArgsSoFar = last(argsSoFar);
				final List<AvailObject> listOfArgs = TupleDescriptor.toList(
					listNodeOfArgsSoFar.expressionsTuple());

				// Store the current parsing state in a special fiber global.
				final AvailObject clientDataGlobalKey =
					AtomDescriptor.clientDataGlobalKey();
				AvailObject fiberGlobals = interpreter.fiber.fiberGlobals();
				fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
					clientDataGlobalKey,
					start.clientDataMap.makeImmutable(),
					true);
				interpreter.fiber.fiberGlobals(fiberGlobals);

				// Call the appropriate prefix function(s).
				assert successorTrees.tupleSize() == 1;
				final AvailObject successorTree = successorTrees.tupleAt(1);
				final AvailObject bundlesMap = successorTree.allBundles();
				for (final MapDescriptor.Entry entry : bundlesMap.mapIterable())
				{
//					final AvailObject message = entry.value.message();
//					final AvailObject method =
//						interpreter.runtime().methodAt(message);
//					assert !method.equalsNull();
//					final AvailObject definitionsTuple =
//						method.definitionsTuple();
//					assert definitionsTuple.tupleSize() > 0;
//					for (final AvailObject macroDefinition : definitionsTuple)
//					{
//						if (macroDefinition.isMacroDefinition())
//						{
//							final AvailObject function =
//								macroDefinition.prefixFunctions().tupleAt(
//									instruction);
//							interpreter.invokeFunctionArguments(
//								function,
//								listOfArgs);
//							TODO[MvG]:LOTS
//							*** If we allow multiple macros with a common
//							*** prefix, then each associated prefix function may
//							*** produce a different value / effect.  Thus, the
//							*** existing bundle tree must be split somehow along
//							*** that boundary.  CRAP!
//						}
//					}
//					assert definitionsTuple.tupleAt(1).isMacroDefinition();
//					{
//						// Macro definitions and non-macro definitions are not allowed to
//						// mix within a method.
//						completedSendNodeForMacro(
//							stateBeforeCall,
//							stateAfterCall,
//							argumentExpressions,
//							bundle,
//							method,
//							continuation);
//						return;
//					}
//					// It invokes a method (not a macro).
//					final List<AvailObject> argTypes =
//						new ArrayList<AvailObject>(argumentExpressions.size());
//					for (final AvailObject argumentExpression : argumentExpressions)
//					{
//						argTypes.add(argumentExpression.expressionType());
//					}
//					// Parsing a method send can't affect the scope.
//					assert stateAfterCall.clientDataMap.equals(
//						stateBeforeCall.clientDataMap);
//					final AvailObject returnType = validateArgumentTypes(
//						stateAfterCall,
//						argTypes,
//						method,
//						new Continuation1<Generator<String>>()
//						{
//							@Override
//							public void value (
//								final @Nullable Generator<String> errorGenerator)
//							{
//								assert errorGenerator != null;
//								valid.value = false;
//								stateAfterCall.expected(errorGenerator);
//							}
//						});
//					if (valid.value)
//					{
//						final AvailObject sendNode = SendNodeDescriptor.from(
//							method,
//							ListNodeDescriptor.newExpressions(
//								TupleDescriptor.fromList(argumentExpressions)),
//							returnType);
//						attempt(stateAfterCall, continuation, sendNode);
//					}
//
//
//					completedSendNode(
//						initialTokenPosition,
//						start,
//						withoutLast(argsSoFar),
//						entry.value,
//						continuation);
				}

				//TODO[MvG]: Implement this parse instruction.
//				*** Stash the client data in the fiber, then run the specified
//				*** prefix function, and re-extract the client data from the
//				*** fiber.

//				*** Crap... we need to be able to get to the prefix functions
//				*** of all applicable macros somehow.  In theory we could just
//				*** look at the successorTrees.  We also need to forbid method
//				*** definitions from being added if the message includes "§".
//				*** We also need to count and type-check the prefix functions.

				fiberGlobals = interpreter.fiber.fiberGlobals();
				final AvailObject newClientData =
					fiberGlobals.mapAt(clientDataGlobalKey);

				assert successorTrees.tupleSize() == 1;
				eventuallyParseRestOfSendNode(
					"Continue send after running prefix function (§)",
					new ParserState(
						start.position,
						newClientData),
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					withoutLast(argsSoFar),
					continuation);
				break;
			}
		}
	}

	/**
	 * A helper method to queue a parsing activity for continuing to parse a
	 * {@linkplain SendNodeDescriptor send phrase}.
	 *
	 * @param description
	 * @param start
	 * @param bundleTree
	 * @param firstArgOrNull
	 * @param initialTokenPosition
	 * @param consumedAnything
	 * @param argsSoFar
	 * @param continuation
	 */
	@InnerAccess void eventuallyParseRestOfSendNode (
		final String description,
		final ParserState start,
		final AvailObject bundleTree,
		final @Nullable AvailObject firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<AvailObject> argsSoFar,
		final Con<AvailObject> continuation)
	{
		eventuallyDo(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					parseRestOfSendNode(
						start,
						bundleTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						continuation);
				}
			},
			description,
			start.position);
	}

	/**
	 * Parse an argument to a message send. Backtracking will find all valid
	 * interpretations.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param explanation
	 *            A {@link String} indicating why it's parsing an argument.
	 * @param firstArgOrNull
	 *            Either a parse node to use as the argument, or null if we
	 *            should parse one now.
	 * @param canReallyParse
	 *            Whether any tokens may be consumed.  This should be false
	 *            specifically when the leftmost argument of a leading-argument
	 *            message is being parsed.
	 * @param continuation
	 *            What to do with the argument.
	 */
	void parseSendArgumentWithExplanationThen (
		final ParserState start,
		final String explanation,
		final @Nullable AvailObject firstArgOrNull,
		final boolean canReallyParse,
		final Con<AvailObject> continuation)
	{
		if (firstArgOrNull == null)
		{
			// There was no leading argument, or it has already been accounted
			// for.  If we haven't actually consumed anything yet then don't
			// allow a *leading* argument to be parsed here.  That would lead
			// to ambiguous left-recursive parsing.
			if (canReallyParse)
			{
				parseExpressionThen(
					start,
					new Con<AvailObject>("Argument expression")
					{
						@Override
						public void value (
							final @Nullable ParserState afterArgument,
							final @Nullable AvailObject argument)
						{
							assert afterArgument != null;
							assert argument != null;
							attempt(afterArgument, continuation, argument);
						}
					});
			}
		}
		else
		{
			// We're parsing a message send with a leading argument, and that
			// argument was explicitly provided to the parser.  We should
			// consume the provided first argument now.
			assert !canReallyParse;
			attempt(start, continuation, firstArgOrNull);
		}
	}

	/**
	 * A complete {@linkplain SendNodeDescriptor send node} has been parsed.
	 * Create the send node and invoke the continuation.
	 *
	 * <p>
	 * If this is a macro, invoke the body immediately with the argument
	 * expressions to produce a parse node.
	 * </p>
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.
	 * @param stateAfterCall
	 *            The parsing state after the message.
	 * @param argumentExpressions
	 *            The {@linkplain ParseNodeDescriptor parse nodes} that will be
	 *            arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle} that
	 *            identifies the message to be sent.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	void completedSendNode (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final List<AvailObject> argumentExpressions,
		final AvailObject bundle,
		final Con<AvailObject> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
		final AvailObject message = bundle.message();
		final AvailObject method = interpreter.runtime().methodAt(message);
		assert !method.equalsNil();
		final AvailObject definitionsTuple = method.definitionsTuple();
		assert definitionsTuple.tupleSize() > 0;

		if (definitionsTuple.tupleAt(1).isMacroDefinition())
		{
			// Macro definitions and non-macro definitions are not allowed to
			// mix within a method.
			completedSendNodeForMacro(
				stateBeforeCall,
				stateAfterCall,
				argumentExpressions,
				bundle,
				method,
				continuation);
			return;
		}
		// It invokes a method (not a macro).
		final List<AvailObject> argTypes =
			new ArrayList<AvailObject>(argumentExpressions.size());
		for (final AvailObject argumentExpression : argumentExpressions)
		{
			argTypes.add(argumentExpression.expressionType());
		}
		// Parsing a method send can't affect the scope.
		assert stateAfterCall.clientDataMap.equals(
			stateBeforeCall.clientDataMap);
		final AvailObject returnType = validateArgumentTypes(
			stateAfterCall,
			argTypes,
			method,
			new Continuation1<Generator<String>>()
			{
				@Override
				public void value (
					final @Nullable Generator<String> errorGenerator)
				{
					assert errorGenerator != null;
					valid.value = false;
					stateAfterCall.expected(errorGenerator);
				}
			});
		if (valid.value)
		{
			final AvailObject sendNode = SendNodeDescriptor.from(
				method,
				ListNodeDescriptor.newExpressions(
					TupleDescriptor.fromList(argumentExpressions)),
				returnType);
			attempt(stateAfterCall, continuation, sendNode);
		}
	}

	/**
	 * Check that the argument types are appropriate for a call to the specified
	 * method.  If they aren't then invoke the failBlock with a suitable {@link
	 * Generator} of {@link String} describing the problem.
	 *
	 * <p>Answer the result type for the call.</p>
	 *
	 * @param parserState
	 * @param argumentTypes
	 * @param method
	 * @param failBlock
	 * @return The result type of the call site.
	 */
	final AvailObject validateArgumentTypes (
		final ParserState parserState,
		final List<AvailObject> argumentTypes,
		final AvailObject method,
		final Continuation1<Generator<String>> failBlock)
	{
		interpreter.currentParserState = parserState;
		try
		{
			return method.validateArgumentTypesInterpreterIfFail(
				argumentTypes,
				interpreter,
				failBlock);
		}
		finally
		{
			interpreter.currentParserState = null;
		}
	}

	/**
	 * A macro invocation has just been parsed.  Run it now if macro execution
	 * is supported.
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.
	 * @param stateAfterCall
	 *            The parsing state after the message.
	 * @param argumentExpressions
	 *            The {@linkplain ParseNodeDescriptor parse nodes} that will be
	 *            arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle} that
	 *            identifies the message to be sent.
	 * @param method
	 *            The {@linkplain MethodDescriptor method}
	 *            that contains the macro signature to be invoked.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	abstract void completedSendNodeForMacro (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final List<AvailObject> argumentExpressions,
		final AvailObject bundle,
		final AvailObject method,
		final Con<AvailObject> continuation);

	/**
	 * Create a bootstrap primitive method. Use the primitive's type declaration
	 * as the argument types.  If the primitive is fallible then generate
	 * suitable primitive failure code (to invoke the {@link MethodDescriptor
	 * #vmCrashMethod}).
	 *
	 * @param methodName
	 *        The name of the primitive method being defined.
	 * @param primitiveNumber
	 *        The {@linkplain Primitive#primitiveNumber primitive number} of the
	 *        {@linkplain MethodDescriptor method} being defined.
	 */
	void bootstrapMethod (
		final String methodName,
		final int primitiveNumber)
	{
		final AvailObject availName = StringDescriptor.from(methodName);
		final AvailObject nameLiteral =
			LiteralNodeDescriptor.syntheticFrom(availName);
		final AvailObject function =
			MethodDescriptor.newPrimitiveFunction(
				Primitive.byPrimitiveNumberOrFail(primitiveNumber));
		final AvailObject send = SendNodeDescriptor.from(
			MethodDescriptor.vmMethodDefinerMethod(),
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				nameLiteral,
				LiteralNodeDescriptor.syntheticFrom(function))),
			TOP.o());
		evaluateModuleStatement(send);
	}

	/**
	 * Create a bootstrap primitive {@linkplain MacroDefinitionDescriptor
	 * macro}. Use the primitive's type declaration as the argument types.  If
	 * the primitive is fallible then generate suitable primitive failure code
	 * (to invoke the {@link MethodDescriptor#vmCrashMethod}).
	 *
	 * @param macroName
	 *        The name of the primitive macro being defined.
	 * @param primitiveNumbers
	 *        The array of {@linkplain Primitive#primitiveNumber primitive
	 *        numbers} of the bodies of the macro being defined.  These
	 *        correspond to the occurrences of the {@linkplain StringDescriptor
	 *        #sectionSign() section sign} (§) in the macro name, plus a final
	 *        body for the complete macro.
	 */
	void bootstrapMacro (
		final String macroName,
		final int[] primitiveNumbers)
	{
		assert primitiveNumbers.length > 0;
		final AvailObject availName = StringDescriptor.from(macroName);
		final AvailObject nameLiteral =
			LiteralNodeDescriptor.syntheticFrom(availName);
		final List<AvailObject> functionsList = new ArrayList<AvailObject>();
		for (final int primitiveNumber : primitiveNumbers)
		{
			functionsList.add(
				MethodDescriptor.newPrimitiveFunction(
					Primitive.byPrimitiveNumberOrFail(primitiveNumber)));
		}
		final AvailObject body =
			functionsList.remove(functionsList.size() - 1);
		final AvailObject functionsTuple =
			TupleDescriptor.fromList(functionsList);
		final AvailObject send = SendNodeDescriptor.from(
			MethodDescriptor.vmMacroDefinerMethod(),
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				nameLiteral,
				LiteralNodeDescriptor.syntheticFrom(functionsTuple),
				LiteralNodeDescriptor.syntheticFrom(body))),
			TOP.o());
		evaluateModuleStatement(send);
	}

	/**
	 * Serialize a function that will publish all atoms that are currently
	 * public in the module.
	 *
	 * @param isPublic
	 *        {@code true} if the atoms are public, {@code false} if they are
	 *        private.
	 */
	private void serializePublicationFunction (final boolean isPublic)
	{
		// Output a function that publishes the initial public set of atoms.
		final AvailObject sourceNames =
			isPublic ? module.names() : module.privateNames();
		AvailObject names = SetDescriptor.empty();
		for (final MapDescriptor.Entry entry : sourceNames.mapIterable())
		{
			names = names.setUnionCanDestroy(entry.value, false);
		}
		final AvailObject send = SendNodeDescriptor.from(
			MethodDescriptor.vmPublishAtomsMethod(),
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(
					LiteralNodeDescriptor.syntheticFrom(names),
					LiteralNodeDescriptor.syntheticFrom(
						AtomDescriptor.objectFromBoolean(isPublic)))),
			TOP.o());
		final AvailObject function = createFunctionToRun(send, 0);
		function.makeImmutable();
		serializer.serialize(function);
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} and install it into the
	 * {@linkplain AvailRuntime runtime}.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @param aBlock
	 *        A {@linkplain Continuation3 continuation} that accepts the
	 *        {@linkplain ModuleName name} of the {@linkplain ModuleDescriptor
	 *        module} undergoing {@linkplain AbstractAvailCompiler compilation},
	 *        the position of the ongoing parse (in bytes), and the size of the
	 *        module (in bytes).
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 */
	public void parseModule (
			final ModuleName qualifiedName,
			final Continuation4<ModuleName, Long, Long, Long> aBlock)
		throws AvailCompilerException
	{
		progressBlock = aBlock;
		greatestGuess = -1;
		greatExpectations.clear();
		final ResolvedModuleName resolvedName =
			interpreter.runtime().moduleNameResolver().resolve(qualifiedName);
		if (resolvedName == null)
		{
			throw new AvailCompilerException(
				qualifiedName,
				0,
				0,
				"Unable to resolve fully-qualified module name \""
					+ qualifiedName.qualifiedName()
					+ "\" to an existing file");
		}
		source = extractSource(qualifiedName, resolvedName);
		tokens = tokenize(source, false);
		startModuleTransaction(
			StringDescriptor.from(resolvedName.qualifiedName()));
		try
		{
			parseModule(resolvedName);
			serializePublicationFunction(true);
			commitModuleTransaction();
		}
		catch (final TerminateCompilationException e)
		{
			rollbackModuleTransaction();
			throw e;
		}
		catch (final AvailCompilerException e)
		{
			rollbackModuleTransaction();
			throw e;
		}
		catch (final AvailAssertionFailedException e)
		{
			rollbackModuleTransaction();
			final Generator<String> errorProducer = new Generator<String>()
			{
				@Override
				public String value ()
				{
					return e.assertionString().asNativeString();
				}
			};
			reportError(
				qualifiedName,
				tokens.get(greatestGuess),
				"Assertion failed ...",
				Collections.singletonList(errorProducer));
		}
		catch (final Throwable e)
		{
			rollbackModuleTransaction();
			final Generator<String> errorProducer = new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringWriter writer = new StringWriter(500);
					final PrintWriter printer = new PrintWriter(writer);
					e.printStackTrace(printer);
					return writer.toString();
				}
			};
			reportError(
				qualifiedName,
				tokens.get(greatestGuess),
				"Encountered exception during compilation ...",
				Collections.singletonList(errorProducer));
		}
	}

	/**
	 * Parse the {@linkplain ModuleDescriptor module} with the specified
	 * fully-qualified {@linkplain ModuleName module name} from the
	 * {@linkplain TokenDescriptor token} stream.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the
	 *        {@linkplain ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 */
	private final void parseModule (
		final ResolvedModuleName qualifiedName)
	throws AvailCompilerException
	{
		final AvailRuntime runtime = interpreter.runtime();
		final long sourceLength = qualifiedName.fileReference().length();
		final Mutable<AvailObject> interpretation = new Mutable<AvailObject>();
		final Mutable<ParserState> state = new Mutable<ParserState>();
		assert interpreter.unresolvedForwards().setSize() == 0;
		greatestGuess = 0;
		greatExpectations.clear();

		state.value = parseModuleHeader(qualifiedName, false);
		if (state.value == null)
		{
			reportError(qualifiedName);
			assert false;
		}
		if (!state.value.atEnd())
		{
			final AvailObject token = state.value.peekToken();
			progressBlock.value(
				qualifiedName,
				(long) token.lineNumber(),
				(long) token.start(),
				sourceLength);
		}

		final String errorString = moduleHeader.applyToModule(module, runtime);
		if (errorString != null)
		{
			state.value.expected(errorString);
			reportError(qualifiedName);
			assert false;
		}

		serializer.serialize(AtomDescriptor.moduleHeaderSectionAtom());
		moduleHeader.serializeHeaderOn(serializer);

		serializer.serialize(AtomDescriptor.moduleBodySectionAtom());
		for (final AvailObject pragmaString : moduleHeader.pragmas)
		{
			final String nativeString = pragmaString.asNativeString();
			final String[] parts = nativeString.split("=", 3);
			assert parts.length == 3;
			final String pragmaKind = parts[0].trim();
			final String pragmaPrim = parts[1].trim();
			final String pragmaName = parts[2].trim();
			//TODO[MvG]: Move these into named constants.
			final boolean isMethod = pragmaKind.equals("method");
			if (isMethod || pragmaKind.equals("macro"))
			{
				if (isMethod)
				{
					final int primNum = Integer.valueOf(pragmaPrim);
					bootstrapMethod(pragmaName, primNum);
				}
				else
				{
					final String[] primNumStrings = pragmaPrim.split(",");
					final int[] primNums = new int[primNumStrings.length];
					for (int i = 0; i < primNums.length; i++)
					{
						primNums[i] = Integer.valueOf(primNumStrings[i]);
					}
					bootstrapMacro(pragmaName, primNums);
				}
			}
			else
			{
				state.value.expected(
					"pragma to have the form method=999=name " +
					"or macro=999[,999...]=name.");
				reportError(qualifiedName);
			}
		}

		module.buildFilteredBundleTreeFrom(
			interpreter.runtime().allBundles());
		fragmentCache = new AvailCompilerFragmentCache();
		while (!state.value.atEnd())
		{
			greatestGuess = 0;
			greatExpectations.clear();
			interpretation.value = null;
			parseOutermostStatement(
				state.value,
				new Con<AvailObject>("Outermost statement")
				{
					@SuppressWarnings("null")
					@Override
					public void value (
						final @Nullable ParserState afterStatement,
						final @Nullable AvailObject stmt)
					{
						assert interpretation.value == null
							: "Statement parser was supposed to catch "
							+ "ambiguity";
						if (stmt.expressionType().equals(
							BottomTypeDescriptor.bottom()))
						{
							afterStatement.expected(
								"top-level statement not to have type ⊥");
							return;
						}
						interpretation.value = stmt;
						state.value = new ParserState(
							afterStatement.position,
							state.value.clientDataMap);
					}
				});
			assert workPool.isEmpty();

			if (canceling)
			{
				reportError(
					qualifiedName,
					tokens.get(Math.max(greatestGuess, state.value.position)),
					"Error during parsing:",
					Collections.<Generator<String>>singletonList(
						new Generator<String>()
						{
							@Override
							public String value ()
							{
								final StringBuilder trace = new StringBuilder();
								trace.append(causeOfCancellation.toString());
								for (final StackTraceElement stackFrame
									: causeOfCancellation.getStackTrace())
								{
									trace.append("\n\t");
									trace.append(stackFrame);
								}
								return trace.toString();
							}
						}));
			}
			if (interpretation.value == null)
			{
				reportError(qualifiedName);
				assert false;
			}
			// Clear the section of the fragment cache associated with the
			// (outermost) statement just parsed...
			privateClearFrags();
			// Now execute the statement so defining words have a chance to
			// run. This lets the defined words be used in subsequent code.
			// It's even callable in later statements and type expressions.
			try
			{
				evaluateModuleStatement(interpretation.value);
			}
			catch (final AvailAssertionFailedException e)
			{
				reportError(
					qualifiedName,
					tokens.get(state.value.position - 1),
					"Assertion failed...",
					Collections.<Generator<String>>singletonList(
						new Generator<String>()
						{
							@Override
							public String value ()
							{
								return e.assertionString().asNativeString();
							}
						}));
			}
			if (!state.value.atEnd())
			{
				final AvailObject token = tokens.get(state.value.position - 1);
				progressBlock.value(
					qualifiedName,
					(long) token.lineNumber(),
					(long) token.start() + 2,
					sourceLength);
			}
		}
		assert state.value.atEnd();
		if (interpreter.unresolvedForwards().setSize() != 0)
		{
			final Formatter formatter = new Formatter();
			formatter.format("the following forwards to be resolved:");
			for (final AvailObject forward : interpreter.unresolvedForwards())
			{
				formatter.format("%n\t%s", forward);
			}
			state.value.expected(formatter.toString());
			formatter.close();
			reportError(qualifiedName);
		}
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} header for the specified
	 * {@linkplain ModuleName module name}. Populate {@link
	 * ModuleHeader#extendedModules} and {@link ModuleHeader#usedModules}.
	 *
	 * @param qualifiedName
	 *        The expected module name.
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	public void parseModuleHeader (final ModuleName qualifiedName)
		throws AvailCompilerException
	{
		progressBlock = null;
		greatestGuess = -1;
		greatExpectations.clear();
		final ResolvedModuleName resolvedName =
			interpreter.runtime().moduleNameResolver().resolve(qualifiedName);
		if (resolvedName == null)
		{
			throw new AvailCompilerException(
				qualifiedName,
				0,
				0,
				"Unable to resolve fully-qualified module name \""
					+ qualifiedName.qualifiedName()
					+ "\" to an existing file");
		}
		if (parseModuleHeader(resolvedName, true) == null)
		{
			reportError(resolvedName);
			assert false;
		}
	}

	/**
	 * Parse the header of the module from the token stream. If successful,
	 * return the {@link ParserState} just after the header, otherwise return
	 * {@code null}.
	 *
	 * <p>If the {@code dependenciesOnly} parameter is true, only parse the bare
	 * minimum needed to determine information about which modules are used by
	 * this one.</p>
	 *
	 * @param qualifiedName
	 *        The expected module name.
	 * @param dependenciesOnly
	 *        Whether to do the bare minimum parsing required to determine
	 *        the modules to which this one refers.
	 * @return The state of parsing just after the header, or {@code null} if it
	 *         failed.
	 */
	private @Nullable ParserState parseModuleHeader (
		final ResolvedModuleName qualifiedName,
		final boolean dependenciesOnly)
	{
		assert workPool.isEmpty();

		// Create the initial parser state: no tokens have been seen, no names
		// are in scope, and we're not part-way through parsing a block.
		ParserState state = new ParserState(
			0,
			MapDescriptor.empty().mapAtPuttingCanDestroy(
				AtomDescriptor.compilerScopeMapKey(),
				MapDescriptor.empty(),
				false));

		// The module header must begin with either SYSTEM MODULE or MODULE,
		// followed by the local name of the module.
		if (isSystemCompiler())
		{
			if (!state.peekToken(SYSTEM, "System keyword"))
			{
				return null;
			}
			state = state.afterToken();
		}
		if (!state.peekToken(ExpectedToken.MODULE, "Module keyword"))
		{
			return null;
		}
		state = state.afterToken();
		final AvailObject localNameToken = state.peekStringLiteral();
		if (localNameToken == null)
		{
			state.expected("module name");
			return null;
		}
		if (!dependenciesOnly)
		{
			final AvailObject localName = localNameToken.literal();
			if (!qualifiedName.localName().equals(localName.asNativeString()))
			{
				state.expected("declared local module name to agree with "
						+ "fully-qualified module name");
				return null;
			}
		}
		state = state.afterToken();

		// Module header section tracking.
		final List<ExpectedToken> expected = new ArrayList<ExpectedToken>(
			Arrays.<ExpectedToken>asList(
				VERSIONS, EXTENDS, USES, NAMES, PRAGMA, BODY));
		final Set<AvailObject> seen = new HashSet<AvailObject>(6);
		final Generator<String> expectedMessage = new Generator<String>()
		{
			@Override
			public String value ()
			{
				final StringBuilder builder = new StringBuilder();
				builder.append(
					expected.size() == 1
					? "module header keyword "
					: "one of the following module header keywords: ");
				boolean first = true;
				for (final ExpectedToken token : expected)
				{
					if (!first)
					{
						builder.append(", ");
					}
					builder.append(token.lexeme().asNativeString());
					first = false;
				}
				return builder.toString();
			}
		};

		// Permit the other sections to appear optionally, singly, and in any
		// order. Parsing of the module header is complete when BODY has been
		// consumed.
		while (true)
		{
			final AvailObject token = state.peekToken();
			final AvailObject lexeme = token.string();
			int tokenIndex = 0;
			for (final ExpectedToken expectedToken : expected)
			{
				if (expectedToken.tokenType() == token.tokenType()
					&& expectedToken.lexeme().equals(lexeme))
				{
					break;
				}
				tokenIndex++;
			}
			// The token was not recognized as beginning a module section, so
			// record what was expected and fail the parse.
			if (tokenIndex == expected.size())
			{
				if (seen.contains(lexeme))
				{
					state.expected(
						lexeme.asNativeString()
						+ " keyword (and related section) to occur only once");
				}
				else
				{
					state.expected(expectedMessage);
				}
				return null;
			}
			expected.remove(tokenIndex);
			seen.add(lexeme);
			state = state.afterToken();
			// When BODY has been encountered, the parse of the module header is
			// complete.
			if (lexeme.equals(BODY.lexeme()))
			{
				return state;
			}
			// On VERSIONS, record the versions.
			else if (lexeme.equals(VERSIONS.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader.versions);
			}
			// On EXTENDS, record the imports.
			else if (lexeme.equals(EXTENDS.lexeme()))
			{
				state = parseImports(state, moduleHeader.extendedModules);
			}
			// On USES, record the imports.
			else if (lexeme.equals(USES.lexeme()))
			{
				state = parseImports(state, moduleHeader.usedModules);
			}
			// On NAMES, record the names.
			else if (lexeme.equals(NAMES.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader.exportedNames);
			}
			// On PRAGMA, record the pragma strings.
			else if (lexeme.equals(PRAGMA.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader.pragmas);
			}
			// If the parser state is now null, then fail the parse.
			if (state == null)
			{
				return null;
			}
		}
	}

	/**
	 * Parse an expression. Backtracking will find all valid interpretations.
	 * This method is a key optimization point, so the fragmentCache is used to
	 * keep track of parsing solutions at this point, simply replaying them on
	 * subsequent parses, as long as the variable declarations up to that point
	 * were identical.
	 *
	 * <p>
	 * Additionally, the fragmentCache also keeps track of actions to perform
	 * when another solution is found at this position, so the solutions and
	 * actions can be added in arbitrary order while ensuring that each action
	 * gets a chance to try each solution.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param originalContinuation
	 *        What to do with the expression.
	 */
	void parseExpressionThen (
		final ParserState start,
		final Con<AvailObject> originalContinuation)
	{
		synchronized (fragmentCache)
		{
			// The first time we parse at this position the fragmentCache will
			// have no knowledge about it.
			if (!fragmentCache.hasStartedParsingAt(start))
			{
				fragmentCache.indicateParsingHasStartedAt(start);
				eventuallyDo(
					new Continuation0()
					{
						@Override
						public void value ()
						{
							parseExpressionUncachedThen(
								start,
								new Con<AvailObject>("Uncached expression")
								{
									@Override
									public void value (
										final @Nullable ParserState afterExpr,
										final @Nullable AvailObject expr)
									{
										assert afterExpr != null;
										assert expr != null;
										synchronized (fragmentCache)
										{
											fragmentCache.addSolution(
												start,
												new AvailCompilerCachedSolution(
													afterExpr,
													expr));
										}
									}
								});
						}
					},
					"Capture expression for caching",
					start.position);
			}
			fragmentCache.addAction(start, originalContinuation);
		}
	}

	/**
	 * Parse an expression whose type is (at least) someType. There may be
	 * multiple expressions that start at the specified starting point.  Only
	 * evaluate expressions whose static type is as strong as the expected type.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param someType
	 *        The type that the expression must return.
	 * @param continuation
	 *        What to do with the result of expression evaluation.
	 */
	void parseAndEvaluateExpressionYieldingInstanceOfThen (
		final ParserState start,
		final AvailObject someType,
		final Con<AvailObject> continuation)
	{
		final AvailObject clientDataInModuleScope =
			start.clientDataMap.mapAtPuttingCanDestroy(
				AtomDescriptor.compilerScopeMapKey(),
				MapDescriptor.empty(),
				false);
		final ParserState startInModuleScope = new ParserState(
			start.position,
			clientDataInModuleScope);
		parseExpressionThen(startInModuleScope, new Con<AvailObject>(
			"Evaluate expression")
		{
			@SuppressWarnings("null")
			@Override
			public void value (
				final @Nullable ParserState afterExpression,
				final @Nullable AvailObject expression)
			{
				if (expression.expressionType().isSubtypeOf(someType))
				{
					// A unique, longest type-correct expression was found.
					final AvailObject value = evaluate(
						expression,
						start.peekToken().lineNumber());
					if (value.isInstanceOf(someType))
					{
						assert afterExpression.clientDataMap.equals(
							startInModuleScope.clientDataMap)
						: "Subexpression should not have been able "
							+ "to cause declaration";
						// Make sure we continue at the position after the
						// expression, but using the scope stack we started
						// with.  That's because the expression was parsed for
						// execution, and as such was excluded from seeing
						// things that would be in scope for regular
						// subexpressions at this point.
						attempt(
							new ParserState(
								afterExpression.position,
								start.clientDataMap),
							continuation,
							value);
					}
					else
					{
						afterExpression.expected(
							"expression to respect its own type declaration");
					}
				}
				else
				{
					afterExpression.expected(
						new Generator<String>()
						{
							@Override
							public String value ()
							{
								return "expression to have type " + someType;
							}
						});
				}
			}
		});
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar (it used to be that <em>all</em> statements had to
	 * be unambiguous, even those in blocks).  The passed continuation will be
	 * invoked at most once, and only if the top-level statement had a single
	 * interpretation.
	 *
	 * <p>
	 * The {@link #workPool} should be empty when invoking this method, as it
	 * will be drained by this method.
	 * </p>
	 *
	 * @param start
	 *            Where to start parsing a top-level statement.
	 * @param continuation
	 *            What to do with the (unambiguous) top-level statement.
	 */
	abstract void parseOutermostStatement (
		final ParserState start,
		final Con<AvailObject> continuation);

	/**
	 * Parse an expression, without directly using the
	 * {@linkplain #fragmentCache}.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the expression.
	 */
	abstract void parseExpressionUncachedThen (
		final ParserState start,
		final Con<AvailObject> continuation);

	/**
	 * Parse and return an occurrence of a raw keyword, literal, or operator
	 * token.  If no suitable token is present, answer null.  The caller is
	 * responsible for skipping the token if it was parsed.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @return
	 *            The token or {@code null}.
	 */
	protected @Nullable AvailObject parseRawTokenOrNull (
		final ParserState start)
	{
		final AvailObject token = start.peekToken();
		switch (token.tokenType())
		{
			case KEYWORD:
			case OPERATOR:
			case LITERAL:
				return token;
			default:
				return null;
		}
	}
}

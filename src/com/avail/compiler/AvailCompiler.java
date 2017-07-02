/**
 * AvailCompiler.java
 * Copyright © 1993-2017, The Avail Foundation, LLC. All rights reserved.
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

import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.AvailThread;
import com.avail.annotations.InnerAccess;
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.exceptions.MalformedPragmaException;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.evaluation.*;
import org.jetbrains.annotations.Nullable;
import com.avail.builder.ModuleName;
import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.compiler.scanning.AvailScannerException;
import com.avail.compiler.scanning.AvailScannerResult;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.dispatch.LookupTree;
import com.avail.exceptions.AvailAssertionFailedException;
import com.avail.exceptions.AvailEmergencyExitException;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.primitive.phrases.P_RejectParsing;
import com.avail.io.TextInterface;
import com.avail.utility.Generator;
import com.avail.utility.Mutable;
import com.avail.utility.MutableOrNull;
import com.avail.utility.Pair;
import com.avail.utility.PrefixSharingList;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.avail.compiler.ExpectedToken.*;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.problems.ProblemType.*;
import static com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import static com.avail.descriptor.AtomDescriptor.*;
import static com.avail.descriptor.LiteralNodeDescriptor.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION;
import static com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION;
import static com.avail.interpreter.AvailLoader.Phase.*;
import static com.avail.utility.PrefixSharingList.*;
import static com.avail.utility.StackPrinter.trace;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.asList;

/**
 * The compiler for Avail code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailCompiler
{
	public final CompilationContext compilationContext;

	/**
	 * The {@link AvailRuntime} for the compiler. Since a compiler cannot
	 * migrate between two runtime environments, it is safe to cache it for
	 * efficient access.
	 */
	@InnerAccess
	final AvailRuntime runtime = AvailRuntime.current();

	public String source ()
	{
		return compilationContext.source();
	}

	/**
	 * The {@linkplain AvailCompiler compiler} notifies a {@code
	 * CompilerProgressReporter} whenever a top-level statement is parsed
	 * unambiguously.
	 *
	 * <p>The {@link #value(Object, Object, Object)} method takes the module
	 * name, the module size in bytes, and the current parse position within the
	 * module.</p>
	 */
	public interface CompilerProgressReporter
	extends Continuation3<ModuleName, Long, Long>
	{
		// nothing
	}

	/**
	 * Answer the {@linkplain ModuleHeader module header} for the current
	 * {@linkplain ModuleDescriptor module} being parsed.
	 *
	 * @return the moduleHeader
	 */
	@InnerAccess ModuleHeader moduleHeader ()
	{
		final ModuleHeader header = compilationContext.getModuleHeader();
		assert header != null;
		return header;
	}

	/**
	 * Answer the fully-qualified name of the {@linkplain ModuleDescriptor
	 * module} undergoing compilation.
	 *
	 * @return The module name.
	 */
	@InnerAccess ModuleName moduleName ()
	{
		return new ModuleName(
			compilationContext.module().moduleName().asNativeString());
	}

	/**
	 * The complete {@linkplain A_Tuple tuple} of {@linkplain A_Token tokens}
	 * extracted from the source text.
	 */
	@InnerAccess final A_Tuple tokensTuple;

	/**
	 * The complete {@linkplain List list} of {@linkplain CommentTokenDescriptor
	 * comment tokens} extracted from the source text.
	 */
	@InnerAccess final List<A_Token> commentTokens;

	/** The memoization of results of previous parsing attempts. */
	@InnerAccess final AvailCompilerFragmentCache fragmentCache =
		new AvailCompilerFragmentCache();

	/**
	 * Asynchronously construct a suitable {@linkplain AvailCompiler
	 * compiler} to parse the specified {@linkplain ModuleName module name}.
	 *
	 * @param resolvedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the
	 *        {@linkplain ModuleDescriptor module} to compile.
	 * @param stopAfterBodyToken
	 *        Whether to stop parsing at the occurrence of the BODY token. This
	 *        is an optimization for faster build analysis.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for any {@linkplain
	 *        A_Fiber fibers} started by the new compiler.
	 * @param pollForAbort
	 *        A zero-argument continuation to invoke
	 * @param succeed
	 *        What to do with the resultant compiler in the event of success.
	 *        This is a continuation that accepts the new compiler.
	 * @param afterFail
	 *        What to do after a failure that the {@linkplain ProblemHandler
	 *        problem handler} does not choose to continue.
	 * @param problemHandler
	 *        A problem handler.
	 */
	public static void create (
		final ResolvedModuleName resolvedName,
		final boolean stopAfterBodyToken,
		final TextInterface textInterface,
		final Generator<Boolean> pollForAbort,
		final CompilerProgressReporter reporter,
		final Continuation1<AvailCompiler> succeed,
		final Continuation0 afterFail,
		final ProblemHandler problemHandler)
	{
		extractSourceThen(
			resolvedName,
			new Continuation1<String>()
			{
				@Override
				public void value (final @Nullable String sourceText)
				{
					assert sourceText != null;
					final AvailScannerResult result;
					try
					{
						result = tokenize(
							sourceText,
							resolvedName.qualifiedName(),
							stopAfterBodyToken);
					}
					catch (final AvailScannerException e)
					{
						final Problem problem = new Problem(
							resolvedName,
							e.failureLineNumber(),
							e.failurePosition(),
							PARSE,
							"Scanner error: {0}",
							e.getMessage())
						{
							@Override
							public void abortCompilation ()
							{
								afterFail.value();
							}
						};
						problemHandler.handle(problem);
						return;
					}
					final AvailCompiler compiler = new AvailCompiler(
						new ModuleHeader(resolvedName),
						ModuleDescriptor.newModule(
							StringDescriptor.from(
								resolvedName.qualifiedName())),
						result,
						textInterface,
						pollForAbort,
						reporter,
						problemHandler);
					succeed.value(compiler);
				}
			},
			afterFail,
			problemHandler);
	}

	/**
	 * Construct a new {@link AvailCompiler}.
	 *
	 * @param moduleHeader
	 *        The {@link ModuleHeader module header} of the module to compile.
	 *        May be null for synthetic modules (for entry points), or when
	 *        parsing the header.
	 * @param module
	 *        The current {@linkplain ModuleDescriptor module}.
	 * @param scannerResult
	 *        An {@link AvailScannerResult}.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for any {@linkplain
	 *        A_Fiber fibers} started by this compiler.
	 * @param pollForAbort
	 *        How to quickly check if the client wants to abort compilation.
	 * @param progressReporter
	 *        How to report progress to the client who instigated compilation.
	 *        This {@linkplain CompilerProgressReporter continuation} that
	 *        accepts the {@linkplain ModuleName name} of the {@linkplain
	 *        ModuleDescriptor module} undergoing {@linkplain
	 *        AvailCompiler compilation}, the line number on which the
	 *        last complete statement concluded, the position of the ongoing
	 *        parse (in bytes), and the size of the module (in bytes).
	 * @param problemHandler
	 *        The {@link ProblemHandler} used for reporting compilation
	 *        problems.
	 */
	public AvailCompiler (
		final @Nullable ModuleHeader moduleHeader,
		final A_Module module,
		final AvailScannerResult scannerResult,
		final TextInterface textInterface,
		final Generator<Boolean> pollForAbort,
		final CompilerProgressReporter progressReporter,
		final ProblemHandler problemHandler)
	{
		this.compilationContext = new CompilationContext(
			moduleHeader,
			module,
			scannerResult.source(),
			textInterface,
			pollForAbort,
			progressReporter,
			problemHandler);
		final List<A_Token> tokens = scannerResult.outputTokens();
		this.tokensTuple = TupleDescriptor.fromList(tokens).makeShared();
		this.commentTokens = scannerResult.commentTokens();
	}

	/**
	 * A list of subexpressions being parsed, represented by {@link
	 * A_BundleTree}s holding the positions within all outer send expressions.
	 */
	@InnerAccess static class PartialSubexpressionList
	{
		/** The {@link A_BundleTree} being parsed at this node. */
		@InnerAccess final A_BundleTree bundleTree;

		/** The parent {@link PartialSubexpressionList} being parsed. */
		@InnerAccess final @Nullable PartialSubexpressionList parent;

		/** How many subexpressions deep that we're parsing. */
		@InnerAccess final int depth;

		/**
		 * Create a list like the receiver, but with a different {@link
		 * A_BundleTree}.
		 *
		 * @param newBundleTree
		 *        The new {@link A_BundleTree} to replace the one in the
		 *        receiver within the copy.
		 * @return A {@link PartialSubexpressionList} like the receiver, but
		 *         with a different message bundle tree.
		 */
		@InnerAccess PartialSubexpressionList advancedTo (
			final A_BundleTree newBundleTree)
		{
			return new PartialSubexpressionList(newBundleTree, parent);
		}

		/**
		 * Construct a new {@link AvailCompiler.PartialSubexpressionList}.
		 *
		 * @param bundleTree
		 *        The current {@link A_BundleTree} being parsed.
		 * @param parent
		 *        The enclosing partially-parsed super-expressions being parsed.
		 */
		@InnerAccess PartialSubexpressionList (
			final A_BundleTree bundleTree,
			final @Nullable PartialSubexpressionList parent)
		{
			this.bundleTree = bundleTree;
			this.parent = parent;
			this.depth = parent == null ? 1 : parent.depth + 1;
		}
	}

	/**
	 * Output a description of the layers of message sends that are being parsed
	 * at this point in history.
	 *
	 * @param partialSubexpressions
	 *        The {@link PartialSubexpressionList} that captured the nesting of
	 *        partially parsed superexpressions.
	 * @param builder
	 *        Where to describe the chain of superexpressions.
	 */
	@InnerAccess static void describeOn (
		final @Nullable PartialSubexpressionList partialSubexpressions,
		final StringBuilder builder)
	{
		PartialSubexpressionList pointer = partialSubexpressions;
		if (pointer == null)
		{
			builder.append("\n\t(top level expression)");
			return;
		}
		final int maxDepth = 10;
		final int limit = max(pointer.depth - maxDepth, 0);
		while (pointer != null && pointer.depth >= limit)
		{
			builder.append("\n\t");
			builder.append(pointer.depth);
			builder.append(". ");
			final A_BundleTree bundleTree = pointer.bundleTree;
			// Reduce to the plans' unique bundles.
			final A_Map bundlesMap = bundleTree.allParsingPlansInProgress();
			final List<A_Bundle> bundles =
				TupleDescriptor.toList(bundlesMap.keysAsSet().asTuple());
			Collections.sort(
				bundles,
				new Comparator<A_Bundle>()
				{
					@Override
					public int compare (
						final @Nullable A_Bundle b1,
						final @Nullable A_Bundle b2)
					{
						assert b1 != null && b2 != null;
						return b1.message().atomName().asNativeString()
							.compareTo(
								b2.message().atomName().asNativeString());
					}
				});
			boolean first = true;
			final int maxBundles = 3;
			for (final A_Bundle bundle :
				bundles.subList(0, min(bundles.size(), maxBundles)))
			{
				if (!first)
				{
					builder.append(", ");
				}
				final A_Map plans = bundlesMap.mapAt(bundle);
				// Pick an active plan arbitrarily for this bundle.
				final A_Set plansInProgress =
					plans.mapIterable().next().value();
				final A_ParsingPlanInProgress planInProgress =
					plansInProgress.iterator().next();
				// Adjust the pc to refer to the actual instruction that caused
				// the argument parse, not the successor instruction that was
				// captured.
				final A_ParsingPlanInProgress adjustedPlanInProgress =
					ParsingPlanInProgressDescriptor.create(
						planInProgress.parsingPlan(),
						planInProgress.parsingPc() - 1);
				builder.append(adjustedPlanInProgress.nameHighlightingPc());
				first = false;
			}
			if (bundles.size() > maxBundles)
			{
				builder.append("… (and ");
				builder.append(bundles.size() - maxBundles);
				builder.append(" others)");
			}
			pointer = pointer.parent;
		}
	}

	/**
	 * A continuation that takes a {@link Solution} or {@code null}.  It also
	 * captures a {@link PartialSubexpressionList} for explaining the chain of
	 * parsing needs that led to this solution.
	 *
	 * @param <Solution>
	 *        The kind of {@link AbstractSolution} that this accepts.
	 */
	@InnerAccess abstract class ConNullable<Solution extends AbstractSolution>
	implements Continuation1<Solution>
	{
		/**
		 * A {@link PartialSubexpressionList} containing all enclosing
		 * incomplete expressions currently being parsed along this history.
		 */
		@InnerAccess final @Nullable PartialSubexpressionList superexpressions;

		/**
		 * Ensure this is not a root {@link PartialSubexpressionList} (i.e., its
		 * {@link #superexpressions} list is not {@code null}), then answer
		 * the parent list.
		 *
		 * @return The (non-null) parent superexpressions list.
		 */
		@InnerAccess final PartialSubexpressionList superexpressions ()
		{
			final PartialSubexpressionList parent = superexpressions;
			assert parent != null;
			return parent;
		}

		/**
		 * Construct a new {@link ConNullable}.
		 *
		 * @param superexpressions
		 *        The enclosing partially-parsed expressions.
		 */
		@InnerAccess ConNullable (
			final @Nullable PartialSubexpressionList superexpressions)
		{
			this.superexpressions = superexpressions;
		}

		@Override
		public abstract void value (
			@Nullable Solution solution);
	}

	/**
	 * This is a subtype of {@link ConNullable}, but it has an abstract
	 * valueNotNull method that subclasses must define (and the value method
	 * is declared final).  If null checking was part of Java's type system
	 * (where it belongs) we wouldn't have to build these kludges just to factor
	 * out the null safety boilerplate code.
	 *
	 * @param <Solution> The kind of solution to feed to this continuation.
	 */
	@InnerAccess abstract class Con<Solution extends AbstractSolution>
	extends ConNullable<Solution>
	{
		/**
		 * Construct a new {@link Con}.
		 *
		 * @param superexpressions
		 *        The enclosing partially-parsed expressions.
		 */
		@InnerAccess Con (
			final @Nullable PartialSubexpressionList superexpressions)
		{
			super(superexpressions);
		}

		/**
		 * The method that will be invoked when a value has been produced.
		 * Neither the value nor the ParserState may be null.
		 *
		 * @param solution The solution that was produced.
		 */
		@InnerAccess abstract void valueNotNull (
			Solution solution);

		@Override
		public final void value (
			final @Nullable Solution solution)
		{
			assert solution != null;
			valueNotNull(solution);
		}
	}

	/**
	 * Execute {@code #tryBlock}, passing a {@linkplain Con continuation} that
	 * it should run upon finding exactly one local {@linkplain CompilerSolution
	 * solution}.  Report ambiguity as an error.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param tryBlock
	 *        What to try. This is a continuation that accepts a continuation
	 *        that tracks completion of parsing.
	 * @param supplyAnswer
	 *        What to do if exactly one result was produced. This is a
	 *        continuation that accepts a solution.
	 * @param afterFail
	 *        What to do after a failure has been reported.
	 */
	private void tryIfUnambiguousThen (
		final ParserState start,
		final Continuation1<Con<CompilerSolution>> tryBlock,
		final Con<CompilerSolution> supplyAnswer,
		final Continuation0 afterFail)
	{
		assert compilationContext.getNoMoreWorkUnits() == null;
		// Augment the start position with a variant that incorporates the
		// solution-accepting continuation.
		final Mutable<Integer> count = new Mutable<>(0);
		final MutableOrNull<A_Phrase> solution = new MutableOrNull<>();
		final MutableOrNull<ParserState> afterStatement = new MutableOrNull<>();
		compilationContext.setNoMoreWorkUnits(new Continuation0()
		{
			@Override
			public void value ()
			{
				// The counters must be read in this order for correctness.
				final long completed =
					compilationContext.getWorkUnitsCompleted().get();
				assert completed
					== compilationContext.getWorkUnitsQueued().get();
				if (compilationContext.diagnostics.pollForAbort.value())
				{
					// We may have been asked to abort subtasks by a failure in
					// another module, so we can't trust the count of solutions.
					afterFail.value();
					return;
				}
				// Ambiguity is detected and reported during the parse, and
				// should never be identified here.
				if (count.value == 0)
				{
					// No solutions were found.  Report the problems.
					compilationContext.diagnostics.reportError(afterFail);
					return;
				}
				// If a simple unambiguous solution was found, then answer
				// it forward to the continuation.
				if (count.value == 1)
				{
					assert solution.value != null;
					supplyAnswer.value(
						new CompilerSolution(
							afterStatement.value,
							solution.value));
				}
				// Otherwise an ambiguity was already reported when the second
				// solution arrived (and subsequent solutions may have arrived
				// and been ignored).  Do nothing.
			}
		});
		compilationContext.attempt(
			start,
			tryBlock,
			new Con<CompilerSolution>(supplyAnswer.superexpressions)
			{
				@Override
				public void valueNotNull (
					final CompilerSolution aSolution)
				{
					synchronized (AvailCompiler.this)
					{
						// It may look like we could hoist all but the increment
						// and capture of the count out of the synchronized
						// section, but then there wouldn't be a write fence
						// after recording the first solution.
						count.value++;
						final int myCount = count.value;
						if (myCount == 1)
						{
							// Save the first solution to arrive.
							afterStatement.value = aSolution.endState();
							solution.value = aSolution.parseNode();
							return;
						}
						if (myCount == 2)
						{
							// We are exactly the second solution to arrive and
							// to increment count.value.  Report the ambiguity
							// between the previously recorded solution and this
							// one.
							final A_Token endToken = tokensTuple.tupleAt(
								max(1, aSolution.endState().tokenIndex));
							reportAmbiguousInterpretations(
								new ParserState(
									endToken.start(),
									endToken.tokenIndex(),
									aSolution.endState.clientDataMap),
								solution.value(),
								aSolution.parseNode(),
								afterFail);
							return;
						}
						// We're at a third or subsequent solution.  Ignore it
						// since we reported the ambiguity when the second
						// solution was reached.
						assert myCount > 2;
					}
				}
			});
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
		 * The position represented by this {@link ParserState}.  In particular,
		 * it's the (one-based) start of the current token within the source.
		 */
		final int position;

		/**
		 * TEMPORARY COMPATIBILITY - The compiler is being reworked to allow
		 * incremental lexical scanning, including ambiguous scanning.  This
		 * will produce a (lazy) directed acyclic graph of tokens, so numbering
		 * them will clearly not make sense.
		 */
		final int tokenIndex;

		/**
		 * A {@linkplain MapDescriptor map} of interesting information used by
		 * the compiler.
		 */
		public final A_Map clientDataMap;

		/**
		 * Construct a new immutable {@link ParserState}.
		 *
		 * @param position
		 *        The one-based start of the current token in the source.
		 * @param clientDataMap
		 *        The {@link MapDescriptor map} of data used by macros while
		 *        parsing Avail code.
		 */
		ParserState (
			final int position,
			final int tokenIndex,  //TODO - TEMPORARY COMPATIBILITY
			final A_Map clientDataMap)
		{
			this.position = position;
			this.tokenIndex = tokenIndex;
			// Note that this map *must* be marked as shared, since parsing
			// proceeds in parallel.
			this.clientDataMap = clientDataMap.makeShared();
		}

		@Override
		public int hashCode ()
		{
			return position * AvailObject.multiplier ^ clientDataMap.hash();
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
				&& tokenIndex == anotherState.tokenIndex
				&& clientDataMap.equals(anotherState.clientDataMap);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"%s%n\tPOSITION = %d%n\tTOKENS = %s %s %s%n\tCLIENT_DATA = %s",
				getClass().getSimpleName(),
				position,
				source().substring(
					max(position - 20, 0),
					max(position - 1, 0)),
				CompilerDiagnostics.errorIndicatorSymbol,
				source().substring(
					min(position - 1, source().length()),
					min(position + 20, source().length())),
				clientDataMap);
		}

		public String shortString ()
		{
			final A_Token token = tokensTuple.tupleAt(position);
			String string = position > 0
				? token.string().asNativeString()
				: "(start)";
			if (string.length() > 20)
			{
				string = string.substring(0, 10) + " … "
					+ string.substring(string.length() - 10);
			}
			return token.lineNumber() + "(" + string + ")";
		}

		/**
		 * Determine if this state represents the end of the file. If so, one
		 * should not invoke {@link #peekToken()} or {@link #afterToken()}
		 * again.
		 *
		 * @return Whether this state represents the end of the file.
		 */
		public boolean atEnd ()
		{
			// Last token is a special end-of-file token.
			return this.tokenIndex == tokensTuple.tupleSize();
//			return this.position == source().length() + 1;
		}

		/**
		 * Answer the {@linkplain TokenDescriptor token} at the current
		 * position.
		 *
		 * @return The token.
		 */
		public A_Token peekToken ()
		{
			return tokensTuple.tupleAt(tokenIndex);
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
			final A_Token token = peekToken();
			return token.tokenType() == expectedToken.tokenType()
				&& token.string().equals(expectedToken.lexeme());
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @param expected
		 *        A {@linkplain Describer describer} of a message to record if
		 *        the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final ExpectedToken expectedToken,
			final Describer expected)
		{
			final A_Token token = peekToken();
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
			return peekToken(expectedToken, new SimpleDescriber(expected));
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
			final A_Token nextToken = tokensTuple.tupleAt(tokenIndex + 1);
			return new ParserState(
				nextToken.start(),
				nextToken.tokenIndex(),
				clientDataMap);
		}

		/**
		 * Parse a string literal. Answer the {@linkplain LiteralTokenDescriptor
		 * string literal token} if found, otherwise answer {@code null}.
		 *
		 * @return The actual {@linkplain LiteralTokenDescriptor literal token}
		 *         or {@code null}.
		 */
		@Nullable A_Token peekStringLiteral ()
		{
			final A_Token token = peekToken();
			if (token.isInstanceOfKind(
				LiteralTokenTypeDescriptor.create(
					TupleTypeDescriptor.stringType())))
			{
				return token;
			}
			return null;
		}

		/**
		 * Answer a {@linkplain A_Tuple tuple} that comprises the {@linkplain
		 * A_Token tokens} from the receiver's position, inclusive, to the
		 * argument's position, exclusive.
		 *
		 * @param after
		 *        The {@linkplain A_Token token} strictly after the last token
		 *        that should appear in the resultant {@linkplain A_Tuple
		 *        tuple}.
		 * @return The requested {@linkplain A_Tuple tuple}.
		 */
		A_Tuple upTo (final ParserState after)
		{
			return tokensTuple.copyTupleFromToCanDestroy(
				tokenIndex,
				after.tokenIndex - 1,
				false);
		}

		/**
		 * Record an expectation at the current parse position. The expectations
		 * captured at the rightmost few reached parse positions constitute the
		 * error message in case the parse fails.
		 *
		 * <p>
		 * The expectation is a {@linkplain Describer}, in case constructing a
		 * {@link String} frivolously would be prohibitive. There is also {@link
		 * #expected(String) another} version of this method that accepts a
		 * String directly.
		 * </p>
		 *
		 * @param describer
		 *        The {@code describer} to capture.
		 */
		void expected (final Describer describer)
		{
			compilationContext.diagnostics.expectedAt(
				describer, tokensTuple.tupleAt(tokenIndex));
		}

		/**
		 * Record an expectation at the current parse position. The expectations
		 * captured at the rightmost parse position constitute the error message
		 * in case the parse fails.
		 *
		 * @param values
		 *        A list of arbitrary {@linkplain AvailObject Avail values} that
		 *        should be stringified.
		 * @param transformer
		 *        A {@linkplain Transformer1 transformer} that accepts the
		 *        stringified values and answers an expectation message.
		 */
		void expected (
			final List<? extends A_BasicObject> values,
			final Transformer1<List<String>, String> transformer)
		{
			expected(new Describer()
			{
				@Override
				public void describeThen (
					final Continuation1<String> continuation)
				{
					Interpreter.stringifyThen(
						runtime,
						compilationContext.getTextInterface(),
						values,
						new Continuation1<List<String>>()
						{
							@Override
							public void value (
								final @Nullable List<String> list)
							{
								continuation.value(transformer.value(list));
							}
						});
				}
			});
		}

		/**
		 * Record an indication of what was expected at this parse position.
		 *
		 * @param aString
		 *        The string describing something that was expected at this
		 *        position under some interpretation so far.
		 */
		void expected (final String aString)
		{
			expected(new SimpleDescriber(aString));
		}

		/**
		 * Evaluate the given parse node.  Pass the result to the continuation,
		 * or if it fails pass the exception to the failure continuation.
		 *
		 * @param expression
		 *        The parse node to evaluate.
		 * @param continuation
		 *        What to do with the result of evaluation.
		 * @param onFailure
		 *        What to do after a failure.
		 */
		void evaluatePhraseThen (
			final A_Phrase expression,
			final Continuation1<AvailObject> continuation,
			final Continuation1<Throwable> onFailure)
		{
			final AvailCompiler compiler = AvailCompiler.this;
			compilationContext.startWorkUnit();
			final AtomicBoolean hasRunEither = new AtomicBoolean(false);
			compiler.evaluatePhraseThen(
				expression,
				peekToken().lineNumber(),
				false,
				compiler.workUnitCompletion(
					peekToken(), hasRunEither, continuation),
				compiler.workUnitCompletion(
					peekToken(), hasRunEither, onFailure));
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
	 * null. Populate the passed {@link List} with the {@linkplain
	 * StringDescriptor strings}.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param strings
	 *        The initially empty list of strings to populate.
	 * @return The parser state after the list of strings, or {@code null} if
	 *         the list of strings is malformed.
	 */
	private static @Nullable ParserState parseStringLiterals (
		final ParserState start,
		final Collection<A_String> strings)
	{
		assert strings.isEmpty();

		ParserState state = start.afterToken();
		A_Token token = start.peekStringLiteral();
		if (token == null)
		{
			return start;
		}
		while (true)
		{
			// We just read a string literal.
			final A_String string = token.literal();
			if (strings.contains(string))
			{
				state.expected("a distinct name, not another occurrence of "
					+ string);
				return null;
			}
			strings.add(string);
			if (!state.peekToken(COMMA))
			{
				return state;
			}
			state = state.afterToken();
			token = state.peekStringLiteral();
			if (token == null)
			{
				state.expected("another string literal after comma");
				return null;
			}
			state = state.afterToken();
		}
	}

	/**
	 * Parse one or more string literal tokens separated by commas. This parse
	 * isn't backtracking like the rest of the grammar - it's greedy. It
	 * considers a comma followed by something other than a string literal to be
	 * an unrecoverable parsing error (not a backtrack).
	 *
	 * <p>
	 * Return the {@link ParserState} after the strings if successful, otherwise
	 * null. Populate the passed {@link List} with the {@linkplain
	 * LiteralTokenDescriptor string literal tokens}.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param stringLiteralTokens
	 *        The initially empty list of string literals to populate.
	 * @return The parser state after the list of strings, or {@code null} if
	 *         the list of strings is malformed.
	 */
	private static @Nullable ParserState parseStringLiteralTokens (
		final ParserState start,
		final List<A_Token> stringLiteralTokens)
	{
		assert stringLiteralTokens.isEmpty();

		ParserState state = start.afterToken();
		A_Token token = start.peekStringLiteral();
		if (token == null)
		{
			return start;
		}
		while (true)
		{
			// We just read a string literal.
			stringLiteralTokens.add(token);
			if (!state.peekToken(COMMA))
			{
				return state;
			}
			state = state.afterToken();
			token = state.peekStringLiteral();
			if (token == null)
			{
				state.expected("another string literal after comma");
				return null;
			}
			state = state.afterToken();
		}
	}

	/**
	 * Parse one or more string literals separated by commas. This parse isn't
	 * backtracking like the rest of the grammar - it's greedy. It considers a
	 * comma followed by something other than a string literal or final ellipsis
	 * to be an unrecoverable parsing error (not a backtrack). A {@linkplain
	 * ExpectedToken#RIGHT_ARROW right arrow} between two strings indicates a
	 * rename.  An ellipsis at the end (the comma before it is optional)
	 * indicates that all names not explicitly mentioned should be imported.
	 *
	 * <p>
	 * Return the {@link ParserState} after the strings if successful, otherwise
	 * null. Populate the passed {@link Mutable} structures with strings,
	 * string → string entries producing a <em>reverse</em> map for renaming,
	 * negated strings, i.e., with a '-' character prefixed to indicate
	 * exclusion, and an optional trailing ellipsis character (the comma before
	 * it is optional).
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param names
	 *        The names that are mentioned explicitly, like "x".
	 * @param renames
	 *        The renames that are provided explicitly in the form "x"→"y".  In
	 *        such a case the new key is "y" and its associated value is "x".
	 * @param excludes
	 *        Names to exclude explicitly, like -"x".
	 * @param wildcard
	 *        Whether a trailing ellipsis was present.
	 * @return The parser state after the list of strings, or {@code null} if
	 *         the list of strings is malformed.
	 */
	private static @Nullable ParserState parseExplicitImportNames (
		final ParserState start,
		final Mutable<A_Set> names,
		final Mutable<A_Map> renames,
		final Mutable<A_Set> excludes,
		final Mutable<Boolean> wildcard)
	{
		// An explicit list of imports was provided, so it's not a wildcard
		// unless an ellipsis also explicitly occurs.
		boolean anything = false;
		wildcard.value = false;
		ParserState state = start;
		while (true)
		{
			if (state.peekToken(ELLIPSIS))
			{
				state = state.afterToken();
				wildcard.value = true;
				return state;
			}
			A_Token token;
			if (state.peekToken(MINUS))
			{
				state = state.afterToken();
				final A_Token negatedToken = state.peekStringLiteral();
				if (negatedToken == null)
				{
					state.expected("string literal after negation");
					return null;
				}
				state = state.afterToken();
				excludes.value = excludes.value.setWithElementCanDestroy(
					negatedToken.literal(), false);
			}
			else if ((token = state.peekStringLiteral()) != null)
			{
				final ParserState stateAtString = state;
				state = state.afterToken();
				if (state.peekToken(RIGHT_ARROW))
				{
					state = state.afterToken();
					final A_Token token2 = state.peekStringLiteral();
					if (token2 == null)
					{
						state.expected(
							"string literal token after right arrow");
						return null;
					}
					if (renames.value.hasKey(token2.literal()))
					{
						state.expected(
							"renames to specify distinct target names");
						return null;
					}
					state = state.afterToken();
					renames.value = renames.value.mapAtPuttingCanDestroy(
						token2.literal(), token.literal(), true);
				}
				else
				{
					final A_String name = token.literal();
					if (names.value.hasElement(name))
					{
						// The same name was explicitly listed twice.  This
						// can't be postponed to validation time since we keep
						// a (de-duplicated) set of names.
						stateAtString.expected(
							"directly imported name not to occur twice");
						return null;
					}
					names.value = names.value.setWithElementCanDestroy(
						name, true);
				}
			}
			else
			{
				if (anything)
				{
					state.expected(
						"a string literal, minus sign, or ellipsis"
						+ " after dangling comma");
					return null;
				}
				state.expected(
					"another string literal, minus sign, ellipsis, or"
					+ " end of import");
				return state;
			}
			anything = true;

			if (state.peekToken(ELLIPSIS))
			{
				//noinspection StatementWithEmptyBody
				// Allow ellipsis with no preceding comma: Fall through without
				// consuming it and let the start of the loop handle it.
			}
			else if (state.peekToken(COMMA))
			{
				// Eat the comma.
				state = state.afterToken();
			}
			else
			{
				state.expected("comma or ellipsis or end of import");
				return state;
			}
		}
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
	 *        The initially empty list of module imports to populate.
	 * @param isExtension
	 *        Whether this is an Extends clause rather than Uses.  This controls
	 *        whether imported names are re-exported.
	 * @return The parser state after the list of imports, or {@code null} if
	 *         the list of imports is malformed.
	 */
	private static @Nullable ParserState parseModuleImports (
		final ParserState start,
		final List<ModuleImport> imports,
		final boolean isExtension)
	{
		boolean anyEntries = false;
		ParserState state = start;
		while (true)
		{
			final A_Token token = state.peekStringLiteral();
			if (token == null)
			{
				if (anyEntries)
				{
					state.expected("another module name after comma");
					return null;
				}
				state.expected("a comma-separated list of module names");
				// It's legal to have no strings.
				return state;
			}
			anyEntries = true;

			final A_String moduleName = token.literal();
			state = state.afterToken();

			final List<A_String> versions = new ArrayList<>();
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

			final Mutable<A_Set> names = new Mutable<>(SetDescriptor.empty());
			final Mutable<A_Map> renames = new Mutable<>(MapDescriptor.empty());
			final Mutable<A_Set> excludes =
				new Mutable<>(SetDescriptor.empty());
			final Mutable<Boolean> wildcard = new Mutable<>(false);

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
				state = parseExplicitImportNames(
					state,
					names,
					renames,
					excludes,
					wildcard);
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
			else
			{
				wildcard.value = true;
			}

			try
			{
				imports.add(
					new ModuleImport(
						moduleName,
						SetDescriptor.fromCollection(versions),
						isExtension,
						names.value,
						renames.value,
						excludes.value,
						wildcard.value));
			}
			catch (final ImportValidationException e)
			{
				state.expected(e.getMessage());
				return null;
			}
			if (state.peekToken(COMMA))
			{
				state = state.afterToken();
			}
			else
			{
				return state;
			}
		}
	}

	/**
	 * Read the source string for the {@linkplain ModuleDescriptor module}
	 * specified by the fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param resolvedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module.
	 * @param continuation
	 *        What to do after the source module has been completely read.
	 *        Accepts the source text of the module.
	 * @param fail
	 *        What to do in the event of a failure that the {@linkplain
	 *        ProblemHandler problem handler} does not wish to continue.
	 * @param problemHandler
	 *        A problem handler.
	 */
	private static void extractSourceThen (
		final ResolvedModuleName resolvedName,
		final Continuation1<String> continuation,
		final Continuation0 fail,
		final ProblemHandler problemHandler)
	{
		final AvailRuntime runtime = AvailRuntime.current();
		final File ref = resolvedName.sourceReference();
		assert ref != null;
		final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		decoder.onMalformedInput(CodingErrorAction.REPLACE);
		decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
		final ByteBuffer input = ByteBuffer.allocateDirect(4096);
		final CharBuffer output = CharBuffer.allocate(4096);
		final Mutable<Long> filePosition = new Mutable<>(0L);
		final AsynchronousFileChannel file;
		try
		{
			file = runtime.openFile(
				ref.toPath(), EnumSet.of(StandardOpenOption.READ));
		}
		catch (final IOException e)
		{
			final Problem problem = new Problem(
				resolvedName,
				1,
				0,
				PARSE,
				"Unable to open source module \"{0}\" [{1}]: {2}",
				resolvedName,
				ref.getAbsolutePath(),
				e.getLocalizedMessage())
			{
				@Override
				public void abortCompilation ()
				{
					fail.value();
				}
			};
			problemHandler.handle(problem);
			return;
		}
		final MutableOrNull<CompletionHandler<Integer, Void>> handler =
			new MutableOrNull<>();
		final StringBuilder sourceBuilder = new StringBuilder(4096);
		handler.value = new CompletionHandler<Integer, Void>()
		{
			@Override
			public void completed (
				@Nullable final Integer bytesRead,
				@Nullable final Void nothing)
			{
				try
				{
					assert bytesRead != null;
					boolean moreInput = true;
					if (bytesRead == -1)
					{
						moreInput = false;
					}
					else
					{
						filePosition.value += bytesRead;
					}
					input.flip();
					final CoderResult result = decoder.decode(
						input, output, !moreInput);
					// UTF-8 never compresses data, so the number of
					// characters encoded can be no greater than the number
					// of bytes encoded. The input buffer and the output
					// buffer are equally sized (in units), so an overflow
					// cannot occur.
					assert !result.isOverflow();
					assert !result.isError();
					// If the decoder didn't consume all of the bytes, then
					// preserve the unconsumed bytes in the next buffer (for
					// decoding).
					if (input.hasRemaining())
					{
						input.compact();
					}
					else
					{
						input.clear();
					}
					output.flip();
					sourceBuilder.append(output);
					// If more input remains, then queue another read.
					if (moreInput)
					{
						output.clear();
						file.read(
							input,
							filePosition.value,
							null,
							handler.value);
					}
					// Otherwise, close the file channel and queue the
					// original continuation.
					else
					{
						decoder.flush(output);
						sourceBuilder.append(output);
						file.close();
						runtime.execute(
							new AvailTask(FiberDescriptor.compilerPriority)
							{
								@Override
								public void value ()
								{
									continuation.value(
										sourceBuilder.toString());
								}
							});
					}
				}
				catch (final IOException e)
				{
					final Problem problem = new Problem(
						resolvedName,
						1,
						0,
						PARSE,
						"Invalid UTF-8 encoding in source module "
						+ "\"{0}\": {1}\n{2}",
						resolvedName,
						e.getLocalizedMessage(),
						trace(e))
					{
						@Override
						public void abortCompilation ()
						{
							fail.value();
						}
					};
					problemHandler.handle(problem);
				}
			}

			@Override
			public void failed (
				@Nullable final Throwable e,
				@Nullable final Void attachment)
			{
				assert e != null;
				final Problem problem = new Problem(
					resolvedName,
					1,
					0,
					EXTERNAL,
					"Unable to read source module \"{0}\": {1}\n{2}",
					resolvedName,
					e.getLocalizedMessage(),
					trace(e))
				{
					@Override
					public void abortCompilation ()
					{
						fail.value();
					}
				};
				problemHandler.handle(problem);
			}
		};
		// Kick off the asynchronous read.
		file.read(input, 0L, null, handler.value);
	}

	/**
	 * Tokenize the {@linkplain ModuleDescriptor module} specified by the
	 * fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param source
	 *        The {@linkplain String string} containing the module's source
	 *        code.
	 * @param moduleName
	 *        The name of the module to tokenize.
	 * @param stopAfterBodyToken
	 *        Stop scanning after encountering the BODY token?
	 * @return A {@linkplain AvailScannerResult scanner result}.
	 * @throws AvailScannerException
	 *         If tokenization failed for any reason.
	 */
	@InnerAccess static AvailScannerResult tokenize (
			final String source,
			final String moduleName,
			final boolean stopAfterBodyToken)
		throws AvailScannerException
	{
		return AvailScanner.scanString(source, moduleName, stopAfterBodyToken);
	}

	/**
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes. Answer the
	 * resulting tree.
	 *
	 * @param object
	 *        The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param transformer
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
	@InnerAccess static A_Phrase treeMapWithParent (
		final A_Phrase object,
		final Transformer3<A_Phrase, A_Phrase, List<A_Phrase>, A_Phrase>
			transformer,
		final A_Phrase parentNode,
		final List<A_Phrase> outerNodes,
		final Map<A_Phrase, A_Phrase> nodeMap)
	{
		if (nodeMap.containsKey(object))
		{
			return nodeMap.get(object);
		}
		final A_Phrase objectCopy = object.copyMutableParseNode();
		objectCopy.childrenMap(
			new Transformer1<A_Phrase, A_Phrase>()
			{
				@Override
				public A_Phrase value (final @Nullable A_Phrase child)
				{
					assert child != null;
					assert child.isInstanceOfKind(PARSE_NODE.mostGeneralType());
					return treeMapWithParent(
						child, transformer, objectCopy, outerNodes, nodeMap);
				}
			});
		final A_Phrase transformed = transformer.valueNotNull(
			objectCopy, parentNode, outerNodes);
		transformed.makeShared();
		nodeMap.put(object, transformed);
		return transformed;
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
	 * @param afterFail
	 *        What to do after reporting the failure.
	 */
	@InnerAccess void reportAmbiguousInterpretations (
		final ParserState where,
		final A_Phrase interpretation1,
		final A_Phrase interpretation2,
		final Continuation0 afterFail)
	{
		final Mutable<A_Phrase> node1 = new Mutable<>(interpretation1);
		final Mutable<A_Phrase> node2 = new Mutable<>(interpretation2);
		findParseTreeDiscriminants(node1, node2);
		where.expected(
			new Describer()
			{
				@Override
				public void describeThen (
					final Continuation1<String> continuation)
				{
					final List<A_Phrase> nodes = Arrays.asList(
						node1.value, node2.value);
					Interpreter.stringifyThen(
						runtime,
						compilationContext.getTextInterface(),
						nodes,
						new Continuation1<List<String>>()
						{
							@Override
							public void value (
								final @Nullable List<String> nodeStrings)
							{
								assert nodeStrings != null;
								final StringBuilder builder =
									new StringBuilder(200);
								builder.append(
									"unambiguous interpretation.  " +
									"Here are two possible parsings..." +
									"\n\t");
								builder.append(nodeStrings.get(0));
								builder.append("\n\t");
								builder.append(nodeStrings.get(1));
								continuation.value(builder.toString());
							}
						});
					}
				});
		compilationContext.diagnostics.reportError(afterFail);
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
		final Mutable<A_Phrase> node1,
		final Mutable<A_Phrase> node2)
	{
		while (true)
		{
			assert !node1.value.equals(node2.value);
			if (!node1.value.parseNodeKind().equals(
				node2.value.parseNodeKind()))
			{
				// The nodes are different kinds, so present them as what's
				// different.
				return;
			}
			if (node1.value.isMacroSubstitutionNode()
				&& node2.value.isMacroSubstitutionNode())
			{
				if (node1.value.apparentSendName().equals(
					node2.value.apparentSendName()))
				{
					// Two occurrences of the same macro.  Drill into the
					// resulting phrases.
					node1.value = node1.value.outputParseNode();
					node2.value = node2.value.outputParseNode();
					continue;
				}
				// Otherwise the macros are different and we should stop.
				return;
			}
			if (node1.value.isMacroSubstitutionNode()
				|| node2.value.isMacroSubstitutionNode())
			{
				// They aren't both macros, but one is, so they're different.
				return;
			}
			if (node1.value.parseNodeKindIsUnder(SEND_NODE)
				&& !node1.value.bundle().equals(node2.value.bundle()))
			{
				// They're sends of different messages, so don't go any deeper.
				return;
			}
			final List<A_Phrase> parts1 = new ArrayList<>();
			node1.value.childrenDo(new Continuation1<A_Phrase>()
			{
				@Override
				public void value (final @Nullable A_Phrase part)
				{
					parts1.add(part);
				}
			});
			final List<A_Phrase> parts2 = new ArrayList<>();
			node2.value.childrenDo(new Continuation1<A_Phrase>()
				{
					@Override
					public void value (final @Nullable A_Phrase part)
					{
						parts2.add(part);
					}
				});
			final boolean isBlock =
				node1.value.parseNodeKindIsUnder(BLOCK_NODE);
			if (parts1.size() != parts2.size() && !isBlock)
			{
				// Different structure at this level.
				return;
			}
			final List<Integer> differentIndices = new ArrayList<>();
			for (int i = 0; i < min(parts1.size(), parts2.size()); i++)
			{
				if (!parts1.get(i).equals(parts2.get(i)))
				{
					differentIndices.add(i);
				}
			}
			if (isBlock)
			{
				if (differentIndices.size() == 0)
				{
					// Statement or argument lists are probably different sizes.
					// Use the block itself.
					return;
				}
				// Show the first argument or statement that differs.
				// Fall through.
			}
			else if (differentIndices.size() != 1)
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
	 * Construct and answer a {@linkplain Continuation1 continuation} that
	 * wraps the specified continuation in logic that will increment the
	 * {@linkplain CompilationContext#workUnitsCompleted count of completed work
	 * units} and potentially call the {@linkplain
	 * CompilationContext#noMoreWorkUnits unambiguous statement}.
	 *
	 * @param token
	 *        The {@linkplain A_Token token} that provides context for the
	 *        work unit completion.
	 * @param optionalSafetyCheck
	 *        Either {@code null} or an {@link AtomicBoolean} which must
	 *        transition from false to true only once.
	 * @param continuation
	 *        What to do as a work unit.
	 * @return A new continuation. It accepts an argument of some kind, which
	 *         will be passed forward to the argument continuation.
	 */
	@InnerAccess <ArgType> Continuation1<ArgType> workUnitCompletion (
		final A_Token token,
		final @Nullable AtomicBoolean optionalSafetyCheck,
		final Continuation1<ArgType> continuation)
	{
		return compilationContext.workUnitCompletion(
			token, optionalSafetyCheck, continuation);
	}

	/**
	 * Eventually execute the specified {@linkplain Continuation0 continuation}
	 * as a {@linkplain AvailCompiler compiler} work unit.
	 *
	 * @param continuation
	 *        What to do at some point in the future.
	 * @param where
	 *        Where the parse is happening.
	 */
	private void workUnitDo (
		final Continuation0 continuation,
		final ParserState where)
	{
		compilationContext.workUnitDo(continuation, where);
	}

	/**
	 * Wrap the {@linkplain Continuation1 continuation of one argument} inside a
	 * {@linkplain Continuation0 continuation of zero arguments} and record that
	 * as per {@linkplain #workUnitDo(Continuation0, ParserState)}.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param here
	 *        Where to start parsing when the continuation runs.
	 * @param continuation
	 *        What to execute with the passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@linkplain
	 *        Continuation1 one-argument continuation}.
	 */
	@InnerAccess <ArgType> void attempt (
		final ParserState here,
		final Continuation1<ArgType> continuation,
		final ArgType argument)
	{
		compilationContext.attempt(here, continuation, argument);
	}

	/**
	 * Start definition of a {@linkplain ModuleDescriptor module}. The entire
	 * definition can be rolled back because the {@linkplain Interpreter
	 * interpreter}'s context module will contain all methods and precedence
	 * rules defined between the transaction start and the rollback (or commit).
	 * Committing simply clears this information.
	 */
	private void startModuleTransaction ()
	{
		compilationContext.setLoader(new AvailLoader(
			compilationContext.module(),
			compilationContext.getTextInterface()));
	}

	/**
	 * Rollback the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction()}.
	 *
	 * @param afterRollback
	 *        What to do after rolling back.
	 */
	@InnerAccess void rollbackModuleTransaction (
		final Continuation0 afterRollback)
	{
		compilationContext
			.module()
			.removeFrom(compilationContext.loader(), afterRollback);
	}

	/**
	 * Commit the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction()}.
	 */
	@InnerAccess void commitModuleTransaction ()
	{
		runtime.addModule(compilationContext.module());
	}

	/**
	 * Evaluate the specified semantic restriction {@linkplain
	 * FunctionDescriptor function} in the module's context; lexically enclosing
	 * variables are not considered in scope, but module variables and constants
	 * are in scope.
	 *
	 * @param restriction
	 *        A {@linkplain SemanticRestrictionDescriptor semantic restriction}.
	 * @param args
	 *        The arguments to the function.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@link Throwable}.
	 */
	@InnerAccess void evaluateSemanticRestrictionFunctionThen (
		final A_SemanticRestriction restriction,
		final List<? extends A_BasicObject> args,
		final Continuation1<AvailObject> onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		final A_Function function = restriction.function();
		final A_RawFunction code = function.code();
		final A_Module mod = code.module();
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			function.kind().returnType(),
			compilationContext.loader(),
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					return StringDescriptor.format(
						"Semantic restriction %s, in %s:%d",
						restriction.definitionMethod().bundles()
							.iterator().next().message(),
						mod.equals(NilDescriptor.nil())
							? "no module"
							: mod.moduleName(),
						code.startingLineNumber());
				}
			});
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		fiber.textInterface(compilationContext.getTextInterface());
		fiber.resultContinuation(onSuccess);
		fiber.failureContinuation(onFailure);
		Interpreter.runOutermostFunction(runtime, fiber, function, args);
	}

	/**
	 * Evaluate the specified macro {@linkplain FunctionDescriptor function} in
	 * the module's context; lexically enclosing variables are not considered in
	 * scope, but module variables and constants are in scope.
	 *
	 * @param macro
	 *        A {@linkplain MacroDefinitionDescriptor macro definition}.
	 * @param args
	 *        The argument phrases to supply the macro.
	 * @param clientParseData
	 *        The map to associate with the {@link
	 *        AtomDescriptor#clientDataGlobalKey()} atom in the fiber.
	 * @param clientParseDataOut
	 *        A {@link MutableOrNull} into which we will store an {@link A_Map}
	 *        when the fiber completes successfully.  The map will be the
	 *        content of the fiber variable holding the client data, extracted
	 *        just after the fiber completes.  If unsuccessful, don't assign to
	 *        the {@code MutableOrNull}.
	 * @param onSuccess
	 *        What to do with the result of the evaluation, a {@linkplain
	 *        A_Phrase phrase}.
	 * @param onFailure
	 *        What to do with a terminal {@link Throwable}.
	 */
	@InnerAccess void evaluateMacroFunctionThen (
		final A_Definition macro,
		final List<? extends A_Phrase> args,
		final A_Map clientParseData,
		final MutableOrNull<A_Map> clientParseDataOut,
		final Continuation1<AvailObject> onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		final A_Function function = macro.bodyBlock();
		final A_RawFunction code = function.code();
		final A_Module mod = code.module();
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			function.kind().returnType(),
			compilationContext.loader(),
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					return StringDescriptor.format(
						"Macro evaluation %s, in %s:%d",
						macro.definitionMethod().bundles()
							.iterator().next().message(),
						mod.equals(NilDescriptor.nil())
							? "no module"
							: mod.moduleName(),
						code.startingLineNumber());
				}
			});
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		fiber.setGeneralFlag(GeneralFlag.IS_EVALUATING_MACRO);
		A_Map fiberGlobals = fiber.fiberGlobals();
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataGlobalKey(), clientParseData, true);
		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(compilationContext.getTextInterface());
		fiber.resultContinuation(new Continuation1<AvailObject>()
		{
			@Override
			public void value (@Nullable final AvailObject outputPhrase)
			{
				assert outputPhrase != null;
				clientParseDataOut.value = fiber
					.fiberGlobals()
					.mapAt(clientDataGlobalKey());
				onSuccess.value(outputPhrase);
			}
		});
		fiber.failureContinuation(onFailure);
		Interpreter.runOutermostFunction(runtime, fiber, function, args);
	}

	/**
	 * Generate a {@linkplain FunctionDescriptor function} from the specified
	 * {@linkplain ParseNodeDescriptor phrase} and evaluate it in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param expressionNode
	 *        A {@linkplain ParseNodeDescriptor parse node}.
	 * @param lineNumber
	 *        The line number on which the expression starts.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do after a failure.
	 */
	@InnerAccess void evaluatePhraseThen (
		final A_Phrase expressionNode,
		final int lineNumber,
		final boolean shouldSerialize,
		final Continuation1<AvailObject> onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		compilationContext.evaluateFunctionThen(
			FunctionDescriptor.createFunctionForPhrase(
				expressionNode, compilationContext.module(), lineNumber),
			lineNumber,
			Collections.<AvailObject>emptyList(),
			MapDescriptor.empty(),
			shouldSerialize,
			onSuccess,
			onFailure);
	}

	/**
	 * Evaluate a parse tree node. It's a top-level statement in a module.
	 * Declarations are handled differently - they cause a variable to be
	 * declared in the module's scope.
	 *
	 * @param startState
	 *        The start {@link ParserState}, for line number reporting.
	 * @param afterStatement
	 *        The {@link ParserState} just after the statement.
	 * @param expression
	 *        The expression to compile and evaluate as a top-level statement in
	 *        the module.
	 * @param declarationRemap
	 *        A {@link Map} holding the isomorphism between phrases and their
	 *        replacements.  This is especially useful for keeping track of how
	 *        to transform references to prior declarations that have been
	 *        transformed from local-scoped to module-scoped.
	 * @param onSuccess
	 *        What to do after success. Note that the result of executing the
	 *        statement must be {@linkplain NilDescriptor#nil() nil}, so there
	 *        is no point in having the continuation accept this value, hence
	 *        the {@linkplain Continuation0 nullary continuation}.
	 * @param afterFail
	 *        What to do after execution of the top-level statement fails.
	 */
	@InnerAccess void evaluateModuleStatementThen (
		final ParserState startState,
		final ParserState afterStatement,
		final A_Phrase expression,
		final Map<A_Phrase, A_Phrase> declarationRemap,
		final Continuation0 onSuccess,
		final Continuation0 afterFail)
	{
		assert !expression.isMacroSubstitutionNode();
		final A_Phrase replacement = treeMapWithParent(
			expression,
			new Transformer3<A_Phrase, A_Phrase, List<A_Phrase>, A_Phrase>()
			{
				@Override
				public @Nullable A_Phrase value (
					final @Nullable A_Phrase phrase,
					final @Nullable A_Phrase parent,
					final @Nullable List<A_Phrase> outerBlocks)
				{
					// The mapping through declarationRemap
					// has already taken place.
					return phrase;
				}
			},
			NilDescriptor.nil(),
			new ArrayList<>(),
			declarationRemap);

		final Continuation1<Throwable> phraseFailure =
			e ->
			{
				assert e != null;
				final A_Token token = startState.peekToken();
				if (e instanceof AvailAssertionFailedException)
				{
					compilationContext.reportAssertionFailureProblem(
						token, (AvailAssertionFailedException) e);
				}
				else if (e instanceof AvailEmergencyExitException)
				{
					compilationContext.reportEmergencyExitProblem(
						token, (AvailEmergencyExitException) e);
				}
				else
				{
					compilationContext.reportExecutionProblem(token, e);
				}
				afterFail.value();
			};

		if (!replacement.parseNodeKindIsUnder(DECLARATION_NODE))
		{
			// Only record module statements that aren't declarations. Users of
			// the module don't care if a module variable or constant is only
			// reachable from the module's methods.
			evaluatePhraseThen(
				replacement,
				startState.peekToken().lineNumber(),
				true,
				ignored -> onSuccess.value(),
				phraseFailure);
			return;
		}
		// It's a declaration, but the parser couldn't previously tell that it
		// was at module scope.  Serialize a function that will cause the
		// declaration to happen, so that references to the global
		// variable/constant from a subsequent module will be able to find it by
		// name.
		final A_Module module = compilationContext.module();
		final AvailLoader loader = compilationContext.loader();
		final A_String name = replacement.token().string();
		final @Nullable String shadowProblem =
			module.variableBindings().hasKey(name)
				? "module variable"
				: module.constantBindings().hasKey(name)
					? "module constant"
					: null;
		switch (replacement.declarationKind())
		{
			case LOCAL_CONSTANT:
			{
				if (shadowProblem != null)
				{
					afterStatement.expected(
						"new module constant "
						+ name
						+ " not to have same name as existing "
						+ shadowProblem);
					compilationContext.diagnostics.reportError(afterFail);
					return;
				}
				loader.startRecordingEffects();
				evaluatePhraseThen(
					replacement.initializationExpression(),
					replacement.token().lineNumber(),
					false,
					val ->
					{
						assert val != null;
						loader.stopRecordingEffects();
						final boolean canSummarize =
							loader.statementCanBeSummarized();
						final A_Type innerType =
							AbstractEnumerationTypeDescriptor
								.withInstance(val);
						final A_Type varType =
							VariableTypeDescriptor.wrapInnerType(innerType);
						final A_Phrase creationSend = SendNodeDescriptor.from(
							TupleDescriptor.empty(),
							SpecialAtom.CREATE_MODULE_VARIABLE.bundle,
							ListNodeDescriptor.newExpressions(
								TupleDescriptor.from(
									syntheticFrom(module),
									syntheticFrom(name),
									syntheticFrom(varType),
									syntheticFrom(
										AtomDescriptor.trueObject()),
									syntheticFrom(
										AtomDescriptor.objectFromBoolean(
											canSummarize)))),
							TOP.o());
						final A_Function creationFunction =
							FunctionDescriptor.createFunctionForPhrase(
								creationSend,
								module,
								replacement.token().lineNumber());
						// Force the declaration to be serialized.
						compilationContext.serializeWithoutSummary(
							creationFunction);
						final A_Variable var =
							VariableSharedGlobalDescriptor.createGlobal(
								varType, module, name, true);
						var.valueWasStablyComputed(canSummarize);
						module.addConstantBinding(name, var);
						// Update the map so that the local constant goes to
						// a module constant.  Then subsequent statements in
						// this sequence will transform uses of the constant
						// appropriately.
						final A_Phrase newConstant =
							DeclarationNodeDescriptor.newModuleConstant(
								replacement.token(),
								var,
								replacement.initializationExpression());
						declarationRemap.put(expression, newConstant);
						// Now create a module variable declaration (i.e.,
						// cheat) JUST for this initializing assignment.
						final A_Phrase newDeclaration =
							DeclarationNodeDescriptor.newModuleVariable(
								replacement.token(),
								var,
								NilDescriptor.nil(),
								replacement.initializationExpression());
						final A_Phrase assign =
							AssignmentNodeDescriptor.from(
								VariableUseNodeDescriptor.newUse(
									replacement.token(), newDeclaration),
								syntheticFrom(val),
								false);
						final A_Function assignFunction =
							FunctionDescriptor.createFunctionForPhrase(
								assign,
								module,
								replacement.token().lineNumber());
						compilationContext.serializeWithoutSummary(
							assignFunction);
						var.setValue(val);
						onSuccess.value();
					},
					phraseFailure);
				break;
			}
			case LOCAL_VARIABLE:
			{
				if (shadowProblem != null)
				{
					afterStatement.expected(
						"new module variable "
						+ name
						+ " not to have same name as existing "
						+ shadowProblem);
					compilationContext.diagnostics.reportError(afterFail);
					return;
				}
				final A_Type varType = VariableTypeDescriptor.wrapInnerType(
					replacement.declaredType());
				final A_Phrase creationSend = SendNodeDescriptor.from(
					TupleDescriptor.empty(),
					SpecialAtom.CREATE_MODULE_VARIABLE.bundle,
					ListNodeDescriptor.newExpressions(
						TupleDescriptor.from(
							syntheticFrom(module),
							syntheticFrom(name),
							syntheticFrom(varType),
							syntheticFrom(AtomDescriptor.falseObject()),
							syntheticFrom(AtomDescriptor.falseObject()))),
					TOP.o());
				final A_Function creationFunction =
					FunctionDescriptor.createFunctionForPhrase(
						creationSend,
						module,
						replacement.token().lineNumber());
				creationFunction.makeImmutable();
				// Force the declaration to be serialized.
				compilationContext.serializeWithoutSummary(creationFunction);
				final A_Variable var =
					VariableSharedGlobalDescriptor.createGlobal(
						varType, module, name, false);
				module.addVariableBinding(name, var);
				if (!replacement.initializationExpression().equalsNil())
				{
					final A_Phrase newDeclaration =
						DeclarationNodeDescriptor.newModuleVariable(
							replacement.token(),
							var,
							replacement.typeExpression(),
							replacement.initializationExpression());
					declarationRemap.put(expression, newDeclaration);
					final A_Phrase assign = AssignmentNodeDescriptor.from(
						VariableUseNodeDescriptor.newUse(
							replacement.token(),
							newDeclaration),
							replacement.initializationExpression(),
						false);
					final A_Function assignFunction =
						FunctionDescriptor.createFunctionForPhrase(
							assign, module, replacement.token().lineNumber());
					evaluatePhraseThen(
						replacement.initializationExpression(),
						replacement.token().lineNumber(),
						false,
						val ->
						{
							assert val != null;
							var.setValue(val);
							compilationContext.serializeWithoutSummary(
								assignFunction);
							onSuccess.value();
						},
						phraseFailure);
				}
				else
				{
					onSuccess.value();
				}
				break;
			}
			default:
				assert false
					: "Expected top-level declaration to have been "
						+ "parsed as local";
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
	 * @param excludedString
	 *        The string to omit from the message, since it was the actual
	 *        encountered token's text.
	 */
	private void expectedKeywordsOf (
		final ParserState where,
		final A_Map incomplete,
		final boolean caseInsensitive,
		final A_String excludedString)
	{
		where.expected(new Describer()
		{
			@Override
			public void describeThen (final Continuation1<String> c)
			{
				final StringBuilder builder = new StringBuilder(200);
				if (caseInsensitive)
				{
					builder.append(
						"one of the following case-insensitive tokens:");
				}
				else
				{
					builder.append("one of the following tokens:");
				}
				final List<String> sorted =
					new ArrayList<>(incomplete.mapSize());
				final boolean detail = incomplete.mapSize() < 10;
				for (final MapDescriptor.Entry entry : incomplete.mapIterable())
				{
					final A_String availTokenString = entry.key();
					if (!availTokenString.equals(excludedString))
					{
						if (!detail)
						{
							sorted.add(availTokenString.asNativeString());
							continue;
						}
						// Collect the plans-in-progress and deduplicate
						// them by their string representation (including
						// the indicator at the current parsing location).
						// We can't just deduplicate by bundle, since the
						// current bundle tree might be eligible for
						// continued parsing at multiple positions.
						final Set<String> strings = new HashSet<>();
						final List<A_ParsingPlanInProgress>
							representativePlansInProgress =
								new ArrayList<>();
						final A_BundleTree nextTree = entry.value();
						for (final Entry successorBundleEntry :
							nextTree.allParsingPlansInProgress().mapIterable())
						{
							final A_Bundle bundle = successorBundleEntry.key();
							for (final Entry definitionEntry :
								successorBundleEntry.value().mapIterable())
							{
								for (final A_ParsingPlanInProgress inProgress
									: definitionEntry.value())
								{
									final A_ParsingPlanInProgress
										previousPlan =
											ParsingPlanInProgressDescriptor
												.create(
													inProgress.parsingPlan(),
													max(
														inProgress.parsingPc()
															- 1,
														1));
									final String moduleName =
										bundle.message().issuingModule()
											.moduleName().asNativeString();
									final String shortModuleName =
										moduleName.substring(
											moduleName.lastIndexOf('/') + 1);
									strings.add(
										previousPlan.nameHighlightingPc()
											+ " from "
											+ shortModuleName);
								}
							}
						}
						final List<String> sortedStrings =
							new ArrayList<>(strings);
						Collections.sort(sortedStrings);
						final StringBuilder buffer = new StringBuilder();
						buffer.append(availTokenString.asNativeString());
						buffer.append("  (");
						boolean first = true;
						for (final String progressString : sortedStrings)
						{
							if (!first)
							{
								buffer.append(", ");
							}
							buffer.append(progressString);
							first = false;
						}
						buffer.append(")");
						sorted.add(buffer.toString());
					}
				}
				Collections.sort(sorted);
				boolean startOfLine = true;
				final int leftColumn = 4 + 4; // ">>> " and a tab.
				int column = leftColumn;
				for (final String s : sorted)
				{
					if (startOfLine)
					{
						builder.append("\n\t");
						column = leftColumn;
					}
					else
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
						startOfLine = true;
					}
				}
				compilationContext.eventuallyDo(
					where.peekToken(),
					new Continuation0()
					{
						@Override
						public void value ()
						{
							c.value(builder.toString());
						}
					});
			}
		});
	}

	/**
	 * Pre-build the state of the initial parse stack.  Now that the top-most
	 * arguments get concatenated into a list, simply start with a list
	 * containing one empty list node.
	 */
	private static List<A_Phrase> initialParseStack =
		Collections.<A_Phrase>singletonList(ListNodeDescriptor.empty());

	/**
	 * Pre-build the state of the initial mark stack.  This stack keeps track of
	 * parsing positions to detect if progress has been made at certain points.
	 * This mechanism serves to prevent empty expressions from being considered
	 * an occurrence of a repeated or optional subexpression, even if it would
	 * otherwise be recognized as such.
	 */
	private static List<Integer> initialMarkStack = Collections.emptyList();

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
	private void parseLeadingKeywordSendThen (
		final ParserState start,
		final Con<CompilerSolution> continuation)
	{
		A_Map clientMap = start.clientDataMap;
		// Start accumulating tokens related to this leading-keyword message
		// send at its first token.
		clientMap = clientMap.mapAtPuttingCanDestroy(
			allTokensKey(), TupleDescriptor.empty(), false);
		parseRestOfSendNode(
			new ParserState(start.position, start.tokenIndex, clientMap),
			compilationContext.loader().rootBundleTree(),
			null,
			start,
			false,  // Nothing consumed yet.
			initialParseStack,
			initialMarkStack,
			new Con<CompilerSolution>(
				new PartialSubexpressionList(
					compilationContext.loader().rootBundleTree(),
					continuation.superexpressions))
			{
				@Override
				public void valueNotNull(
					final CompilerSolution solution)
				{
					continuation.value(solution);
				}
			});
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
	@InnerAccess void parseLeadingArgumentSendAfterThen (
		final ParserState start,
		final A_Phrase leadingArgument,
		final ParserState initialTokenPosition,
		final Con<CompilerSolution> continuation)
	{
		assert start.position != initialTokenPosition.position;
		assert leadingArgument != null;
		A_Map clientMap = start.clientDataMap;
		// Start accumulating tokens related to this leading-argument message
		// send after the leading argument.
		clientMap = clientMap.mapAtPuttingCanDestroy(
			allTokensKey(), TupleDescriptor.empty(), false);
		parseRestOfSendNode(
			new ParserState(start.position, start.tokenIndex, clientMap),
			compilationContext.loader().rootBundleTree(),
			leadingArgument,
			initialTokenPosition,
			false,  // Leading argument does not yet count as something parsed.
			initialParseStack,
			initialMarkStack,
			new Con<CompilerSolution>(
				new PartialSubexpressionList(
					compilationContext.loader().rootBundleTree(),
					continuation.superexpressions))
			{
				@Override
				public void valueNotNull(
					final CompilerSolution solution)
				{
					continuation.value(solution);
				}
			});
	}

	/**
	 * Parse an expression with an optional leading-argument message send around
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
	@InnerAccess void parseOptionalLeadingArgumentSendAfterThen (
		final ParserState startOfLeadingArgument,
		final ParserState afterLeadingArgument,
		final A_Phrase node,
		final Con<CompilerSolution> continuation)
	{
		// It's optional, so try it with no wrapping.  We have to try this even
		// if it's a supercast, since we may be parsing an expression to be a
		// non-leading argument of some send.
		compilationContext.attempt(
			afterLeadingArgument,
			continuation,
			new CompilerSolution(afterLeadingArgument, node));
		// Try to wrap it in a leading-argument message send.
		compilationContext.attempt(
			afterLeadingArgument,
			new Con<CompilerSolution>(continuation.superexpressions)
			{
				@Override
				public void valueNotNull (
					final CompilerSolution solution2)
				{
					parseLeadingArgumentSendAfterThen(
						solution2.endState(),
						solution2.parseNode(),
						startOfLeadingArgument,
						new Con<CompilerSolution>(
							continuation.superexpressions)
						{
							@Override
							public void valueNotNull (
								final CompilerSolution solutionAfter)
							{
								parseOptionalLeadingArgumentSendAfterThen(
									startOfLeadingArgument,
									solutionAfter.endState(),
									solutionAfter.parseNode(),
									continuation);
							}
						});
				}
			},
			new CompilerSolution(afterLeadingArgument, node));
	}

	/** Statistic for matching an exact token. */
	private static final Statistic matchTokenStat =
		new Statistic(
			"(Match particular token)",
			StatisticReport.RUNNING_PARSING_INSTRUCTIONS);

	/** Statistic for matching a token case-insensitively. */
	private static final Statistic matchTokenInsensitivelyStat =
		new Statistic(
			"(Match insensitive token)",
			StatisticReport.RUNNING_PARSING_INSTRUCTIONS);

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
	 * @param marksSoFar
	 *            The stack of mark positions used to test if parsing certain
	 *            subexpressions makes progress.
	 * @param continuation
	 *            What to do with a fully parsed send node.
	 */
	@InnerAccess void parseRestOfSendNode (
		final ParserState start,
		final A_BundleTree bundleTree,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con<CompilerSolution> continuation)
	{
		bundleTree.expand(compilationContext.module());
		final A_Set complete = bundleTree.lazyComplete();
		final A_Map incomplete = bundleTree.lazyIncomplete();
		final A_Map caseInsensitive =
			bundleTree.lazyIncompleteCaseInsensitive();
		final A_Map actions = bundleTree.lazyActions();
		final A_Map prefilter = bundleTree.lazyPrefilterMap();
		final A_BasicObject typeFilterTreePojo =
			bundleTree.lazyTypeFilterTreePojo();
		final boolean anyComplete = complete.setSize() > 0;

		if (anyComplete && consumedAnything && firstArgOrNull == null)
		{
			// There are complete messages, we didn't leave a leading argument
			// stranded, and we made progress in the file (i.e., the message
			// send does not consist of exactly zero tokens).
			assert marksSoFar.isEmpty();
			assert argsSoFar.size() == 1;
			final A_Phrase args = argsSoFar.get(0);
			for (final A_Bundle bundle : complete)
			{
				if (AvailRuntime.debugCompilerSteps)
				{
					System.out.println(
						"Completed send/macro: "
						+ bundle.message() + " " + args);
				}
				completedSendNode(
					initialTokenPosition, start, args, bundle, continuation);
			}
		}
		if (incomplete.mapSize() > 0 && firstArgOrNull == null)
		{
			boolean keywordRecognized = false;
			final A_Token keywordToken = start.peekToken();
			final A_String keywordString = keywordToken.string();
			final TokenType tokenType = keywordToken.tokenType();
			if (tokenType == KEYWORD || tokenType == OPERATOR)
			{
				if (incomplete.hasKey(keywordString))
				{
					final long timeBefore = System.nanoTime();
					final A_BundleTree successor =
						incomplete.mapAt(keywordString);
					if (AvailRuntime.debugCompilerSteps)
					{
						System.out.println(
							"Matched token: " + keywordString
							+ "@" + keywordToken.lineNumber()
							+ " for " + successor);
					}
					keywordRecognized = true;
					// Record this token for the call site.
					A_Map clientMap = start.clientDataMap;
					clientMap = clientMap.mapAtPuttingCanDestroy(
						allTokensKey(),
						clientMap.mapAt(allTokensKey()).appendCanDestroy(
							keywordToken, false),
						false);
					final ParserState afterRecordingToken = new ParserState(
						start.afterToken().position,
						start.afterToken().tokenIndex,
						clientMap);
					eventuallyParseRestOfSendNode(
						afterRecordingToken,
						successor,
						null,
						initialTokenPosition,
						true,  // Just consumed a token.
						argsSoFar,
						marksSoFar,
						continuation);
					final long timeAfter = System.nanoTime();
					final AvailThread thread =
						(AvailThread) Thread.currentThread();
					matchTokenStat.record(
						timeAfter - timeBefore,
						thread.interpreter.interpreterIndex);
				}
			}
			if (!keywordRecognized && consumedAnything)
			{
				expectedKeywordsOf(start, incomplete, false, keywordString);
			}
		}
		if (caseInsensitive.mapSize() > 0 && firstArgOrNull == null)
		{
			boolean keywordRecognized = false;
			final A_Token keywordToken = start.peekToken();
			final A_String lowercaseString = keywordToken.lowerCaseString();
			final TokenType tokenType = keywordToken.tokenType();
			if (tokenType == KEYWORD || tokenType == OPERATOR)
			{
				if (caseInsensitive.hasKey(lowercaseString))
				{
					final long timeBefore = System.nanoTime();
					final A_BundleTree successor =
						caseInsensitive.mapAt(lowercaseString);
					if (AvailRuntime.debugCompilerSteps)
					{
						System.out.println(
							"Matched insensitive token: "
							+ keywordToken.string()
							+ "@" + keywordToken.lineNumber()
							+ " for " + successor);
					}
					keywordRecognized = true;
					// Record this token for the call site.
					A_Map clientMap = start.clientDataMap;
					clientMap = clientMap.mapAtPuttingCanDestroy(
						allTokensKey(),
						clientMap.mapAt(allTokensKey()).appendCanDestroy(
							keywordToken, false),
						false);
					final ParserState afterRecordingToken = new ParserState(
						start.afterToken().position,
						start.afterToken().tokenIndex,
						clientMap);
					eventuallyParseRestOfSendNode(
						afterRecordingToken,
						successor,
						null,
						initialTokenPosition,
						true,  // Just consumed a token.
						argsSoFar,
						marksSoFar,
						continuation);
					final long timeAfter = System.nanoTime();
					final AvailThread thread =
						(AvailThread) Thread.currentThread();
					matchTokenInsensitivelyStat.record(
						timeAfter - timeBefore,
						thread.interpreter.interpreterIndex);
				}
			}
			if (!keywordRecognized && consumedAnything)
			{
				expectedKeywordsOf(
					start, caseInsensitive, true, lowercaseString);
			}
		}
		boolean skipCheckArgumentAction = false;
		if (prefilter.mapSize() > 0)
		{
			assert firstArgOrNull == null;
			final A_Phrase latestArgument = last(argsSoFar);
			if (latestArgument.isMacroSubstitutionNode()
				|| latestArgument.isInstanceOfKind(SEND_NODE.mostGeneralType()))
			{
				final A_Bundle argumentBundle =
					latestArgument.apparentSendName().bundleOrNil();
				assert !argumentBundle.equalsNil();
				if (prefilter.hasKey(argumentBundle))
				{
					final A_BundleTree successor =
						prefilter.mapAt(argumentBundle);
					if (AvailRuntime.debugCompilerSteps)
					{
						System.out.println(
							"Grammatical prefilter: " + argumentBundle
							+ " to " + successor);
					}
					eventuallyParseRestOfSendNode(
						start,
						successor,
						null,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
					// Don't allow any check-argument actions to be processed
					// normally, as it would ignore the restriction which we've
					// been so careful to prefilter.
					skipCheckArgumentAction = true;
				}
				// The argument name was not in the prefilter map, so fall
				// through to allow normal action processing, including the
				// default check-argument action if it's present.
			}
		}
		if (!typeFilterTreePojo.equalsNil())
		{
			// Use the most recently pushed phrase's type to look up the
			// successor bundle tree.  This implements parallel argument type
			// filtering.
			assert firstArgOrNull == null;
			final A_Phrase latestPhrase = last(argsSoFar);
			@SuppressWarnings("unchecked")
			final LookupTree<A_Tuple, A_BundleTree, Void>
				typeFilterTree =
					(LookupTree<A_Tuple, A_BundleTree, Void>)
						typeFilterTreePojo.javaObject();
			final A_BundleTree successor =
				MessageBundleTreeDescriptor.parserTypeChecker.lookupByValue(
					typeFilterTree, latestPhrase, null);
			if (AvailRuntime.debugCompilerSteps)
			{
				System.out.println(
					"Type filter: " + latestPhrase
					+ " -> " + successor);
			}
			// Don't complain if at least one plan was happy with the type of
			// the argument.  Otherwise list all argument type/plan expectations
			// as neatly as possible.
			if (successor.allParsingPlansInProgress().mapSize() == 0)
			{
				start.expected(new Describer()
				{
					@Override
					public void describeThen (
						final Continuation1<String> continueWithDescription)
					{
						Interpreter.stringifyThen(
							runtime,
							compilationContext.getTextInterface(),
							latestPhrase.expressionType(),
							new Continuation1<String>()
							{
								@Override
								public void value (
									@Nullable final String actualTypeString)
								{
									assert actualTypeString != null;
									describeFailedTypeTestThen(
										actualTypeString,
										bundleTree,
										continueWithDescription);
								}
							});
					}
				});
			}
			eventuallyParseRestOfSendNode(
				start,
				successor,
				null,
				initialTokenPosition,
				consumedAnything,
				argsSoFar,
				marksSoFar,
				continuation);
			// Parse instruction optimization allows there to be some plans that
			// do a type filter here, but some that are able to postpone it.
			// Therefore, also allow general actions to be collected here by
			// falling through.
		}
		if (actions.mapSize() > 0)
		{
			for (final Entry entry : actions.mapIterable())
			{
				final A_Number key = entry.key();
				final int keyInt = key.extractInt();
				final ParsingOperation op = decode(keyInt);
				if (skipCheckArgumentAction && op == CHECK_ARGUMENT)
				{
					// Skip this action, because the latest argument was a send
					// that had an entry in the prefilter map, so it has already
					// been dealt with.
					continue;
				}
				// Eliminate it before queueing a work unit if it shouldn't run
				// due to there being a first argument already pre-parsed.
				if (firstArgOrNull == null || op.canRunIfHasFirstArgument)
				{
					final A_Tuple value = entry.value();
					compilationContext.workUnitDo(
						new Continuation0()
						{
							@Override
							public void value ()
							{
								runParsingInstructionThen(
									start,
									keyInt,
									firstArgOrNull,
									argsSoFar,
									marksSoFar,
									initialTokenPosition,
									consumedAnything,
									value,
									continuation);
							}
						},
						start);
				}
			}
		}
	}

	@InnerAccess void describeFailedTypeTestThen (
		final String actualTypeString,
		final A_BundleTree bundleTree,
		final Continuation1<String> continuation)
	{
		// TODO(MvG) Present the full phrase type if it can be a macro argument.
		final Map<A_Type, Set<String>> definitionsByType = new HashMap<>();
		for (final Entry entry
			: bundleTree.allParsingPlansInProgress().mapIterable())
		{
			final A_Map submap = entry.value();
			for (final Entry subentry : submap.mapIterable())
			{
				final A_Set inProgressSet = subentry.value();
				for (final A_ParsingPlanInProgress planInProgress
					: inProgressSet)
				{
					final A_DefinitionParsingPlan plan =
						planInProgress.parsingPlan();
					final A_Tuple instructions = plan.parsingInstructions();
					final int instruction =
						instructions.tupleIntAt(planInProgress.parsingPc());
					final int typeIndex =
						TYPE_CHECK_ARGUMENT.typeCheckArgumentIndex(instruction);
					final A_Type argType =
						MessageSplitter.constantForIndex(typeIndex);
					Set<String> planStrings = definitionsByType.get(argType);
					if (planStrings == null)
					{
						planStrings = new HashSet<>();
						definitionsByType.put(
							argType.expressionType(), planStrings);
					}
					planStrings.add(planInProgress.nameHighlightingPc());
				}
			}
		}
		final List<A_Type> types = new ArrayList<>(definitionsByType.keySet());
		// Generate the type names in parallel.
		Interpreter.stringifyThen(
			runtime,
			compilationContext.getTextInterface(),
			types,
			new Continuation1<List<String>>()
			{
				@Override
				public void value (
					@Nullable final List<String> typeNames)
				{
					// Stitch the type names back onto the plan
					// strings, prior to sorting by type name.
					assert typeNames != null;
					assert typeNames.size() == types.size();
					final List<Pair<String, List<String>>> pairs =
						new ArrayList<>();
					for (int i = 0; i < types.size(); i++)
					{
						final A_Type type = types.get(i);
						final List<String> planStrings =
							new ArrayList<String>(definitionsByType.get(type));
						// Sort individual lists of plans.
						Collections.sort(planStrings);
						pairs.add(new Pair<>(typeNames.get(i), planStrings));
					}
					// Now sort by type names.
					Collections.sort(
						pairs,
						new Comparator<Pair<String, List<String>>>()
						{
							@Override
							public int compare (
								final Pair<String, List<String>> o1,
								final Pair<String, List<String>> o2)
							{
								return o1.first().compareTo(o2.first());
							}
						});
					// Print it all out.
					StringBuilder builder = new StringBuilder(100);
					builder.append("phrase to have a type other than ");
					builder.append(actualTypeString);
					builder.append(".  Expecting:");
					for (final Pair<String, List<String>> pair : pairs)
					{
						builder.append("\n\t");
						builder.append(pair.first().replace("\n", "\n\t\t"));
						for (final String planString : pair.second())
						{
							builder.append("\n\t\t");
							builder.append(planString);
						}
					}
					continuation.value(builder.toString());
				}
			}
		);
	}

	/**
	 * Execute one non-keyword-parsing instruction, then run the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param instruction
	 *            An int encoding the {@linkplain ParsingOperation
	 *            parsing instruction} to execute.
	 * @param firstArgOrNull
	 *            Either the already-parsed first argument or null. If we're
	 *            looking for leading-argument message sends to wrap an
	 *            expression then this is not-null before the first argument
	 *            position is encountered, otherwise it's null and we should
	 *            reject attempts to start with an argument (before a keyword).
	 * @param argsSoFar
	 *            The message arguments that have been parsed so far.
	 * @param marksSoFar
	 *            The parsing markers that have been recorded so far.
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
	@InnerAccess void runParsingInstructionThen (
		final ParserState start,
		final int instruction,
		final @Nullable A_Phrase firstArgOrNull,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final A_Tuple successorTrees,
		final Con<CompilerSolution> continuation)
	{
		final ParsingOperation op = decode(instruction);
		if (AvailRuntime.debugCompilerSteps)
		{
			if (op.ordinal() >= ParsingOperation.distinctInstructions)
			{
				System.out.println(
					"Instr @"
						+ start.shortString()
						+ ": "
						+ op.name()
						+ " ("
						+ operand(instruction)
						+ ") -> "
						+ successorTrees);
			}
			else
			{
				System.out.println(
					"Instr @"
						+ start.shortString()
						+ ": "
						+ op.name()
						+ " -> "
						+ successorTrees);
			}
		}

		final long timeBefore = System.nanoTime();
		switch (op)
		{
			case EMPTY_LIST:
			{
				// Push an empty list node and continue.
				assert successorTrees.tupleSize() == 1;
				final List<A_Phrase> newArgsSoFar =
					append(argsSoFar, ListNodeDescriptor.empty());
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case APPEND_ARGUMENT:
			{
				// Append the item that's the last thing onto the list that's
				// the second last thing. Pop both and push the new list (the
				// original list must not change), then continue.
				assert successorTrees.tupleSize() == 1;
				final A_Phrase value = last(argsSoFar);
				final List<A_Phrase> poppedOnce = withoutLast(argsSoFar);
				final A_Phrase oldNode = last(poppedOnce);
				final A_Phrase listNode = oldNode.copyWith(value);
				final List<A_Phrase> newArgsSoFar =
					append(withoutLast(poppedOnce), listNode);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case PREPEND:
			{
				// Prepend the item that's the last thing onto the list that's
				// the second last thing. Pop both and push the new list (the
				// original list must not change), then continue.
				assert successorTrees.tupleSize() == 1;
				final A_Phrase value = last(argsSoFar);
				final List<A_Phrase> poppedOnce = withoutLast(argsSoFar);
				final A_Phrase oldNode = last(poppedOnce);
				final A_Phrase listNode = oldNode.prependWith(value);
				final List<A_Phrase> newArgsSoFar =
					append(withoutLast(poppedOnce), listNode);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case SAVE_PARSE_POSITION:
			{
				// Push current parse position on the mark stack.
				assert successorTrees.tupleSize() == 1;
				final int marker =
					firstArgOrNull == null
						? start.position
						: initialTokenPosition.position;
				final List<Integer> newMarksSoFar =
					PrefixSharingList.append(marksSoFar, marker);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					newMarksSoFar,
					continuation);
				break;
			}
			case DISCARD_SAVED_PARSE_POSITION:
			{
				// Pop from the mark stack.
				assert successorTrees.tupleSize() == 1;
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					withoutLast(marksSoFar),
					continuation);
				break;
			}
			case ENSURE_PARSE_PROGRESS:
			{
				// Check for parser progress.  Abort this avenue of parsing if
				// the parse position is still equal to the position on the
				// mark stack.  Pop the old mark and push the new mark.
				assert successorTrees.tupleSize() == 1;
				final int oldMarker = last(marksSoFar);
				if (oldMarker == start.position)
				{
					// No progress has been made.  Reject this path.
					return;
				}
				final int newMarker = start.position;
				final List<Integer> newMarksSoFar =
					append(withoutLast(marksSoFar), newMarker);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					newMarksSoFar,
					continuation);
				break;
			}
			case PARSE_ARGUMENT:
			case PARSE_TOP_VALUED_ARGUMENT:
			{
				// Parse an argument and continue.
				assert successorTrees.tupleSize() == 1;
				final PartialSubexpressionList partialSubexpressionList =
					firstArgOrNull == null
						? continuation.superexpressions().advancedTo(
							successorTrees.tupleAt(1))
						: continuation.superexpressions;
				parseSendArgumentWithExplanationThen(
					start,
					op == PARSE_ARGUMENT
						? "argument"
						: "top-valued argument",
					firstArgOrNull,
					firstArgOrNull == null
						&& initialTokenPosition.position != start.position,
					op == PARSE_TOP_VALUED_ARGUMENT,
					new Con<CompilerSolution>(partialSubexpressionList)
					{
						@Override
						public void valueNotNull (
							final CompilerSolution solution)
						{
							final List<A_Phrase> newArgsSoFar =
								append(argsSoFar, solution.parseNode());
							eventuallyParseRestOfSendNode(
								solution.endState(),
								successorTrees.tupleAt(1),
								null,
								initialTokenPosition,
								// The argument counts as something that was
								// consumed if it's not a leading argument...
								firstArgOrNull == null,
								newArgsSoFar,
								marksSoFar,
								continuation);
						}
					});
				break;
			}
			case PARSE_VARIABLE_REFERENCE:
			{
				assert successorTrees.tupleSize() == 1;
				final PartialSubexpressionList partialSubexpressionList =
					firstArgOrNull == null
						? continuation.superexpressions().advancedTo(
							successorTrees.tupleAt(1))
						: continuation.superexpressions;
				parseSendArgumentWithExplanationThen(
					start,
					"variable reference",
					firstArgOrNull,
					firstArgOrNull == null
						&& initialTokenPosition.position != start.position,
					false,
					new Con<CompilerSolution>(
						partialSubexpressionList)
					{
						@Override
						public void valueNotNull (
							final CompilerSolution
								variableUseSolution)
						{
							assert successorTrees.tupleSize() == 1;
							final A_Phrase variableUse =
								variableUseSolution.parseNode();
							final A_Phrase rawVariableUse =
								variableUse.stripMacro();
							final ParserState afterUse =
								variableUseSolution.endState();
							if (!rawVariableUse.parseNodeKindIsUnder(
								VARIABLE_USE_NODE))
							{
								if (consumedAnything)
								{
									// At least one token besides the variable
									// use has been encountered, so go ahead and
									// report that we expected a variable.
									afterUse.expected(
										describeWhyVariableUseIsExpected(
											successorTrees.tupleAt(1)));
								}
								// It wasn't a variable use node, so give up.
								return;
							}
							// Make sure taking a reference is appropriate.
							final DeclarationKind declarationKind =
								rawVariableUse.declaration().declarationKind();
							if (!declarationKind.isVariable())
							{
								if (consumedAnything)
								{
									// Only complain about this not being a
									// variable if we've parsed something
									// besides the variable reference argument.
									afterUse.expected(
										"variable for reference argument to "
										+ "be assignable, not "
										+ declarationKind.nativeKindName());
								}
								return;
							}
							// Create a variable reference from this use.
							final A_Phrase rawVariableReference =
								ReferenceNodeDescriptor.fromUse(rawVariableUse);
							final A_Phrase variableReference =
								variableUse.isMacroSubstitutionNode()
									? MacroSubstitutionNodeDescriptor
										.fromOriginalSendAndReplacement(
											variableUse.macroOriginalSendNode(),
											rawVariableReference)
									: rawVariableReference;
							eventuallyParseRestOfSendNode(
								afterUse,
								successorTrees.tupleAt(1),
								null,
								initialTokenPosition,
								// The argument counts as something that was
								// consumed if it's not a leading argument...
								firstArgOrNull == null,
								append(argsSoFar, variableReference),
								marksSoFar,
								continuation);
						}
					});
				break;
			}
			case PARSE_ARGUMENT_IN_MODULE_SCOPE:
			{
				assert successorTrees.tupleSize() == 1;
				parseArgumentInModuleScopeThen(
					start,
					firstArgOrNull,
					argsSoFar,
					marksSoFar,
					initialTokenPosition,
					successorTrees,
					new Con<CompilerSolution>(
						continuation.superexpressions)
					{
						@Override
						public void valueNotNull(
							final CompilerSolution solution)
						{
							continuation.valueNotNull(solution);
						}
					});
				break;
			}
			case PARSE_ANY_RAW_TOKEN:
			case PARSE_RAW_KEYWORD_TOKEN:
			case PARSE_RAW_STRING_LITERAL_TOKEN:
			case PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN:
			{
				// Parse a raw token and continue.  In particular, push a
				// literal node whose token is a synthetic literal token whose
				// value is the actual token that was parsed.
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
				final A_Token newToken = parseRawTokenOrNull(start);
				if (newToken == null)
				{
					if (consumedAnything)
					{
						start.expected(
							"a token, not end of file");
					}
					break;
				}
				if (op == PARSE_RAW_KEYWORD_TOKEN
					&& newToken.tokenType() != KEYWORD)
				{
					if (consumedAnything)
					{
						start.expected(
							"a keyword token, not " + newToken.string());
					}
					break;
				}
				if (op == PARSE_RAW_STRING_LITERAL_TOKEN
					&& (newToken.tokenType() != LITERAL
						|| !newToken.literal().isInstanceOf(
							TupleTypeDescriptor.stringType())))
				{
					if (consumedAnything)
					{
						start.expected(
							"a string literal token, not "
								+ (newToken.tokenType() != LITERAL
									? newToken.string()
									: newToken.literal()));
					}
					break;
				}
				if (op == PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN
					&& (newToken.tokenType() != LITERAL
						|| !newToken.literal().isInstanceOf(
							IntegerRangeTypeDescriptor.wholeNumbers())))
				{
					if (consumedAnything)
					{
						start.expected(
							"a whole number literal token, not "
								+ (newToken.tokenType() != LITERAL
									   ? newToken.string()
									   : newToken.literal()));
					}
					break;
				}

				final ParserState afterToken = start.afterToken();
				final A_Token syntheticToken = LiteralTokenDescriptor.create(
					newToken.string(),
					newToken.leadingWhitespace(),
					newToken.trailingWhitespace(),
					newToken.start(),
					newToken.lineNumber(),
					newToken.tokenIndex(),
					SYNTHETIC_LITERAL,
					newToken);
				final A_Phrase literalNode =
					fromToken(syntheticToken);
				final List<A_Phrase> newArgsSoFar =
					append(argsSoFar, literalNode);
				eventuallyParseRestOfSendNode(
					afterToken,
					successorTrees.tupleAt(1),
					null,
					initialTokenPosition,
					true,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case CONCATENATE:
			{
				assert successorTrees.tupleSize() == 1;
				final A_Phrase right = last(argsSoFar);
				final List<A_Phrase> popped1 = withoutLast(argsSoFar);
				A_Phrase concatenated = last(popped1);
				final List<A_Phrase> popped2 = withoutLast(popped1);
				for (A_Phrase rightElement : right.expressionsTuple())
				{
					concatenated = concatenated.copyWith(rightElement);
				}
				final List<A_Phrase> newArgsSoFar =
					append(popped2, concatenated);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case RESERVED_15:
				break;
			case BRANCH:
				// $FALL-THROUGH$
				// Fall through.  The successorTrees will be different
				// for the jump versus parallel-branch.
			case JUMP:
			{
				for (int i = successorTrees.tupleSize(); i >= 1; i--)
				{
					final A_BundleTree successorTree =
						successorTrees.tupleAt(i);
					eventuallyParseRestOfSendNode(
						start,
						successorTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
				}
				break;
			}
			case PARSE_PART:
			case PARSE_PART_CASE_INSENSITIVELY:
			case TYPE_CHECK_ARGUMENT:
			{
				assert false
					: op.name() + " instruction should not be dispatched";
				break;
			}
			case CHECK_ARGUMENT:
			{
				// CheckArgument.  An actual argument has just been parsed (and
				// pushed).  Make sure it satisfies any grammatical
				// restrictions.  The message bundle tree's lazy prefilter map
				// deals with that efficiently.
				assert successorTrees.tupleSize() == 1;
				assert firstArgOrNull == null;
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					null,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case CONVERT:
			{
				// Convert the argument.
				assert successorTrees.tupleSize() == 1;
				final A_Phrase input = last(argsSoFar);
				final AtomicBoolean sanityFlag = new AtomicBoolean();
				op.conversionRule(instruction).convert(
					input,
					start,
					initialTokenPosition,
					new Continuation1<A_Phrase>()
					{
						@Override
						public void value (
							final @Nullable A_Phrase replacementExpression)
						{
							assert sanityFlag.compareAndSet(false, true);
							assert replacementExpression != null;
							final List<A_Phrase> newArgsSoFar =
								append(
									withoutLast(argsSoFar),
									replacementExpression);
							eventuallyParseRestOfSendNode(
								start,
								successorTrees.tupleAt(1),
								firstArgOrNull,
								initialTokenPosition,
								consumedAnything,
								newArgsSoFar,
								marksSoFar,
								continuation);
						}
					},
					new Continuation1<Throwable>()
					{
						@Override
						public void value (final @Nullable Throwable e)
						{
							// Deal with a failed conversion.  As of 2016-08-28,
							// this can only happen during an expression
							// evaluation.
							assert sanityFlag.compareAndSet(false, true);
							assert e != null;
							start.expected(new Describer()
							{
								@Override
								public void describeThen (
									final Continuation1<String> withString)
								{
									withString.value(
										"evaluation of expression not to have "
											+ "thrown Java exception:\n"
											+ trace(e));
								}
							});
						}
					});
				break;
			}
			case PREPARE_TO_RUN_PREFIX_FUNCTION:
			{
				/*
				 * Prepare a copy of the arguments that have been parsed so far,
				 * and push them as a list node onto the parse stack, in
				 * preparation for a RUN_PREFIX_FUNCTION, which must come next.
				 * The current instruction and the following one are always
				 * generated at the point a section checkpoint (§) is found in
				 * a method name.
				 *
				 * Also note that this instruction was detected specially by
				 * MessageBundleTreeDescriptor.o_Expand(AvailObject), preventing
				 * the successors from having multiple bundles in the same tree.
				 */
				List<A_Phrase> stackCopy = argsSoFar;
				// Only do N-1 steps.  We simply couldn't encode zero as an
				// operand, so we always bias by one automatically.
				for (int i = op.fixupDepth(instruction); i > 1; i--)
				{
					// Pop the last element and append it to the second last.
					final A_Phrase value = last(stackCopy);
					final List<A_Phrase> poppedOnce = withoutLast(stackCopy);
					final A_Phrase oldNode = last(poppedOnce);
					final A_Phrase listNode = oldNode.copyWith(value);
					stackCopy = append(withoutLast(poppedOnce), listNode);
				}
				assert stackCopy.size() == 1;
				final A_Phrase newListNode = stackCopy.get(0);
				final List<A_Phrase> newStack = append(argsSoFar, newListNode);
				for (final A_BundleTree successorTree : successorTrees)
				{
					eventuallyParseRestOfSendNode(
						start,
						successorTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						newStack,
						marksSoFar,
						continuation);
				}
				break;
			}
			case RUN_PREFIX_FUNCTION:
			{
				/* Extract the list node pushed by the
				 * PREPARE_TO_RUN_PREFIX_FUNCTION instruction that should have
				 * just run.  Pass it to the indicated prefix function, which
				 * will communicate parser state changes via fiber globals.
				 *
				 * We are always operating on a single definition parse plan
				 * here, because the message bundle tree's o_Expand(AvailObject)
				 * detected the previous instruction, always a
				 * PREPARE_TO_RUN_PREFIX_FUNCTION, and put each plan into a new
				 * tree.  Go to that plan's (macro) definition to find its
				 * prefix functions, subscripting that tuple by this
				 * RUN_PREFIX_FUNCTION's operand.
				 */
				assert successorTrees.tupleSize() == 1;
				final A_BundleTree successorTree = successorTrees.tupleAt(1);
				// Look inside the only successor to find the only bundle.
				final A_Map bundlesMap =
					successorTree.allParsingPlansInProgress();
				assert bundlesMap.mapSize() == 1;
				final A_Map submap = bundlesMap.mapIterable().next().value();
				assert submap.mapSize() == 1;
				final A_Definition definition =
					submap.mapIterable().next().key();
				final A_Tuple prefixFunctions = definition.prefixFunctions();
				final int prefixIndex = op.prefixFunctionSubscript(instruction);
				final A_Function prefixFunction =
					prefixFunctions.tupleAt(prefixIndex);
				final A_Phrase prefixArgumentsList = last(argsSoFar);
				final List<A_Phrase> withoutPrefixArguments =
					withoutLast(argsSoFar);
				final List<AvailObject> listOfArgs = TupleDescriptor.toList(
					prefixArgumentsList.expressionsTuple());
				runPrefixFunctionThen(
					start,
					successorTree,
					prefixFunction,
					listOfArgs,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					withoutPrefixArguments,
					marksSoFar,
					continuation);
				break;
			}
			case PERMUTE_LIST:
			{
				final int permutationIndex = op.permutationIndex(instruction);
				final A_Tuple permutation =
					MessageSplitter.permutationAtIndex(permutationIndex);
				final A_Phrase poppedList = last(argsSoFar);
				List<A_Phrase> stack = withoutLast(argsSoFar);
				stack = append(
					stack,
					PermutedListNodeDescriptor.fromListAndPermutation(
						poppedList, permutation));
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					stack,
					marksSoFar,
					continuation);
				break;
			}
			case CHECK_AT_LEAST:
			{
				final int limit = op.requiredMinimumSize(instruction);
				final A_Phrase top = last(argsSoFar);
				if (top.expressionsSize() >= limit)
				{
					eventuallyParseRestOfSendNode(
						start,
						successorTrees.tupleAt(1),
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
				}
				break;
			}
			case CHECK_AT_MOST:
			{
				final int limit = op.requiredMaximumSize(instruction);
				final A_Phrase top = last(argsSoFar);
				if (top.expressionsSize() <= limit)
				{
					eventuallyParseRestOfSendNode(
						start,
						successorTrees.tupleAt(1),
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
				}
				break;
			}
			case WRAP_IN_LIST:
			{
				assert successorTrees.tupleSize() == 1;
				final int listSize = op.listSize(instruction);
				final int totalSize = argsSoFar.size();
				final List<A_Phrase> unpopped =
					argsSoFar.subList(0, totalSize - listSize);
				final List<A_Phrase> popped =
					argsSoFar.subList(totalSize - listSize, totalSize);
				final A_Phrase newListNode = ListNodeDescriptor.newExpressions(
					TupleDescriptor.fromList(popped));
				final List<A_Phrase> newArgsSoFar =
					append(unpopped, newListNode);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case PUSH_LITERAL:
			{
				final AvailObject constant = MessageSplitter.constantForIndex(
					op.literalIndex(instruction));
				final A_Token innerToken = initialTokenPosition.peekToken();
				final A_Token token = LiteralTokenDescriptor.create(
					StringDescriptor.from(constant.toString()),
					innerToken.leadingWhitespace(),
					innerToken.trailingWhitespace(),
					innerToken.start(),
					innerToken.lineNumber(),
					innerToken.tokenIndex(),
					LITERAL,
					constant);
				final A_Phrase literalNode =
					fromToken(token);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					append(argsSoFar, literalNode),
					marksSoFar,
					continuation);
				break;
			}
			case REVERSE_STACK:
			{
				assert successorTrees.tupleSize() == 1;
				final int depthToReverse = op.depthToReverse(instruction);
				final int totalSize = argsSoFar.size();
				final List<A_Phrase> unpopped =
					argsSoFar.subList(0, totalSize - depthToReverse);
				final List<A_Phrase> popped =
					new ArrayList<>(
						argsSoFar.subList(
							totalSize - depthToReverse, totalSize));
				Collections.reverse(popped);
				final List<A_Phrase> newArgsSoFar =
					new ArrayList<>(unpopped);
				newArgsSoFar.addAll(popped);
				eventuallyParseRestOfSendNode(
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
		}
		final long timeAfter = System.nanoTime();
		final AvailThread thread = (AvailThread) Thread.currentThread();
		op.parsingStatisticInNanoseconds.record(
			timeAfter - timeBefore,
			thread.interpreter.interpreterIndex);
	}

	/**
	 * Attempt the specified prefix function.  It may throw an {@link
	 * AvailRejectedParseException} if a specific parsing problem needs to be
	 * described.
	 *
	 * @param start
	 *        The {@link ParserState} at which the prefix function is being run.
	 * @param successorTree
	 *        The {@link A_BundleTree} with which to continue parsing.
	 * @param prefixFunction
	 *        The prefix {@link A_Function} to invoke.
	 * @param listOfArgs
	 *        The argument {@linkplain A_Phrase phrases} to pass to the prefix
	 *        function.
	 * @param firstArgOrNull
	 *        The leading argument if it has already been parsed but not
	 *        consumed.
	 * @param initialTokenPosition
	 *        The {@link ParserState} at which the current potential macro
	 *        invocation started.
	 * @param consumedAnything
	 *        Whether any tokens have been consumed so far at this macro site.
	 * @param argsSoFar
	 *        The stack of parse nodes.
	 * @param marksSoFar
	 *        The stack of markers that detect epsilon transitions
	 *        (subexpressions consisting of no tokens).
	 * @param continuation
	 *        What should eventually be done with the completed macro
	 *        invocation, should parsing ever get that far.
	 */
	private void runPrefixFunctionThen (
		final ParserState start,
		final A_BundleTree successorTree,
		final A_Function prefixFunction,
		final List<AvailObject> listOfArgs,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con<CompilerSolution> continuation)
	{
		if (!prefixFunction.kind().acceptsListOfArgValues(listOfArgs))
		{
			return;
		}
		compilationContext.startWorkUnit();
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			prefixFunction.kind().returnType(),
			compilationContext.loader(),
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					final A_RawFunction code = prefixFunction.code();
					return StringDescriptor.format(
						"Macro prefix %s, in %s:%d",
						code.methodName(),
						code.module().moduleName(),
						code.startingLineNumber());
				}
			});
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		final A_Tuple constituentTokens = initialTokenPosition.upTo(start);
		final A_Map withTokens = start.clientDataMap.mapAtPuttingCanDestroy(
			allTokensKey(),
			constituentTokens,
			false).makeImmutable();
		A_Map fiberGlobals = fiber.fiberGlobals();
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataGlobalKey(), withTokens, true);
		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(compilationContext.getTextInterface());
		final AtomicBoolean hasRunEither = new AtomicBoolean(false);
		fiber.resultContinuation(compilationContext.workUnitCompletion(
			start.peekToken(),
			hasRunEither,
			new Continuation1<AvailObject>()
			{
				@Override
				public void value (
					final @Nullable AvailObject ignoredResult)
				{
					// The prefix function ran successfully.
					final A_Map replacementClientDataMap =
						fiber.fiberGlobals().mapAt(clientDataGlobalKey());
					final ParserState newState = new ParserState(
						start.position,
						start.tokenIndex,
						replacementClientDataMap);
					eventuallyParseRestOfSendNode(
						newState,
						successorTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
				}
			}));
		fiber.failureContinuation(compilationContext.workUnitCompletion(
			start.peekToken(),
			hasRunEither,
			new Continuation1<Throwable>()
			{
				@Override
				public void value (final @Nullable Throwable e)
				{
					assert e != null;
					// The prefix function failed in some way.
					if (e instanceof AvailAcceptedParseException)
					{
						// Prefix functions are allowed to explicitly accept a
						// parse.
						final A_Map replacementClientDataMap =
							fiber.fiberGlobals().mapAt(clientDataGlobalKey());
						final ParserState newState = new ParserState(
							start.position,
							start.tokenIndex,
							replacementClientDataMap);
						eventuallyParseRestOfSendNode(
							newState,
							successorTree,
							firstArgOrNull,
							initialTokenPosition,
							consumedAnything,
							argsSoFar,
							marksSoFar,
							continuation);
					}
					if (e instanceof AvailRejectedParseException)
					{
						final AvailRejectedParseException stronger =
							(AvailRejectedParseException) e;
						start.expected(
							stronger.rejectionString().asNativeString());
					}
					else
					{
						start.expected(new FormattingDescriber(
							"prefix function not to have failed with:\n%s", e));
					}
				}
			}));
		Interpreter.runOutermostFunction(
			runtime, fiber, prefixFunction, listOfArgs);
	}

	/**
	 * Check the proposed message send for validity. Use not only the applicable
	 * {@linkplain MethodDefinitionDescriptor method definitions}, but also any
	 * semantic restrictions. The semantic restrictions may choose to
	 * {@linkplain P_RejectParsing reject the parse}, indicating that the
	 * argument types are mutually incompatible.
	 *
	 * @param bundle
	 *        A {@linkplain MessageBundleDescriptor message bundle}.
	 * @param argTypes
	 *        The argument types.
	 * @param state
	 *        The {@linkplain ParserState parser state} after the function
	 *        evaluates successfully.
	 * @param macroOrNil
	 *        A {@link MacroDefinitionDescriptor macro definition} if this is
	 *        for a macro invocation, otherwise {@code nil}.
	 * @param originalOnSuccess
	 *        What to do with the strengthened return type.
	 * @param originalOnFailure
	 *        What to do if validation fails.
	 */
	private void validateArgumentTypes (
		final A_Bundle bundle,
		final List<? extends A_Type> argTypes,
		final A_Definition macroOrNil,
		final ParserState state,
		final Continuation1<A_Type> originalOnSuccess,
		final Continuation1<Describer> originalOnFailure)
	{
		final A_Method method = bundle.bundleMethod();
		final A_Tuple methodDefinitions = method.definitionsTuple();
		final A_Set restrictions = method.semanticRestrictions();
		// Filter the definitions down to those that are locally most specific.
		// Fail if more than one survives.
		compilationContext.startWorkUnit();
		final AtomicBoolean hasRunEither = new AtomicBoolean(false);
		final Continuation1<A_Type> onSuccess =
			compilationContext.workUnitCompletion(
				state.peekToken(), hasRunEither, originalOnSuccess);
		final Continuation1<Describer> onFailure =
			compilationContext.workUnitCompletion(
				state.peekToken(), hasRunEither, originalOnFailure);
		if (methodDefinitions.tupleSize() > 0)
		{
			// There are method definitions.
			for (
				int index = 1, end = argTypes.size();
				index <= end;
				index++)
			{
				final int finalIndex = index;
				final A_Type finalType = argTypes.get(finalIndex - 1);
				if (finalType.isBottom() || finalType.isTop())
				{
					onFailure.value(new Describer()
					{
						@Override
						public void describeThen (final Continuation1<String> c)
						{
							Interpreter.stringifyThen(
								runtime,
								compilationContext.getTextInterface(),
								argTypes.get(finalIndex - 1),
								new Continuation1<String>()
								{
									@Override
									public void value (final @Nullable String s)
									{
										assert s != null;
										c.value(String.format(
											"argument #%d of message %s "
											+ "to have a type other than %s",
											finalIndex,
											bundle.message().atomName(),
											s));
									}
								});
						}
					});
					return;
				}
			}
		}
		// Find all method definitions that could match the argument types.
		// Only consider definitions that are defined in the current module or
		// an ancestor.
		final A_Set allAncestors = compilationContext.module().allAncestors();
		final List<A_Definition> filteredByTypes = macroOrNil.equalsNil()
			? method.filterByTypes(argTypes)
			: Collections.singletonList(macroOrNil);
		final List<A_Definition> satisfyingDefinitions = new ArrayList<>();
		for (final A_Definition definition : filteredByTypes)
		{
			if (allAncestors.hasElement(definition.definitionModule()))
			{
				satisfyingDefinitions.add(definition);
			}
		}
		if (satisfyingDefinitions.isEmpty())
		{
			onFailure.value(describeWhyDefinitionsAreInapplicable(
				bundle,
				argTypes,
				macroOrNil.equalsNil()
					? methodDefinitions
					: TupleDescriptor.from(macroOrNil),
				allAncestors));
			return;
		}
		// Compute the intersection of the return types of the possible callees.
		// Macro bodies return phrases, but that's not what we want here.
		final Mutable<A_Type> intersection;
		if (macroOrNil.equalsNil())
		{
			intersection = new Mutable<>(
				satisfyingDefinitions.get(0).bodySignature().returnType());
			for (int i = 1, end = satisfyingDefinitions.size(); i < end; i++)
			{
				intersection.value = intersection.value.typeIntersection(
					satisfyingDefinitions.get(i).bodySignature().returnType());
			}
		}
		else
		{
			// The macro's semantic type (expressionType) is the authoritative
			// type to check against the macro body's actual return phrase's
			// semantic type.  Semantic restrictions may still narrow it below.
			intersection = new Mutable<>(
				macroOrNil.bodySignature().returnType().expressionType());
		}
		// Determine which semantic restrictions are relevant.
		final List<A_SemanticRestriction> restrictionsToTry =
			new ArrayList<>(restrictions.setSize());
		for (final A_SemanticRestriction restriction : restrictions)
		{
			if (allAncestors.hasElement(restriction.definitionModule()))
			{
				if (restriction.function().kind().acceptsListOfArgValues(
					argTypes))
				{
					restrictionsToTry.add(restriction);
				}
			}
		}
		// If there are no relevant semantic restrictions, then just invoke the
		// success continuation with the intersection and exit early.
		if (restrictionsToTry.isEmpty())
		{
			onSuccess.value(intersection.value);
			return;
		}
		// Run all relevant semantic restrictions, in parallel, computing the
		// type intersection of their results.
		final Mutable<Integer> outstanding = new Mutable<>(
			restrictionsToTry.size());
		final List<Describer> failureMessages = new ArrayList<>();
		final Continuation0 whenDone = new Continuation0()
		{
			@Override
			public void value ()
			{
				assert outstanding.value == 0;
				if (failureMessages.isEmpty())
				{
					onSuccess.value(intersection.value);
					return;
				}
				onFailure.value(new Describer()
				{
					int index = 0;

					@Override
					public void describeThen (
						final Continuation1<String> continuation)
					{
						assert !failureMessages.isEmpty();
						final StringBuilder builder = new StringBuilder();
						final MutableOrNull<Continuation0> looper =
							new MutableOrNull<>(null);
						looper.value = new Continuation0()
						{
							@Override
							public void value()
							{
								failureMessages.get(index).describeThen(
									new Continuation1<String>()
									{
										@Override
										public void value (
											final @Nullable String string)
										{
											if (index > 0)
											{
												builder.append(
													"\n-------------------\n");
											}
											builder.append(string);
											index++;
											if (index < failureMessages.size())
											{
												looper.value().value();
											}
											else
											{
												continuation.value(
													builder.toString());
											}
										}
									});
							}
						};
						looper.value().value();
					}
				});
			}
		};
		final Continuation1<AvailObject> intersectAndDecrement =
			new Continuation1<AvailObject>()
			{
				@Override
				public void value (
					final @Nullable AvailObject restrictionType)
				{
					assert restrictionType != null;
					synchronized (outstanding)
					{
						if (failureMessages.isEmpty())
						{
							intersection.value =
								intersection.value.typeIntersection(
									restrictionType);
						}
						outstanding.value--;
						if (outstanding.value == 0)
						{
							whenDone.value();
						}
					}
				}
			};
		final Continuation1<Throwable> failAndDecrement =
			new Continuation1<Throwable>()
			{
				@Override
				public void value (final @Nullable Throwable e)
				{
					assert e != null;
					if (e instanceof AvailAcceptedParseException)
					{
						// This is really a success.
						intersectAndDecrement.value(TOP.o());
						return;
					}
					final Describer message;
					if (e instanceof AvailRejectedParseException)
					{
						final AvailRejectedParseException rej =
							(AvailRejectedParseException) e;
						message = new Describer()
						{
							@Override
							public void describeThen(
								final Continuation1<String> c)
							{
								c.value(
									rej.rejectionString().asNativeString()
									+ " (while parsing send of "
									+ bundle.message().atomName()
										.asNativeString()
									+ ")");
							}
						};
					}
					else if (e instanceof FiberTerminationException)
					{
						message = new Describer()
						{
							@Override
							public void describeThen(
								final Continuation1<String> c)
							{
								c.value(
									"semantic restriction not to raise an "
									+ "unhandled exception (while parsing "
									+ "send of "
									+ bundle.message().atomName()
										.asNativeString()
									+ "):\n\t"
									+ e.toString());
							}
						};
					}
					else if (e instanceof AvailAssertionFailedException)
					{
						final AvailAssertionFailedException ex =
							(AvailAssertionFailedException) e;
						message = new SimpleDescriber(
							"assertion not to have failed "
							+ "(while parsing send of "
							+ bundle.message().atomName().asNativeString()
							+ "):\n\t"
							+ ex.assertionString().asNativeString());
					}
					else
					{
						message = new FormattingDescriber(
							"unexpected error: %s", e);
					}
					synchronized (outstanding)
					{
						failureMessages.add(message);
						outstanding.value--;
						if (outstanding.value == 0)
						{
							whenDone.value();
						}
					}
				}
			};
		// Launch the semantic restriction in parallel.
		for (final A_SemanticRestriction restriction : restrictionsToTry)
		{
			evaluateSemanticRestrictionFunctionThen(
				restriction,
				argTypes,
				intersectAndDecrement,
				failAndDecrement);
		}
	}

	/**
	 * Given a collection of definitions, whether for methods or for macros, but
	 * not both, and given argument types (phrase types in the case of macros)
	 * for a call site, produce a reasonable explanation of why the definitions
	 * were all rejected.
	 *
	 * @param bundle
	 *        The target bundle for the call site.
	 * @param argTypes
	 *        The types of the arguments, or their phrase types if this is for a
	 *        macro lookup
	 * @param definitionsTuple
	 *        The method or macro (but not both) definitions that were visible
	 *        (defined in the current or an ancestor module) but not applicable.
	 * @param allAncestorModules
	 *        The {@linkplain A_Set set} containing the current {@linkplain
	 *        A_Module module} and its ancestors.
	 * @return
	 *        A {@link Describer} able to describe why none of the definitions
	 *        were applicable.
	 */
	private Describer describeWhyDefinitionsAreInapplicable (
		final A_Bundle bundle,
		final List<? extends A_Type> argTypes,
		final A_Tuple definitionsTuple,
		final A_Set allAncestorModules)
	{
		assert definitionsTuple.tupleSize() > 0;
		return new Describer()
		{
			@Override
			public void describeThen (final Continuation1<String> c)
			{
				final String kindOfDefinition =
					definitionsTuple.tupleAt(1).isMacroDefinition()
						? "macro"
						: "method";
				final List<A_Definition> allVisible = new ArrayList<>();
				for (final A_Definition def : definitionsTuple)
				{
					if (allAncestorModules.hasElement(def.definitionModule()))
					{
						allVisible.add(def);
					}
				}
				final List<Integer> allFailedIndices = new ArrayList<>(3);
				each_arg:
				for (int i = 1, end = argTypes.size(); i <= end; i++)
				{
					for (final A_Definition definition : allVisible)
					{
						final A_Type sig = definition.bodySignature();
						if (argTypes.get(i - 1).isSubtypeOf(
							sig.argsTupleType().typeAtIndex(i)))
						{
							continue each_arg;
						}
					}
					allFailedIndices.add(i);
				}
				if (allFailedIndices.size() == 0)
				{
					// Each argument applied to at least one definition, so put
					// the blame on them all instead of none.
					for (int i = 1, end = argTypes.size(); i <= end; i++)
					{
						allFailedIndices.add(i);
					}
				}
				// Don't stringify all the argument types, just the failed ones.
				// And don't stringify the same value twice. Obviously side
				// effects in stringifiers won't work right here…
				final List<A_BasicObject> uniqueValues = new ArrayList<>();
				final Map<A_BasicObject, Integer> valuesToStringify =
					new HashMap<>();
				for (final int i : allFailedIndices)
				{
					final A_Type argType = argTypes.get(i - 1);
					if (!valuesToStringify.containsKey(argType))
					{
						valuesToStringify.put(argType, uniqueValues.size());
						uniqueValues.add(argType);
					}
					for (final A_Definition definition : allVisible)
					{
						final A_Type signatureArgumentsType =
							definition.bodySignature().argsTupleType();
						final A_Type sigType =
							signatureArgumentsType.typeAtIndex(i);
						if (!valuesToStringify.containsKey(sigType))
						{
							valuesToStringify.put(sigType, uniqueValues.size());
							uniqueValues.add(sigType);
						}
					}
				}
				Interpreter.stringifyThen(
					runtime,
					compilationContext.getTextInterface(),
					uniqueValues,
					new Continuation1<List<String>>()
					{
						@Override
						public void value (
							final @Nullable List<String> strings)
						{
							assert strings != null;
							@SuppressWarnings("resource")
							final Formatter builder = new Formatter();
							builder.format(
								"arguments at indices %s of message %s to "
								+ "match a visible %s definition:%n",
								allFailedIndices,
								bundle.message().atomName(),
								kindOfDefinition);
							builder.format("\tI got:%n");
							for (final int i : allFailedIndices)
							{
								final A_Type argType = argTypes.get(i - 1);
								final String s = strings.get(
									valuesToStringify.get(argType));
								builder.format("\t\t#%d = %s%n", i, s);
							}
							builder.format(
								"\tI expected%s:",
								allVisible.size() > 1 ? " one of" : "");
							for (final A_Definition definition : allVisible)
							{
								builder.format(
									"%n\t\tFrom module %s @ line #%s,",
									definition.definitionModule().moduleName(),
									definition.isMethodDefinition()
										? definition.bodyBlock().code()
											.startingLineNumber()
										: "unknown");
								final A_Type signatureArgumentsType =
									definition.bodySignature().argsTupleType();
								for (final int i : allFailedIndices)
								{
									final A_Type sigType =
										signatureArgumentsType.typeAtIndex(i);
									final String s = strings.get(
										valuesToStringify.get(sigType));
									builder.format("%n\t\t\t#%d = %s", i, s);
								}
							}
							assert !allVisible.isEmpty()
								: "No visible implementations; should have "
									+ "been excluded.";
							c.value(builder.toString());
						}
					});
			}
		};
	}

	/**
	 * Produce a {@link Describer} that says a variable use was expected, and
	 * indicates why.
	 *
	 * @param successorTree
	 *        The next {@link A_BundleTree} after the current instruction.
	 * @return The {@link Describer}.
	 */
	@InnerAccess Describer describeWhyVariableUseIsExpected (
		final A_BundleTree successorTree)
	{
		return new Describer()
		{
			@Override
			public void describeThen (
				final Continuation1<String> continuation)
			{
				final A_Set bundles =
					successorTree.allParsingPlansInProgress().keysAsSet();
				final StringBuilder builder = new StringBuilder();
				builder.append("a variable use, for one of:");
				if (bundles.setSize() > 2)
				{
					builder.append("\n\t");
				}
				else
				{
					builder.append(" ");
				}
				boolean first = true;
				for (final A_Bundle bundle : bundles)
				{
					if (!first)
					{
						builder.append(", ");
					}
					builder.append(bundle.message().atomName());
					first = false;
				}
				continuation.value(builder.toString());
			}
		};
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
	 * @param argumentsListNode
	 *            The {@linkplain ListNodeDescriptor list node} that will hold
	 *            all the arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle}
	 *            that identifies the message to be sent.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	private void completedSendNode (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final A_Phrase argumentsListNode,
		final A_Bundle bundle,
		final Con<CompilerSolution> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<>(true);
		final A_Method method = bundle.bundleMethod();
		final A_Tuple macroDefinitionsTuple = method.macroDefinitionsTuple();
		final A_Tuple definitionsTuple = method.definitionsTuple();
		if (definitionsTuple.tupleSize() + macroDefinitionsTuple.tupleSize()
			== 0)
		{
			stateAfterCall.expected(
				"there to be a method or macro definition for "
				+ bundle.message()
				+ ", but there wasn't");
			return;
		}

		// An applicable macro definition (even if ambiguous) prevents this site
		// from being a method invocation.
		A_Definition macro = NilDescriptor.nil();
		if (macroDefinitionsTuple.tupleSize() > 0)
		{
			// Find all macro definitions that could match the argument phrases.
			// Only consider definitions that are defined in the current module
			// or an ancestor.
			final A_Set allAncestors =
				compilationContext.module().allAncestors();
			final List<A_Definition> visibleDefinitions =
				new ArrayList<>(macroDefinitionsTuple.tupleSize());
			for (final A_Definition definition : macroDefinitionsTuple)
			{
				if (allAncestors.hasElement(definition.definitionModule()))
				{
					visibleDefinitions.add(definition);
				}
			}
			AvailErrorCode errorCode = null;
			if (visibleDefinitions.size() == macroDefinitionsTuple.tupleSize())
			{
				// All macro definitions are visible.  Use the lookup tree.
				try
				{
					macro = method.lookupMacroByPhraseTuple(
						argumentsListNode.expressionsTuple());
				}
				catch (final MethodDefinitionException e)
				{
					errorCode = e.errorCode();
				}
			}
			else
			{
				// Some of the macro definitions are not visible.  Search the
				// hard (but hopefully infrequent) way.
				final List<A_Type> phraseTypes =
					new ArrayList<>(method.numArgs());
				for (final A_Phrase argPhrase :
					argumentsListNode.expressionsTuple())
				{
					phraseTypes.add(AbstractEnumerationTypeDescriptor
						.withInstance(argPhrase));
				}
				final List<A_Definition> filtered = new ArrayList<>();
				for (final A_Definition macroDefinition : visibleDefinitions)
				{
					if (macroDefinition.bodySignature()
						.couldEverBeInvokedWith(phraseTypes))
					{
						filtered.add(macroDefinition);
					}
				}

				if (filtered.size() == 0)
				{
					// Nothing is visible.
					stateAfterCall.expected(
						"perhaps some definition of the macro "
						+ bundle.message()
						+ " to be visible");
					errorCode = E_NO_METHOD_DEFINITION;
					// Fall through.
				}
				else if (filtered.size() == 1)
				{
					macro = filtered.get(0);
				}
				else
				{
					// Find the most specific macro(s).
					assert filtered.size() > 1;
					final List<A_Definition> mostSpecific = new ArrayList<>();
					for (final A_Definition candidate : filtered)
					{
						boolean isMostSpecific = true;
						for (final A_Definition other : filtered)
						{
							if (!candidate.equals(other))
							{
								if (candidate.bodySignature()
									.acceptsArgTypesFromFunctionType(
										other.bodySignature()))
								{
									isMostSpecific = false;
									break;
								}
							}
						}
						if (isMostSpecific)
						{
							mostSpecific.add(candidate);
						}
					}
					assert mostSpecific.size() >= 1;
					if (mostSpecific.size() == 1)
					{
						// There is one most-specific macro.
						macro = mostSpecific.get(0);
					}
					else
					{
						// There are multiple most-specific macros.
						errorCode = E_AMBIGUOUS_METHOD_DEFINITION;
					}
				}
			}

			if (macro.equalsNil())
			{
				// Failed lookup.
				if (errorCode != E_NO_METHOD_DEFINITION)
				{
					final AvailErrorCode finalErrorCode = errorCode;
					assert finalErrorCode != null;
					stateAfterCall.expected(
						new Describer()
						{
							@Override
							public void describeThen (
								final Continuation1<String> withString)
							{
								final String string =
									finalErrorCode ==
											E_AMBIGUOUS_METHOD_DEFINITION
										? "unambiguous definition of macro "
											+ bundle.message()
										: "successful macro lookup, not: "
											+ finalErrorCode.name();
								withString.value(string);
							}
						});
					// Don't try to treat it as a method invocation.
					return;
				}
				if (definitionsTuple.tupleSize() == 0)
				{
					// There are only macro definitions, but the arguments were
					// not the right types.
					final List<A_Type> phraseTypes =
						new ArrayList<>(method.numArgs());
					for (final A_Phrase argPhrase :
						argumentsListNode.expressionsTuple())
					{
						phraseTypes.add(AbstractEnumerationTypeDescriptor
							.withInstance(argPhrase));
					}
					stateAfterCall.expected(
						describeWhyDefinitionsAreInapplicable(
							bundle,
							phraseTypes,
							macroDefinitionsTuple,
							allAncestors));
					// Don't report it as a failed method lookup, since there
					// were none.
					return;
				}
				// No macro definition matched, and there are method definitions
				// also possible, so fall through and treat it as a potential
				// method invocation site instead.
			}
			// Fall through to test semantic restrictions and run the macro if
			// one was found.
		}
		// It invokes a method (not a macro).  We compute the union of the
		// superUnionType() and the expressionType() for lookup, since if this
		// is a supercall we want to know what semantic restrictions and
		// function return types will be reached by the method definition(s)
		// actually being invoked.
		final A_Type argTupleType =
			argumentsListNode.superUnionType().typeUnion(
				argumentsListNode.expressionType());
		final int argCount = argumentsListNode.expressionsSize();
		final List<A_Type> argTypes = new ArrayList<>(argCount);
		for (int i = 1; i <= argCount; i++)
		{
			argTypes.add(argTupleType.typeAtIndex(i));
		}
		// Parsing a macro send must not affect the scope.
		final ParserState afterState = new ParserState(
			stateAfterCall.position,
			stateAfterCall.tokenIndex,
			stateBeforeCall.clientDataMap);
		final A_Definition finalMacro = macro;
		// Validate the message send before reifying a send phrase.
		validateArgumentTypes(
			bundle,
			argTypes,
			finalMacro,
			stateAfterCall,
			new Continuation1<A_Type>()
			{
				@Override
				public void value (final @Nullable A_Type expectedYieldType)
				{
					assert expectedYieldType != null;
					if (finalMacro.equalsNil())
					{
						final A_Phrase sendNode = SendNodeDescriptor.from(
							stateBeforeCall.upTo(stateAfterCall),
							bundle,
							argumentsListNode,
							expectedYieldType);
						compilationContext.attempt(
							afterState,
							continuation,
							new CompilerSolution(
								afterState, sendNode));
						return;
					}
					completedSendNodeForMacro(
						stateBeforeCall,
						stateAfterCall,
						argumentsListNode,
						bundle,
						finalMacro,
						expectedYieldType,
						new Con<CompilerSolution>(
							continuation.superexpressions)
						{
							@Override
							public void valueNotNull(
								final CompilerSolution macroSolution)
							{
								assert macroSolution.parseNode()
									.isMacroSubstitutionNode();
								continuation.value(macroSolution);
							}
						});
				}
			},
			new Continuation1<Describer>()
			{
				@Override
				public void value (
					final @Nullable Describer errorGenerator)
				{
					assert errorGenerator != null;
					valid.value = false;
					stateAfterCall.expected(errorGenerator);
				}
			});
	}

	/**
	 * Parse an argument to a message send. Backtracking will find all valid
	 * interpretations.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param kindOfArgument
	 *            A {@link String}, in the form of a noun phrase, saying the
	 *            kind of argument that is expected.
	 * @param firstArgOrNull
	 *            Either a parse node to use as the argument, or null if we
	 *            should parse one now.
	 * @param canReallyParse
	 *            Whether any tokens may be consumed.  This should be false
	 *            specifically when the leftmost argument of a leading-argument
	 *            message is being parsed.
	 * @param wrapInLiteral
	 *            Whether the argument should be wrapped inside a literal node.
	 *            This allows statements to be more easily processed by macros.
	 * @param continuation
	 *            What to do with the argument.
	 */
	private void parseSendArgumentWithExplanationThen (
		final ParserState start,
		final String kindOfArgument,
		final @Nullable A_Phrase firstArgOrNull,
		final boolean canReallyParse,
		final boolean wrapInLiteral,
		final Con<CompilerSolution> continuation)
	{
		if (firstArgOrNull == null)
		{
			// There was no leading argument, or it has already been accounted
			// for.  If we haven't actually consumed anything yet then don't
			// allow a *leading* argument to be parsed here.  That would lead to
			// ambiguous left-recursive parsing.
			if (canReallyParse)
			{
				parseExpressionThen(
					start,
					new Con<CompilerSolution>(
						continuation.superexpressions)
					{
						@Override
						public void valueNotNull (
							CompilerSolution solution)
						{
							// Only accept a ⊤-valued or ⊥-valued expression if
							// wrapInLiteral is true.
							final A_Phrase argument = solution.parseNode();
							final ParserState afterArgument =
								solution.endState();
							if (!wrapInLiteral)
							{
								final A_Type type = argument.expressionType();
								final @Nullable String badTypeName =
									type.isTop()
										? "⊤"
										: type.isBottom() ? "⊥" : null;
								if (badTypeName != null)
								{
									final Describer describer = new Describer()
									{
										@Override
										public void describeThen (
											final Continuation1<String> c)
										{
											StringBuilder b =
												new StringBuilder(100);
											b.append(kindOfArgument);
											b.append(
												" to have a type other than ");
											b.append(badTypeName);
											b.append(" in:");
											describeOn(
												continuation.superexpressions,
												b);
											c.value(b.toString());
										}
									};
									afterArgument.expected(describer);
									return;
								}
							}
							compilationContext.attempt(
								afterArgument,
								continuation,
								new CompilerSolution(
									afterArgument,
									wrapInLiteral
										? wrapAsLiteral(argument)
										: argument));
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

			// wrapInLiteral allows us to accept anything, even expressions that
			// are ⊤- or ⊥-valued.
			if (wrapInLiteral)
			{
				compilationContext.attempt(
					start,
					continuation,
					new CompilerSolution(
						start, wrapAsLiteral(firstArgOrNull)));
				return;
			}
			final A_Type expressionType = firstArgOrNull.expressionType();
			if (expressionType.isTop())
			{
				start.expected("leading argument not to be ⊤-valued.");
				return;
			}
			if (expressionType.isBottom())
			{
				start.expected("leading argument not to be ⊥-valued.");
				return;
			}
			compilationContext.attempt(
				start,
				continuation,
				new CompilerSolution(
					start, firstArgOrNull));
		}
	}

	/**
	 * Transform the argument, a {@linkplain A_Phrase phrase}, into a {@link
	 * LiteralNodeDescriptor literal phrase} whose value is the original phrase.
	 * If the given phrase is a {@linkplain MacroSubstitutionNodeDescriptor
	 * macro substitution phrase} then extract its {@link
	 * A_Phrase#apparentSendName()}, strip off the macro substitution, wrap the
	 * resulting expression in a literal node, then re-apply the same
	 * apparentSendName to the new literal node to produce another macro
	 * substitution phrase.
	 *
	 * @param phrase
	 *        A phrase.
	 * @return A literal phrase that yields the given phrase as its value.
	 */
	@InnerAccess A_Phrase wrapAsLiteral (
		final A_Phrase phrase)
	{
		if (phrase.isMacroSubstitutionNode())
		{
			return MacroSubstitutionNodeDescriptor
				.fromOriginalSendAndReplacement(
					phrase.macroOriginalSendNode(),
					syntheticFrom(phrase));
		}
		return syntheticFrom(phrase);
	}

	/**
	 * Parse an argument in the top-most scope.  This is an important capability
	 * for parsing type expressions, and the macro facility may make good use
	 * of it for other purposes.
	 *
	 * @param start
	 *            The position at which parsing should occur.
	 * @param firstArgOrNull
	 *            An optional already parsed expression which, if present, must
	 *            be used as a leading argument.  If it's {@code null} then no
	 *            leading argument has been parsed, and a request to parse a
	 *            leading argument should simply produce no local solution.
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
	 * @param marksSoFar
	 *            The stack of mark positions used to test if parsing certain
	 *            subexpressions makes progress.
	 * @param successorTrees
	 *            A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            MessageBundleTreeDescriptor message bundle trees} along which
	 *            to continue parsing if a local solution is found.
	 * @param continuation
	 *            What to do once we have a fully parsed send node (of which we
	 *            are currently parsing an argument).
	 */
	private void parseArgumentInModuleScopeThen (
		final ParserState start,
		final @Nullable A_Phrase firstArgOrNull,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final ParserState initialTokenPosition,
		final A_Tuple successorTrees,
		final Con<CompilerSolution> continuation)
	{
		// Parse an argument in the outermost (module) scope and continue.
		assert successorTrees.tupleSize() == 1;
		final A_Map clientDataInGlobalScope =
			start.clientDataMap.mapAtPuttingCanDestroy(
				compilerScopeMapKey(),
				MapDescriptor.empty(),
				false);
		final ParserState startInGlobalScope = new ParserState(
			start.position,
			start.tokenIndex,
			clientDataInGlobalScope);
		parseSendArgumentWithExplanationThen(
			startInGlobalScope,
			"module-scoped argument",
			firstArgOrNull,
			firstArgOrNull == null
				&& initialTokenPosition.position != start.position,
			false,  // Static argument can't be top-valued
			new Con<CompilerSolution>(continuation.superexpressions)
			{
				@Override
				public void valueNotNull (
					final CompilerSolution solution)
				{
					final A_Phrase newArg = solution.parseNode();
					final ParserState afterArg = solution.endState();
					if (newArg.hasSuperCast())
					{
						afterArg.expected(
							"global-scoped argument, not supercast");
						return;
					}
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
						final A_Set usedLocals =
							usesWhichLocalVariables(newArg);
						if (usedLocals.setSize() > 0)
						{
							// A leading argument was supplied which
							// used at least one local.  It shouldn't
							// have.
							afterArg.expected(new Describer()
							{
								@Override
								public void describeThen (
									final Continuation1<String> c)
								{
									final List<String> localNames =
										new ArrayList<>();
									for (final A_Phrase usedLocal : usedLocals)
									{
										final A_String name =
											usedLocal.token().string();
										localNames.add(name.asNativeString());
									}
									c.value(
										"a leading argument which "
										+ "was supposed to be parsed in "
										+ "module scope, but it referred to "
										+ "some local variables: "
										+ localNames.toString());
								}
							});
							return;
						}
					}
					final List<A_Phrase> newArgsSoFar =
						append(argsSoFar, newArg);
					final ParserState afterArgButInScope = new ParserState(
						afterArg.position,
						afterArg.tokenIndex,
						start.clientDataMap);
					eventuallyParseRestOfSendNode(
						afterArgButInScope,
						successorTrees.tupleAt(1),
						null,
						initialTokenPosition,
						// The argument counts as something that was
						// consumed if it's not a leading argument...
						firstArgOrNull == null,
						newArgsSoFar,
						marksSoFar,
						continuation);
				}
			});
	}

	/**
	 * A macro invocation has just been parsed.  Run its body now to produce a
	 * substitute phrase.
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.
	 * @param stateAfterCall
	 *            The parsing state after the message.
	 * @param argumentsListNode
	 *            The {@linkplain ListNodeDescriptor list node} that will hold
	 *            all the arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle} that
	 *            identifies the message to be sent.
	 * @param macroDefinitionToInvoke
	 *            The actual {@link MacroDefinitionDescriptor macro definition}
	 *            to invoke (statically).
	 * @param expectedYieldType
	 *            What semantic type the expression returned from the macro
	 *            invocation is expected to yield.  This will be narrowed
	 *            further by the actual phrase returned by the macro body,
	 *            although if it's not a send phrase then the resulting phrase
	 *            is <em>checked</em> against this expected yield type instead.
	 * @param continuation
	 *            What to do with the resulting send node solution.
	 */
	@InnerAccess void completedSendNodeForMacro (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final A_Phrase argumentsListNode,
		final A_Bundle bundle,
		final A_Definition macroDefinitionToInvoke,
		final A_Type expectedYieldType,
		final Con<CompilerSolution> continuation)
	{
		final A_Tuple argumentsTuple = argumentsListNode.expressionsTuple();
		final int argCount = argumentsTuple.tupleSize();
		// Strip off macro substitution wrappers from the arguments.  These
		// were preserved only long enough to test grammatical restrictions.
		final List<A_Phrase> argumentsList = new ArrayList<>(argCount);
		for (final A_Phrase argument : argumentsTuple)
		{
			argumentsList.add(argument);
		}
		// Capture all of the tokens that comprised the entire macro send.
		final A_Tuple constituentTokens = stateBeforeCall.upTo(stateAfterCall);
		assert constituentTokens.tupleSize() != 0;
		final A_Map withTokensAndBundle =
			stateAfterCall.clientDataMap
				.mapAtPuttingCanDestroy(
					allTokensKey(), constituentTokens, false)
				.mapAtPuttingCanDestroy(
					macroBundleKey(), bundle, true)
				.makeShared();
		compilationContext.startWorkUnit();
		final MutableOrNull<A_Map> clientDataAfterRunning =
			new MutableOrNull<>();
		final AtomicBoolean hasRunEither = new AtomicBoolean(false);
		if (AvailRuntime.debugMacroExpansions)
		{
			System.out.println(
				"PRE-EVAL:"
					+ stateAfterCall.peekToken().lineNumber()
					+ "("
					+ stateAfterCall.peekToken().start()
					+ ") "
					+ macroDefinitionToInvoke
					+ " "
					+ argumentsList);
		}
		evaluateMacroFunctionThen(
			macroDefinitionToInvoke,
			argumentsList,
			withTokensAndBundle,
			clientDataAfterRunning,
			compilationContext.workUnitCompletion(
				stateAfterCall.peekToken(),
				hasRunEither,
				new Continuation1<AvailObject>()
				{
					@Override
					public void value (final @Nullable AvailObject replacement)
					{
						assert replacement != null;
						assert clientDataAfterRunning.value != null;
						// In theory a fiber can produce anything, although you
						// have to mess with continuations to get it wrong.
						if (!replacement.isInstanceOfKind(
							PARSE_NODE.mostGeneralType()))
						{
							stateAfterCall.expected(
								Collections.singletonList(replacement),
								new Transformer1<List<String>, String>()
								{
									@Override
									public @Nullable String value (
										final @Nullable List<String> list)
									{
										assert list != null;
										return String.format(
											"Macro body for %s to have "
												+ "produced a phrase, not %s",
											bundle.message(),
											list.get(0));
									}
								});
							return;
						}
						final A_Phrase adjustedReplacement;
						if (replacement.parseNodeKindIsUnder(SEND_NODE))
						{
							// Strengthen the send node produced by the macro.
							adjustedReplacement = SendNodeDescriptor.from(
								replacement.tokens(),
								replacement.bundle(),
								replacement.argumentsListNode(),
								replacement.expressionType().typeIntersection(
									expectedYieldType));
						}
						else if (replacement.expressionType().isSubtypeOf(
							expectedYieldType))
						{
							// No adjustment necessary.
							adjustedReplacement = replacement;
						}
						else
						{
							// Not a send node, so it's impossible to
							// strengthen it to what the semantic
							// restrictions promised it should be.
							stateAfterCall.expected(
								"macro "
									+ bundle.message().atomName()
									+ " to produce either a send node to "
									+ "be strengthened, or a phrase that "
									+ "yields "
									+ expectedYieldType
									+ ", not "
									+ replacement);
							return;
						}
						// Continue after this macro invocation with whatever
						// client data was set up by the macro.
						final ParserState stateAfter = new ParserState(
							stateAfterCall.position,
							stateAfterCall.tokenIndex,
							clientDataAfterRunning.value());
						final A_Phrase original = SendNodeDescriptor.from(
							constituentTokens,
							bundle,
							argumentsListNode,
							macroDefinitionToInvoke
								.bodySignature().returnType());
						final A_Phrase substitution =
							MacroSubstitutionNodeDescriptor
								.fromOriginalSendAndReplacement(
									original, adjustedReplacement);
						if (AvailRuntime.debugMacroExpansions)
						{
							System.out.println(
								":"
									+ stateAfter.peekToken().lineNumber()
									+ "("
									+ stateAfter.peekToken().start()
									+ ") "
									+ substitution);
						}
						compilationContext.attempt(
							stateAfter,
							continuation,
							new CompilerSolution(
								stateAfter, substitution));
					}
				}),
			compilationContext.workUnitCompletion(
				stateAfterCall.peekToken(),
				hasRunEither,
				new Continuation1<Throwable>()
				{
					@Override
					public void value (final @Nullable Throwable e)
					{
						assert e != null;
						if (e instanceof AvailAcceptedParseException)
						{
							stateAfterCall.expected(
								"macro body to reject the parse or produce "
									+ "a replacement expression, not merely accept "
									+ "its phrases like a semantic restriction");
						}
						else if (e instanceof AvailRejectedParseException)
						{
							final AvailRejectedParseException rej =
								(AvailRejectedParseException) e;
							stateAfterCall.expected(
								rej.rejectionString().asNativeString());
						}
						else
						{
							stateAfterCall.expected(
								"evaluation of macro body not to raise an "
									+ "unhandled exception:\n\t"
									+ e);
						}
					}
				}));
	}

	/**
	 * Check a property of the Avail virtual machine.
	 *
	 * @param pragmaToken
	 *        The string literal token specifying the pragma.
	 * @param state
	 *        The {@linkplain ParserState state} following a parse of the
	 *        {@linkplain ModuleHeader module header}.
	 * @param propertyName
	 *        The name of the property that is being checked.
	 * @param propertyValue
	 *        A value that should be checked, somehow, for conformance.
	 * @param success
	 *        What to do after the check completes successfully.
	 * @param failure
	 *        What to do after the check completes unsuccessfully.
	 * @throws MalformedPragmaException
	 *         If there's a problem with this check pragma.
	 */
	private void pragmaCheckThen (
		final A_Token pragmaToken,
		final ParserState state,
		final String propertyName,
		final String propertyValue,
		final Continuation0 success,
		final Continuation0 failure)
	throws MalformedPragmaException
	{
		switch (propertyName)
		{
			case "version":
				// Split the versions at commas.
				final String[] versions = propertyValue.split(",");
				for (int i = 0; i < versions.length; i++)
				{
					versions[i] = versions[i].trim();
				}
				// Put the required versions into a set.
				A_Set requiredVersions = SetDescriptor.empty();
				for (final String version : versions)
				{
					requiredVersions =
						requiredVersions.setWithElementCanDestroy(
							StringDescriptor.from(version),
							true);
				}
				// Ask for the guaranteed versions.
				final A_Set activeVersions = AvailRuntime.activeVersions();
				// If the intersection of the sets is empty, then the module and
				// the virtual machine are incompatible.
				if (!requiredVersions.setIntersects(activeVersions))
				{
					throw new MalformedPragmaException(
						String.format(
							"Module and virtual machine are not compatible; "
								+ "the virtual machine guarantees versions %s, "
								+ "but the current module requires %s",
							activeVersions,
							requiredVersions));
				}
				break;
			default:
				final Set<String> viableAssertions = new HashSet<>();
				viableAssertions.add("version");
				throw new MalformedPragmaException(
					String.format(
						"Expected check pragma to assert one of the following "
							+ "properties: %s",
						viableAssertions));
		}
		success.value();
	}

	/**
	 * Create a bootstrap primitive method. Use the primitive's type declaration
	 * as the argument types.  If the primitive is fallible then generate
	 * suitable primitive failure code (to invoke the {@link MethodDescriptor
	 * #vmCrashAtom()}'s bundle).
	 *
	 * @param state
	 *        The {@linkplain ParserState state} following a parse of the
	 *        {@linkplain ModuleHeader module header}.
	 * @param token
	 *        A token with which to associate the definition of the function.
	 *        Since this is a bootstrap method, it's appropriate to use the
	 *        string token within the pragma for this purpose.
	 * @param methodName
	 *        The name of the primitive method being defined.
	 * @param primitiveName
	 *        The {@linkplain Primitive#name() primitive name} of the
	 *        {@linkplain MethodDescriptor method} being defined.
	 * @param success
	 *        What to do after the method is bootstrapped successfully.
	 * @param failure
	 *        What to do if the attempt to bootstrap the method fails.
	 */
	private void bootstrapMethodThen (
		final ParserState state,
		final A_Token token,
		final String methodName,
		final String primitiveName,
		final Continuation0 success,
		final Continuation0 failure)
	{
		final A_String availName = StringDescriptor.from(methodName);
		final A_Phrase nameLiteral =
			syntheticFrom(availName);
		final Primitive primitive = Primitive.byName(primitiveName);
		assert primitive != null;
		final A_Function function =
			FunctionDescriptor.newPrimitiveFunction(
				primitive,
				compilationContext.module(),
				token.lineNumber());
		final A_Phrase send = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialAtom.METHOD_DEFINER.bundle,
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				nameLiteral,
				syntheticFrom(function))),
			TOP.o());
		evaluateModuleStatementThen(
			state,
			state,
			send,
			new HashMap<A_Phrase, A_Phrase>(),
			success,
			failure);
	}

	/**
	 * Create a bootstrap primitive {@linkplain MacroDefinitionDescriptor
	 * macro}. Use the primitive's type declaration as the argument types.  If
	 * the primitive is fallible then generate suitable primitive failure code
	 * (to invoke the {@link SpecialAtom#CRASH}'s bundle).
	 *
	 * @param state
	 *        The {@linkplain ParserState state} following a parse of the
	 *        {@linkplain ModuleHeader module header}.
	 * @param token
	 *        A token with which to associate the definition of the function(s).
	 *        Since this is a bootstrap macro (and possibly prefix functions),
	 *        it's appropriate to use the string token within the pragma for
	 *        this purpose.
	 * @param macroName
	 *        The name of the primitive macro being defined.
	 * @param primitiveNames
	 *        The array of {@linkplain String}s that are bootstrap macro names.
	 *        These correspond to the occurrences of the {@linkplain
	 *        Metacharacter#SECTION_SIGN section sign} (§) in the macro
	 *        name, plus a final body for the complete macro.
	 * @param success
	 *        What to do after the macro is defined successfully.
	 * @param failure
	 *        What to do after compilation fails.
	 */
	private void bootstrapMacroThen (
		final ParserState state,
		final A_Token token,
		final String macroName,
		final String[] primitiveNames,
		final Continuation0 success,
		final Continuation0 failure)
	{
		assert primitiveNames.length > 0;
		final A_String availName = StringDescriptor.from(macroName);
		final AvailObject token1 = LiteralTokenDescriptor.create(
			StringDescriptor.from(availName.toString()),
			TupleDescriptor.empty(),
			TupleDescriptor.empty(),
			0,
			0,
			-1,
			SYNTHETIC_LITERAL,
			availName);
		final A_Phrase nameLiteral =
			fromToken(token1);
		final List<A_Phrase> functionLiterals = new ArrayList<>();
		try
		{
			for (final String primitiveName: primitiveNames)
			{
				final Primitive prim = Primitive.byName(primitiveName);
				assert prim != null : "Invalid bootstrap macro primitive name";
				functionLiterals.add(
					syntheticFrom(
						FunctionDescriptor.newPrimitiveFunction(
							prim,
							compilationContext.module(),
							token.lineNumber())));
			}
		}
		catch (final RuntimeException e)
		{
			compilationContext.reportInternalProblem(state.peekToken(), e);
			failure.value();
			return;
		}
		final A_Phrase bodyLiteral =
			functionLiterals.remove(functionLiterals.size() - 1);
		final A_Phrase send = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialAtom.MACRO_DEFINER.bundle,
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				nameLiteral,
				ListNodeDescriptor.newExpressions(
					TupleDescriptor.fromList(functionLiterals)),
				bodyLiteral)),
			TOP.o());
		evaluateModuleStatementThen(
			state,
			state,
			send,
			new HashMap<A_Phrase, A_Phrase>(),
			success,
			failure);
	}

	/**
	 * Create a bootstrap primitive lexer. Validate the primitive's type
	 * declaration against what's needed for a lexer function.  If either
	 * primitive is fallible then generate suitable primitive failure code for
	 * it (to invoke the {@link MethodDescriptor #vmCrashAtom()}'s bundle).
	 *
	 * <p>The filter takes a character and answers a boolean indicating whether
	 * the lexer should be attempted when that character is next in the source
	 * file.</p>
	 *
	 * <p>The body takes a character (which has already passed the filter), the
	 * entire source string, and the one-based index of the current character in
	 * the string.  It returns nothing, but it invokes a success primitive for
	 * each successful lexing (passing a tuple of tokens and the character
	 * position after what was lexed), and/or invokes a failure primitive to
	 * give specific diagnostics about what went wrong.</p>
	 *
	 * @param state
	 *        The {@linkplain ParserState state} following a parse of the
	 *        {@linkplain ModuleHeader module header}.
	 * @param token
	 *        A token with which to associate the definition of the lexer
	 *        function.  Since this is a bootstrap lexer, it's appropriate to
	 *        use the string token within the pragma for this purpose.
	 * @param lexerName
	 *        The name of the lexer being defined.
	 * @param filterPrimitiveName
	 *        The {@linkplain Primitive#name() primitive name} of the filter
	 *        for the lexer being defined.
	 * @param bodyPrimitiveName
	 *        The {@linkplain Primitive#name() primitive name} of the body of
	 *        the lexer being defined.
	 * @param success
	 *        What to do after the method is bootstrapped successfully.
	 * @param failure
	 *        What to do if the attempt to bootstrap the method fails.
	 * @throws MalformedPragmaException
	 *         If the lexer pragma cannot be created.
	 */
	private void bootstrapLexerThen (
		final ParserState state,
		final A_Token token,
		final String lexerName,
		final String filterPrimitiveName,
		final String bodyPrimitiveName,
		final Continuation0 success,
		final Continuation0 failure)
	throws MalformedPragmaException
	{
		// Process the filter primitive.
		final Primitive filterPrimitive = Primitive.byName(filterPrimitiveName);
		if (filterPrimitive == null)
		{
			throw new MalformedPragmaException(
				"Unknown lexer filter primitive name ("
					+ filterPrimitiveName
					+ ")");
		}
		final A_Type filterFunctionType =
			filterPrimitive.blockTypeRestriction();
		if (!filterFunctionType.equals(
			LexerDescriptor.lexerFilterFunctionType()))
		{
			throw new MalformedPragmaException(
				"Type signature for filter primitive is invalid for lexer. "
					+ "Primitive has "
					+ filterFunctionType
					+ " but a lexer filter needs "
					+ LexerDescriptor.lexerFilterFunctionType());
		}
		final A_Function filterFunction =
			FunctionDescriptor.newPrimitiveFunction(
				filterPrimitive,
				compilationContext.module(), token.lineNumber());

		// Process the body primitive.
		final Primitive bodyPrimitive = Primitive.byName(bodyPrimitiveName);
		if (bodyPrimitive == null)
		{
			throw new MalformedPragmaException(
				"Unknown lexer body primitive name ("
					+ bodyPrimitiveName
					+ ")");
		}
		final A_Type bodyFunctionType =
			bodyPrimitive.blockTypeRestriction();
		if (!bodyFunctionType.equals(LexerDescriptor.lexerBodyFunctionType()))
		{
			throw new MalformedPragmaException(
				"Type signature for body primitive is invalid for lexer. "
					+ "Primitive has "
					+ bodyFunctionType
					+ " but a lexer body needs "
					+ LexerDescriptor.lexerBodyFunctionType());
		}
		final A_Function bodyFunction =
			FunctionDescriptor.newPrimitiveFunction(
				bodyPrimitive,
				compilationContext.module(), token.lineNumber());

		// Process the lexer name.
		final A_String availName = StringDescriptor.from(lexerName);
		final A_Phrase nameLiteral =
			syntheticFrom(availName);

		// Build a phrase to define the lexer.
		final A_Phrase send = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialAtom.LEXER_DEFINER.bundle,
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				nameLiteral,
				syntheticFrom(filterFunction),
				syntheticFrom(bodyFunction))),
			TOP.o());
		evaluateModuleStatementThen(
			state,
			state,
			send,
			new HashMap<A_Phrase, A_Phrase>(),
			success,
			failure);
	}

	/**
	 * Apply a {@link ExpectedToken#PRAGMA_CHECK check} pragma that was detected
	 * during parse of the {@linkplain ModuleHeader module header}.
	 *
	 * @param pragmaToken
	 *        The string literal token specifying the pragma.
	 * @param pragmaValue
	 *        The pragma {@link String} after {@code "check="}.
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param success
	 *        What to do after the pragmas have been applied successfully.
	 * @param failure
	 *        What to do if a problem is found with one of the pragma
	 *        definitions.
	 * @throws MalformedPragmaException if the pragma is malformed.
	 */
	@InnerAccess void applyCheckPragmaThen (
		final A_Token pragmaToken,
		final String pragmaValue,
		final ParserState state,
		final Continuation0 success,
		final Continuation0 failure)
	throws MalformedPragmaException
	{
		final String[] parts = pragmaValue.split("=", 2);
		if (parts.length != 2)
		{
			throw new MalformedPragmaException(
				"Should have the form 'check=<property>=<value>'.");
		}
		final String propertyName = parts[0].trim();
		final String propertyValue = parts[1].trim();
		pragmaCheckThen(
			pragmaToken, state, propertyName, propertyValue, success, failure);
	}

	/**
	 * Apply a method pragma detected during parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param pragmaValue
	 *        The pragma {@link String} after "method=".
	 * @throws MalformedPragmaException
	 *         If this method-pragma is malformed.
	 */
	@InnerAccess void applyMethodPragmaThen (
		final A_Token pragmaToken,
		final String pragmaValue,
		final ParserState state,
		final Continuation0 success,
		final Continuation0 failure)
	throws MalformedPragmaException
	{
		final String[] parts = pragmaValue.split("=", 2);
		if (parts.length != 2)
		{
			throw new MalformedPragmaException(
				String.format(
					"Expected method pragma to have the form "
						+ "%s=primitiveName=name",
					PRAGMA_METHOD.lexemeJavaString));
		}
		final String primName = parts[0].trim();
		final String methodName = parts[1].trim();
		bootstrapMethodThen(
			state, pragmaToken, methodName, primName, success, failure);
	}

	/**
	 * Apply a macro pragma detected during parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param pragmaToken
	 *        The string literal token specifying the pragma.
	 * @param pragmaValue
	 *        The pragma {@link String} after "macro=".
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param success
	 *        What to do after the macro is defined successfully.
	 * @param failure
	 *        What to do if the attempt to define the macro fails.
	 */
	@InnerAccess void applyMacroPragmaThen (
		final A_Token pragmaToken,
		final String pragmaValue,
		final ParserState state,
		final Continuation0 success,
		final Continuation0 failure)
	{
		final String[] parts = pragmaValue.split("=", 2);
		if (parts.length != 2)
		{
			throw new IllegalArgumentException();
		}
		final String pragmaPrim = parts[0].trim();
		final String macroName = parts[1].trim();
		final String[] primNameStrings = pragmaPrim.split(",");
		final String[] primNames = new String[primNameStrings.length];
		for (int i = 0; i < primNames.length; i++)
		{
			final String primName = primNameStrings[i];
			final @Nullable Primitive prim = Primitive.byName(primName);
			if (prim == null)
			{
				compilationContext.diagnostics.reportError(
					pragmaToken,
					"Malformed pragma at %s on line %d:",
					String.format(
						"Expected macro pragma to reference "
							+ "a valid primitive, not %s",
						primName),
					failure);
				return;
			}
			primNames[i] = primName;
		}
		bootstrapMacroThen(
			state, pragmaToken, macroName, primNames, success, failure);
	}

	/**
	 * Apply a stringify pragma detected during parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param pragmaToken
	 *        The string literal token specifying the pragma.
	 * @param pragmaValue
	 *        The pragma {@link String} after "stringify=".
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param success
	 *        What to do after the stringification name is defined successfully.
	 * @param failure
	 *        What to do after stringification fails.
	 */
	@InnerAccess void applyStringifyPragmaThen (
		final A_Token pragmaToken,
		final String pragmaValue,
		final ParserState state,
		final Continuation0 success,
		final Continuation0 failure)
	{
		final A_String availName = StringDescriptor.from(pragmaValue);
		final A_Set atoms =
			compilationContext.module().trueNamesForStringName(availName);
		if (atoms.setSize() == 0)
		{
			compilationContext.diagnostics.reportError(
				pragmaToken,
				"Problem in stringification macro at %s on line %d:",
				String.format(
					"stringification method \"%s\" should be introduced"
						+ " in this module",
					availName.asNativeString()),
				failure);
			return;
		}
		else if (atoms.setSize() > 1)
		{
			compilationContext.diagnostics.reportError(
				pragmaToken,
				"Problem in stringification macro at %s on line %d:",
				String.format(
					"stringification method \"%s\" is ambiguous",
					availName.asNativeString()),
				failure);
			return;
		}
		final A_Atom atom = atoms.asTuple().tupleAt(1);
		final A_Phrase send = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialAtom.DECLARE_STRINGIFIER.bundle,
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				syntheticFrom(atom))),
			TOP.o());
		evaluateModuleStatementThen(
			state,
			state,
			send,
			new HashMap<A_Phrase, A_Phrase>(),
			success,
			failure);
	}

	/**
	 * Apply a lexer definition pragma detected during parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param pragmaToken
	 *        The string literal token specifying the pragma.
	 * @param pragmaValue
	 *        The pragma {@link String} after "lexer=".
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param success
	 *        What to do after the lexer is defined successfully.
	 * @param failure
	 *        What to do if the attempt to define the lexer fails.
	 */
	@InnerAccess void applyLexerPragmaThen (
		final A_Token pragmaToken,
		final String pragmaValue,
		final ParserState state,
		final Continuation0 success,
		final Continuation0 failure)
	{
		final String lexerName;
		final String filterPrimitiveName;
		final String bodyPrimitiveName;
		try
		{
			final String[] parts = pragmaValue.split("=", 2);
			if (parts.length != 2)
			{
				throw new IllegalArgumentException();
			}
			final String primNames = parts[0].trim();
			final String[] primParts = primNames.split(",", 2);
			if (primParts.length != 2)
			{
				throw new IllegalArgumentException();
			}
			filterPrimitiveName = primParts[0];
			bodyPrimitiveName = primParts[1];
			lexerName = parts[1].trim();
		}
		catch (final IllegalArgumentException e)
		{
			compilationContext.diagnostics.handleProblem(new Problem(
					moduleName(),
					pragmaToken.lineNumber(),
					pragmaToken.start(),
					PARSE,
					"Expected lexer pragma to have the form "
						+ "{0}=filterPrim,bodyPrim=lexerName",
					PRAGMA_LEXER.lexemeJavaString)
				{
					@Override
					public void abortCompilation ()
					{
						failure.value();
					}
				});
			return;
		}
		try
		{
			bootstrapLexerThen(
				state,
				pragmaToken,
				lexerName,
				filterPrimitiveName,
				bodyPrimitiveName,
				success,
				failure);
		}
		catch (final MalformedPragmaException e)
		{
			compilationContext.diagnostics.handleProblem(new Problem(
					moduleName(),
					pragmaToken.lineNumber(),
					pragmaToken.start(),
					PARSE,
					e.problem(),
					PRAGMA_LEXER.lexemeJavaString)
				{
					@Override
					public void abortCompilation ()
					{
						failure.value();
					}
				});
		}
	}

	/**
	 * Apply any pragmas detected during the parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param success
	 *        What to do after the pragmas have been applied successfully.
	 * @param failure
	 *        What to do after a problem is found with one of the pragma
	 *        definitions.
	 */
	private void applyPragmasThen (
		final ParserState state,
		final Continuation0 success,
		final Continuation0 failure)
	{
		// Report pragma installation problems relative to the pragma section.
		final A_Token pragmaToken = moduleHeader().pragmaToken;
		if (pragmaToken != null)
		{
			recordExpectationsRelativeTo(pragmaToken.start());
		}
		final Iterator<A_Token> iterator = moduleHeader().pragmas.iterator();
		final MutableOrNull<Continuation0> body = new MutableOrNull<>();
		final Continuation0 next = new Continuation0()
		{
			@Override
			public void value ()
			{
				compilationContext.eventuallyDo(
					state.peekToken(),
					body.value());
			}
		};
		body.value = new Continuation0()
		{
			@Override
			public void value ()
			{
				if (!iterator.hasNext())
				{
					// Done with all the pragmas, if any.  Report any new
					// problems relative to the body section.
					recordExpectationsRelativeTo(state.position);
					success.value();
					return;
				}
				final A_Token pragmaToken = iterator.next();
				final A_String pragmaString = pragmaToken.literal();
				final String nativeString = pragmaString.asNativeString();
				final String[] pragmaParts = nativeString.split("=", 2);
				if (pragmaParts.length != 2)
				{
					compilationContext.diagnostics.reportError(
						pragmaToken,
						"Malformed pragma at %s on line %d:",
						"Pragma should have the form key=value",
						failure);
					return;
				}
				final String pragmaKind = pragmaParts[0].trim();
				final String pragmaValue = pragmaParts[1].trim();
				try
				{
					switch (pragmaKind)
					{
						case "check":
						{
							assert pragmaKind.equals(
								PRAGMA_CHECK.lexemeJavaString);
							applyCheckPragmaThen(
								pragmaToken, pragmaValue, state, next, failure);
							break;
						}
						case "method":
						{
							assert pragmaKind.equals(
								PRAGMA_METHOD.lexemeJavaString);
							applyMethodPragmaThen(
								pragmaToken, pragmaValue, state, next, failure);
							break;
						}
						case "macro":
						{
							assert pragmaKind.equals(
								PRAGMA_MACRO.lexemeJavaString);
							applyMacroPragmaThen(
								pragmaToken, pragmaValue, state, next, failure);
							break;
						}
						case "stringify":
						{
							assert pragmaKind.equals(
								PRAGMA_STRINGIFY.lexemeJavaString);
							applyStringifyPragmaThen(
								pragmaToken, pragmaValue, state, next, failure);
							break;
						}
						case "lexer":
						{
							assert pragmaKind.equals(
								PRAGMA_LEXER.lexemeJavaString);
							applyLexerPragmaThen(
								pragmaToken, pragmaValue, state, next, failure);
							break;
						}
						default:
							compilationContext.diagnostics.reportError(
								pragmaToken,
								"Malformed pragma at %s on line %d:",
								String.format(
									"Pragma key should be one of "
										+ "%s, %s, %s, %s, or %s",
									PRAGMA_CHECK.lexemeJavaString,
									PRAGMA_METHOD.lexemeJavaString,
									PRAGMA_MACRO.lexemeJavaString,
									PRAGMA_STRINGIFY.lexemeJavaString,
									PRAGMA_LEXER.lexemeJavaString),
								failure);
							return;
					}
				}
				catch (MalformedPragmaException e)
				{
					compilationContext.diagnostics.reportError(
						pragmaToken,
						"Malformed pragma at %s on line %d:",
						String.format(
							"Malformed pragma: %s",
							e.problem()),
						failure);
					return;
				}
			}
		};
		compilationContext.loader().setPhase(EXECUTING);
		next.value();
	}

	/**
	 * Parse a {@linkplain ModuleHeader module header} from the {@linkplain
	 * TokenDescriptor token list} and apply any side-effects. Then {@linkplain
	 * #parseAndExecuteOutermostStatements(ParserState, Continuation0) parse the
	 * module body} and apply any side-effects.
	 *
	 * @param afterFail
	 *        What to do after compilation fails.
	 */
	@InnerAccess void parseModuleCompletely (final Continuation0 afterFail)
	{
		final ParserState afterHeader = parseModuleHeader(false);
		if (afterHeader == null)
		{
			compilationContext.diagnostics.reportError(afterFail);
			return;
		}
		// Update the reporter. This condition just prevents the reporter from
		// being called twice at the end of a file.
		if (!afterHeader.atEnd())
		{
			compilationContext.getProgressReporter().value(
				moduleName(),
				(long) source().length(),
				(long) afterHeader.peekToken().start());
		}
		// Run any side-effects implied by this module header against the
		// module.
		final String errorString =
			moduleHeader().applyToModule(
				compilationContext.module(),
				runtime);
		if (errorString != null)
		{
			compilationContext.getProgressReporter().value(
				moduleName(),
				(long) source().length(),
				(long) source().length());
			afterHeader.expected(errorString);
			compilationContext.diagnostics.reportError(afterFail);
			return;
		}
		compilationContext.loader().createFilteredBundleTree();
		applyPragmasThen(
			afterHeader,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					// Parse the body of the module.
					if (!afterHeader.atEnd())
					{
						compilationContext.eventuallyDo(
							afterHeader.peekToken(),
							new Continuation0()
							{
								@Override
								public void value ()
								{
									parseAndExecuteOutermostStatements(
										afterHeader, afterFail);
								}
							});
					}
					else
					{
						final Continuation0 reporter =
							compilationContext.getSuccessReporter();
						assert reporter != null;
						reporter.value();
					}
				}
			},
			afterFail);
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} body from the {@linkplain
	 * TokenDescriptor token} list, execute it, and repeat if we're not at the
	 * end of the module.
	 *
	 * @param start
	 *        The {@linkplain ParserState parse state} after parsing a
	 *        {@linkplain ModuleHeader module header}.
	 * @param afterFail
	 *        What to do after compilation fails.
	 */
	@InnerAccess void parseAndExecuteOutermostStatements (
		final ParserState start,
		final Continuation0 afterFail)
	{
		compilationContext.loader().setPhase(COMPILING);
		parseOutermostStatement(
			start,
			new Con<CompilerSolution>(null)
			{
				@Override
				public void valueNotNull (
					final CompilerSolution solution)
				{
					// The counters must be read in this order for correctness.
					assert compilationContext.getWorkUnitsCompleted().get()
						== compilationContext.getWorkUnitsQueued().get();

					// In case the top level statement is compound, process the
					// base statements individually.
					final ParserState afterStatement = solution.endState();
					final A_Phrase unambiguousStatement = solution.parseNode();
					final List<A_Phrase> simpleStatements = new ArrayList<>();
					unambiguousStatement.statementsDo(
						new Continuation1<A_Phrase>()
						{
							@Override
							public void value (
								final @Nullable A_Phrase simpleStatement)
							{
								assert simpleStatement != null;
								assert simpleStatement.parseNodeKindIsUnder(
									STATEMENT_NODE);
								simpleStatements.add(simpleStatement);
							}
						});

					// For each top-level simple statement, (1) transform it to
					// have referenced previously transformed top-level
					// declarations mapped from local scope into global scope,
					// (2) if it's itself a declaration, transform it and record
					// the transformation for subsequent statements, and (3)
					// execute it.  The declarationRemap accumulates the
					// transformations.  Parts 2 and 3 actually happen together
					// so that module constants can have types as strong as the
					// actual values produced by running their initialization
					// expressions.
					final Map<A_Phrase, A_Phrase> declarationRemap =
						new HashMap<A_Phrase, A_Phrase>();
					final Iterator<A_Phrase> simpleStatementIterator =
						simpleStatements.iterator();

					// What to do after running all these simple statements.
					final Continuation0 resumeParsing = new Continuation0()
					{
						@Override
						public void value ()
						{
							if (afterStatement.atEnd())
							{
								// End of the module..
								reachedEndOfModule(afterStatement, afterFail);
								return;
							}
							// Not the end; report progress.
							compilationContext.getProgressReporter().value(
								moduleName(),
								(long) source().length(),
								(long) afterStatement.peekToken().start());
							compilationContext.eventuallyDo(
								afterStatement.peekToken(),
								new Continuation0()
								{
									@Override
									public void value ()
									{
										parseAndExecuteOutermostStatements(
											new ParserState(
												afterStatement.position,
												afterStatement.tokenIndex,
												start.clientDataMap),
											afterFail);
									}
								});
						}
					};

					// What to do after running a simple statement (or to get
					// the first one to run).
					final MutableOrNull<Continuation0> executeSimpleStatement =
						new MutableOrNull<>();
					executeSimpleStatement.value = new Continuation0()
					{
						@Override
						public void value ()
						{
							if (!simpleStatementIterator.hasNext())
							{
								resumeParsing.value();
								return;
							}
							final A_Phrase statement =
								simpleStatementIterator.next();
							if (AvailLoader.debugLoadedStatements)
							{
								System.out.println(
									moduleName().qualifiedName()
										+ ":" + start.peekToken().lineNumber()
										+ " Running statement:\n" + statement);
							}
							evaluateModuleStatementThen(
								start,
								afterStatement,
								statement,
								declarationRemap,
								executeSimpleStatement.value(),
								afterFail);
						}
					};

					// Kick off execution of these simple statements.
					compilationContext.loader().setPhase(EXECUTING);
					executeSimpleStatement.value().value();
				}
			},
			afterFail);
	}

	/**
	 * We just reached the end of the module.
	 *
	 * @param afterModule
	 * @param afterFail
	 */
	@InnerAccess void reachedEndOfModule (
		final ParserState afterModule,
		final Continuation0 afterFail)
	{
		final AvailLoader theLoader = compilationContext.loader();
		if (theLoader.pendingForwards.setSize() != 0)
		{
			@SuppressWarnings("resource")
			final Formatter formatter = new Formatter();
			formatter.format("the following forwards to be resolved:");
			for (final A_BasicObject forward : theLoader.pendingForwards)
			{
				formatter.format("%n\t%s", forward);
			}
			afterModule.expected(formatter.toString());
			compilationContext.diagnostics.reportError(afterFail);
			return;
		}
		// Clear the section of the fragment cache
		// associated with the (outermost) statement
		// just parsed and executed...
		synchronized (fragmentCache)
		{
			fragmentCache.clear();
		}
		final Continuation0 reporter = compilationContext.getSuccessReporter();
		assert reporter != null;
		reporter.value();
	}

	/**
	 * Clear any information about potential problems encountered during
	 * parsing.  Reset the problem information to record relative to the
	 * provided one-based source position.
	 *
	 * @param positionInSource
	 *        The earliest source position for which we should record problem
	 *        information.
	 */
	@InnerAccess synchronized void recordExpectationsRelativeTo (
		final int positionInSource)
	{
		compilationContext.diagnostics.startParsingAt(positionInSource);
	}

	/**
	 * Parse a {@linkplain ModuleHeader module header} from the {@linkplain
	 * TokenDescriptor token} list.  Eventually, and in some thread, invoke
	 * either the {@code onSuccess} continuation or the {@code afterFail}
	 * continuation, depending on whether the parsing was successful.
	 *
	 * @param onSuccess
	 *        What to do when the header has been parsed.
	 * @param afterFail
	 *        What to do after parsing the header fails.
	 */
	public void parseModuleHeader (
		final Continuation1<ModuleHeader> onSuccess,
		final Continuation0 afterFail)
	{
		recordExpectationsRelativeTo(1);
		if (parseModuleHeader(true) != null)
		{
			onSuccess.value(moduleHeader());
		}
		else
		{
			compilationContext.diagnostics.reportError(afterFail);
		}
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} from the {@linkplain
	 * TokenDescriptor token} list and install it into the {@linkplain
	 * AvailRuntime runtime}.
	 *
	 * @param succeed
	 *        What to do after compilation succeeds. This {@linkplain
	 *        Continuation1 continuation} is invoked with an {@link
	 *        AvailCompilerResult} that includes the completed module.
	 * @param afterFail
	 *        What to do after compilation fails.
	 */
	public synchronized void parseModule (
		final Continuation1<AvailCompilerResult> succeed,
		final Continuation0 afterFail)
	{
		compilationContext.setSuccessReporter(new Continuation0()
		{
			@Override
			public void value ()
			{
				serializePublicationFunction(true);
				serializePublicationFunction(false);
				commitModuleTransaction();
				succeed.value(new AvailCompilerResult(
					compilationContext.module(),
					commentTokens));
			}
		});
		startModuleTransaction();
		compilationContext.eventuallyDo(
			TokenDescriptor.createSyntheticStart(),
			new Continuation0()
			{
				@Override
				public void value ()
				{
					parseModuleCompletely(new Continuation0()
					{
						@Override
						public void value ()
						{
							rollbackModuleTransaction(afterFail);
						}
					});
				}
			});
	}

	/**
	 * Parse a command, compiling it into the current {@linkplain
	 * ModuleDescriptor module}, from the {@linkplain
	 * TokenDescriptor token} list.
	 *
	 * @param succeed
	 *        What to do after compilation succeeds. This {@linkplain
	 *        Continuation2 continuation} is invoked with a {@linkplain List
	 *        list} of {@link A_Phrase phrases} that represent the possible
	 *        solutions of compiling the command and a {@linkplain Continuation1
	 *        continuation} that cleans up this compiler and its module (and
	 *        then continues with a post-cleanup {@linkplain Continuation0
	 *        continuation}).
	 * @param afterFail
	 *        What to do after compilation fails.
	 */
	public synchronized void parseCommand (
		final Continuation2<List<A_Phrase>, Continuation1<Continuation0>>
			succeed,
		final Continuation0 afterFail)
	{
		// The counters must be read in this order for correctness.
		assert compilationContext.getWorkUnitsCompleted().get() == 0
			&& compilationContext.getWorkUnitsQueued().get() == 0;
		// Start a module transaction, just to complete any necessary
		// initialization. We are going to rollback this transaction no matter
		// what happens.
		startModuleTransaction();
		compilationContext.loader().createFilteredBundleTree();
		final List<A_Phrase> solutions = new ArrayList<>();
		compilationContext.setNoMoreWorkUnits(new Continuation0()
		{
			@Override
			public void value ()
			{
				// The counters must be read in this order for correctness.
				assert compilationContext.getWorkUnitsCompleted().get()
					== compilationContext.getWorkUnitsQueued().get();
				// If no solutions were found, then report an error.
				if (solutions.isEmpty())
				{
					compilationContext.diagnostics.reportError(
						new Continuation0()
						{
							@Override
							public void value ()
							{
								rollbackModuleTransaction(afterFail);
							}
						});
					return;
				}
				succeed.value(
					solutions,
					new Continuation1<Continuation0>()
					{
						@Override
						public void value (
							final @Nullable Continuation0 postCleanup)
						{
							assert postCleanup != null;
							rollbackModuleTransaction(postCleanup);
						}
					});
			}
		});
		recordExpectationsRelativeTo(1);
		compilationContext.eventuallyDo(
			TokenDescriptor.createSyntheticStart(),
			new Continuation0()
			{
				@Override
				public void value ()
				{
					A_Map clientData = MapDescriptor.empty();
					clientData = clientData.mapAtPuttingCanDestroy(
						compilerScopeMapKey(), MapDescriptor.empty(), true);
					clientData = clientData.mapAtPuttingCanDestroy(
						allTokensKey(), TupleDescriptor.empty(), true);
					// Rollback the module transaction no matter what happens.
					parseExpressionThen(
						new ParserState(1, 1, clientData),
						new Con<CompilerSolution>(null)
						{
							@Override
							public void valueNotNull (
								final CompilerSolution solution)
							{
								final A_Phrase expression =
									solution.parseNode();
								final ParserState afterExpression =
									solution.endState();
								if (expression.hasSuperCast())
								{
									afterExpression.expected(
										"a valid command, not a supercast");
								}
								if (afterExpression.atEnd())
								{
									synchronized (solutions)
									{
										solutions.add(expression);
									}
								}
								else
								{
									afterExpression.expected("end of command");
								}
							}
						});
				}
			});
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
	 * @param dependenciesOnly
	 *        Whether to do the bare minimum parsing required to determine the
	 *        modules to which this one refers.
	 * @return The state of parsing just after the header, or {@code null} if it
	 *         failed.
	 */
	private @Nullable ParserState parseModuleHeader (
		final boolean dependenciesOnly)
	{
		// Create the initial parser state: no tokens have been seen, and no
		// names are in scope.
		A_Map clientData = MapDescriptor.empty();
		clientData = clientData.mapAtPuttingCanDestroy(
			compilerScopeMapKey(), MapDescriptor.empty(), true);
		clientData = clientData.mapAtPuttingCanDestroy(
			allTokensKey(), TupleDescriptor.empty(), true);
		ParserState state = new ParserState(1, 1, clientData);

		recordExpectationsRelativeTo(1);

		if (!state.peekToken(ExpectedToken.MODULE, "Module keyword"))
		{
			return null;
		}
		state = state.afterToken();
		final A_Token localNameToken = state.peekStringLiteral();
		if (localNameToken == null)
		{
			state.expected("module name");
			return null;
		}
		final A_String localName = localNameToken.literal();
		if (!moduleName().localName().equals(
			localName.asNativeString()))
		{
			state.expected(
				"declared local module name to agree with "
				+ "fully-qualified module name");
			return null;
		}
		state = state.afterToken();

		// Module header section tracking.
		final List<ExpectedToken> expected = new ArrayList<>(asList(
			VERSIONS, EXTENDS, USES, NAMES, ENTRIES, PRAGMA, FILES, BODY));
		final Set<A_String> seen = new HashSet<>();

		// Permit the other sections to appear optionally, singly, and in any
		// order. Parsing of the module header is complete when BODY has been
		// consumed.
		while (true)
		{
			final A_Token token = state.peekToken();
			recordExpectationsRelativeTo(state.position);
			final A_String lexeme = token.string();
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
					state.expected(new Describer()
					{
						@Override
						public void describeThen (
							final Continuation1<String> c)
						{
							final StringBuilder builder = new StringBuilder();
							builder.append(
								expected.size() == 1
								? "module header keyword "
								: "one of these module header keywords: ");
							boolean first = true;
							for (final ExpectedToken expectedToken : expected)
							{
								if (!first)
								{
									builder.append(", ");
								}
								builder.append(
									expectedToken.lexeme().asNativeString());
								first = false;
							}
							c.value(builder.toString());
						}
					});
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

			if (lexeme.equals(FILES.lexeme()))
			{
				// TODO Finish this.
				assert false : "Files section is not yet implemented";
				// state = parseFilesSection(
				//     state, moduleHeader().filePatterns);
			}
			// On VERSIONS, record the versions.
			else if (lexeme.equals(VERSIONS.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader().versions);
			}
			// On EXTENDS, record the imports.
			else if (lexeme.equals(EXTENDS.lexeme()))
			{
				state = parseModuleImports(
					state, moduleHeader().importedModules, true);
			}
			// On USES, record the imports.
			else if (lexeme.equals(USES.lexeme()))
			{
				state = parseModuleImports(
					state, moduleHeader().importedModules, false);
			}
			// On NAMES, record the names.
			else if (lexeme.equals(NAMES.lexeme()))
			{
				state = parseStringLiterals(
					state, moduleHeader().exportedNames);
				if (state == null)
				{
					return null;
				}
			}
			// ON ENTRIES, record the names.
			else if (lexeme.equals(ENTRIES.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader().entryPoints);
			}
			// On PRAGMA, record the pragma string literals.
			else if (lexeme.equals(PRAGMA.lexeme()))
			{
				// Keep track of where the pragma section starts so that when we
				// actually perform them, we can report on just that section.
				moduleHeader().pragmaToken = token;
				state = parseStringLiteralTokens(state, moduleHeader().pragmas);
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
	@InnerAccess void parseExpressionThen (
		final ParserState start,
		final Con<CompilerSolution> originalContinuation)
	{
		synchronized (fragmentCache)
		{
			// The first time we parse at this position the fragmentCache will
			// have no knowledge about it.
			if (!fragmentCache.hasStartedParsingAt(start))
			{
				start.expected(new Describer()
				{
					@Override
					public void describeThen (
						final Continuation1<String> withDescription)
					{
						final StringBuilder builder = new StringBuilder();
						builder.append(
							"an expression for (at least) this reason:");
						describeOn(
							originalContinuation.superexpressions, builder);
						withDescription.value(builder.toString());
					}
				});
				fragmentCache.indicateParsingHasStartedAt(start);
				final Con<CompilerSolution> action =
					new Con<CompilerSolution>(
						originalContinuation.superexpressions)
					{
						@Override
						public void valueNotNull (
							final CompilerSolution solution)
						{
							try
							{
								synchronized (fragmentCache)
								{
									fragmentCache.addSolution(
										start, solution);
								}
							}
							catch (final DuplicateSolutionException e)
							{
								final A_Token tokenAfter =
									solution.endState().peekToken();
								compilationContext.diagnostics.handleProblem(
									new Problem(
										moduleName(),
										tokenAfter.lineNumber(),
										tokenAfter.start(),
										INTERNAL,
										"Duplicate expressions were parsed at "
											+ "the same position "
											+ "(line {0}): {1}",
										start.peekToken().lineNumber(),
										solution.parseNode())
									{
										@Override
										public void abortCompilation ()
										{
											compilationContext.diagnostics
												.isShuttingDown = true;
										}
									});
							}
						}
					};
				compilationContext.workUnitDo(
					new Continuation0()
					{
						@Override
						public void value ()
						{
							parseExpressionUncachedThen(start, action);
						}
					},
					start);
			}
			compilationContext.workUnitDo(
				new Continuation0()
				{
					@Override
					public void value ()
					{
						synchronized (fragmentCache)
						{
							fragmentCache.addAction(
								start, originalContinuation);
						}
					}
				},
				start);
		}
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar (it used to be that <em>all</em> statements had to
	 * be unambiguous, even those in blocks).  The passed continuation will be
	 * invoked at most once, and only if the top-level statement had a single
	 * interpretation.
	 *
	 * @param start
	 *            Where to start parsing a top-level statement.
	 * @param continuation
	 *            What to do with the (unambiguous) top-level statement.
	 * @param afterFail
	 *            What to run after a failure has been reported.
	 */
	@InnerAccess void parseOutermostStatement (
		final ParserState start,
		final Con<CompilerSolution> continuation,
		final Continuation0 afterFail)
	{
		// If a parsing error happens during parsing of this outermost
		// statement, only show the section of the file starting here.
		recordExpectationsRelativeTo(start.position);
		tryIfUnambiguousThen(
			start,
			new Continuation1<Con<CompilerSolution>>()
			{
				@Override
				public void value (
					final Con<CompilerSolution> whenFoundStatement)
				{
					parseExpressionThen(
						start,
						new Con<CompilerSolution>(null)
						{
							@Override
							public void valueNotNull (
								final CompilerSolution solution)
							{
								final A_Phrase expression =
									solution.parseNode();
								final ParserState afterExpression =
									solution.endState();
								if (expression.parseNodeKindIsUnder(
									STATEMENT_NODE))
								{
									whenFoundStatement.value(
										new CompilerSolution(
											afterExpression, expression));
									return;
								}
								afterExpression.expected(
									new FormattingDescriber(
										"an outer level statement, not %s (%s)",
										expression.parseNodeKind(),
										expression));
							}
						});
				}
			},
			continuation,
			afterFail);
	}

	/**
	 * Parse an expression, without directly using the
	 * {@linkplain #fragmentCache}.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the expression.
	 */
	@InnerAccess void parseExpressionUncachedThen (
		final ParserState start,
		final Con<CompilerSolution> continuation)
	{
		final Con<CompilerSolution> newContinuation =
			new Con<CompilerSolution>(continuation.superexpressions)
			{
				@Override
				public void valueNotNull (
					final CompilerSolution solution)
				{
					parseOptionalLeadingArgumentSendAfterThen(
						start,
						solution.endState(),
						solution.parseNode(),
						continuation);
				}
			};
		parseLeadingKeywordSendThen(start, newContinuation);
	}

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
	@InnerAccess @Nullable A_Token parseRawTokenOrNull (
		final ParserState start)
	{
		final A_Token token = start.peekToken();
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

	/**
	 * A helper method to queue a parsing activity for continuing to parse a
	 * {@linkplain SendNodeDescriptor send phrase}.
	 *
	 * @param start
	 * @param bundleTree
	 * @param firstArgOrNull
	 * @param initialTokenPosition
	 * @param consumedAnything
	 * @param argsSoFar
	 * @param marksSoFar
	 * @param continuation
	 */
	@InnerAccess void eventuallyParseRestOfSendNode (
		final ParserState start,
		final A_BundleTree bundleTree,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con<CompilerSolution> continuation)
	{
		compilationContext.workUnitDo(
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
						marksSoFar,
						continuation);
				}
			},
			start);
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
	@InnerAccess A_Set usesWhichLocalVariables (
		final A_Phrase parseTree)
	{
		final Mutable<A_Set> usedDeclarations =
			new Mutable<>(SetDescriptor.empty());
		parseTree.childrenDo(new Continuation1<A_Phrase>()
		{
			@Override
			public void value (final @Nullable A_Phrase node)
			{
				assert node != null;
				if (node.isInstanceOfKind(VARIABLE_USE_NODE.mostGeneralType()))
				{
					final A_Phrase declaration = node.declaration();
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
	 * Serialize a function that will publish all atoms that are currently
	 * public in the module.
	 *
	 * @param isPublic
	 *        {@code true} if the atoms are public, {@code false} if they are
	 *        private.
	 */
	@InnerAccess void serializePublicationFunction (final boolean isPublic)
	{
		// Output a function that publishes the initial public set of atoms.
		final A_Map sourceNames =
			isPublic
				? compilationContext.module().importedNames()
				: compilationContext.module().privateNames();
		A_Set names = SetDescriptor.empty();
		for (final MapDescriptor.Entry entry : sourceNames.mapIterable())
		{
			names = names.setUnionCanDestroy(
				entry.value().makeImmutable(), true);
		}
		final A_Phrase send = SendNodeDescriptor.from(
			TupleDescriptor.empty(),
			SpecialAtom.PUBLISH_ATOMS.bundle,
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(
					syntheticFrom(names),
					syntheticFrom(
						AtomDescriptor.objectFromBoolean(isPublic)))),
			TOP.o());
		final A_Function function =
			FunctionDescriptor.createFunctionForPhrase(send,
				compilationContext.module(), 0);
		function.makeImmutable();
		synchronized (this)
		{
			compilationContext.serializer.serialize(function);
		}
	}
}

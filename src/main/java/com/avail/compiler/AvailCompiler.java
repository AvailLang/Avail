/*
 * AvailCompiler.java
 * Copyright © 1993-2018, The Avail Foundation, LLC. All rights reserved.
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
import com.avail.AvailRuntimeConfiguration;
import com.avail.builder.ModuleName;
import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.dispatch.LookupTree;
import com.avail.exceptions.AvailAssertionFailedException;
import com.avail.exceptions.AvailEmergencyExitException;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.primitive.phrases.P_RejectParsing;
import com.avail.io.SimpleCompletionHandler;
import com.avail.io.TextInterface;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.utility.*;
import com.avail.utility.evaluation.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.PragmaKind.pragmaKindByLexeme;
import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.*;
import static com.avail.compiler.problems.ProblemType.EXTERNAL;
import static com.avail.compiler.problems.ProblemType.PARSE;
import static com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import static com.avail.compiler.splitter.MessageSplitter.constantForIndex;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.AssignmentPhraseDescriptor.newAssignment;
import static com.avail.descriptor.AtomDescriptor.*;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.*;
import static com.avail.descriptor.CompiledCodeDescriptor.newPrimitiveRawFunction;
import static com.avail.descriptor.DeclarationPhraseDescriptor.newModuleConstant;
import static com.avail.descriptor.DeclarationPhraseDescriptor.newModuleVariable;
import static com.avail.descriptor.FiberDescriptor.newLoaderFiber;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionDescriptor.createFunctionForPhrase;
import static com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.LexerDescriptor.lexerFilterFunctionType;
import static com.avail.descriptor.ListPhraseDescriptor.emptyListNode;
import static com.avail.descriptor.ListPhraseDescriptor.newListNode;
import static com.avail.descriptor.LiteralPhraseDescriptor.literalNodeFromToken;
import static com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.MacroSubstitutionPhraseDescriptor.newMacroSubstitution;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.MapDescriptor.mapFromPairs;
import static com.avail.descriptor.MarkerPhraseDescriptor.newMarkerNode;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.*;
import static com.avail.descriptor.ModuleDescriptor.newModule;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.*;
import static com.avail.descriptor.ParsingPlanInProgressDescriptor.newPlanInProgress;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.descriptor.SendPhraseDescriptor.newSendNode;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableSharedGlobalDescriptor.createGlobal;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.descriptor.VariableUsePhraseDescriptor.newUse;
import static com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION;
import static com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION;
import static com.avail.interpreter.AvailLoader.Phase.COMPILING;
import static com.avail.interpreter.AvailLoader.Phase.EXECUTING_FOR_COMPILE;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.interpreter.Interpreter.stringifyThen;
import static com.avail.interpreter.Primitive.primitiveByName;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForConstant;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Locks.lockWhile;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.PrefixSharingList.append;
import static com.avail.utility.PrefixSharingList.last;
import static com.avail.utility.StackPrinter.trace;
import static com.avail.utility.Strings.increaseIndentation;
import static com.avail.utility.evaluation.Combinator.recurse;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * The compiler for Avail code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailCompiler
{
	/**
	 * The {@link CompilationContext} for this compiler.  It tracks parsing and
	 * lexing tasks, and handles serialization to a {@link
	 * IndexedRepositoryManager repository} if necessary.
	 */
	public final CompilationContext compilationContext;

	/**
	 * The Avail {@link A_String} containing the complete content of the module
	 * being compiled.
	 *
	 * @return The module source string.
	 */
	public A_String source ()
	{
		return compilationContext.source();
	}

	/**
	 * The {@linkplain AvailCompiler compiler} notifies a {@code
	 * CompilerProgressReporter} whenever a top-level statement is parsed
	 * unambiguously.
	 *
	 * <p>The {@link #value(ModuleName, long, long)} method takes the module
	 * name, the module size in bytes, and the current parse position in bytes
	 * within the module.</p>
	 */
	@FunctionalInterface
	public interface CompilerProgressReporter
	{
		/**
		 * Accept a notification about progress loading a module.  The {@link
		 * ModuleName} is provided, as well as the length of the module source
		 * in characters and the number of characters that have now been
		 * successfully parsed and executed.
		 *
		 * @param moduleName
		 *        The module's name.
		 * @param moduleSize
		 *        The module's source size in bytes.
		 * @param position
		 *        The number of bytes of the module source that have been parsed
		 *        and executed so far.
		 */
		void value (ModuleName moduleName, long moduleSize, long position);
	}

	/**
	 * The {@linkplain AvailCompiler compiler} notifies a {@code
	 * GlobalProgressReporter} whenever a top-level statement is parsed
	 * unambiguously.
	 *
	 * <p>The {@link #value(long, long)} method takes the total number of bytes
	 * of source being compiled and the current number of bytes that have been
	 * compiled and executed.</p>
	 */
	@FunctionalInterface
	public interface GlobalProgressReporter
	{
		/**
		 * Accept a notification about progress loading a module.  The {@link
		 * ModuleName} is provided, as well as the length of the module source
		 * in characters and the number of characters that have now been
		 * successfully parsed and executed.
		 *
		 * @param totalSize
		 *        The total source size in bytes.
		 * @param position
		 *        The number of bytes of source that have been parsed and
		 *        executed so far.
		 */
		void value (long totalSize, long position);
	}

	/**
	 * Answer the {@linkplain ModuleHeader module header} for the current
	 * {@linkplain ModuleDescriptor module} being parsed.
	 *
	 * @return the moduleHeader
	 */
	private ModuleHeader moduleHeader ()
	{
		return stripNull(compilationContext.getModuleHeader());
	}

	/**
	 * Answer the fully-qualified name of the {@linkplain ModuleDescriptor
	 * module} undergoing compilation.
	 *
	 * @return The module name.
	 */
	private ModuleName moduleName ()
	{
		return new ModuleName(
			compilationContext.module().moduleName().asNativeString());
	}

	/** The memoization of results of previous parsing attempts. */
	private final AvailCompilerFragmentCache fragmentCache =
		new AvailCompilerFragmentCache();

	/**
	 * Asynchronously construct a suitable {@code AvailCompiler} to parse the
	 * specified {@linkplain ModuleName module name}.
	 *
	 * @param resolvedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the
	 *        {@linkplain ModuleDescriptor module} to compile.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for any {@linkplain
	 *        A_Fiber fibers} started by the new compiler.
	 * @param pollForAbort
	 *        A {@link BooleanSupplier} that indicates whether to abort.
	 * @param reporter
	 *        The {@link CompilerProgressReporter} used to report progress.
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
		final TextInterface textInterface,
		final BooleanSupplier pollForAbort,
		final CompilerProgressReporter reporter,
		final Continuation1NotNull<AvailCompiler> succeed,
		final Continuation0 afterFail,
		final ProblemHandler problemHandler)
	{
		extractSourceThen(
			resolvedName,
			sourceText -> succeed.value(
				new AvailCompiler(
					new ModuleHeader(resolvedName),
					newModule(stringFrom(resolvedName.qualifiedName())),
					stringFrom(sourceText),
					textInterface,
					pollForAbort,
					reporter,
					problemHandler)),
			afterFail,
			problemHandler);
	}

	/**
	 * Construct a new {@code AvailCompiler}.
	 *
	 * @param moduleHeader
	 *        The {@link ModuleHeader module header} of the module to compile.
	 *        May be null for synthetic modules (for entry points), or when
	 *        parsing the header.
	 * @param module
	 *        The current {@linkplain ModuleDescriptor module}.`
	 * @param source
	 *        The source {@link A_String}.
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
		final A_String source,
		final TextInterface textInterface,
		final BooleanSupplier pollForAbort,
		final CompilerProgressReporter progressReporter,
		final ProblemHandler problemHandler)
	{
		this.compilationContext = new CompilationContext(
			moduleHeader,
			module,
			source,
			textInterface,
			pollForAbort,
			progressReporter,
			problemHandler);
	}

	/**
	 * A list of subexpressions being parsed, represented by {@link
	 * A_BundleTree}s holding the positions within all outer send expressions.
	 */
	static class PartialSubexpressionList
	{
		/** The {@link A_BundleTree} being parsed at this moment. */
		final A_BundleTree bundleTree;

		/** The parent {@link PartialSubexpressionList} being parsed. */
		final @Nullable PartialSubexpressionList parent;

		/** How many subexpressions deep that we're parsing. */
		final int depth;

		/**
		 * Create a list like the receiver, but with a different {@link
		 * A_BundleTree}.
		 *
		 * @param newBundleTree
		 *        The new {@link A_BundleTree} to replace the one in the
		 *        receiver within the copy.
		 * @return A {@code PartialSubexpressionList} like the receiver, but
		 *         with a different message bundle tree.
		 */
		PartialSubexpressionList advancedTo (
			final A_BundleTree newBundleTree)
		{
			return new PartialSubexpressionList(newBundleTree, parent);
		}

		/**
		 * Construct a new {@code PartialSubexpressionList}.
		 *
		 * @param bundleTree
		 *        The current {@link A_BundleTree} being parsed.
		 * @param parent
		 *        The enclosing partially-parsed super-expressions being parsed.
		 */
		PartialSubexpressionList (
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
	private void describeOn (
		final @Nullable PartialSubexpressionList partialSubexpressions,
		final StringBuilder builder)
	{
		@Nullable PartialSubexpressionList pointer = partialSubexpressions;
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
			if (bundleTree.equals(compilationContext.loader().rootBundleTree()))
			{
				builder.append("an expression");
			}
			else
			{
				// Reduce to the plans' unique bundles.
				final A_Map bundlesMap = bundleTree.allParsingPlansInProgress();
				final List<A_Bundle> bundles =
					toList(bundlesMap.keysAsSet().asTuple());
				bundles.sort(
					comparing(b -> b.message().atomName().asNativeString()));
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
					// Adjust the pc to refer to the actual instruction that
					// caused the argument parse, not the successor instruction
					// that was captured.
					final A_ParsingPlanInProgress adjustedPlanInProgress =
						newPlanInProgress(
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
			}
			pointer = pointer.parent;
		}
	}

	/**
	 * A simple static factory for constructing {@link Con1}s.  Java is crummy
	 * at deducing parameter types for constructors, so this static method can
	 * be used to elide the CompilerSolution type.
	 *
	 * @param superexpressions
	 *        The {@link PartialSubexpressionList} that explains why this
	 *        continuation exists.
	 * @param continuation
	 *        The {@link Continuation1NotNull} to invoke.
	 * @return The new {@link Con1}.
	 */
	static Con1 Con (
		final @Nullable PartialSubexpressionList superexpressions,
		final Continuation1NotNull<CompilerSolution> continuation)
	{
		return new Con1(superexpressions, continuation);
	}

	/**
	 * Execute {@code #tryBlock}, passing a {@linkplain Con1 continuation} that
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
	 */
	private void tryIfUnambiguousThen (
		final ParserState start,
		final Continuation1NotNull<Con1> tryBlock,
		final Con1 supplyAnswer)
	{
		assert compilationContext.getNoMoreWorkUnits() == null;
		final List<CompilerSolution> solutions = new ArrayList<>(1);
		compilationContext.setNoMoreWorkUnits(() ->
		{
			if (compilationContext.diagnostics.pollForAbort.getAsBoolean())
			{
				// We may have been asked to abort sub-tasks by a failure in
				// another module, so we can't trust the count of solutions.
				compilationContext.diagnostics.reportError();
				return;
			}
			final int count = solutions.size();
			switch (count)
			{
				case 0:
				{
					// No solutions were found.  Report the problems.
					compilationContext.diagnostics.reportError();
					break;
				}
				case 1:
				{
					// A unique solution was found.
					supplyAnswer.value(solutions.get(0));
					break;
				}
				default:
				{
					final CompilerSolution solution1 = solutions.get(0);
					final CompilerSolution solution2 = solutions.get(1);
					reportAmbiguousInterpretations(
						solution1.endState(),
						solution1.phrase(),
						solution2.phrase());
					break;
				}
			}
		});
		start.workUnitDo(
			tryBlock,
			Con(
				supplyAnswer.superexpressions,
				newSolution -> captureOneMoreSolution(newSolution, solutions)));
	}

	/**
	 * As part of determining an unambiguous interpretation of some top-level
	 * expression, deal with one (more) solution having been found.
	 *
	 * @param newSolution
	 *        The new {@link CompilerSolution}.
	 * @param solutions
	 *        The mutable {@link List} that collects {@link CompilerSolution}s,
	 *        at least up to the second solution to arrive.
	 */
	private synchronized void captureOneMoreSolution (
		final CompilerSolution newSolution,
		final List<CompilerSolution> solutions)
	{
		// Ignore any solutions discovered after the first two.
		if (solutions.size() < 2)
		{
			solutions.add(newSolution);
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
		final Continuation1NotNull<String> continuation,
		final Continuation0 fail,
		final ProblemHandler problemHandler)
	{
		final AvailRuntime runtime = currentRuntime();
		final File ref = resolvedName.sourceReference();
		final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		decoder.onMalformedInput(CodingErrorAction.REPLACE);
		decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
		final ByteBuffer input = ByteBuffer.allocateDirect(4096);
		final CharBuffer output = CharBuffer.allocate(4096);
		final AsynchronousFileChannel file;
		try
		{
			file = runtime.ioSystem().openFile(
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
		final StringBuilder sourceBuilder = new StringBuilder(4096);
		final MutableLong filePosition = new MutableLong(0L);
		// Kick off the asynchronous read.
		file.read(
			input,
			0L,
			null,
			new SimpleCompletionHandler<>(
				(bytesRead, unused, handler) ->
				{
					try
					{
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
								handler);
						}
						// Otherwise, close the file channel and queue the
						// original continuation.
						else
						{
							decoder.flush(output);
							sourceBuilder.append(output);
							file.close();
							runtime.execute(
								FiberDescriptor.compilerPriority,
								() -> continuation.value(sourceBuilder.toString()));
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
				},
				// failure
				(e, ignored, handler) -> {
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
				}));
	}

	/**
	 * Map the entire phrase through the (destructive) transformation specified
	 * by aBlock, children before parents. The block takes three arguments: the
	 * phrase, its parent, and the list of enclosing block phrases. Answer the
	 * recursively transformed phrase.
	 *
	 * @param object
	 *        The current {@linkplain PhraseDescriptor phrase}.
	 * @param transformer
	 *        What to do with each descendant.
	 * @param parentPhrase
	 *        This phrase's parent.
	 * @param outerPhrases
	 *        The list of {@linkplain BlockPhraseDescriptor blocks} surrounding
	 *        this phrase, from outermost to innermost.
	 * @param phraseMap
	 *        The {@link Map} from old {@linkplain PhraseDescriptor phrases} to
	 *        newly copied, mutable phrases.  This should ensure the consistency
	 *        of declaration references.
	 * @return A replacement for this phrase, possibly this phrase itself.
	 */
	private static A_Phrase treeMapWithParent (
		final A_Phrase object,
		final Transformer3<A_Phrase, A_Phrase, List<A_Phrase>, A_Phrase>
			transformer,
		final A_Phrase parentPhrase,
		final List<A_Phrase> outerPhrases,
		final Map<A_Phrase, A_Phrase> phraseMap)
	{
		if (phraseMap.containsKey(object))
		{
			return phraseMap.get(object);
		}
		final A_Phrase objectCopy = object.copyMutablePhrase();
		objectCopy.childrenMap(
			child ->
			{
				assert child != null;
				assert child.isInstanceOfKind(PARSE_PHRASE.mostGeneralType());
				return treeMapWithParent(
					child, transformer, objectCopy, outerPhrases, phraseMap);
			});
		final A_Phrase transformed = transformer.valueNotNull(
			objectCopy, parentPhrase, outerPhrases);
		transformed.makeShared();
		phraseMap.put(object, transformed);
		return transformed;
	}

	/**
	 * A statement was parsed correctly in two different ways. There may be more
	 * ways, but we stop after two as it's already an error. Report the error.
	 *
	 * @param where
	 *        Where the expressions were parsed from.
	 * @param interpretation1
	 *        The first interpretation as a {@linkplain PhraseDescriptor
	 *        phrase}.
	 * @param interpretation2
	 *        The second interpretation as a {@linkplain PhraseDescriptor
	 *        phrase}.
	 */
	private void reportAmbiguousInterpretations (
		final ParserState where,
		final A_Phrase interpretation1,
		final A_Phrase interpretation2)
	{
		final Mutable<A_Phrase> phrase1 = new Mutable<>(interpretation1);
		final Mutable<A_Phrase> phrase2 = new Mutable<>(interpretation2);
		findParseTreeDiscriminants(phrase1, phrase2);
		where.expected(
			STRONG,
			asList(phrase1.value, phrase2.value),
			strings ->
				"unambiguous interpretation.  "
					+ "Here are two possible parsings...\n\t"
					+ strings.get(0)
					+ "\n\t"
					+ strings.get(1));
		compilationContext.diagnostics.reportError();
	}

	/**
	 * Given two unequal phrases, find the smallest descendant phrases that
	 * still contain all the differences.  The given {@link Mutable} objects
	 * initially contain references to the root phrases, but are updated to
	 * refer to the most specific pair of phrases that contain all the
	 * differences.
	 *
	 * @param phrase1
	 *        A {@code Mutable} reference to a {@linkplain PhraseDescriptor
	 *        phrase}.  Updated to hold the most specific difference.
	 * @param phrase2
	 *        The {@code Mutable} reference to the other phrase. Updated to hold
	 *        the most specific difference.
	 */
	private static void findParseTreeDiscriminants (
		final Mutable<A_Phrase> phrase1,
		final Mutable<A_Phrase> phrase2)
	{
		while (true)
		{
			assert !phrase1.value.equals(phrase2.value);
			if (!phrase1.value.phraseKind().equals(
				phrase2.value.phraseKind()))
			{
				// The phrases are different kinds, so present them as what's
				// different.
				return;
			}
			if (phrase1.value.isMacroSubstitutionNode()
				&& phrase2.value.isMacroSubstitutionNode())
			{
				if (phrase1.value.apparentSendName().equals(
					phrase2.value.apparentSendName()))
				{
					// Two occurrences of the same macro.  Drill into the
					// resulting phrases.
					phrase1.value = phrase1.value.outputPhrase();
					phrase2.value = phrase2.value.outputPhrase();
					continue;
				}
				// Otherwise the macros are different and we should stop.
				return;
			}
			if (phrase1.value.isMacroSubstitutionNode()
				|| phrase2.value.isMacroSubstitutionNode())
			{
				// They aren't both macros, but one is, so they're different.
				return;
			}
			if (phrase1.value.phraseKindIsUnder(SEND_PHRASE)
				&& !phrase1.value.bundle().equals(phrase2.value.bundle()))
			{
				// They're sends of different messages, so don't go any deeper.
				return;
			}
			final List<A_Phrase> parts1 = new ArrayList<>();
			phrase1.value.childrenDo(parts1::add);
			final List<A_Phrase> parts2 = new ArrayList<>();
			phrase2.value.childrenDo(parts2::add);
			final boolean isBlock =
				phrase1.value.phraseKindIsUnder(BLOCK_PHRASE);
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
			phrase1.value = parts1.get(differentIndices.get(0));
			phrase2.value = parts2.get(differentIndices.get(0));
		}
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
		final AvailLoader newLoader = new AvailLoader(
			compilationContext.module(),
			compilationContext.getTextInterface());
		compilationContext.setLoader(newLoader);
	}

	/**
	 * Rollback the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction()}.
	 *
	 * @param afterRollback
	 *        What to do after rolling back.
	 */
	private void rollbackModuleTransaction (
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
	private void commitModuleTransaction ()
	{
		compilationContext.runtime.addModule(compilationContext.module());
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
	 * @param lexingState
	 *        The position at which the semantic restriction is being evaluated.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@link Throwable}.
	 */
	private void evaluateSemanticRestrictionFunctionThen (
		final A_SemanticRestriction restriction,
		final List<? extends A_BasicObject> args,
		final LexingState lexingState,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		final A_Function function = restriction.function();
		final A_RawFunction code = function.code();
		final A_Module mod = code.module();
		final A_Fiber fiber = newLoaderFiber(
			function.kind().returnType(),
			compilationContext.loader(),
			() ->
				formatString("Semantic restriction %s, in %s:%d",
					restriction.definitionMethod().bundles()
						.iterator().next().message(),
					mod.equals(nil)
						? "no module"
						: mod.moduleName(), code.startingLineNumber()));
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		fiber.textInterface(compilationContext.getTextInterface());
		lexingState.setFiberContinuationsTrackingWork(
			fiber, onSuccess, onFailure);
		runOutermostFunction(compilationContext.runtime, fiber, function, args);
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
	 *        SpecialAtom#CLIENT_DATA_GLOBAL_KEY} atom in the fiber.
	 * @param clientParseDataOut
	 *        A {@link MutableOrNull} into which we will store an {@link A_Map}
	 *        when the fiber completes successfully.  The map will be the
	 *        content of the fiber variable holding the client data, extracted
	 *        just after the fiber completes.  If unsuccessful, don't assign to
	 *        the {@code MutableOrNull}.
	 * @param lexingState
	 *        The position at which the macro body is being evaluated.
	 * @param onSuccess
	 *        What to do with the result of the evaluation, an {@link A_Phrase}.
	 * @param onFailure
	 *        What to do with a terminal {@link Throwable}.
	 */
	private void evaluateMacroFunctionThen (
		final A_Definition macro,
		final List<? extends A_Phrase> args,
		final A_Map clientParseData,
		final MutableOrNull<A_Map> clientParseDataOut,
		final LexingState lexingState,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		final A_Function function = macro.bodyBlock();
		final A_RawFunction code = function.code();
		final A_Module mod = code.module();
		final A_Fiber fiber = newLoaderFiber(
			function.kind().returnType(),
			compilationContext.loader(),
			() -> formatString("Macro evaluation %s, in %s:%d",
				macro.definitionMethod().bundles()
					.iterator().next().message(),
				mod.equals(nil)
					? "no module"
					: mod.moduleName(), code.startingLineNumber()));
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		fiber.setGeneralFlag(GeneralFlag.IS_EVALUATING_MACRO);
		A_Map fiberGlobals = fiber.fiberGlobals();
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true);
		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(compilationContext.getTextInterface());
		lexingState.setFiberContinuationsTrackingWork(
			fiber,
			outputPhrase ->
			{
				clientParseDataOut.value =
					fiber.fiberGlobals().mapAt(CLIENT_DATA_GLOBAL_KEY.atom);
				onSuccess.value(outputPhrase);
			},
			onFailure);
		runOutermostFunction(compilationContext.runtime, fiber, function, args);
	}

	/**
	 * Evaluate a phrase. It's a top-level statement in a module. Declarations
	 * are handled differently - they cause a variable to be declared in the
	 * module's scope.
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
	 *        statement must be {@linkplain NilDescriptor#nil nil}, so there
	 *        is no point in having the continuation accept this value, hence
	 *        the {@linkplain Continuation0 nullary continuation}.
	 */
	void evaluateModuleStatementThen (
		final ParserState startState,
		final ParserState afterStatement,
		final A_Phrase expression,
		final Map<A_Phrase, A_Phrase> declarationRemap,
		final Continuation0 onSuccess)
	{
		assert !expression.isMacroSubstitutionNode();
		// The mapping through declarationRemap has already taken place.
		final A_Phrase replacement = treeMapWithParent(
			expression,
			(phrase, parent, outerBlocks) -> phrase,
			nil,
			new ArrayList<>(),
			declarationRemap);

		final Continuation1NotNull<Throwable> phraseFailure =
			e ->
			{
				if (e instanceof AvailAssertionFailedException)
				{
					compilationContext.reportAssertionFailureProblem(
						startState.lineNumber(),
						startState.position(),
						(AvailAssertionFailedException) e);
				}
				else if (e instanceof AvailEmergencyExitException)
				{
					compilationContext.reportEmergencyExitProblem(
						startState.lineNumber(),
						startState.position(),
						(AvailEmergencyExitException) e);
				}
				else
				{
					compilationContext.reportExecutionProblem(
						startState.lineNumber(),
						startState.position(),
						e);
				}
				compilationContext.diagnostics.reportError();
			};

		if (!replacement.phraseKindIsUnder(DECLARATION_PHRASE))
		{
			// Only record module statements that aren't declarations. Users of
			// the module don't care if a module variable or constant is only
			// reachable from the module's methods.
			compilationContext.evaluatePhraseThen(
				replacement,
				afterStatement.lexingState,
				true,
				false,
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
						STRONG,
						"new module constant "
						+ name
						+ " not to have same name as existing "
						+ shadowProblem);
					compilationContext.diagnostics.reportError();
					return;
				}
				loader.startRecordingEffects();
				compilationContext.evaluatePhraseThen(
					replacement.initializationExpression(),
					afterStatement.lexingState,
					false,
					false,
					val ->
					{
						loader.stopRecordingEffects();
						final boolean canSummarize =
							loader.statementCanBeSummarized();
						final A_Type innerType = instanceTypeOrMetaOn(val);
						final A_Type varType = variableTypeFor(innerType);
						final A_Phrase creationSend = newSendNode(
							emptyTuple(),
							CREATE_MODULE_VARIABLE.bundle,
							newListNode(
								tuple(
									syntheticLiteralNodeFor(module),
									syntheticLiteralNodeFor(name),
									syntheticLiteralNodeFor(varType),
									syntheticLiteralNodeFor(trueObject()),
									syntheticLiteralNodeFor(
										objectFromBoolean(canSummarize)))),
							TOP.o());
						final A_Function creationFunction =
							createFunctionForPhrase(
								creationSend,
								module,
								replacement.token().lineNumber());
						// Force the declaration to be serialized.
						compilationContext.serializeWithoutSummary(
							creationFunction);
						final A_Variable var =
							createGlobal(varType, module, name, true);
						var.valueWasStablyComputed(canSummarize);
						module.addConstantBinding(name, var);
						// Update the map so that the local constant goes to
						// a module constant.  Then subsequent statements in
						// this sequence will transform uses of the constant
						// appropriately.
						final A_Phrase newConstant =
							newModuleConstant(
								replacement.token(),
								var,
								replacement.initializationExpression());
						declarationRemap.put(expression, newConstant);
						// Now create a module variable declaration (i.e.,
						// cheat) JUST for this initializing assignment.
						final A_Phrase newDeclaration =
							newModuleVariable(
								replacement.token(),
								var,
								nil,
								replacement.initializationExpression());
						final A_Phrase assign =
							newAssignment(
								newUse(replacement.token(), newDeclaration),
								syntheticLiteralNodeFor(val),
								expression.tokens(),
								false);
						final A_Function assignFunction =
							createFunctionForPhrase(
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
						STRONG,
						"new module variable "
						+ name
						+ " not to have same name as existing "
						+ shadowProblem);
					compilationContext.diagnostics.reportError();
					return;
				}
				final A_Type varType =
					variableTypeFor(replacement.declaredType());
				final A_Phrase creationSend = newSendNode(
					emptyTuple(),
					CREATE_MODULE_VARIABLE.bundle,
					newListNode(
						tuple(
							syntheticLiteralNodeFor(module),
							syntheticLiteralNodeFor(name),
							syntheticLiteralNodeFor(varType),
							syntheticLiteralNodeFor(falseObject()),
							syntheticLiteralNodeFor(falseObject()))),
					TOP.o());
				final A_Function creationFunction =
					createFunctionForPhrase(
						creationSend,
						module,
						replacement.token().lineNumber());
				creationFunction.makeImmutable();
				// Force the declaration to be serialized.
				compilationContext.serializeWithoutSummary(creationFunction);
				final A_Variable var =
					createGlobal(varType, module, name, false);
				module.addVariableBinding(name, var);
				if (!replacement.initializationExpression().equalsNil())
				{
					final A_Phrase newDeclaration =
						newModuleVariable(
							replacement.token(),
							var,
							replacement.typeExpression(),
							replacement.initializationExpression());
					declarationRemap.put(expression, newDeclaration);
					final A_Phrase assign = newAssignment(
						newUse(replacement.token(), newDeclaration),
						replacement.initializationExpression(),
						tuple(expression.token()),
						false);
					final A_Function assignFunction =
						createFunctionForPhrase(
							assign, module, replacement.token().lineNumber());
					compilationContext.evaluatePhraseThen(
						replacement.initializationExpression(),
						afterStatement.lexingState,
						false,
						false,
						val ->
						{
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
	 * keywords are keys of the {@link A_Map} argument {@code incomplete}.
	 *
	 * @param where
	 *        Where the keywords were expected.
	 * @param incomplete
	 *        A map of partially parsed keywords, where the keys are the strings
	 *        that were expected at this position.
	 * @param caseInsensitive
	 *        {@code true} if the parsed keywords are case-insensitive, {@code
	 *        false} otherwise.
	 * @param excludedStrings
	 *        The {@link Set} of {@link A_String}s to omit from the message,
	 *        since they were the actual encountered tokens' texts.  Note that
	 *        this set may have multiple elements because multiple lexers may
	 *        have produced competing tokens at this position.
	 */
	private void expectedKeywordsOf (
		final ParserState where,
		final A_Map incomplete,
		final boolean caseInsensitive,
		final Set<A_String> excludedStrings)
	{
		where.expected(
			MEDIUM,
			c ->
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
				for (final Entry entry : incomplete.mapIterable())
				{
					final A_String availTokenString = entry.key();
					if (!excludedStrings.contains(availTokenString))
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
										previousPlan = newPlanInProgress(
											inProgress.parsingPlan(),
											max(inProgress.parsingPc() - 1, 1));
									final A_Module issuingModule =
										bundle.message().issuingModule();
									final String moduleName =
										issuingModule.equalsNil()
											? "(built-in)"
											: issuingModule.moduleName()
												.asNativeString();
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
						buffer.append(')');
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
					where.lexingState,
					() -> c.value(builder.toString()));
			});
	}

	/**
	 * Pre-build the state of the initial parse stack.  Now that the top-most
	 * arguments get concatenated into a list, simply start with a list
	 * containing one empty list phrase.
	 */
	private static final List<A_Phrase> initialParseStack =
		singletonList(emptyListNode());

	/**
	 * Pre-build the state of the initial mark stack.  This stack keeps track of
	 * parsing positions to detect if progress has been made at certain points.
	 * This mechanism serves to prevent empty expressions from being considered
	 * an occurrence of a repeated or optional subexpression, even if it would
	 * otherwise be recognized as such.
	 */
	private static final List<Integer> initialMarkStack = emptyList();

	/**
	 * Parse a send phrase. To prevent infinite left-recursion and false
	 * ambiguity, we only allow a send with a leading keyword to be parsed from
	 * here, since leading underscore sends are dealt with iteratively
	 * afterward.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param continuation
	 *        What to do after parsing a complete send phrase.
	 */
	private void parseLeadingKeywordSendThen (
		final ParserState start,
		final Con1 continuation)
	{
		parseRestOfSendNode(
			start,
			compilationContext.loader().rootBundleTree(),
			null,
			start,
			false,  // Nothing consumed yet.
			false,  // (ditto)
			emptyList(),
			initialParseStack,
			initialMarkStack,
			Con(
				new PartialSubexpressionList(
					compilationContext.loader().rootBundleTree(),
					continuation.superexpressions),
				continuation));
	}

	/**
	 * Parse a send phrase whose leading argument has already been parsed.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param leadingArgument
	 *        The argument that was already parsed.
	 * @param initialTokenPosition
	 *        Where the leading argument started.
	 * @param continuation
	 *        What to do after parsing a send phrase.
	 */
	private void parseLeadingArgumentSendAfterThen (
		final ParserState start,
		final A_Phrase leadingArgument,
		final ParserState initialTokenPosition,
		final Con1 continuation)
	{
		assert start.lexingState != initialTokenPosition.lexingState;
		parseRestOfSendNode(
			start,
			compilationContext.loader().rootBundleTree(),
			leadingArgument,
			initialTokenPosition,
			false,  // Leading argument does not yet count as something parsed.
			false,  // (ditto)
			emptyList(),
			initialParseStack,
			initialMarkStack,
			Con(
				new PartialSubexpressionList(
					compilationContext.loader().rootBundleTree(),
					continuation.superexpressions),
				continuation));
	}

	/**
	 * Parse an expression with an optional leading-argument message send around
	 * it. Backtracking will find all valid interpretations.
	 *
	 * @param startOfLeadingArgument
	 *        Where the leading argument started.
	 * @param afterLeadingArgument
	 *        Just after the leading argument.
	 * @param phrase
	 *        An expression that acts as the first argument for a potential
	 *        leading-argument message send, or possibly a chain of them.
	 * @param continuation
	 *        What to do with either the passed phrase, or the phrase wrapped in
	 *        a leading-argument send.
	 */
	private void parseOptionalLeadingArgumentSendAfterThen (
		final ParserState startOfLeadingArgument,
		final ParserState afterLeadingArgument,
		final A_Phrase phrase,
		final Con1 continuation)
	{
		// It's optional, so try it with no wrapping.  We have to try this even
		// if it's a supercast, since we may be parsing an expression to be a
		// non-leading argument of some send.
		afterLeadingArgument.workUnitDo(
			continuation,
			new CompilerSolution(afterLeadingArgument, phrase));
		// Try to wrap it in a leading-argument message send.
		final Con1 con = Con(
			continuation.superexpressions,
			solution2 -> parseLeadingArgumentSendAfterThen(
				solution2.endState(),
				solution2.phrase(),
				startOfLeadingArgument,
				Con(
					continuation.superexpressions,
					solutionAfter -> parseOptionalLeadingArgumentSendAfterThen(
						startOfLeadingArgument,
						solutionAfter.endState(),
						solutionAfter.phrase(),
						continuation))));
		afterLeadingArgument.workUnitDo(
			con,
			new CompilerSolution(afterLeadingArgument, phrase));
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

	/** Statistic for type-checking an argument. */
	private static final Statistic typeCheckArgumentStat =
		new Statistic(
			"(type-check argument)",
			StatisticReport.RUNNING_PARSING_INSTRUCTIONS);

	/** Marker phrase to signal cleanly reaching the end of the input. */
	private static final A_Phrase endOfFileMarkerPhrase =
		newMarkerNode(stringFrom("End of file marker")).makeShared();

	/**
	 * We've parsed part of a send. Try to finish the job.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param bundleTreeArg
	 *        The bundle tree used to parse at this position.
	 * @param firstArgOrNull
	 *        Either null or an argument that must be consumed before any
	 *        keywords (or completion of a send).
	 * @param initialTokenPosition
	 *        The parse position where the send phrase started to be processed.
	 *        Does not count the position of the first argument if there are no
	 *        leading keywords.
	 * @param consumedAnything
	 *        Whether any actual tokens have been consumed so far for this send
	 *        phrase.  That includes any leading argument.
	 * @param consumedAnythingBeforeLatestArgument
	 *        Whether any tokens or arguments had been consumed before
	 *        encountering the most recent argument.  This is to improve
	 *        diagnostics when argument type checking is postponed past matches
	 *        for subsequent tokens.
	 * @param consumedTokens
	 *        The immutable {@link List} of {@link A_Token}s that have been
	 *        consumed so far in this potential method/macro send, only
	 *        including tokens that correspond with literal message parts of
	 *        this send (like the "+" of "_+_"), and raw tokens (like the "…" of
	 *        "…:=_;").
	 * @param argsSoFar
	 *        The list of arguments parsed so far. I do not modify it. This is a
	 *        stack of expressions that the parsing instructions will assemble
	 *        into a list that correlates with the top-level non-backquoted
	 *        underscores and guillemet groups in the message name.
	 * @param marksSoFar
	 *        The stack of mark positions used to test if parsing certain
	 *        subexpressions makes progress.
	 * @param continuation
	 *        What to do with a fully parsed send phrase.
	 */
	private void parseRestOfSendNode (
		final ParserState start,
		final A_BundleTree bundleTreeArg,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final boolean consumedAnythingBeforeLatestArgument,
		final List<A_Token> consumedTokens,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con1 continuation)
	{
		A_BundleTree tempBundleTree = bundleTreeArg;
		// If a bundle tree is marked as a source of a cycle, its latest
		// backward jump field is always the target.  Just continue processing
		// there and it'll never have to expand the current node.  However, it's
		// the expand() that might set up the cycle in the first place...
		tempBundleTree.expand(compilationContext.module());
		while (tempBundleTree.isSourceOfCycle())
		{
			// Jump to its (once-)equivalent ancestor.
			tempBundleTree = tempBundleTree.latestBackwardJump();
			// Give it a chance to find an equivalent ancestor of its own.
			tempBundleTree.expand(compilationContext.module());
			// Abort if the bundle trees have diverged.
			if (!tempBundleTree.allParsingPlansInProgress().equals(
				bundleTreeArg.allParsingPlansInProgress()))
			{
				// They've diverged.  Disconnect the backward link.
				bundleTreeArg.isSourceOfCycle(false);
				tempBundleTree = bundleTreeArg;
				break;
			}
		}
		final A_BundleTree bundleTree = tempBundleTree;

		boolean skipCheckArgumentAction = false;
		if (firstArgOrNull == null)
		{
			// A call site is only valid if at least one token has been parsed.
			if (consumedAnything)
			{
				final A_Set complete = bundleTree.lazyComplete();
				if (complete.setSize() > 0)
				{
					// There are complete messages, we didn't leave a leading
					// argument stranded, and we made progress in the file
					// (i.e., the message contains at least one token).
					assert marksSoFar.isEmpty();
					assert argsSoFar.size() == 1;
					final A_Phrase args = argsSoFar.get(0);
					for (final A_Bundle bundle : complete)
					{
						if (AvailRuntimeConfiguration.debugCompilerSteps)
						{
							System.out.println(
								"Completed send/macro: "
								+ bundle.message() + ' ' + args);
						}
						completedSendNode(
							initialTokenPosition,
							start,
							args,
							bundle,
							consumedTokens,
							continuation);
					}
				}
			}
			final A_Map incomplete = bundleTree.lazyIncomplete();
			if (incomplete.mapSize() > 0)
			{
				attemptToConsumeToken(
					start,
					initialTokenPosition,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation,
					incomplete,
					false);
			}
			final A_Map caseInsensitive =
				bundleTree.lazyIncompleteCaseInsensitive();
			if (caseInsensitive.mapSize() > 0)
			{
				attemptToConsumeToken(
					start,
					initialTokenPosition,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation,
					caseInsensitive,
					true);
			}
			final A_Map prefilter = bundleTree.lazyPrefilterMap();
			if (prefilter.mapSize() > 0)
			{
				final A_Phrase latestArgument = last(argsSoFar);
				if (latestArgument.isMacroSubstitutionNode()
					|| latestArgument
					.isInstanceOfKind(SEND_PHRASE.mostGeneralType()))
				{
					final A_Bundle argumentBundle =
						latestArgument.apparentSendName().bundleOrNil();
					assert !argumentBundle.equalsNil();
					if (prefilter.hasKey(argumentBundle))
					{
						final A_BundleTree successor =
							prefilter.mapAt(argumentBundle);
						if (AvailRuntimeConfiguration.debugCompilerSteps)
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
							consumedAnythingBeforeLatestArgument,
							consumedTokens,
							argsSoFar,
							marksSoFar,
							continuation);
						// Don't allow any check-argument actions to be
						// processed normally, as it would ignore the
						// restriction which we've been so careful to prefilter.
						skipCheckArgumentAction = true;
					}
					// The argument name was not in the prefilter map, so fall
					// through to allow normal action processing, including the
					// default check-argument action if it's present.
				}
			}
			final A_BasicObject typeFilterTreePojo =
				bundleTree.lazyTypeFilterTreePojo();
			if (!typeFilterTreePojo.equalsNil())
			{
				// Use the most recently pushed phrase's type to look up the
				// successor bundle tree.  This implements aggregated argument
				// type filtering.
				final A_Phrase latestPhrase = last(argsSoFar);
				final LookupTree<A_Tuple, A_BundleTree, A_BundleTree>
					typeFilterTree = typeFilterTreePojo.javaObjectNotNull();
				final long timeBefore = captureNanos();
				final A_BundleTree successor =
					MessageBundleTreeDescriptor.parserTypeChecker.lookupByValue(
						typeFilterTree,
						latestPhrase,
						bundleTree.latestBackwardJump());
				final long timeAfter = captureNanos();
				typeCheckArgumentStat.record(
					timeAfter - timeBefore,
					Interpreter.currentIndex());
				if (AvailRuntimeConfiguration.debugCompilerSteps)
				{
					System.out.println(
						"Type filter: " + latestPhrase
							+ " -> " + successor);
				}
				// Don't complain if at least one plan was happy with the type
				// of the argument.  Otherwise list all argument type/plan
				// expectations as neatly as possible.
				if (successor.allParsingPlansInProgress().mapSize() == 0)
				{
					// Also be silent if no static tokens have been consumed
					// yet.
					if (!consumedTokens.isEmpty())
					{
						start.expected(
							MEDIUM,
							continueWithDescription -> stringifyThen(
								compilationContext.runtime,
								compilationContext.getTextInterface(),
								latestPhrase.expressionType(),
								actualTypeString -> describeFailedTypeTestThen(
									actualTypeString,
									bundleTree,
									continueWithDescription)));
					}
				}
				eventuallyParseRestOfSendNode(
					start,
					successor,
					null,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation);
				// Parse instruction optimization allows there to be some plans
				// that do a type filter here, but some that are able to
				// postpone it.  Therefore, also allow general actions to be
				// collected here by falling through.
			}
		}
		final A_Map actions = bundleTree.lazyActions();
		if (actions.mapSize() > 0)
		{
			for (final Entry entry : actions.mapIterable())
			{
				final int keyInt = entry.key().extractInt();
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
					start.workUnitDo(
						value -> runParsingInstructionThen(
							start,
							keyInt,
							firstArgOrNull,
							argsSoFar,
							marksSoFar,
							initialTokenPosition,
							consumedAnything,
							consumedAnythingBeforeLatestArgument,
							consumedTokens,
							value,
							continuation),
						entry.value());
				}
			}
		}
	}

	/**
	 * Attempt to consume a token from the source.
	 *
	 * @param start
	 *        Where to start consuming the token.
	 * @param initialTokenPosition
	 *        Where the current potential send phrase started.
	 * @param consumedAnythingBeforeLatestArgument
	 *        Whether any tokens or arguments had been consumed before
	 *        encountering the most recent argument.  This is to improve
	 *        diagnostics when argument type checking is postponed past matches
	 *        for subsequent tokens.
	 * @param consumedTokens
	 *        An immutable {@link PrefixSharingList} of {@link A_Token}s that
	 *        have been consumed so far for the current potential send phrase.
	 *        The tokens are only those that match literal parts of the message
	 *        name or explicit token arguments.
	 * @param argsSoFar
	 *        The argument phrases that have been accumulated so far.
	 * @param marksSoFar
	 *        The mark stack.
	 * @param continuation
	 *        What to do when the current potential send phrase is complete.
	 * @param tokenMap
	 *        A map from string to message bundle tree, used for parsing tokens
	 *        when in this state.
	 * @param caseInsensitive
	 *        Whether to match the token case-insensitively.
	 */
	private void attemptToConsumeToken (
		final ParserState start,
		final ParserState initialTokenPosition,
		final boolean consumedAnythingBeforeLatestArgument,
		final List<A_Token> consumedTokens,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con1 continuation,
		final A_Map tokenMap,
		final boolean caseInsensitive)
	{
		skipWhitespaceAndComments(
			start,
			afterWhiteSpaceStates ->
			{
				for (final ParserState afterWhiteSpace : afterWhiteSpaceStates)
				{
					afterWhiteSpace.lexingState.withTokensDo(tokens ->
					{
						// At least one of them must be a non-whitespace, but we
						// can completely ignore the whitespaces(/comments).
						boolean foundOne = false;
						boolean recognized = false;
						for (final A_Token token : tokens)
						{
							final TokenType tokenType = token.tokenType();
							if (tokenType == COMMENT || tokenType == WHITESPACE)
							{
								continue;
							}
							foundOne = true;
							final A_String string = caseInsensitive
								? token.lowerCaseString()
								: token.string();
							if (tokenType != KEYWORD && tokenType != OPERATOR)
							{
								continue;
							}
							if (!tokenMap.hasKey(string))
							{
								continue;
							}
							final long timeBefore = captureNanos();
							final A_BundleTree successor =
								tokenMap.mapAt(string);
							if (AvailRuntimeConfiguration.debugCompilerSteps)
							{
								System.out.println(
									format(
										"Matched %s token: %s @%d for %s",
										caseInsensitive ? "insensitive " : "",
										string,
										token.lineNumber(),
										successor));
							}
							recognized = true;
							// Record this token for the call site.
							final ParserState afterToken = new ParserState(
								token.nextLexingState(), start.clientDataMap);
							eventuallyParseRestOfSendNode(
								afterToken,
								successor,
								null,
								initialTokenPosition,
								true,  // Just consumed a token.
								consumedAnythingBeforeLatestArgument,
								append(consumedTokens, token),
								argsSoFar,
								marksSoFar,
								continuation);
							final long timeAfter = captureNanos();
							final Statistic stat = caseInsensitive
								? matchTokenInsensitivelyStat
								: matchTokenStat;
							stat.record(
								timeAfter - timeBefore,
								Interpreter.currentIndex());
						}
						assert foundOne;
						// Only report if at least one static token has been
						// consumed.
						if (!recognized && !consumedTokens.isEmpty())
						{
							final Set<A_String> strings =
								tokens.stream()
									.map(
										caseInsensitive
											? A_Token::lowerCaseString
											: A_Token::string)
									.collect(toSet());
							expectedKeywordsOf(
								start, tokenMap, caseInsensitive, strings);
						}
					});
				}
			});
	}

	/**
	 * Skip whitespace and comments, and evaluate the given {@link
	 * Continuation1NotNull} with each possible successive {@link A_Token}s.
	 *
	 * @param start
	 *        Where to start scanning.
	 * @param continuation
	 *        What to do with each possible next non-whitespace token.
	 */
	void nextNonwhitespaceTokensDo (
		final ParserState start,
		final Continuation1NotNull<A_Token> continuation)
	{
		compilationContext.startWorkUnits(1);
		skipWhitespaceAndComments(
			start,
			compilationContext.workUnitCompletion(
				start.lexingState,
				null,
				statesAfterWhitespace ->
				{
					for (final ParserState state : statesAfterWhitespace)
					{
						state.lexingState.withTokensDo(
							tokens ->
							{
								for (final A_Token token : tokens)
								{
									final TokenType tokenType =
										token.tokenType();
									if (tokenType != WHITESPACE
										&& tokenType != COMMENT)
									{
										state.workUnitDo(continuation, token);
									}
								}
							});
					}
				}));
	}

	/**
	 * Skip over whitespace and comment tokens, collecting the latter.  Produce
	 * a {@link List} of {@link ParserState}s corresponding to the possible
	 * positions after completely parsing runs of whitespaces and comments
	 * (i.e., the potential {@link A_Token}s that follow each such {@link
	 * ParserState} must include at least one token that isn't whitespace or a
	 * comment).  Invoke the continuation with this list of parser states.
	 *
	 * <p>Informally, it just skips as many whitespace and comment tokens as it
	 * can, but the nature of the ambiguous lexer makes this more subtle to
	 * express.</p>
	 *
	 * <p>Note that the continuation always gets invoked exactly once, after any
	 * relevant lexing has completed.</p>
	 *
	 * @param start
	 *        Where to start consuming the token.
	 * @param continuation
	 *        What to invoke with the collection of successor {@link
	 *        ParserState}s.
	 */
	private static void skipWhitespaceAndComments (
		final ParserState start,
		final Continuation1NotNull<List<ParserState>> continuation)
	{
		final AtomicBoolean ran = new AtomicBoolean(false);
		skipWhitespaceAndComments(
			start,
			list ->
			{
				final boolean old = ran.getAndSet(true);
				assert !old : "Completed skipping whitespace twice.";
				continuation.value(list);
			},
			new AtomicBoolean(false));
	}

	/**
	 * Skip over whitespace and comment tokens, collecting the latter.  Produce
	 * a {@link List} of {@link ParserState}s corresponding to the possible
	 * positions after completely parsing runs of whitespaces and comments
	 * (i.e., the potential {@link A_Token}s that follow each such {@link
	 * ParserState} must include at least one token that isn't whitespace or a
	 * comment).  Invoke the continuation with this list of parser states.
	 *
	 * <p>Informally, it just skips as many whitespace and comment tokens as it
	 * can, but the nature of the ambiguous lexer makes this more subtle to
	 * express.</p>
	 *
	 * @param start
	 *        Where to start consuming the token.
	 * @param continuation
	 *        What to invoke with the collection of successor {@link
	 *        ParserState}s.
	 * @param ambiguousWhitespace
	 *        An {@link AtomicBoolean}, which should be set to {@code false} by
	 *        the outermost caller, but will be set to {@code true} if any part
	 *        of the sequence of whitespace tokens is determined to be
	 *        ambiguously scanned.  The ambiguity will be reported in that case,
	 *        so there's no need for the client to ever read the value.
	 */
	private static void skipWhitespaceAndComments (
		final ParserState start,
		final Continuation1NotNull<List<ParserState>> continuation,
		final AtomicBoolean ambiguousWhitespace)
	{
		if (ambiguousWhitespace.get())
		{
			// Should probably be queued instead of called directly.
			continuation.value(emptyList());
			return;
		}
		start.lexingState.withTokensDo(tokens ->
		{
			final List<A_Token> toSkip = new ArrayList<>(1);
			final List<A_Token> toKeep = new ArrayList<>(1);
			for (final A_Token token : tokens)
			{
				final TokenType tokenType = token.tokenType();
				if (tokenType == COMMENT || tokenType == WHITESPACE)
				{
					for (final A_Token previousToSkip : toSkip)
					{
						if (previousToSkip.string().equals(token.string()))
						{
							ambiguousWhitespace.set(true);
							if (tokenType == WHITESPACE
								&& token.string().tupleSize() < 50)
							{
								start.expected(
									STRONG,
									"the whitespace " + token.string()
										+ " to be uniquely lexically"
										+ " scanned.  There are probably"
										+ " multiple conflicting lexers"
										+ " visible in this module.");
							}
							else if (tokenType == COMMENT
								&& token.string().tupleSize() < 100)
							{
								start.expected(
									STRONG,
									"the comment " + token.string()
										+ " to be uniquely lexically"
										+ " scanned.  There are probably"
										+ " multiple conflicting lexers"
										+ " visible in this module.");
							}
							else
							{
								start.expected(
									STRONG,
									"the comment or whitespace ("
										+ token.string().tupleSize()
										+ " characters) to be uniquely"
										+ " lexically scanned.  There are"
										+ " probably multiple conflicting"
										+ " lexers visible in this"
										+ " module.");
							}
							continuation.value(emptyList());
							return;
						}
					}
					toSkip.add(token);
				}
				else
				{
					toKeep.add(token);
				}
			}
			if (toSkip.size() == 0)
			{
				if (toKeep.size() == 0)
				{
					start.expected(
						STRONG,
						"a way to parse tokens here, but all lexers were "
							+ "unproductive");
					continuation.value(emptyList());
				}
				else
				{
					// The common case where no interpretation is
					// whitespace/comment, but there's a non-whitespace token
					// (or end of file).  Allow parsing to continue right here.
					continuation.value(singletonList(start));
				}
				return;
			}
			if (toSkip.size() == 1 && toKeep.size() == 0)
			{
				// Common case of an unambiguous whitespace/comment token.
				final A_Token token = toSkip.get(0);
				skipWhitespaceAndComments(
					new ParserState(
						token.nextLexingState(), start.clientDataMap),
					continuation,
					ambiguousWhitespace);
				return;
			}
			// Rarer, more complicated cases with at least two interpretations,
			// at least one of which is whitespace/comment.
			final List<ParserState> result = new ArrayList<>(3);
			if (toKeep.size() > 0)
			{
				// There's at least one non-whitespace token present at start.
				result.add(start);
			}
			final MutableInt countdown = new MutableInt(toSkip.size());
			for (final A_Token tokenToSkip : toSkip)
			{
				// Common case of an unambiguous whitespace/comment token.
				final ParserState after = new ParserState(
					tokenToSkip.nextLexingState(), start.clientDataMap);
				skipWhitespaceAndComments(
					after,
					partialList ->
					{
						synchronized (countdown)
						{
							result.addAll(partialList);
							countdown.value--;
							assert countdown.value >= 0;
							if (countdown.value == 0)
							{
								continuation.value(result);
							}
						}
					},
					ambiguousWhitespace);
			}
		});
	}

	/**
	 * A type test for a leaf argument of a potential method or macro invocation
	 * site has failed to produce any viable candidates.  Arrange to have a
	 * suitable diagnostic description of the problem produced, then passed to
	 * the given continuation.  This method may or may not return before the
	 * description has been constructed and passed to the continuation.
	 *
	 * @param actualTypeString
	 *        A String describing the actual type of the argument.
	 * @param bundleTree
	 *        The {@link A_BundleTree} at which parsing was foiled.  There may
	 *        be multiple potential methods and/or macros at this position, none
	 *        of which will have survived the type test.
	 * @param continuation
	 *        What to do once a description of the problem has been produced.
	 */
	private void describeFailedTypeTestThen (
		final String actualTypeString,
		final A_BundleTree bundleTree,
		final Continuation1NotNull<String> continuation)
	{
		final Set<A_Type> typeSet = new HashSet<>();
		final Map<String, Set<A_Type>> typesByPlanString = new HashMap<>();
		for (final Entry entry
			: bundleTree.allParsingPlansInProgress().mapIterable())
		{
			final A_Map submap = entry.value();
			for (final Entry subentry : submap.mapIterable())
			{
				for (final A_ParsingPlanInProgress planInProgress
					: subentry.value())
				{
					final A_DefinitionParsingPlan plan =
						planInProgress.parsingPlan();
					final A_Tuple instructions = plan.parsingInstructions();
					final int instruction =
						instructions.tupleIntAt(planInProgress.parsingPc());
					final int typeIndex =
						TYPE_CHECK_ARGUMENT.typeCheckArgumentIndex(instruction);
					// TODO(MvG) Present the full phrase type if it can be a
					// macro argument.
					final A_Type argType =
						constantForIndex(typeIndex).expressionType();
					typeSet.add(argType);
					// Add the type under the given plan *string*, even if it's
					// a different underlying message bundle.
					final Set<A_Type> typesForPlan =
						typesByPlanString.computeIfAbsent(
							planInProgress.nameHighlightingPc(),
							planString -> new HashSet<>());
					typesForPlan.add(argType);
				}
			}
		}
		final List<A_Type> typeList = new ArrayList<>(typeSet);
		// Generate the type names in parallel.
		stringifyThen(
			compilationContext.runtime,
			compilationContext.getTextInterface(),
			typeList,
			typeNamesList ->
			{
				assert typeList.size() == typeNamesList.size();
				final Map<A_Type, String> typeMap =
					IntStream.range(0, typeList.size())
					.boxed()
					.collect(toMap(typeList::get, typeNamesList::get));
				// Stitch the type names back onto the plan strings, prior to
				// sorting by type name.
				final List<Map.Entry<String, Set<A_Type>>> entries =
					new ArrayList<>(typesByPlanString.entrySet());
				entries.sort(comparing(Map.Entry::getKey));

				final StringBuilder builder = new StringBuilder(100);
				builder.append("phrase to have a type other than ");
				builder.append(actualTypeString);
				builder.append(".  Expecting:");
				for (final Map.Entry<String, Set<A_Type>> entry : entries)
				{
					final String planString = entry.getKey();
					final Set<A_Type> types = entry.getValue();
					builder.append("\n\t");
					builder.append(planString);
					builder.append("   ");
					final List<String> typeNames = types.stream()
						.map(typeMap::get)
						.sorted()
						.collect(toList());
					boolean first = true;
					for (final String typeName : typeNames)
					{
						if (!first)
						{
							builder.append(", ");
						}
						first = false;
						builder.append(increaseIndentation(typeName, 2));
					}
				}
				continuation.value(builder.toString());
			}
		);
	}

	/**
	 * Execute one non-keyword-parsing instruction, then run the continuation.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param instruction
	 *        An int encoding the {@linkplain ParsingOperation parsing
	 *        instruction} to execute.
	 * @param firstArgOrNull
	 *        Either the already-parsed first argument or null. If we're looking
	 *        for leading-argument message sends to wrap an expression then this
	 *        is not-null before the first argument position is encountered,
	 *        otherwise it's null and we should reject attempts to start with an
	 *        argument (before a keyword).
	 * @param argsSoFar
	 *        The message arguments that have been parsed so far.
	 * @param marksSoFar
	 *        The parsing markers that have been recorded so far.
	 * @param initialTokenPosition
	 *        The position at which parsing of this message started. If it was
	 *        parsed as a leading argument send (i.e., firstArgOrNull started
	 *        out non-null) then the position is of the token following the
	 *        first argument.
	 * @param consumedAnything
	 *        Whether any tokens or arguments have been consumed yet.
	 * @param consumedAnythingBeforeLatestArgument
	 *        Whether any tokens or arguments had been consumed before
	 *        encountering the most recent argument.  This is to improve
	 *        diagnostics when argument type checking is postponed past matches
	 *        for subsequent tokens.
	 * @param consumedTokens
	 *        The immutable {@link List} of "static" {@link A_Token}s that have
	 *        been encountered and consumed for the current method or macro
	 *        invocation being parsed.  These are the tokens that correspond
	 *        with tokens that occur verbatim inside the name of the method or
	 *        macro.
	 * @param successorTrees
	 *        The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        MessageBundleTreeDescriptor bundle trees} at which to continue
	 *        parsing.
	 * @param continuation
	 *        What to do with a complete {@linkplain SendPhraseDescriptor
	 *        message send phrase}.
	 */
	private void runParsingInstructionThen (
		final ParserState start,
		final int instruction,
		final @Nullable A_Phrase firstArgOrNull,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final boolean consumedAnythingBeforeLatestArgument,
		final List<A_Token> consumedTokens,
		final A_Tuple successorTrees,
		final Con1 continuation)
	{
		final ParsingOperation op = decode(instruction);
		if (AvailRuntimeConfiguration.debugCompilerSteps)
		{
			if (op.ordinal() >= distinctInstructions)
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

		final long timeBefore = captureNanos();
		op.execute(
			this,
			instruction,
			successorTrees,
			start,
			firstArgOrNull,
			argsSoFar,
			marksSoFar,
			initialTokenPosition,
			consumedAnything,
			consumedAnythingBeforeLatestArgument,
			consumedTokens,
			continuation);
		final long timeAfter = captureNanos();
		op.parsingStatisticInNanoseconds.record(
			timeAfter - timeBefore,
			Interpreter.currentIndex());
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
	 * @param consumedAnythingBeforeLatestArgument
	 *        Whether any tokens or arguments had been consumed before
	 *        encountering the most recent argument.  This is to improve
	 *        diagnostics when argument type checking is postponed past matches
	 *        for subsequent tokens.
	 * @param consumedTokens
	 *        The list of {@link A_Token}s that have been consumed so far for
	 *        this message send.
	 * @param argsSoFar
	 *        The stack of phrases.
	 * @param marksSoFar
	 *        The stack of markers that detect epsilon transitions
	 *        (subexpressions consisting of no tokens).
	 * @param continuation
	 *        What should eventually be done with the completed macro
	 *        invocation, should parsing ever get that far.
	 */
	void runPrefixFunctionThen (
		final ParserState start,
		final A_BundleTree successorTree,
		final A_Function prefixFunction,
		final List<AvailObject> listOfArgs,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final boolean consumedAnythingBeforeLatestArgument,
		final List<A_Token> consumedTokens,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con1 continuation)
	{
		if (!prefixFunction.kind().acceptsListOfArgValues(listOfArgs))
		{
			return;
		}
		final A_Fiber fiber = newLoaderFiber(
			prefixFunction.kind().returnType(),
			compilationContext.loader(),
			() ->
			{
				final A_RawFunction code = prefixFunction.code();
				return
					formatString("Macro prefix %s, in %s:%d",
						code.methodName(),
						code.module().moduleName(),
						code.startingLineNumber());
			});
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		final A_Map withTokens = start.clientDataMap
			.mapAtPuttingCanDestroy(
				ALL_TOKENS_KEY.atom,
				tupleFromList(start.lexingState.allTokens),
				false)
			.mapAtPuttingCanDestroy(
				STATIC_TOKENS_KEY.atom, tupleFromList(consumedTokens), false);
		A_Map fiberGlobals = fiber.fiberGlobals();
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, withTokens.makeImmutable(), true);
		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(compilationContext.getTextInterface());
		start.lexingState.setFiberContinuationsTrackingWork(
			fiber,
			ignoredResult ->
			{
				// The prefix function ran successfully.
				final A_Map replacementClientDataMap =
					fiber.fiberGlobals().mapAt(CLIENT_DATA_GLOBAL_KEY.atom);
				eventuallyParseRestOfSendNode(
					start.withMap(replacementClientDataMap),
					successorTree,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation);
			},
			e ->
			{
				// The prefix function failed in some way.
				if (e instanceof AvailAcceptedParseException)
				{
					// Prefix functions are allowed to explicitly accept a
					// parse.
					final A_Map replacementClientDataMap =
						fiber.fiberGlobals().mapAt(CLIENT_DATA_GLOBAL_KEY.atom);
					eventuallyParseRestOfSendNode(
						start.withMap(replacementClientDataMap),
						successorTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						consumedAnythingBeforeLatestArgument,
						consumedTokens,
						argsSoFar,
						marksSoFar,
						continuation);
				}
				if (e instanceof AvailRejectedParseException)
				{
					final AvailRejectedParseException stronger = cast(e);
					start.expected(
						stronger.level,
						stronger.rejectionString().asNativeString());
				}
				else
				{
					start.expected(
						STRONG,
						new FormattingDescriber(
							"prefix function not to have failed with:\n%s", e));
				}
			});
		runOutermostFunction(
			compilationContext.runtime, fiber, prefixFunction, listOfArgs);
	}

	/**
	 * Check the proposed message send for validity. Use not only the applicable
	 * {@linkplain MethodDefinitionDescriptor method definitions}, but also any
	 * semantic restrictions. The semantic restrictions may choose to
	 * {@linkplain P_RejectParsing reject the parse}, indicating that the
	 * argument types are mutually incompatible.  If all semantic restrictions
	 * succeed, invoke onSuccess with the intersection of the produced types and
	 * the applicable method body return types.
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
	 * @param onSuccess
	 *        What to do with the strengthened return type.  This may be
	 *        invoked at most once, and only if no semantic restriction rejected
	 *        the parse.
	 */
	private void validateArgumentTypes (
		final A_Bundle bundle,
		final List<? extends A_Type> argTypes,
		final A_Definition macroOrNil,
		final ParserState state,
		final Continuation1NotNull<A_Type> onSuccess)
	{
		final A_Method method = bundle.bundleMethod();
		final A_Tuple methodDefinitions = method.definitionsTuple();
		final A_Set restrictions = method.semanticRestrictions();
		// Filter the definitions down to those that are locally most specific.
		// Fail if more than one survives.

		if (methodDefinitions.tupleSize() > 0)
		{
			// There are method definitions.
			// Compiler should have assured there were no bottom or
			// top argument expressions.
			argTypes.forEach(
				argType ->
				{
					assert !argType.isBottom() && !argType.isTop();
				});
		}
		// Find all method definitions that could match the argument types.
		// Only consider definitions that are defined in the current module or
		// an ancestor.
		final A_Set allAncestors = compilationContext.module().allAncestors();
		final List<A_Definition> filteredByTypes = macroOrNil.equalsNil()
			? method.filterByTypes(argTypes)
			: singletonList(macroOrNil);
		final List<A_Definition> satisfyingDefinitions = new ArrayList<>();
		for (final A_Definition definition : filteredByTypes)
		{
			final A_Module definitionModule = definition.definitionModule();
			if (definitionModule.equalsNil()
				|| allAncestors.hasElement(definitionModule))
			{
				satisfyingDefinitions.add(definition);
			}
		}
		if (satisfyingDefinitions.isEmpty())
		{
			state.expected(
				STRONG,
				describeWhyDefinitionsAreInapplicable(
					bundle,
					argTypes,
					macroOrNil.equalsNil()
						? methodDefinitions
						: tuple(macroOrNil),
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
			final A_Module definitionModule = restriction.definitionModule();
			if (definitionModule.equalsNil()
				|| allAncestors.hasElement(restriction.definitionModule()))
			{
				if (restriction.function().kind().acceptsListOfArgValues(
					argTypes))
				{
					restrictionsToTry.add(restriction);
				}
			}
		}
		// If there are no relevant semantic restrictions, then immediately
		// invoke the success continuation and exit.
		if (restrictionsToTry.isEmpty())
		{
			onSuccess.value(intersection.value);
			return;
		}
		// Run all relevant semantic restrictions, in parallel, computing the
		// type intersection of their results.
		final AtomicInteger outstanding =
			new AtomicInteger(restrictionsToTry.size());
		final AtomicInteger failureCount = new AtomicInteger(0);
		final ReadWriteLock outstandingLock = new ReentrantReadWriteLock();
		// This runs when the last applicable semantic restriction finishes.
		final Continuation0 whenDone = () ->
		{
			assert outstanding.get() == 0;
			if (failureCount.get() == 0)
			{
				// No failures occurred.  Invoke success.
				onSuccess.value(intersection.value);
			}
		};
		final Continuation1NotNull<AvailObject> intersectAndDecrement =
			restrictionType ->
			{
				assert restrictionType.isType();
				lockWhile(
					outstandingLock.writeLock(),
					() ->
					{
						if (failureCount.get() == 0)
						{
							intersection.value =
								intersection.value.typeIntersection(
									restrictionType);
						}
					});
				if (outstanding.decrementAndGet() == 0)
				{
					whenDone.value();
				}
			};
		final Continuation1NotNull<Throwable> failAndDecrement =
			e ->
			{
				if (e instanceof AvailAcceptedParseException)
				{
					// This is really a success.
					intersectAndDecrement.value(TOP.o());
					return;
				}
				if (e instanceof AvailRejectedParseException)
				{
					final AvailRejectedParseException rej =
						(AvailRejectedParseException) e;
					state.expected(
						rej.level,
						rej.rejectionString().asNativeString()
						+ " (while parsing send of "
						+ bundle.message().atomName().asNativeString()
						+ ')');
				}
				else if (e instanceof FiberTerminationException)
				{
					state.expected(
						STRONG,
						"semantic restriction not to raise an "
						+ "unhandled exception (while parsing "
						+ "send of "
						+ bundle.message().atomName().asNativeString()
						+ "):\n\t"
						+ e);
				}
				else if (e instanceof AvailAssertionFailedException)
				{
					final AvailAssertionFailedException ex =
						(AvailAssertionFailedException) e;
					state.expected(
						STRONG,
						"assertion not to have failed "
						+ "(while parsing send of "
						+ bundle.message().atomName().asNativeString()
						+ "):\n\t"
						+ ex.getAssertionString().asNativeString());
				}
				else
				{
					state.expected(
						STRONG,
						new FormattingDescriber(
							"unexpected error: %s", e));
				}
				failureCount.incrementAndGet();
				if (outstanding.decrementAndGet() == 0)
				{
					whenDone.value();
				}
			};
		// Launch the semantic restrictions in parallel.
		for (final A_SemanticRestriction restriction : restrictionsToTry)
		{
			evaluateSemanticRestrictionFunctionThen(
				restriction,
				argTypes,
				state.lexingState,
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
		return c ->
		{
			final String kindOfDefinition =
				definitionsTuple.tupleAt(1).isMacroDefinition()
					? "macro"
					: "method";
			final List<A_Definition> allVisible = new ArrayList<>();
			for (final A_Definition def : definitionsTuple)
			{
				final A_Module definingModule = def.definitionModule();
				if (definingModule.equalsNil()
					|| allAncestorModules.hasElement(def.definitionModule()))
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
			stringifyThen(
				compilationContext.runtime,
				compilationContext.getTextInterface(),
				uniqueValues,
				strings ->
				{
					@SuppressWarnings({
						"resource",
						"IOResourceOpenedButNotSafelyClosed"
					})
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
							definition.definitionModuleName(),
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
					if (allVisible.isEmpty())
					{
						c.value(
							"[[[Internal problem - No visible implementations;"
								+ " should have been excluded.]]]\n"
								+ builder);
					}
					else
					{
						c.value(builder.toString());
					}
				});
		};
	}

	/**
	 * A complete {@linkplain SendPhraseDescriptor send phrase} has been parsed.
	 * Create the send phrase and invoke the continuation.
	 *
	 * <p>
	 * If this is a macro, invoke the body immediately with the argument
	 * expressions to produce a phrase.
	 * </p>
	 *
	 * @param stateBeforeCall
	 *        The initial parsing state, prior to parsing the entire message.
	 * @param stateAfterCall
	 *        The parsing state after the message.
	 * @param argumentsListNode
	 *        The {@linkplain ListPhraseDescriptor list phrase} that will hold
	 *        all the arguments of the new send phrase.
	 * @param bundle
	 *        The {@linkplain MessageBundleDescriptor message bundle} that
	 *        identifies the message to be sent.
	 * @param consumedTokens
	 *        The list of all tokens collected for this send phrase.  This
	 *        includes only those tokens that are operator or keyword tokens
	 *        that correspond with parts of the method name itself, not the
	 *        arguments.
	 * @param continuation
	 *        What to do with the resulting send phrase.
	 */
	private void completedSendNode (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final A_Phrase argumentsListNode,
		final A_Bundle bundle,
		final List<A_Token> consumedTokens,
		final Con1 continuation)
	{
		final A_Method method = bundle.bundleMethod();
		final A_Tuple macroDefinitionsTuple = method.macroDefinitionsTuple();
		final A_Tuple definitionsTuple = method.definitionsTuple();
		if (definitionsTuple.tupleSize() + macroDefinitionsTuple.tupleSize()
			== 0)
		{
			stateAfterCall.expected(
				STRONG,
				"there to be a method or macro definition for "
				+ bundle.message()
				+ ", but there wasn't");
			return;
		}

		// An applicable macro definition (even if ambiguous) prevents this site
		// from being a method invocation.
		A_Definition macro = nil;
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
				final A_Module definitionModule = definition.definitionModule();
				if (definitionModule.equalsNil()
					|| allAncestors.hasElement(definitionModule))
				{
					visibleDefinitions.add(definition);
				}
			}
			@Nullable AvailErrorCode errorCode = null;
			if (visibleDefinitions.size() == macroDefinitionsTuple.tupleSize())
			{
				// All macro definitions are visible.  Use the lookup tree.
				final A_Tuple matchingMacros =
					method.lookupMacroByPhraseTuple(
						argumentsListNode.expressionsTuple());
				switch (matchingMacros.tupleSize())
				{
					case 0:
					{
						errorCode = E_NO_METHOD_DEFINITION;
						break;
					}
					case 1:
					{
						macro = matchingMacros.tupleAt(1);
						break;
					}
					default:
					{
						errorCode = E_AMBIGUOUS_METHOD_DEFINITION;
						break;
					}
				}
			}
			else
			{
				// Some of the macro definitions are not visible.  Search the
				// hard (but hopefully infrequent) way.
				final List<TypeRestriction> phraseRestrictions =
					new ArrayList<>(method.numArgs());
				for (final A_Phrase argPhrase :
					argumentsListNode.expressionsTuple())
				{
					phraseRestrictions.add(
						restrictionForConstant(argPhrase, BOXED));
				}
				final List<A_Definition> filtered = new ArrayList<>();
				for (final A_Definition macroDefinition : visibleDefinitions)
				{
					if (macroDefinition.bodySignature().couldEverBeInvokedWith(
						phraseRestrictions))
					{
						filtered.add(macroDefinition);
					}
				}

				if (filtered.size() == 0)
				{
					// Nothing is visible.
					stateAfterCall.expected(
						WEAK,
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
					// assert filtered.size() > 1;
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
					final AvailErrorCode finalErrorCode = stripNull(errorCode);
					stateAfterCall.expected(
						MEDIUM,
						withString -> withString.value(
							finalErrorCode == E_AMBIGUOUS_METHOD_DEFINITION
								? "unambiguous definition of macro "
									+ bundle.message()
								: "successful macro lookup, not: "
									+ finalErrorCode.name()));
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
						phraseTypes.add(instanceTypeOrMetaOn(argPhrase));
					}
					stateAfterCall.expected(
						MEDIUM,
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
		final ParserState afterState =
			stateAfterCall.withMap(stateBeforeCall.clientDataMap);
		final A_Definition finalMacro = macro;
		// Validate the message send before reifying a send phrase.
		validateArgumentTypes(
			bundle,
			argTypes,
			finalMacro,
			stateAfterCall,
			expectedYieldType ->
			{
				if (finalMacro.equalsNil())
				{
					final A_Phrase sendNode = newSendNode(
						tupleFromList(consumedTokens),
						bundle,
						argumentsListNode,
						expectedYieldType);
					afterState.workUnitDo(
						continuation,
						new CompilerSolution(afterState, sendNode));
					return;
				}
				completedSendNodeForMacro(
					stateAfterCall,
					argumentsListNode,
					bundle,
					consumedTokens,
					finalMacro,
					expectedYieldType,
					Con(
						continuation.superexpressions,
						macroSolution ->
						{
							assert macroSolution.phrase()
								.isMacroSubstitutionNode();
							continuation.value(macroSolution);
						}));
			});
	}

	/**
	 * Parse an argument to a message send. Backtracking will find all valid
	 * interpretations.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param kindOfArgument
	 *        A {@link String}, in the form of a noun phrase, saying the kind of
	 *        argument that is expected.
	 * @param firstArgOrNull
	 *        Either a phrase to use as the argument, or null if we should
	 *        parse one now.
	 * @param canReallyParse
	 *        Whether any tokens may be consumed.  This should be false
	 *        specifically when the leftmost argument of a leading-argument
	 *        message is being parsed.
	 * @param wrapInLiteral
	 *        Whether the argument should be wrapped inside a literal phrase.
	 *        This allows statements to be more easily processed by macros.
	 * @param continuation
	 *        What to do with the argument.
	 */
	void parseSendArgumentWithExplanationThen (
		final ParserState start,
		final String kindOfArgument,
		final @Nullable A_Phrase firstArgOrNull,
		final boolean canReallyParse,
		final boolean wrapInLiteral,
		final Con1 continuation)
	{
		if (firstArgOrNull != null)
		{
			// We're parsing a message send with a leading argument, and that
			// argument was explicitly provided to the parser.  We should
			// consume the provided first argument now.
			assert !canReallyParse;

			// wrapInLiteral allows us to accept anything, even expressions that
			// are ⊤- or ⊥-valued.
			if (wrapInLiteral)
			{
				start.workUnitDo(
					continuation,
					new CompilerSolution(start, wrapAsLiteral(firstArgOrNull)));
				return;
			}
			final A_Type expressionType = firstArgOrNull.expressionType();
			if (expressionType.isTop())
			{
				start.expected(
					WEAK, "leading argument not to be ⊤-valued.");
				return;
			}
			if (expressionType.isBottom())
			{
				start.expected(
					WEAK, "leading argument not to be ⊥-valued.");
				return;
			}
			start.workUnitDo(
				continuation, new CompilerSolution(start, firstArgOrNull));
			return;
		}
		// There was no leading argument, or it has already been accounted for.
		// If we haven't actually consumed anything yet then don't allow a
		// *leading* argument to be parsed here.  That would lead to ambiguous
		// left-recursive parsing.
		if (!canReallyParse)
		{
			return;
		}
		parseExpressionThen(
			start,
			Con(
				continuation.superexpressions,
				solution ->
				{
					// Only accept a ⊤-valued or ⊥-valued expression if
					// wrapInLiteral is true.
					final A_Phrase argument = solution.phrase();
					final ParserState afterArgument = solution.endState();
					if (!wrapInLiteral)
					{
						final A_Type type = argument.expressionType();
						final @Nullable String badTypeName =
							type.isTop()
								? "⊤"
								: type.isBottom() ? "⊥" : null;
						if (badTypeName != null)
						{
							final Describer describer = c ->
							{
								final StringBuilder b = new StringBuilder(100);
								b.append(kindOfArgument);
								b.append(" to have a type other than ");
								b.append(badTypeName);
								b.append(" in:");
								describeOn(continuation.superexpressions, b);
								c.value(b.toString());
							};
							afterArgument.expected(WEAK, describer);
							return;
						}
					}
					final CompilerSolution argument1 =
						new CompilerSolution(
							afterArgument,
							wrapInLiteral ? wrapAsLiteral(argument) : argument);
					afterArgument.workUnitDo(continuation, argument1);
				}));
	}

	/**
	 * Transform the argument, a {@linkplain A_Phrase phrase}, into a {@link
	 * LiteralPhraseDescriptor literal phrase} whose value is the original
	 * phrase. If the given phrase is a {@linkplain
	 * MacroSubstitutionPhraseDescriptor macro substitution phrase} then extract
	 * its {@link A_Phrase#apparentSendName()}, strip off the macro
	 * substitution, wrap the resulting expression in a literal phrase, then
	 * re-apply the same apparentSendName to the new literal phrase to produce
	 * another macro substitution phrase.
	 *
	 * @param phrase
	 *        A phrase.
	 * @return A literal phrase that yields the given phrase as its value.
	 */
	private static A_Phrase wrapAsLiteral (
		final A_Phrase phrase)
	{
		if (phrase.isMacroSubstitutionNode())
		{
			return newMacroSubstitution(
				phrase.macroOriginalSendNode(),
				syntheticLiteralNodeFor(phrase));
		}
		return syntheticLiteralNodeFor(phrase);
	}

	/**
	 * Parse an argument in the top-most scope.  This is an important capability
	 * for parsing type expressions, and the macro facility may make good use
	 * of it for other purposes.
	 *
	 * @param start
	 *        The position at which parsing should occur.
	 * @param initialTokenPosition
	 *        The parse position where the send phrase started to be processed.
	 *        Does not count the position of the first argument if there are no
	 *        leading keywords.
	 * @param firstArgOrNull
	 *        An optional already parsed expression which, if present, must be
	 *        used as a leading argument.  If it's {@code null} then no leading
	 *        argument has been parsed, and a request to parse a leading
	 *        argument should simply produce no local solution.
	 * @param consumedAnything
	 *        Whether anything has yet been consumed for this invocation.
	 * @param consumedTokens
	 *        The tokens that have been consumed for this invocation.
	 * @param argsSoFar
	 *        The list of arguments parsed so far. I do not modify it. This is a
	 *        stack of expressions that the parsing instructions will assemble
	 *        into a list that correlates with the top-level non-backquoted
	 *        underscores and guillemet groups in the message name.
	 * @param marksSoFar
	 *        The stack of mark positions used to test if parsing certain
	 *        subexpressions makes progress.
	 * @param successorTrees
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        MessageBundleTreeDescriptor message bundle trees} along which to
	 *        continue parsing if a local solution is found.
	 * @param continuation
	 *        What to do once we have a fully parsed send phrase (of which we
	 *        are currently parsing an argument).
	 */
	void parseArgumentInModuleScopeThen (
		final ParserState start,
		final ParserState initialTokenPosition,
		final @Nullable A_Phrase firstArgOrNull,
		final boolean consumedAnything,
		final List<A_Token> consumedTokens,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final A_Tuple successorTrees,
		final Con1 continuation)
	{
		// Parse an argument in the outermost (module) scope and continue.
		assert successorTrees.tupleSize() == 1;
		final A_Map clientDataInGlobalScope =
			start.clientDataMap.mapAtPuttingCanDestroy(
				COMPILER_SCOPE_MAP_KEY.atom,
				emptyMap(),
				false);
		parseSendArgumentWithExplanationThen(
			start.withMap(clientDataInGlobalScope),
			"module-scoped argument",
			firstArgOrNull,
			firstArgOrNull == null
				&& initialTokenPosition.lexingState != start.lexingState,
			false,  // Static argument can't be top-valued
			Con(
				continuation.superexpressions,
				solution ->
				{
					final A_Phrase newArg = solution.phrase();
					final ParserState afterArg = solution.endState();
					if (newArg.hasSuperCast())
					{
						afterArg.expected(
							STRONG,
							"global-scoped argument, not supercast");
						return;
					}
					//noinspection VariableNotUsedInsideIf
					if (firstArgOrNull != null)
					{
						// A leading argument was already supplied.  We couldn't
						// prevent it from referring to variables that were in
						// scope during its parsing, but we can reject it if the
						// leading argument is supposed to be parsed in global
						// scope, which is the case here, and there are
						// references to local variables within the argument's
						// parse tree.
						final A_Set usedLocals =
							usesWhichLocalVariables(newArg);
						if (usedLocals.setSize() > 0)
						{
							// A leading argument was supplied which
							// used at least one local.  It shouldn't
							// have.
							afterArg.expected(
								WEAK,
								c ->
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
										+ localNames);
								});
							return;
						}
					}
					final List<A_Phrase> newArgsSoFar =
						append(argsSoFar, newArg);
					eventuallyParseRestOfSendNode(
						afterArg.withMap(start.clientDataMap),
						successorTrees.tupleAt(1),
						null,
						initialTokenPosition,
						// The argument counts as something that was
						// consumed if it's not a leading argument...
						firstArgOrNull == null,
						// We're about to parse an argument, so whatever was
						// in consumedAnything should be moved into
						// consumedAnythingBeforeLatestArgument.
						consumedAnything,
						consumedTokens,
						newArgsSoFar,
						marksSoFar,
						continuation);
				}));
	}

	/**
	 * A macro invocation has just been parsed.  Run its body now to produce a
	 * substitute phrase.
	 *
	 * @param stateAfterCall
	 *        The parsing state after the message.
	 * @param argumentsListNode
	 *        The {@linkplain ListPhraseDescriptor list phrase} that will hold
	 *        all the arguments of the new send phrase.
	 * @param bundle
	 *        The {@linkplain MessageBundleDescriptor message bundle} that
	 *        identifies the message to be sent.
	 * @param consumedTokens
	 *        The list of all tokens collected for this send phrase.  This
	 *        includes only those tokens that are operator or keyword tokens
	 *        that correspond with parts of the method name itself, not the
	 *        arguments.
	 * @param macroDefinitionToInvoke
	 *        The actual {@link MacroDefinitionDescriptor macro definition} to
	 *        invoke (statically).
	 * @param expectedYieldType
	 *        What semantic type the expression returned from the macro
	 *        invocation is expected to yield.  This will be narrowed further by
	 *        the actual phrase returned by the macro body, although if it's not
	 *        a send phrase then the resulting phrase is <em>checked</em>
	 *        against this expected yield type instead.
	 * @param continuation
	 *        What to do with the resulting send phrase solution.
	 */
	private void completedSendNodeForMacro (
		final ParserState stateAfterCall,
		final A_Phrase argumentsListNode,
		final A_Bundle bundle,
        final List<A_Token> consumedTokens,
		final A_Definition macroDefinitionToInvoke,
		final A_Type expectedYieldType,
		final Con1 continuation)
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
		final A_Map withTokensAndBundle = stateAfterCall.clientDataMap
			.mapAtPuttingCanDestroy(
				STATIC_TOKENS_KEY.atom, tupleFromList(consumedTokens), false)
			.mapAtPuttingCanDestroy(
				ALL_TOKENS_KEY.atom,
				tupleFromList(stateAfterCall.lexingState.allTokens),
				false)
			.mapAtPuttingCanDestroy(MACRO_BUNDLE_KEY.atom, bundle, true)
			.makeShared();
		if (AvailRuntimeConfiguration.debugMacroExpansions)
		{
			System.out.println(
				"PRE-EVAL:"
					+ stateAfterCall.lineNumber()
					+ '('
					+ stateAfterCall.position()
					+ ") "
					+ macroDefinitionToInvoke
					+ ' '
					+ argumentsList);
		}
		final MutableOrNull<A_Map> clientDataAfterRunning =
			new MutableOrNull<>();
		evaluateMacroFunctionThen(
			macroDefinitionToInvoke,
			argumentsList,
			withTokensAndBundle,
			clientDataAfterRunning,
			stateAfterCall.lexingState,
			replacement ->
			{
				assert clientDataAfterRunning.value != null;
				// In theory a fiber can produce anything, although you
				// have to mess with continuations to get it wrong.
				if (!replacement.isInstanceOfKind(
					PARSE_PHRASE.mostGeneralType()))
				{
					stateAfterCall.expected(
						STRONG,
						singletonList(replacement),
						list -> format(
							"Macro body for %s to have "
								+ "produced a phrase, not %s",
							bundle.message(),
							list.get(0)));
					return;
				}
				final A_Phrase adjustedReplacement;
				if (replacement.phraseKindIsUnder(SEND_PHRASE))
				{
					// Strengthen the send phrase produced by the macro.
					adjustedReplacement = newSendNode(
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
					// Not a send phrase, so it's impossible to
					// strengthen it to what the semantic
					// restrictions promised it should be.
					stateAfterCall.expected(
						STRONG,
						"macro "
							+ bundle.message().atomName()
							+ " to produce either a send phrase to "
							+ "be strengthened, or a phrase that "
							+ "yields "
							+ expectedYieldType
							+ ", not "
							+ replacement);
					return;
				}
				// Continue after this macro invocation with whatever
				// client data was set up by the macro.
				final ParserState stateAfter = stateAfterCall.withMap(
					clientDataAfterRunning.value());
				final A_Phrase original = newSendNode(
					tupleFromList(consumedTokens),
					bundle,
					argumentsListNode,
					macroDefinitionToInvoke.bodySignature().returnType());
				final A_Phrase substitution =
					newMacroSubstitution(original, adjustedReplacement);
				if (AvailRuntimeConfiguration.debugMacroExpansions)
				{
					System.out.println(
						":"
							+ stateAfter.lineNumber()
							+ '('
							+ stateAfter.position()
							+ ") "
							+ substitution);
				}
				stateAfter.workUnitDo(
					continuation,
					new CompilerSolution(stateAfter, substitution));
			},
			e ->
			{
				if (e instanceof AvailAcceptedParseException)
				{
					stateAfterCall.expected(
						STRONG,
						"macro body to reject the parse or produce "
							+ "a replacement expression, not merely "
							+ "accept its phrases like a semantic "
							+ "restriction");
				}
				else if (e instanceof AvailRejectedParseException)
				{
					final AvailRejectedParseException rej =
						(AvailRejectedParseException) e;
					stateAfterCall.expected(
						rej.level,
						rej.rejectionString().asNativeString());
				}
				else
				{
					stateAfterCall.expected(
						STRONG,
						"evaluation of macro body not to raise an "
							+ "unhandled exception:\n\t"
							+ e);
				}
			});
	}

	/**
	 * Check a property of the Avail virtual machine.
	 *
	 * @param state
	 *        The {@link ParserState} at which the pragma was found.
	 * @param propertyName
	 *        The name of the property that is being checked.
	 * @param propertyValue
	 *        A value that should be checked, somehow, for conformance.
	 * @param success
	 *        What to do after the check completes successfully.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	void pragmaCheckThen (
		final ParserState state,
		final String propertyName,
		final String propertyValue,
		final Continuation0 success)
	{
		if ("version".equals(propertyName))
		{
			// Split the versions at commas.
			final String[] versions = propertyValue.split(",");
			for (int i = 0; i < versions.length; i++)
			{
				versions[i] = versions[i].trim();
			}
			// Put the required versions into a set.
			A_Set requiredVersions = emptySet();
			for (final String version : versions)
			{
				requiredVersions =
					requiredVersions.setWithElementCanDestroy(
						stringFrom(version),
						true);
			}
			// Ask for the guaranteed versions.
			final A_Set activeVersions =
				AvailRuntimeConfiguration.activeVersions();
			// If the intersection of the sets is empty, then the module and
			// the virtual machine are incompatible.
			if (!requiredVersions.setIntersects(activeVersions))
			{
				state.expected(
					STRONG,
					format(
						"Module and virtual machine are not compatible; "
							+ "the virtual machine guarantees versions %s, "
							+ "but the current module requires %s",
						activeVersions,
						requiredVersions));
				return;
			}
		}
		else
		{
			final Set<String> viableAssertions = new HashSet<>();
			viableAssertions.add("version");
			state.expected(
				STRONG,
				format(
					"Expected check pragma to assert one of the following "
						+ "properties: %s",
					viableAssertions));
			return;
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
	 */
	void bootstrapMethodThen (
		final ParserState state,
		final A_Token token,
		final String methodName,
		final String primitiveName,
		final Continuation0 success)
	{
		final A_String availName = stringFrom(methodName);
		final A_Phrase nameLiteral = syntheticLiteralNodeFor(availName);
		final Primitive primitive = stripNull(primitiveByName(primitiveName));
		final A_Function function =
			createFunction(
				newPrimitiveRawFunction(
					primitive,
					compilationContext.module(),
					token.lineNumber()),
				emptyTuple());
		function.makeShared();
		final A_Phrase send = newSendNode(
			emptyTuple(),
			METHOD_DEFINER.bundle,
			newListNode(
				tuple(nameLiteral, syntheticLiteralNodeFor(function))),
			TOP.o());
		evaluateModuleStatementThen(
			state, state, send, new HashMap<>(), success);
	}

	/**
	 * Create a bootstrap primitive {@linkplain MacroDefinitionDescriptor
	 * macro}. Use the primitive's type declaration as the argument types.  If
	 * the primitive is fallible then generate suitable primitive failure code
	 * (to invoke the {@link SpecialMethodAtom#CRASH}'s bundle).
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
	 */
	void bootstrapMacroThen (
		final ParserState state,
		final A_Token token,
		final String macroName,
		final String[] primitiveNames,
		final Continuation0 success)
	{
		assert primitiveNames.length > 0;
		final A_String availName = stringFrom(macroName);
		final AvailObject token1 = literalToken(
			stringFrom(availName.toString()),
			0,
			0,
			availName);
		final A_Phrase nameLiteral = literalNodeFromToken(token1);
		final List<A_Phrase> functionLiterals = new ArrayList<>();
		try
		{
			for (final String primitiveName: primitiveNames)
			{
				final Primitive prim =
					stripNull(primitiveByName(primitiveName));
				functionLiterals.add(
					syntheticLiteralNodeFor(
						createFunction(
							newPrimitiveRawFunction(
								prim,
								compilationContext.module(),
								token.lineNumber()),
							emptyTuple())));
			}
		}
		catch (final RuntimeException e)
		{
			compilationContext.reportInternalProblem(
				state.lineNumber(),
				state.position(),
				e);
			compilationContext.diagnostics.reportError();
			return;
		}
		final A_Phrase bodyLiteral =
			functionLiterals.remove(functionLiterals.size() - 1);
		final A_Phrase send = newSendNode(
			emptyTuple(),
			MACRO_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					newListNode(tupleFromList(functionLiterals)),
					bodyLiteral)),
			TOP.o());
		evaluateModuleStatementThen(
			state, state, send, new HashMap<>(), success);
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
	 * @param lexerAtom
	 *        The name (an {@link A_Atom atom}) of the lexer being defined.
	 * @param filterPrimitiveName
	 *        The {@linkplain Primitive#name() primitive name} of the filter
	 *        for the lexer being defined.
	 * @param bodyPrimitiveName
	 *        The {@linkplain Primitive#name() primitive name} of the body of
	 *        the lexer being defined.
	 * @param success
	 *        What to do after the method is bootstrapped successfully.
	 */
	void bootstrapLexerThen (
		final ParserState state,
		final A_Token token,
		final A_Atom lexerAtom,
		final String filterPrimitiveName,
		final String bodyPrimitiveName,
		final Continuation0 success)
	{
		// Process the filter primitive.
		final @Nullable Primitive filterPrimitive =
			primitiveByName(filterPrimitiveName);
		if (filterPrimitive == null)
		{
			state.expected(STRONG, "a valid primitive for the lexer filter");
			return;
		}
		final A_Type filterFunctionType =
			filterPrimitive.blockTypeRestriction();
		if (!filterFunctionType.equals(lexerFilterFunctionType()))
		{
			state.expected(
				STRONG,
				"a primitive lexer filter function with type "
				+ lexerFilterFunctionType()
				+ ", not "
				+ filterFunctionType);
			return;
		}
		final A_Function filterFunction =
			createFunction(
				newPrimitiveRawFunction(
					filterPrimitive,
					compilationContext.module(),
					token.lineNumber()),
				emptyTuple());

		// Process the body primitive.
		final @Nullable Primitive bodyPrimitive =
			primitiveByName(bodyPrimitiveName);
		if (bodyPrimitive == null)
		{
			state.expected(
				STRONG,
				"a valid primitive for the lexer body");
			return;
		}
		final A_Type bodyFunctionType = bodyPrimitive.blockTypeRestriction();
		if (!bodyFunctionType.equals(lexerBodyFunctionType()))
		{
			state.expected(
				STRONG,
				"a primitive lexer body function with type "
				+ lexerBodyFunctionType()
				+ ", not "
				+ bodyFunctionType);
		}
		final A_Function bodyFunction =
			createFunction(
				newPrimitiveRawFunction(
					bodyPrimitive,
					compilationContext.module(),
					token.lineNumber()),
				emptyTuple());

		// Process the lexer name.
		final A_Phrase nameLiteral = syntheticLiteralNodeFor(lexerAtom);

		// Build a phrase to define the lexer.
		final A_Phrase send = newSendNode(
			emptyTuple(),
			LEXER_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					syntheticLiteralNodeFor(filterFunction),
					syntheticLiteralNodeFor(bodyFunction))),
			TOP.o());
		evaluateModuleStatementThen(
			new ParserState(token.nextLexingState(), emptyMap()),
			state,
			send,
			new HashMap<>(),
			success);
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
	 */
	private void applyPragmasThen (
		final ParserState state,
		final Continuation0 success)
	{
		final Iterator<A_Token> iterator = moduleHeader().pragmas.iterator();
		compilationContext.loader().setPhase(EXECUTING_FOR_COMPILE);
		recurse(
			recurse ->
			{
				if (!iterator.hasNext())
				{
					// Done with all the pragmas, if any.  Report any new
					// problems relative to the body section.
					recordExpectationsRelativeTo(state);
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
						pragmaToken.nextLexingState(),
						"Malformed pragma at %s on line %d:",
						"Pragma should have the form key=value");
					return;
				}
				final String pragmaKindString = pragmaParts[0].trim();
				final String pragmaValue = pragmaParts[1].trim();
				final @Nullable PragmaKind pragmaKind =
					pragmaKindByLexeme(pragmaKindString);
				if (pragmaKind == null)
				{
					compilationContext.diagnostics.reportError(
						pragmaToken.nextLexingState(),
						"Unsupported pragma kind at %s on line %d:",
						"Pragma kind should be one of: "
							+ Arrays.stream(PragmaKind.values())
								.map(PragmaKind::lexeme)
								.collect(toList()));
					return;
				}
				pragmaKind.applyThen(
					this,
					pragmaToken,
					pragmaValue,
					state,
					() -> state.lexingState.compilationContext.eventuallyDo(
						state.lexingState, recurse));
			});
	}

	/**
	 * Parse a {@linkplain ModuleHeader module header} from the {@linkplain
	 * TokenDescriptor token list} and apply any side-effects. Then {@linkplain
	 * #parseAndExecuteOutermostStatements(ParserState) parse the module body}
	 * and apply any side-effects.  Finally, execute the {@link
	 * CompilerDiagnostics}'s successReporter.
	 */
	private void parseModuleCompletely ()
	{
		parseModuleHeader(
			afterHeader ->
			{
				compilationContext.getProgressReporter().value(
					moduleName(),
					source().tupleSize(),
					afterHeader.position());
				// Run any side-effects implied by this module header against
				// the module.
				final @Nullable String errorString =
					moduleHeader().applyToModule(
						compilationContext.module(),
						compilationContext.runtime);
				if (errorString != null)
				{
					compilationContext.getProgressReporter().value(
						moduleName(),
						source().tupleSize(),
						source().tupleSize());
					afterHeader.expected(STRONG, errorString);
					compilationContext.diagnostics.reportError();
					return;
				}
				compilationContext.loader().prepareForCompilingModuleBody();
				applyPragmasThen(
					afterHeader,
					() -> parseAndExecuteOutermostStatements(afterHeader));
			});
	}

	/**
	 * Parse a top-level statement, execute it, and repeat if we're not at the
	 * end of the module.
	 *
	 * @param start
	 *        The {@linkplain ParserState parse state} after parsing a
	 *        {@linkplain ModuleHeader module header}.
	 */
	private void parseAndExecuteOutermostStatements (
		final ParserState start)
	{
		compilationContext.loader().setPhase(COMPILING);
		// Forget any accumulated tokens from previous top-level statements.
		final LexingState startLexingState = start.lexingState;
		final ParserState startWithoutAnyTokens = new ParserState(
			new LexingState(
				startLexingState.compilationContext,
				startLexingState.position,
				startLexingState.lineNumber,
				emptyList()),
			start.clientDataMap);
		parseOutermostStatement(
			startWithoutAnyTokens,
			Con(
				null,
				solution ->
				{
					// The counters must be read in this order for correctness.
					assert compilationContext.getWorkUnitsCompleted()
						== compilationContext.getWorkUnitsQueued();

					// Check if we're cleanly at the end.
					if (solution.phrase().equals(endOfFileMarkerPhrase))
					{
						reachedEndOfModule(solution.endState());
						return;
					}

					// In case the top level statement is compound, process the
					// base statements individually.
					final ParserState afterStatement = solution.endState();
					final A_Phrase unambiguousStatement = solution.phrase();
					final List<A_Phrase> simpleStatements = new ArrayList<>();
					unambiguousStatement.statementsDo(
						simpleStatement ->
						{
							assert simpleStatement.phraseKindIsUnder(
								STATEMENT_PHRASE);
							simpleStatements.add(simpleStatement);
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

					// What to do after running all these simple statements.
					final Continuation0 resumeParsing = () ->
					{
						// Report progress.
						compilationContext.getProgressReporter().value(
							moduleName(),
							source().tupleSize(),
							afterStatement.position());
						parseAndExecuteOutermostStatements(
							afterStatement.withMap(start.clientDataMap));
					};

					compilationContext.loader().setPhase(EXECUTING_FOR_COMPILE);
					// Run the simple statements in succession.
					final Iterator<A_Phrase> simpleStatementIterator =
						simpleStatements.iterator();
					final Map<A_Phrase, A_Phrase> declarationRemap =
						new HashMap<>();
					recurse(
						executeNextSimpleStatement ->
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
										+ ':' + start.lineNumber()
										+ " Running statement:\n" + statement);
							}
							evaluateModuleStatementThen(
								start,
								afterStatement,
								statement,
								declarationRemap,
								executeNextSimpleStatement);
						});
				}));
	}

	/**
	 * We just reached the end of the module.
	 *
	 * @param afterModule
	 *        The position at the end of the module.
	 */
	private void reachedEndOfModule (
		final ParserState afterModule)
	{
		final AvailLoader theLoader = compilationContext.loader();
		if (theLoader.pendingForwards.setSize() != 0)
		{
			@SuppressWarnings({
				"resource",
				"IOResourceOpenedButNotSafelyClosed"
			})
			final Formatter formatter = new Formatter();
			formatter.format("the following forwards to be resolved:");
			for (final A_BasicObject forward : theLoader.pendingForwards)
			{
				formatter.format("%n\t%s", forward);
			}
			afterModule.expected(STRONG, formatter.toString());
			compilationContext.diagnostics.reportError();
			return;
		}
		// Clear the section of the fragment cache
		// associated with the (outermost) statement
		// just parsed and executed...
		synchronized (fragmentCache)
		{
			fragmentCache.clear();
		}
		compilationContext.diagnostics.getSuccessReporter().value();
	}

	/**
	 * Clear any information about potential problems encountered during
	 * parsing.  Reset the problem information to record relative to the
	 * given {@link ParserState}.
	 *
	 * @param positionInSource
	 *        The {@link ParserState} at the earliest source position for which
	 *        we should record problem information.
	 */
	private synchronized void recordExpectationsRelativeTo (
		final ParserState positionInSource)
	{
		compilationContext.diagnostics.startParsingAt(positionInSource);
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} from the source and install
	 * it into the {@linkplain AvailRuntime runtime}.  This method generally
	 * returns long before the module has been parsed, but either the onSuccess
	 * or afterFail continuation is invoked when module parsing has completed or
	 * failed.
	 *
	 * @param onSuccess
	 *        What to do when the entire module has been parsed successfully.
	 * @param afterFail
	 *        What to do after compilation fails.
	 */
	public synchronized void parseModule (
		final Continuation1NotNull<A_Module> onSuccess,
		final Continuation0 afterFail)
	{
		final AtomicBoolean ran = new AtomicBoolean(false);
		compilationContext.diagnostics.setSuccessAndFailureReporters(
			() ->
			{
				final boolean old = ran.getAndSet(true);
				assert !old : "Attempting to succeed twice.";
				serializePublicationFunction(true);
				serializePublicationFunction(false);
				commitModuleTransaction();
				onSuccess.value(compilationContext.module());
			},
			() -> rollbackModuleTransaction(afterFail));
		startModuleTransaction();
		parseModuleCompletely();
	}

	/**
	 * Parse a command, compiling it into the current {@link ModuleDescriptor
	 * module}, from the {@linkplain TokenDescriptor token} list.
	 *
	 * @param onSuccess
	 *        What to do after compilation succeeds. This {@linkplain
	 *        Continuation2NotNull continuation} is invoked with a {@linkplain
	 *        List list} of {@link A_Phrase phrases} that represent the possible
	 *        solutions of compiling the command and a {@linkplain
	 *        Continuation1NotNull continuation} that cleans up this compiler
	 *        and its module (and then continues with a post-cleanup {@linkplain
	 *        Continuation0 continuation}).
	 * @param afterFail
	 *        What to do after compilation fails.
	 */
	public synchronized void parseCommand (
		final Continuation2NotNull
			      <List<A_Phrase>, Continuation1NotNull<Continuation0>>
			onSuccess,
		final Continuation0 afterFail)
	{
		compilationContext.diagnostics.setSuccessAndFailureReporters(
			() -> { }, afterFail);
		assert compilationContext.getWorkUnitsCompleted() == 0
			&& compilationContext.getWorkUnitsQueued() == 0;
		// Start a module transaction, just to complete any necessary
		// initialization. We are going to rollback this transaction no matter
		// what happens.
		startModuleTransaction();
		final AvailLoader loader = compilationContext.loader();
		loader.prepareForCompilingModuleBody();
		final A_Map clientData = mapFromPairs(
			COMPILER_SCOPE_MAP_KEY.atom,
			emptyMap(),
			STATIC_TOKENS_KEY.atom,
			emptyTuple(),
			ALL_TOKENS_KEY.atom,
			emptyTuple());
		final ParserState start = new ParserState(
			new LexingState(compilationContext, 1, 1, emptyList()), clientData);
		final List<A_Phrase> solutions = new ArrayList<>();
		compilationContext.setNoMoreWorkUnits(
			() ->
			{
				// The counters must be read in this order for correctness.
				assert compilationContext.getWorkUnitsCompleted()
					== compilationContext.getWorkUnitsQueued();
				// If no solutions were found, then report an error.
				if (solutions.isEmpty())
				{
					start.expected(STRONG, "an invocation of an entry point");
					compilationContext.diagnostics.reportError();
					return;
				}
				onSuccess.value(solutions, this::rollbackModuleTransaction);
			});
		recordExpectationsRelativeTo(start);
		parseExpressionThen(
			start,
			Con(
				null,
				solution ->
				{
					final A_Phrase expression = solution.phrase();
					final ParserState afterExpression = solution.endState();
					if (expression.equals(endOfFileMarkerPhrase))
					{
						afterExpression.expected(
							STRONG,
							"a valid command, not just whitespace");
						return;
					}
					if (expression.hasSuperCast())
					{
						afterExpression.expected(
							STRONG,
							"a valid command, not a supercast");
						return;
					}
					// Check that after the expression is only whitespace and
					// the end-of-file.
					nextNonwhitespaceTokensDo(
						afterExpression,
						token ->
						{
							if (token.tokenType() == END_OF_FILE)
							{
								synchronized (solutions)
								{
									solutions.add(expression);
								}
							}
							else
							{
								afterExpression.expected(
									STRONG, "end of command");
							}
						}
					);
				}));
	}

	/**
	 * The given phrase must contain only subexpressions that are literal
	 * phrases or list phrases.  Convert the structure into a nested tuple of
	 * tokens.
	 *
	 * <p>The tokens are kept, rather than extracting the literal strings or
	 * integers, so that error reporting can refer to the token positions.</p>
	 *
	 * @param phrase
	 *        The root literal phrase or list phrase.
	 * @return The token of the literal phrase, or a tuple with the (recursive)
	 *         tuples of the list phrase's subexpressions' tokens.
	 */
	private static AvailObject convertHeaderPhraseToValue (
		final A_Phrase phrase)
	{
		switch (phrase.phraseKind())
		{
			case LITERAL_PHRASE:
			{
				return (AvailObject) phrase.token();
			}
			case LIST_PHRASE:
			case PERMUTED_LIST_PHRASE:
			{
				final A_Tuple expressions = phrase.expressionsTuple();
				return generateObjectTupleFrom(
					expressions.tupleSize(),
					index -> convertHeaderPhraseToValue(
						expressions.tupleAt(index))
				);
			}
			case MACRO_SUBSTITUTION_PHRASE:
			{
				//noinspection TailRecursion
				return convertHeaderPhraseToValue(phrase.stripMacro());
			}
			default:
			{
				throw new RuntimeException(
					"Unexpected phrase type in header: " +
					phrase.phraseKind().name());
			}
		}
	}

	/**
	 * Extract a {@link A_String string} from the given string literal {@link
	 * A_Token token}.
	 *
	 * @param token The string literal token.
	 * @return The token's string.
	 */
	private static A_String stringFromToken (final A_Token token)
	{
		assert token.isInstanceOfKind(TOKEN.o());
		final A_Token innerToken = token.literal();
		final A_String literal = innerToken.literal();
		assert literal.isInstanceOfKind(stringType());
		return literal;
	}

	/**
	 * Process a header that has just been parsed.
	 *
	 * @param headerPhrase
	 *        The invocation of {@link SpecialMethodAtom#MODULE_HEADER}
	 *        that was just parsed.
	 * @param stateAfterHeader
	 *        The {@link ParserState} after the module's header.
	 * @return Whether header processing was successful.  If unsuccessful,
	 *         arrangements will already have been made (and perhaps already
	 *         executed) to present the error.
	 */
	private boolean processHeaderMacro (
		final A_Phrase headerPhrase,
		final ParserState stateAfterHeader)
	{
		final ModuleHeader header = moduleHeader();

		assert headerPhrase.phraseKindIsUnder(SEND_PHRASE);
		assert headerPhrase.apparentSendName().equals(
			MODULE_HEADER.atom);
		final A_Tuple args =
			convertHeaderPhraseToValue(headerPhrase.argumentsListNode());
		assert args.tupleSize() == 6;
		final A_Token moduleNameToken = args.tupleAt(1);
		final A_Tuple optionalVersions = args.tupleAt(2);
		final A_Tuple allImports = args.tupleAt(3);
		final A_Tuple optionalNames = args.tupleAt(4);
		final A_Tuple optionalEntries = args.tupleAt(5);
		final A_Tuple optionalPragmas = args.tupleAt(6);

		// Module name was checked against file name in a prefix function.
		final A_String moduleName = stringFromToken(moduleNameToken);
		assert moduleName.asNativeString().equals(moduleName().localName());

		// Module versions were already checked for duplicates
		if (optionalVersions.tupleSize() > 0)
		{
			assert optionalVersions.tupleSize() == 1;
			for (final A_Token versionStringToken : optionalVersions.tupleAt(1))
			{
				final A_String versionString = stringFromToken(
					versionStringToken);
				assert !header.versions.contains(versionString);
				header.versions.add(versionString);
			}
		}

		// Imports section (all Extends/Uses subsections)
		for (final A_Tuple importSection : allImports)
		{
			final A_Token importKindToken = importSection.tupleAt(1);
			assert importKindToken.isInstanceOfKind(TOKEN.o());
			final A_Number importKind = importKindToken.literal();
			assert importKind.isInt();
			final int importKindInt = importKind.extractInt();
			assert importKindInt >= 1 && importKindInt <= 2;
			final boolean isExtension = importKindInt == 1;

			for (final A_Tuple moduleImport : importSection.tupleAt(2))
			{
				// <importedModule, optionalVersions, optionalNamesPart>
				assert moduleImport.tupleSize() == 3;
				final A_Token importedModuleToken = moduleImport.tupleAt(1);
				final A_String importedModuleName =
					stringFromToken(importedModuleToken);

				final A_Tuple optionalImportVersions = moduleImport.tupleAt(2);
				assert optionalImportVersions.isTuple();

				A_Set importVersions = emptySet();
				if (optionalImportVersions.tupleSize() > 0)
				{
					assert optionalImportVersions.tupleSize() == 1;
					for (final A_Token importVersionToken
						: optionalImportVersions.tupleAt(1))
					{
						final A_String importVersionString =
							stringFromToken(importVersionToken);
						// Guaranteed by P_ModuleHeaderPrefixCheckImportVersion.
						assert !importVersions.hasElement(importVersionString);
						importVersions =
							importVersions.setWithElementCanDestroy(
								importVersionString, true);
					}
				}

				A_Set importedNames = emptySet();
				A_Map importedRenames = emptyMap();
				A_Set importedExcludes = emptySet();
				boolean wildcard = true;

				final A_Tuple optionalNamesPart = moduleImport.tupleAt(3);
				// <filterEntries, finalEllipsis>?
				if (optionalNamesPart.tupleSize() > 0)
				{
					assert optionalNamesPart.tupleSize() == 1;
					final A_Tuple namesPart = optionalNamesPart.tupleAt(1);
					assert namesPart.tupleSize() == 2;
					// <filterEntries, finalEllipsis>
					for (final A_Tuple filterEntry : namesPart.tupleAt(1))
					{
						// <negation, name, rename>
						assert filterEntry.tupleSize() == 3;
						final A_Token negationLiteralToken =
							filterEntry.tupleAt(1);
						final boolean negation =
							negationLiteralToken.literal().extractBoolean();
						final A_Token nameToken = filterEntry.tupleAt(2);
						final A_String name = stringFromToken(nameToken);
						final A_Tuple optionalRename = filterEntry.tupleAt(3);
						if (optionalRename.tupleSize() > 0)
						{
							// Process a renamed import
							assert optionalRename.tupleSize() == 1;
							final A_Token renameToken =
								optionalRename.tupleAt(1);
							if (negation)
							{
								renameToken
									.nextLexingState()
									.expected(
										STRONG,
										"negated or renaming import, but "
											+ "not both");
								compilationContext.diagnostics.reportError();
								return false;
							}
							final A_String rename =
								stringFromToken(renameToken);
							if (importedRenames.hasKey(rename))
							{
								renameToken
									.nextLexingState()
									.expected(
										STRONG,
										"renames to specify distinct "
											+ "target names");
								compilationContext.diagnostics.reportError();
								return false;
							}
							importedRenames =
								importedRenames.mapAtPuttingCanDestroy(
									rename, name, true);
						}
						else if (negation)
						{
							// Process an excluded import.
							if (importedExcludes.hasElement(name))
							{
								nameToken.nextLexingState().expected(
									STRONG, "import exclusions to be unique");
								compilationContext.diagnostics.reportError();
								return false;
							}
							importedExcludes =
								importedExcludes.setWithElementCanDestroy(
									name, true);
						}
						else
						{
							// Process a regular import (neither a negation
							// nor an exclusion).
							if (importedNames.hasElement(name))
							{
								nameToken.nextLexingState().expected(
									STRONG, "import names to be unique");
								compilationContext.diagnostics.reportError();
								return false;
							}
							importedNames =
								importedNames.setWithElementCanDestroy(
									name, true);
						}
					}

					// Check for the trailing ellipsis.
					final A_Token finalEllipsisLiteralToken =
						namesPart.tupleAt(2);
					final A_Atom finalEllipsis =
						finalEllipsisLiteralToken.literal();
					assert finalEllipsis.isBoolean();
					wildcard = finalEllipsis.extractBoolean();
				}

				try
				{
					moduleHeader().importedModules.add(
						new ModuleImport(
							importedModuleName,
							importVersions,
							isExtension,
							importedNames,
							importedRenames,
							importedExcludes,
							wildcard));
				}
				catch (final ImportValidationException e)
				{
					importedModuleToken.nextLexingState().expected(
						STRONG, e.getMessage());
					compilationContext.diagnostics.reportError();
					return false;
				}
			}  // modules of an import subsection
		}  // imports section

		// Names section
		if (optionalNames.tupleSize() > 0)
		{
			assert optionalNames.tupleSize() == 1;
			for (final A_Token nameToken : optionalNames.tupleAt(1))
			{
				final A_String nameString = stringFromToken(nameToken);
				if (header.exportedNames.contains(nameString))
				{
					compilationContext.diagnostics.reportError(
						nameToken.nextLexingState(),
						"Duplicate declared name detected at %s on line %d:",
						format(
							"Declared name %s should be unique", nameString));
					return false;
				}
				header.exportedNames.add(nameString);
			}
		}

		// Entries section
		if (optionalEntries.tupleSize() > 0)
		{
			assert optionalEntries.tupleSize() == 1;
			for (final A_Token entryToken : optionalEntries.tupleAt(1))
			{
				header.entryPoints.add(stringFromToken(entryToken));
			}
		}

		// Pragmas section
		if (optionalPragmas.tupleSize() > 0)
		{
			assert optionalPragmas.tupleSize() == 1;
			for (final A_Token pragmaToken : optionalPragmas.tupleAt(1))
			{
				final A_Token innerToken = pragmaToken.literal();
				header.pragmas.add(innerToken);
			}
		}
		header.startOfBodyPosition = stateAfterHeader.position();
		header.startOfBodyLineNumber = stateAfterHeader.lineNumber();
		return true;
	}

	/**
	 * Parse the header of the module from the token stream. If successful,
	 * invoke onSuccess with the {@link ParserState} just after the header,
	 * otherwise invoke onFail without reporting the problem.
	 *
	 * <p>If the {@code dependenciesOnly} parameter is true, only parse the bare
	 * minimum needed to determine information about which modules are used by
	 * this one.</p>
	 *
	 * @param onSuccess
	 *        What to do after successfully parsing the header.  The compilation
	 *        context's header will have been updated, and the {@link
	 *        Continuation1NotNull} will be passed the {@link ParserState} after
	 *        the header.
	 */
	public void parseModuleHeader (
		final Continuation1NotNull<ParserState> onSuccess)
	{
		// Create the initial parser state: no tokens have been seen, and no
		// names are in scope.
		final A_Map clientData = mapFromPairs(
			COMPILER_SCOPE_MAP_KEY.atom,
			emptyMap(),
			STATIC_TOKENS_KEY.atom,
			emptyMap(),
			ALL_TOKENS_KEY.atom,
			emptyTuple());
		final ParserState state = new ParserState(
			new LexingState(compilationContext, 1, 1, emptyList()), clientData);

		recordExpectationsRelativeTo(state);

		// Parse an invocation of the special module header macro.
		parseOutermostStatement(
			state,
			Con(
				null,
				solution ->
				{
					final A_Phrase headerPhrase = solution.phrase();
					assert headerPhrase.phraseKindIsUnder(
						EXPRESSION_AS_STATEMENT_PHRASE);
					assert headerPhrase.apparentSendName().equals(
						MODULE_HEADER.atom);
					try
					{
						final boolean ok = processHeaderMacro(
							headerPhrase.expression(),
							solution.endState());
						if (ok)
						{
							onSuccess.value(solution.endState());
						}
					}
					catch (final Exception e)
					{
						compilationContext.diagnostics.reportError(
							solution.endState().lexingState,
							"Unexpected exception encountered while processing "
								+ "module header (ends at %s, line %d):",
							trace(e));
					}
				}));
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
	private void parseExpressionThen (
		final ParserState start,
		final Con1 originalContinuation)
	{
		// The first time we parse at this position the fragmentCache will
		// have no knowledge about it.
		final AvailCompilerBipartiteRendezvous rendezvous =
			fragmentCache.getRendezvous(start);
		if (!rendezvous.getAndSetStartedParsing())
		{
			// We're the (only) cause of the transition from hasn't-started to
			// has-started.  Suppress reporting if there are no superexpressions
			// being parsed, or if we're at the root of the bundle tree.
			final boolean isRoot =
				originalContinuation.superexpressions == null
				|| originalContinuation.superexpressions().bundleTree.equals(
					compilationContext.loader().rootBundleTree());
			start.expected(
				isRoot ? SILENT : WEAK,
				withDescription ->
				{
					final StringBuilder builder = new StringBuilder();
					builder.append("an expression for (at least) this reason:");
					describeOn(originalContinuation.superexpressions, builder);
					withDescription.value(builder.toString());
				});
			start.workUnitDo(
				a -> parseExpressionUncachedThen(start, a),
				Con(
					originalContinuation.superexpressions,
					rendezvous::addSolution));
		}
		start.workUnitDo(rendezvous::addAction, originalContinuation);
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar (it used to be that <em>all</em> statements had to
	 * be unambiguous, even those in blocks).  The passed continuation will be
	 * invoked at most once, and only if the top-level statement had a single
	 * interpretation.
	 *
	 * @param start
	 *        Where to start parsing a top-level statement.
	 * @param continuation
	 *        What to do with the (unambiguous) top-level statement.
	 */
	private void parseOutermostStatement (
		final ParserState start,
		final Con1 continuation)
	{
		// If a parsing error happens during parsing of this outermost
		// statement, only show the section of the file starting here.
		recordExpectationsRelativeTo(start);
		tryIfUnambiguousThen(
			start,
			whenFoundStatement ->
			{
				parseExpressionThen(
					start,
					Con(
						null,
						solution ->
						{
							final A_Phrase expression = solution.phrase();
							final ParserState afterExpression =
								solution.endState();
							if (expression.phraseKindIsUnder(STATEMENT_PHRASE))
							{
								whenFoundStatement.value(
									new CompilerSolution(
										afterExpression, expression));
								return;
							}
							afterExpression.expected(
								STRONG,
								new FormattingDescriber(
									"an outer level statement, not %s (%s)",
									expression.phraseKind(),
									expression));
						}));
				nextNonwhitespaceTokensDo(
					start,
					token ->
					{
						if (token.tokenType() == END_OF_FILE)
						{
							whenFoundStatement.value(
								new CompilerSolution(
									start, endOfFileMarkerPhrase));
						}
					}
				);
			},
			continuation);
	}

	/**
	 * Parse an expression, without directly using the {@link #fragmentCache}.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param continuation
	 *        What to do with the expression.
	 */
	private void parseExpressionUncachedThen (
		final ParserState start,
		final Con1 continuation)
	{
		parseLeadingKeywordSendThen(
			start,
			Con(
				continuation.superexpressions,
				solution -> parseOptionalLeadingArgumentSendAfterThen(
					start,
					solution.endState(),
					solution.phrase(),
					continuation)));
	}

	/**
	 * A helper method to queue a parsing activity for continuing to parse a
	 * {@linkplain SendPhraseDescriptor send phrase}.
	 *
	 * @param start
	 *        The current {@link ParserState}.
	 * @param bundleTree
	 *        The current {@link A_BundleTree} being applied.
	 * @param firstArgOrNull
	 *        Either null or a pre-parsed first argument phrase.
	 * @param initialTokenPosition
	 *        The position at which parsing of this message started. If it was
	 *        parsed as a leading argument send (i.e., firstArgOrNull started
	 *        out non-null) then the position is of the token following the
	 *        first argument.
	 * @param consumedAnything
	 *        Whether any tokens have been consumed yet.
	 * @param consumedAnythingBeforeLatestArgument
	 *        Whether any tokens or arguments had been consumed before
	 *        encountering the most recent argument.  This is to improve
	 *        diagnostics when argument type checking is postponed past matches
	 *        for subsequent tokens.
	 * @param consumedTokens
	 *        The {@link A_Token}s that have been consumed so far for this
	 *        invocation.
	 * @param argsSoFar
	 *        The arguments stack.
	 * @param marksSoFar
	 *        The marks stack.
	 * @param continuation
	 *        What to do with a completed phrase.
	 */
	void eventuallyParseRestOfSendNode (
		final ParserState start,
		final A_BundleTree bundleTree,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final boolean consumedAnythingBeforeLatestArgument,
		final List<A_Token> consumedTokens,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con1 continuation)
	{
		start.workUnitDo(
			ignored -> parseRestOfSendNode(
				start,
				bundleTree,
				firstArgOrNull,
				initialTokenPosition,
				consumedAnything,
				consumedAnythingBeforeLatestArgument,
				consumedTokens,
				argsSoFar,
				marksSoFar,
				continuation),
			"ignored");
	}

	/**
	 * Answer the {@linkplain SetDescriptor set} of {@linkplain
	 * DeclarationPhraseDescriptor declaration phrases} which are used by this
	 * parse tree but are locally declared (i.e., not at global module scope).
	 *
	 * @param phrase
	 *        The phrase to recursively examine.
	 * @return The set of the local declarations that were used in the phrase.
	 */
	private static A_Set usesWhichLocalVariables (
		final A_Phrase phrase)
	{
		final Mutable<A_Set> usedDeclarations = new Mutable<>(emptySet());
		phrase.childrenDo(
			childPhrase ->
			{
				if (childPhrase.isInstanceOfKind(
					VARIABLE_USE_PHRASE.mostGeneralType()))
				{
					final A_Phrase declaration = childPhrase.declaration();
					if (!declaration.declarationKind().isModuleScoped())
					{
						usedDeclarations.value =
							usedDeclarations.value.setWithElementCanDestroy(
								declaration, true);
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
	private void serializePublicationFunction (final boolean isPublic)
	{
		// Output a function that publishes the initial public set of atoms.
		final A_Map sourceNames =
			isPublic
				? compilationContext.module().importedNames()
				: compilationContext.module().privateNames();
		A_Set names = emptySet();
		for (final Entry entry : sourceNames.mapIterable())
		{
			names = names.setUnionCanDestroy(
				entry.value().makeImmutable(), true);
		}
		final A_Phrase send = newSendNode(
			emptyTuple(),
			PUBLISH_ATOMS.bundle,
			newListNode(
				tuple(
					syntheticLiteralNodeFor(names),
					syntheticLiteralNodeFor(objectFromBoolean(isPublic)))),
			TOP.o());
		final A_Function function = createFunctionForPhrase(
			send, compilationContext.module(), 0);
		function.makeImmutable();
		privateSerializeFunction(function);
	}

	/**
	 * Hold the monitor and serialize the given function.
	 *
	 * @param function
	 *        The {@link A_Function} to serialize.
	 */
	private synchronized void privateSerializeFunction (
		final A_Function function)
	{
		compilationContext.serializer.serialize(function);
	}
}

/*
 * CompilationContext.java
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
import com.avail.AvailRuntimeSupport;
import com.avail.builder.ModuleName;
import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.AvailCompiler.CompilerProgressReporter;
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.problems.ProblemType;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.*;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.AvailAssertionFailedException;
import com.avail.exceptions.AvailEmergencyExitException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.effects.LoadingEffect;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.io.TextInterface;
import com.avail.serialization.Serializer;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.compiler.problems.ProblemType.EXECUTION;
import static com.avail.compiler.problems.ProblemType.INTERNAL;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY;
import static com.avail.descriptor.FiberDescriptor.newLoaderFiber;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionDescriptor.createFunctionForPhrase;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.StackPrinter.trace;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Collections.emptyList;

/**
 * A {@code CompilationContext} lasts for a module's entire compilation
 * activity.
 */
public class CompilationContext
{
	/** The {@linkplain Logger logger}. */
	public static final Logger logger = Logger.getLogger(
		CompilationContext.class.getName());

	/**
	 * The {@link CompilerDiagnostics} that tracks potential errors during
	 * compilation.
	 */
	public final CompilerDiagnostics diagnostics;

	/**
	 * The {@link AvailRuntime} for the compiler. Since a compiler cannot
	 * migrate between two runtime environments, it is safe to cache it for
	 * efficient access.
	 */
	public final AvailRuntime runtime = currentRuntime();

	/**
	 * The header information for the current module being parsed.
	 */
	private final @Nullable ModuleHeader moduleHeader;

	public ModuleHeader getModuleHeader ()
	{
		return stripNull(moduleHeader);
	}

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	private final A_Module module;

	/**
	 * Answer the {@link A_Module} being compiled.
	 *
	 * @return This compilation context's {@link A_Module}.
	 */
	public A_Module module ()
	{
		return module;
	}

	/**
	 * Answer the fully-qualified name of the {@linkplain ModuleDescriptor
	 * module} undergoing compilation.
	 *
	 * @return The module name.
	 */
	ModuleName moduleName ()
	{
		return new ModuleName(module().moduleName().asNativeString());
	}

	/**
	 * The {@linkplain AvailLoader loader} created and operated by this
	 * {@linkplain AvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 */
	private @Nullable AvailLoader loader;

	public void setLoader (final AvailLoader loader)
	{
		this.loader = loader;
	}

	/**
	 * Answer the {@linkplain AvailLoader loader} created and operated by this
	 * {@linkplain AvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 *
	 * @return A loader.
	 */
	public AvailLoader loader ()
	{
		return stripNull(loader);
	}

	/**
	 * The source text of the Avail {@linkplain ModuleDescriptor module}
	 * undergoing compilation.
	 */
	final A_String source;

	/**
	 * The Avail {@link A_String} containing the entire content of the module
	 * source file.
	 *
	 * @return The source code.
	 */
	public A_String source ()
	{
		return source;
	}

	/**
	 * The {@linkplain TextInterface text interface} for any {@linkplain
	 * A_Fiber fibers} started by this {@linkplain AvailCompiler
	 * compiler}.
	 */
	private final TextInterface textInterface;

	public TextInterface getTextInterface ()
	{
		return textInterface;
	}

	/** The number of work units that have been queued. */
	private final AtomicLong workUnitsQueued = new AtomicLong(0);

	public long getWorkUnitsQueued ()
	{
		return workUnitsQueued.get();
	}

	/** The number of work units that have been completed. */
	private final AtomicLong workUnitsCompleted = new AtomicLong(0);

	public long getWorkUnitsCompleted ()
	{
		return workUnitsCompleted.get();
	}

	/**
	 * What to do when there are no more work units.
	 */
	private volatile @Nullable Continuation0 noMoreWorkUnits = null;

	public @Nullable Continuation0 getNoMoreWorkUnits ()
	{
		return noMoreWorkUnits;
	}

	public void setNoMoreWorkUnits (
		final @Nullable Continuation0 newNoMoreWorkUnits)
	{
		assert (newNoMoreWorkUnits == null)
				!= (this.noMoreWorkUnits == null)
			: "noMoreWorkUnits must transition to or from null";

		if (Interpreter.debugWorkUnits)
		{
			final boolean wasNull = this.noMoreWorkUnits == null;
			final boolean isNull = newNoMoreWorkUnits == null;
			System.out.println(
				(isNull ? "\nClear" : "\nSet")
					+ " noMoreWorkUnits (was "
					+ (wasNull ? "null)" : "non-null)"));

			final StringBuilder builder = new StringBuilder();
			final Throwable e = new Throwable().fillInStackTrace();
			builder.append(trace(e));
			@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
			final String trace = builder.toString().replaceAll(
				"\\A.*\\R((.*\\R){6})(.|\\R)*\\z", "$1");
			logWorkUnits("SetNoMoreWorkUnits:\n\t" + trace.trim());
		}
		final AtomicBoolean ran = new AtomicBoolean(false);
		this.noMoreWorkUnits = newNoMoreWorkUnits == null
			? null
			: () ->
			{
				assert !ran.getAndSet(true)
					: "Attempting to invoke the same noMoreWorkUnits twice";
				if (Interpreter.debugWorkUnits)
				{
					logWorkUnits("Running noMoreWorkUnits");
				}
				stripNull(newNoMoreWorkUnits).value();
			};
	}

	/**
	 * The {@linkplain CompilerProgressReporter} that reports compilation
	 * progress at various checkpoints. It accepts the {@linkplain
	 * ResolvedModuleName name} of the {@linkplain ModuleDescriptor module}
	 * undergoing {@linkplain AvailCompiler compilation}, the line
	 * number on which the last complete statement concluded, the position of
	 * the ongoing parse (in bytes), and the size of the module (in bytes).
	 */
	private final CompilerProgressReporter progressReporter;

	public CompilerProgressReporter getProgressReporter ()
	{
		return progressReporter;
	}

	/** The output stream on which the serializer writes. */
	public final ByteArrayOutputStream serializerOutputStream =
		new ByteArrayOutputStream(1000);

	/**
	 * The serializer that captures the sequence of bytes representing the
	 * module during compilation.
	 */
	final Serializer serializer;

	/**
	 * Create a {@code CompilationContext} for compiling an {@link A_Module}.
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
	public CompilationContext (
		final @Nullable ModuleHeader moduleHeader,
		final A_Module module,
		final A_String source,
		final TextInterface textInterface,
		final BooleanSupplier pollForAbort,
		final CompilerProgressReporter progressReporter,
		final ProblemHandler problemHandler)
	{
		this.loader = new AvailLoader(module, textInterface);
		this.moduleHeader = moduleHeader;
		this.module = module;
		this.source = source;
		this.textInterface = textInterface;
		this.progressReporter = progressReporter;
		this.diagnostics = new CompilerDiagnostics(
			source, moduleName(), pollForAbort, problemHandler);
		this.serializer = new Serializer(serializerOutputStream, module);
	}

	/**
	 * Record the fact that this token was encountered while parsing the current
	 * top-level statement.
	 *
	 * @param token The token that was encountered.
	 */
	public void recordToken (final A_Token token)
	{
		diagnostics.recordToken(token);
	}

	/**
	 * Attempt the {@linkplain Continuation0 zero-argument continuation}. The
	 * implementation is free to execute it now or to put it in a bag of
	 * continuations to run later <em>in an arbitrary order</em>. There may be
	 * performance and/or scale benefits to processing entries in FIFO, LIFO, or
	 * some hybrid order, but the correctness is not affected by a choice of
	 * order. The implementation may run the expression in parallel with the
	 * invoking thread and other such expressions.
	 *
	 * @param lexingState
	 *        The {@link LexingState} for which to report problems.
	 * @param continuation
	 *        What to do at some point in the future.
	 */
	public void eventuallyDo (
		final LexingState lexingState,
		final Continuation0 continuation)
	{
		runtime.execute(
			FiberDescriptor.compilerPriority,
			() ->
			{
				try
				{
					continuation.value();
				}
				catch (final Exception e)
				{
					reportInternalProblem(
						lexingState.lineNumber, lexingState.position, e);
				}
			});
	}

	/** The cached module name. */
	private volatile @Nullable String debugModuleName = null;

	/**
	 * Lazily extract the module name, and prefix all relevant debug lines with
	 * it.
	 *
	 * @param debugString
	 *        The string to log, with the module name prefixed on each line.
	 */
	private void logWorkUnits (final String debugString)
	{
		@Nullable String moduleName = debugModuleName;
		if (moduleName == null)
		{
			moduleName = moduleName().localName();
			debugModuleName = moduleName;
		}
		final StringBuilder builder = new StringBuilder();
		for (final String line : debugString.split("\n"))
		{
			builder.append(moduleName);
			builder.append("   ");
			builder.append(line);
			builder.append('\n');
		}
		System.out.print(builder);
	}

	/**
	 * Start N work units, which are about to be queued.
	 *
	 * @param countToBeQueued
	 *        The number of new work units that are being queued.
	 */
	public void startWorkUnits (final int countToBeQueued)
	{
		assert getNoMoreWorkUnits() != null;
		assert countToBeQueued > 0;
		final long queued = workUnitsQueued.addAndGet(countToBeQueued);
		if (Interpreter.debugWorkUnits)
		{
			final long completed = getWorkUnitsCompleted();
			final StringBuilder builder = new StringBuilder();
			final Throwable e = new Throwable().fillInStackTrace();
			builder.append(trace(e));
			String[] lines = builder.toString().split("\n", 7);
			if (lines.length > 6)
			{
				lines = Arrays.copyOf(lines, lines.length - 1);
			}
			logWorkUnits(
				"Starting work unit: queued = "
					+ queued
					+ ", completed = "
					+ completed
					+ " (delta="
					+ (queued - completed)
					+ ')'
					+ (countToBeQueued > 1
						   ? " (bulk = +" + countToBeQueued + ')'
						   : "")
					+ "\n\t"
					+ join("\n", lines).trim());
		}
		if (logger.isLoggable(Level.FINEST))
		{
			logger.log(
				Level.FINEST,
				format(
					"Started work unit: %d/%d%n",
					getWorkUnitsCompleted(),
					getWorkUnitsQueued()));
		}
	}

	/**
	 * Construct and answer a {@linkplain Continuation1NotNull continuation}
	 * that wraps the specified continuation in logic that will increment the
	 * {@linkplain #workUnitsCompleted count of completed work units} and
	 * potentially call the {@linkplain #noMoreWorkUnits unambiguous statement}.
	 *
	 * @param lexingState
	 *        The {@link LexingState} for which to report problems.
	 * @param optionalSafetyCheck
	 *        Either {@code null} or an {@link AtomicBoolean} which must
	 *        transition from false to true only once.
	 * @param continuation
	 *        What to do as a work unit.
	 * @param <ArgType>
	 *        The type of value that will be passed to the continuation.
	 * @return A new continuation. It accepts an argument of some kind, which
	 *         will be passed forward to the argument continuation.
	 */
	public <ArgType> Continuation1NotNull<ArgType> workUnitCompletion (
		final LexingState lexingState,
		final @Nullable AtomicBoolean optionalSafetyCheck,
		final Continuation1NotNull<ArgType> continuation)
	{
		assert getNoMoreWorkUnits() != null;
		final AtomicBoolean hasRunSafetyCheck = optionalSafetyCheck != null
			? optionalSafetyCheck
			: new AtomicBoolean(false);
		if (Interpreter.debugWorkUnits)
		{
			logWorkUnits(
				"Creating unit for continuation @" + continuation.hashCode());
		}
		return value ->
		{
			final boolean hadRun = hasRunSafetyCheck.getAndSet(true);
			assert !hadRun;
			try
			{
				continuation.value(value);
			}
			catch (final Exception e)
			{
				reportInternalProblem(
					lexingState.lineNumber, lexingState.position, e);
			}
			finally
			{
				// We increment and read completed and then read queued.  That's
				// because at every moment completed must always be <= queued.
				// No matter how many new tasks are being queued and completed
				// by other threads, that invariant holds.  The other fact is
				// that the moment the counters have the same (nonzero) value,
				// they will forever after have that same value.  Note that we
				// still have to do the fused incrementAndGet so that we know if
				// *we* were the (sole) cause of exhaustion.
				final long completed = workUnitsCompleted.incrementAndGet();
				final long queued = getWorkUnitsQueued();
				assert completed <= queued;
				if (Interpreter.debugWorkUnits)
				{
					logWorkUnits(
						"Completed work unit: queued = "
							+ queued
							+ ", completed = "
							+ completed
							+ " (delta="
							+ (queued - completed)
							+ ')');
				}
				if (logger.isLoggable(Level.FINEST))
				{
					logger.log(
						Level.FINEST,
						format(
							"Completed work unit: %d/%d%n", completed, queued));
				}
				if (completed == queued)
				{
					try
					{
						final Continuation0 noMore =
							stripNull(getNoMoreWorkUnits());
						setNoMoreWorkUnits(null);
						noMore.value();
					}
					catch (final Exception e)
					{
						reportInternalProblem(
							lexingState.lineNumber, lexingState.position, e);
					}
				}
			}
		};
	}

	/**
	 * Eventually execute the specified {@link List} of {@linkplain
	 * Continuation1NotNull}s as {@linkplain AvailCompiler compiler} work units.
	 * Note that the queued work unit count must be increased by the full amount
	 * up-front to ensure the completion of the first N tasks before the N+1st
	 * can be queued doesn't trigger execution of {@link #noMoreWorkUnits}.
	 * Each continuation will be passed the same given argument.
	 *
	 * @param lexingState
	 *        The {@link LexingState} for which to report problems.
	 * @param continuations
	 *        A non-empty list of things to do at some point in the future.
	 * @param argument
	 *        The argument to pass to each continuation.
	 * @param <ArgType>
	 *        The type of the argument to pass to the continuations.
	 */
	public <ArgType> void workUnitsDo (
		final LexingState lexingState,
		final List<Continuation1NotNull<ArgType>> continuations,
		final ArgType argument)
	{
		assert !continuations.isEmpty();
		assert getNoMoreWorkUnits() != null;

		// Start by increasing the queued counter by the number of actions we're
		// adding.
		startWorkUnits(continuations.size());
		// We're tracking work units, so we have to make sure to account for the
		// new unit being queued, to increment the completed count when it
		// completes, and to run the noMoreWorkUnits action as soon the counters
		// coincide (indicating the last work unit just completed).
		for (final Continuation1NotNull<ArgType> continuation : continuations)
		{
			final Continuation1NotNull<ArgType> workUnit =
				workUnitCompletion(lexingState, null, continuation);
			runtime.execute(
				FiberDescriptor.compilerPriority,
				() -> workUnit.value(argument));
		}
	}

	/**
	 * Evaluate the specified {@linkplain FunctionDescriptor function} in the
	 * module's context; lexically enclosing variables are not considered in
	 * scope, but module variables and constants are in scope.
	 *
	 * @param function
	 *        A function.
	 * @param lexingState
	 *        The position at which this function occurs in the module.
	 * @param args
	 *        The arguments to the function.
	 * @param clientParseData
	 *        The map to associate with the {@link
	 *        SpecialAtom#CLIENT_DATA_GLOBAL_KEY} atom in the fiber.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param trackTasks
	 *        Whether to track that this fiber is running, and when done, to
	 *        run {@link #noMoreWorkUnits} if the queued/completed counts agree.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@link Throwable}.
	 */
	private void evaluateFunctionThen (
		final A_Function function,
		final LexingState lexingState,
		final List<? extends A_BasicObject> args,
		final A_Map clientParseData,
		final boolean shouldSerialize,
		final boolean trackTasks,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		final A_RawFunction code = function.code();
		assert code.numArgs() == args.size();
		final A_Fiber fiber = newLoaderFiber(
			function.kind().returnType(),
			loader(),
			() -> formatString(
				"Eval fn=%s, in %s:%d",
				code.methodName(),
				code.module().moduleName(),
				code.startingLineNumber()));
		A_Map fiberGlobals = fiber.fiberGlobals();
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true);
		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(textInterface);
		if (shouldSerialize)
		{
			loader().startRecordingEffects();
		}
		Continuation1NotNull<AvailObject> adjustedSuccess = onSuccess;
		if (shouldSerialize)
		{
			final long before = AvailRuntimeSupport.captureNanos();
			adjustedSuccess = successValue ->
			{
				final long after = AvailRuntimeSupport.captureNanos();
				Interpreter.current().recordTopStatementEvaluation(
					after - before, module, lexingState.lineNumber);
				loader().stopRecordingEffects();
				serializeAfterRunning(function);
				onSuccess.value(successValue);
			};
		}
		if (trackTasks)
		{
			lexingState.setFiberContinuationsTrackingWork(
				fiber, adjustedSuccess, onFailure);
		}
		else
		{
			fiber.setSuccessAndFailureContinuations(adjustedSuccess, onFailure);
		}
		Interpreter.runOutermostFunction(runtime, fiber, function, args);
	}

	/**
	 * Generate a {@linkplain FunctionDescriptor function} from the specified
	 * {@linkplain PhraseDescriptor phrase} and evaluate it in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param expressionNode
	 *        A {@linkplain PhraseDescriptor phrase}.
	 * @param lexingState
	 *        The position at which the expression starts.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param trackTasks
	 *        Whether to track that this fiber is running, and when done, to
	 *        run {@link #noMoreWorkUnits} if the queued/completed counts agree.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do after a failure.
	 */
	public void evaluatePhraseThen (
		final A_Phrase expressionNode,
		final LexingState lexingState,
		final boolean shouldSerialize,
		final boolean trackTasks,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		evaluateFunctionThen(
			createFunctionForPhrase(
				expressionNode, module(), lexingState.lineNumber),
			lexingState,
			emptyList(),
			emptyMap(),
			shouldSerialize,
			trackTasks,
			onSuccess,
			onFailure);
	}

	/**
	 * Evaluate the given phrase.  Pass the result to the continuation, or if it
	 * fails pass the exception to the failure continuation.
	 *
	 * @param lexingState
	 *        The {@link LexingState} at which the phrase starts.
	 * @param expression
	 *        The phrase to evaluate.
	 * @param continuation
	 *        What to do with the result of evaluation.
	 * @param onFailure
	 *        What to do after a failure.
	 */
	void evaluatePhraseAtThen (
		final LexingState lexingState,
		final A_Phrase expression,
		final Continuation1NotNull<AvailObject> continuation,
		final Continuation1NotNull<Throwable> onFailure)
	{
		evaluatePhraseThen(
			expression, lexingState, false, true, continuation, onFailure);
	}

	/**
	 * Serialize either the given function or something semantically equivalent.
	 * The equivalent is expected to be faster, but can only be used if no
	 * triggers had been tripped during execution of the function.  The triggers
	 * include reading or writing shared variables, or executing certain
	 * primitives.
	 *
	 * @param function The function that has already run.
	 */
	private synchronized void serializeAfterRunning (
		final A_Function function)
	{
		final A_RawFunction code = function.code();
		final int startingLineNumber = code.startingLineNumber();
		if (loader().statementCanBeSummarized())
		{
			// Output summarized functions instead of what ran.  Associate the
			// original phrase with it, which allows the subphrases to be marked
			// up in an editor and stepped in a debugger, even though the
			// top-level phrase itself will be invalid.
			final Iterator<LoadingEffect> iterator =
				loader().recordedEffects().iterator();
			while (iterator.hasNext())
			{
				final L1InstructionWriter writer =
					new L1InstructionWriter(
						module,
						startingLineNumber,
						code.originatingPhrase());
				writer.argumentTypes();
				writer.setReturnType(Types.TOP.o());
				int batchCount = 0;
				for (; batchCount < 100 && iterator.hasNext(); batchCount++)
				{
					if (batchCount > 0)
					{
						writer.write(
							startingLineNumber, L1Operation.L1_doPop);
					}
					iterator.next().writeEffectTo(writer);
				}
				assert batchCount > 0;
				// Flush the batch.
				final A_Function summaryFunction =
					createFunction(writer.compiledCode(), emptyTuple());
				if (AvailLoader.debugUnsummarizedStatements)
				{
					System.out.println(
						module.moduleName().asNativeString()
							+ ':' + startingLineNumber
							+ " Summary -- " + function
							+ "(batch = " + batchCount + ')');
				}
				serializer.serialize(summaryFunction);
			}
		}
		else
		{
			// Can't summarize; write the original function.
			if (AvailLoader.debugUnsummarizedStatements)
			{
				System.out.println(
					module
						+ ":" + startingLineNumber
						+ " Unsummarized -- " + function);
			}
			serializer.serialize(function);
		}
	}

	/**
	 * Serialize the given function without attempting to summarize it into
	 * equivalent statements.
	 *
	 * @param function The function that has already run.
	 */
	synchronized void serializeWithoutSummary (
		final A_Function function)
	{
		if (AvailLoader.debugUnsummarizedStatements)
		{
			System.out.println(
				module
					+ ":" + function.code().startingLineNumber()
					+ " Forced -- " + function);
		}
		serializer.serialize(function);
	}

	/**
	 * Report an {@linkplain ProblemType#INTERNAL internal} {@linkplain
	 * Problem problem}.
	 *
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The unexpected {@linkplain Throwable exception} that is the
	 *        proximal cause of the problem.
	 */
	void reportInternalProblem (
		final int lineNumber,
		final int position,
		final Throwable e)
	{
		diagnostics.compilationIsInvalid = true;
		final Problem problem = new Problem(
			moduleName(),
			lineNumber,
			position,
			INTERNAL,
			"Internal error: {0}\n{1}",
			e.getMessage(),
			trace(e))
		{
			@Override
			protected void abortCompilation ()
			{
				// Nothing else needed
			}
		};
		diagnostics.handleProblem(problem);
	}

	/**
	 * Report an {@linkplain ProblemType#EXECUTION execution} {@linkplain
	 * Problem problem}.
	 *
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The unexpected {@linkplain Throwable exception} that is the
	 *        proximal cause of the problem.
	 */
	void reportExecutionProblem (
		final int lineNumber,
		final int position,
		final Throwable e)
	{
		diagnostics.compilationIsInvalid = true;
		if (e instanceof FiberTerminationException)
		{
			diagnostics.handleProblem(
				new Problem(
					moduleName(),
					lineNumber,
					position,
					EXECUTION,
					"Execution error: Avail stack reported above.\n")
				{
					@Override
					public void abortCompilation ()
					{
						// Nothing else needed
					}
				});
		}
		else
		{
		diagnostics.handleProblem(
			new Problem(
				moduleName(),
				lineNumber,
				position,
				EXECUTION,
				"Execution error: {0}\n{1}",
				e.getMessage(),
				trace(e))
			{
				@Override
				public void abortCompilation ()
				{
					// Nothing else needed
				}
			});
		}
	}

	/**
	 * Report an {@linkplain ProblemType#EXECUTION assertion failure}
	 * {@linkplain Problem problem}.
	 *
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The {@linkplain AvailAssertionFailedException assertion failure}.
	 */
	void reportAssertionFailureProblem (
		final int lineNumber,
		final int position,
		final AvailAssertionFailedException e)
	{
		diagnostics.compilationIsInvalid = true;
		diagnostics.handleProblem(new Problem(
			moduleName(),
			lineNumber,
			position,
			EXECUTION,
			"{0}",
			e.getMessage())
		{
			@Override
			public void abortCompilation ()
			{
				// Nothing else needed
			}
		});
	}

	/**
	 * Report an {@linkplain ProblemType#EXECUTION emergency exit}
	 * {@linkplain Problem problem}.
	 *
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The {@linkplain AvailEmergencyExitException emergency exit
	 *        failure}.
	 */
	void reportEmergencyExitProblem (
		final int lineNumber,
		final int position,
		final AvailEmergencyExitException e)
	{
		diagnostics.compilationIsInvalid = true;
		diagnostics.handleProblem(new Problem(
			moduleName(),
			lineNumber,
			position,
			EXECUTION,
			"{0}",
			e.getMessage())
		{
			@Override
			public void abortCompilation ()
			{
				// Nothing else needed
			}
		});
	}
}
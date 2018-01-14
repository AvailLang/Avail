/**
 * AvailLoader.java
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

package com.avail.interpreter;

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.compiler.CompilationContext;
import com.avail.compiler.scanning.LexingState;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.AmbiguousNameException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.effects.LoadingEffect;
import com.avail.interpreter.effects.LoadingEffectToAddDefinition;
import com.avail.interpreter.effects.LoadingEffectToRunPrimitive;
import com.avail.interpreter.primitive.bootstrap.lexing.*;
import com.avail.io.TextInterface;
import com.avail.utility.Mutable;
import com.avail.utility.MutableInt;
import com.avail.utility.MutableOrNull;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractDefinitionDescriptor
	.newAbstractDefinition;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom
	.EXPLICIT_SUBCLASSING_KEY;
import static com.avail.descriptor.AtomDescriptor.createAtom;
import static com.avail.descriptor.AtomDescriptor.createSpecialAtom;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberDescriptor.newLoaderFiber;
import static com.avail.descriptor.ForwardDefinitionDescriptor
	.newForwardDefinition;
import static com.avail.descriptor.FunctionDescriptor.newPrimitiveFunction;
import static com.avail.descriptor.GrammaticalRestrictionDescriptor
	.newGrammaticalRestriction;
import static com.avail.descriptor.LexerDescriptor.newLexer;
import static com.avail.descriptor.MacroDefinitionDescriptor.newMacroDefinition;
import static com.avail.descriptor.MessageBundleTreeDescriptor.newBundleTree;
import static com.avail.descriptor.MethodDefinitionDescriptor
	.newMethodDefinition;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ParsingPlanInProgressDescriptor
	.newPlanInProgress;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.AvailLoader.Phase.*;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.StackPrinter.trace;
import static java.util.Collections.emptyMap;

/**
 * An {@code AvailLoader} is responsible for orchestrating module-level
 * side-effects, such as those caused by {@linkplain MethodDefinitionDescriptor
 * method}, {@linkplain AbstractDefinitionDescriptor abstract}, and {@linkplain
 * ForwardDefinitionDescriptor forward} definitions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailLoader
{
	public static class LexicalScanner
	{
		/**
		 * The {@link List} of all {@link A_Lexer lexers} which are visible
		 * within the module being compiled.
		 */
		public final List<A_Lexer> allVisibleLexers = new ArrayList<>();

		boolean frozen = false;

		public synchronized void freezeFromChanges()
		{
			assert !frozen;
			frozen = true;
		}

		/**
		 * A 256-way dispatch table that takes a Latin-1 character's Unicode
		 * codepoint (which is in [0..255]) to a {@link A_Tuple tuple} of
		 * {@link A_Lexer lexers}.  Non-Latin1 characters (i.e., with codepoints
		 * ≥ 256) are tracked separately in {@link #nonLatin1Lexers}.
		 *
		 * <p>This array is populated lazily and incrementally, so if an entry
		 * is nil, it should be constructed by testing the character against all
		 * visible lexers' filter functions.  Place the ones that pass into a
		 * set, then normalize it by looking up that set in a map from such sets
		 * to tuples.  This causes two equal sets of lexers to be canonicalized
		 * to the same tuple, thereby reusing it.</p>
		 *
		 * <p>When a new lexer is defined, we null all entries of this dispatch
		 * table and clear the supplementary map, allowing the entries to be
		 * incrementally constructed.  We also clear the map from lexer sets to
		 * canonical tuples.</p>
		 */
		final A_Tuple[] latin1ApplicableLexers = new A_Tuple[256];

		/**
		 * The lock controlling access to the array of lexers filtered on
		 * characters within the Latin1 block of Unicode (i.e., U+0000..U+00FF).
		 */
		final ReentrantReadWriteLock latin1Lock = new ReentrantReadWriteLock();

		/**
		 * A map from non-Latin-1 codepoint (i.e., ≥ 256) to the tuple of lexers
		 * that should run when that character is encountered at a lexing point.
		 * Should only be accessed within {@link #nonLatin1Lock}.
		 */
		final Map<Integer, A_Tuple> nonLatin1Lexers = new HashMap<>();

		/** A lock to protect {@link #nonLatin1Lexers}. */
		final ReentrantReadWriteLock nonLatin1Lock =
			new ReentrantReadWriteLock();

		/**
		 * The canonical mapping from each set of lexers to a tuple of lexers.
		 */
		final Map<A_Set, A_Tuple> canonicalLexerTuples = new HashMap<>();

		public LexicalScanner ()
		{
			// Nothing else to initialize.
		}

		/**
		 * Add an {@link A_Lexer}.  Update not just the current lexing
		 * information for this loader, but also the specified atom's bundle's
		 * method and the current module.
		 *
		 * <p>This must be called as an L1-safe task, which precludes execution
		 * of Avail code while it's running.  That does not preclude other
		 * non-Avail code from running (i.e., other L1-safe tasks), so this
		 * method is synchronized to achieve that.</p>
		 *
		 * @param lexer
		 *        The {@link A_Lexer} to add.
		 */
		public synchronized void addLexer (
			final A_Lexer lexer)
		{
			assert !frozen;
			lexer.lexerMethod().setLexer(lexer);
			final A_Module module = lexer.definitionModule();
			if (!module.equalsNil())
			{
				module.addLexer(lexer);
			}
			// Update the loader's lexing tables...
			allVisibleLexers.add(lexer);
			// Since the existence of at least one non-null entry in the Latin-1
			// or non-Latin-1 tables implies there must be at least one
			// canonical tuple of lexers, we can skip clearing the tables if the
			// canonical map is empty.
			if (!canonicalLexerTuples.isEmpty())
			{
				Arrays.fill(latin1ApplicableLexers, null);
				nonLatin1Lexers.clear();
				canonicalLexerTuples.clear();
			}
		}

		/**
		 * Collect the lexers should run when we encounter a character with the
		 * given (int) code point, then pass this tuple of lexers to the
		 * supplied {@link Continuation1NotNull}.
		 *
		 * <p>We pass it forward rather than return it, since sometimes this
		 * requires lexer filter functions to run, which we must not do
		 * synchronously.  However, if the lexer filters have already run for
		 * this code point, we <em>may</em> invoke the continuation
		 * synchronously for performance.</p>
		 *
		 * @param lexingState
		 *        The {@link LexingState} at which the lexical scanning is
		 *        happening.
		 * @param codePoint
		 *        The full Unicode code point in the range 0..1114111.
		 * @param continuation
		 *        What to invoke with the tuple of tokens at this position.
		 * @param onFailure
		 *        What to do if lexical scanning fails.
		 */
		public void getLexersForCodePointThen (
			final LexingState lexingState,
			final int codePoint,
			final Continuation1NotNull<A_Tuple> continuation,
			final Continuation1NotNull<Map<A_Lexer, Throwable>> onFailure)
		{
			if ((codePoint & ~255) == 0)
			{
				latin1Lock.readLock().lock();
				final A_Tuple tuple;
				try
				{
					tuple = latin1ApplicableLexers[codePoint];
				}
				finally
				{
					latin1Lock.readLock().unlock();
				}
				if (tuple != null)
				{
					continuation.value(tuple);
					return;
				}

				// Run the filters to produce the set of applicable lexers, then
				// use the canonical map to make it a tuple, then invoke the
				// continuation with it.
				selectLexersPassingFilterThen(
					lexingState,
					codePoint,
					(applicableLexers, failures) ->
					{
						assert applicableLexers != null;
						assert failures != null;
						if (!failures.isEmpty())
						{
							onFailure.value(failures);
							// Don't cache the successful lexer filter
							// results, because we shouldn't continue and
							// neither should lexing of any codePoint
							// equal to the one that caused this trouble.
							return;
						}
						A_Tuple tupleOfLexers;
						synchronized (canonicalLexerTuples)
						{
							tupleOfLexers = canonicalLexerTuples.get(
								applicableLexers);
							if (tupleOfLexers == null)
							{
								tupleOfLexers = applicableLexers.asTuple();
								canonicalLexerTuples.put(
									applicableLexers.makeShared(),
									tupleOfLexers.makeShared());
							}
						}
						// Just replace it, even if another thread beat
						// us to the punch, since it's semantically
						// idempotent.
						latin1Lock.writeLock().lock();
						try
						{
							latin1ApplicableLexers[codePoint] = tupleOfLexers;
						}
						finally
						{
							latin1Lock.writeLock().unlock();
						}
						continuation.value(tupleOfLexers);
					});
			}
			else
			{
				// It's non-Latin1.
				nonLatin1Lock.readLock().lock();
				final A_Tuple tuple;
				try
				{
					tuple = nonLatin1Lexers.get(codePoint);
				}
				finally
				{
					nonLatin1Lock.readLock().unlock();
				}
				if (tuple != null)
				{
					continuation.value(tuple);
					return;
				}
				// Run the filters to produce the set of applicable lexers, then
				// use the canonical map to make it a tuple, then invoke the
				// continuation with it.
				selectLexersPassingFilterThen(
					lexingState,
					codePoint,
					(applicableLexers, failures) ->
					{
						assert applicableLexers != null;
						assert failures != null;
						if (!failures.isEmpty())
						{
							onFailure.value(failures);
							// Don't cache the successful lexer filter
							// results, because we shouldn't continue and
							// neither should lexing of any codePoint
							// equal to the one that caused this trouble.
							return;
						}
						A_Tuple tupleOfLexers;
						synchronized (canonicalLexerTuples)
						{
							tupleOfLexers = canonicalLexerTuples.get(
								applicableLexers);
							if (tupleOfLexers == null)
							{
								tupleOfLexers =
									applicableLexers.asTuple().makeShared();
								canonicalLexerTuples.put(
									applicableLexers.makeShared(),
									tupleOfLexers.makeShared());
							}
							// Just replace it, even if another thread beat
							// us to the punch, since it's semantically
							// idempotent.
							nonLatin1Lock.writeLock().lock();
							try
							{
								nonLatin1Lexers.put(codePoint, tupleOfLexers);
							}
							finally
							{
								nonLatin1Lock.writeLock().unlock();
							}
						}
						continuation.value(tupleOfLexers);
					});
			}
		}

		/**
		 * Collect the lexers that should run when we encounter a character with
		 * the given (int) code point, then pass this set of lexers to the
		 * supplied {@link Continuation1}.
		 *
		 * <p>We pass it forward rather than return it, since sometimes this
		 * requires lexer filter functions to run, which we must not do
		 * synchronously.  However, if the lexer filters have already run for
		 * this code point, we <em>may</em> invoke the continuation
		 * synchronously for performance.</p>
		 *
		 * @param lexingState
		 *        The {@link LexingState} at which scanning encountered this
		 *        codePoint for the first time.
		 * @param codePoint
		 *        The full Unicode code point in the range 0..1114111.
		 * @param continuation
		 *        What to invoke with the {@link A_Set set} of {@link A_Lexer
		 *        lexers} and a (normally empty) map from lexer to throwable,
		 *        indicating lexer filter invocations that raised exceptions.
		 */
		private void selectLexersPassingFilterThen (
			final LexingState lexingState,
			final int codePoint,
			final Continuation2<A_Set, Map<A_Lexer, Throwable>> continuation)
		{
			final MutableInt countdown =
				new MutableInt(allVisibleLexers.size());
			if (countdown.value == 0)
			{
				continuation.value(emptySet(), emptyMap());
				return;
			}
			// Initially use the immutable emptyMap for the failureMap, but
			// replace it if/when the first error happens.
			final Mutable<Map<A_Lexer, Throwable>> failureMap =
				new Mutable<>(emptyMap());
			final List<A_Character> argsList = Collections.singletonList(
				fromCodePoint(codePoint));
			final Object joinLock = new Object();
			final List<A_Lexer> applicableLexers = new ArrayList<>();
			final CompilationContext compilationContext =
				lexingState.compilationContext;
			final AvailLoader loader = compilationContext.loader();
			compilationContext.startWorkUnits(allVisibleLexers.size());
			for (final A_Lexer lexer : allVisibleLexers)
			{
				final A_Fiber fiber = newLoaderFiber(
					booleanType(),
					loader,
					() -> formatString(
						"Check lexer filter %s for U+%04x",
						lexer.lexerMethod().chooseBundle(loader.module())
							.message().atomName(),
						codePoint));
				Continuation1NotNull<AvailObject> fiberSuccess = boolValue ->
				{
					assert boolValue.isBoolean();
					final boolean countdownHitZero;
					synchronized (joinLock)
					{
						if (boolValue.extractBoolean())
						{
							applicableLexers.add(lexer);
						}
						countdown.value--;
						assert countdown.value >= 0;
						countdownHitZero = countdown.value == 0;
					}
					if (countdownHitZero)
					{
						// This was the fiber reporting the last result.
						continuation.value(
							setFromCollection(applicableLexers),
							failureMap.value);
					}
				};
				Continuation1NotNull<Throwable> fiberFailure = throwable ->
				{
					final boolean countdownHitZero;
					synchronized (joinLock)
					{
						if (failureMap.value.isEmpty())
						{
							failureMap.value = new HashMap<>();
						}
						failureMap.value.put(lexer, throwable);
						countdown.value--;
						assert countdown.value >= 0;
						countdownHitZero = countdown.value == 0;
					}
					if (countdownHitZero)
					{
						// This was the fiber reporting the last
						// result (a fiber failure).
						continuation.value(
							setFromCollection(applicableLexers),
							failureMap.value);
					}
				};
				fiber.textInterface(loader.textInterface);

				assert (compilationContext.getNoMoreWorkUnits() != null);
				// Trace it as a work unit.
				final AtomicBoolean oneWay = new AtomicBoolean();
				fiberSuccess = compilationContext.workUnitCompletion(
					lexingState, oneWay, fiberSuccess);
				fiberFailure = compilationContext.workUnitCompletion(
					lexingState, oneWay, fiberFailure);
				fiber.resultContinuation(fiberSuccess);
				fiber.failureContinuation(fiberFailure);
				Interpreter.runOutermostFunction(
					loader.runtime(),
					fiber,
					lexer.lexerFilterFunction(),
					argsList);
			}
		}
	}

	/**
	 * The macro-state of the loader.  During compilation from a file, a loader
	 * will ratchet between {@link #COMPILING} for a top-level statement and
	 * {@link #EXECUTING_FOR_COMPILE}.  Similarly, when loading from a file, the
	 * loader's {@link #phase} alternates between {@link #LOADING} and {@link
	 * #EXECUTING_FOR_LOAD}.
	 */
	public enum Phase
	{
		/** No statements have been loaded or compiled yet. */
		INITIALIZING,

		/** A top-level statement is being compiled. */
		COMPILING,

		/** A top-level statement is being loaded from a repository. */
		LOADING,

		/** A top-level statement is being executed. */
		EXECUTING_FOR_COMPILE,

		/** A top-level statement is being executed. */
		EXECUTING_FOR_LOAD,

		/** The fully-loaded module is now being unloaded. */
		UNLOADING;

		public boolean isExecuting ()
		{
			return this == EXECUTING_FOR_COMPILE || this == EXECUTING_FOR_LOAD;
		}
	}

	/** The current loading setPhase. */
	private volatile Phase phase;

	/**
	 * Get the current loading {@link Phase}.
	 *
	 * @return The loader's current setPhase.
	 */
	public Phase phase ()
	{
		return phase;
	}

	/**
	 * Set the current loading {@link Phase}.
	 *
	 * @param newPhase The new setPhase.
	 */
	public void setPhase (final Phase newPhase)
	{
		phase = newPhase;
	}

	/**
	 * Allow investigation of why a top-level expression is being excluded from
	 * summarization.
	 */
	public static boolean debugUnsummarizedStatements = false;

	/**
	 * Show the top-level statements that are executed during loading or
	 * compilation.
	 */
	public static boolean debugLoadedStatements = false;

	/**
	 * A flag that controls whether compilation attempts to use the fast-loader
	 * to rewrite some top-level statements into a faster form.
	 */
	public static boolean enableFastLoader = true;

	/**
	 * The {@link AvailRuntime} for the loader. Since a {@linkplain AvailLoader
	 * loader} cannot migrate between two
	 * {@linkplain AvailRuntime runtimes}, it
	 * is safe to cache it for efficient access.
	 */
	private final AvailRuntime runtime = currentRuntime();

	/**
	 * Answer the {@link AvailRuntime} for the loader.
	 *
	 * @return The Avail runtime.
	 */
	public AvailRuntime runtime ()
	{
		return runtime;
	}

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing {@linkplain
	 * AvailLoader loader}.
	 */
	private final A_Module module;

	/**
	 * Answer the {@linkplain ModuleDescriptor module} undergoing loading by
	 * this {@code AvailLoader}.
	 *
	 * @return A module.
	 */
	public A_Module module ()
	{
		return module;
	}

	private @Nullable CompilationContext compilationContext;

	/**
	 * Set the current {@link CompilationContext} for this loader.
	 *
	 * @param context A {@link CompilationContext}.
	 */
	public void compilationContext (final CompilationContext context)
	{
		compilationContext = context;
	}

	/**
	 * Answer the current {@link CompilationContext}, failing if there is none
	 * for this loader.
	 *
	 * @return A {@link CompilationContext}.
	 */
	public CompilationContext compilationContext ()
	{
		return stripNull(compilationContext);
	}

	/** Used for extracting tokens from the source text. */
	private LexicalScanner lexicalScanner;

	/**
	 * Answer the {@link LexicalScanner} used for creating tokens from source
	 * code for this {@link AvailLoader}.
	 */
	public LexicalScanner lexicalScanner ()
	{
		return lexicalScanner;
	}

	/**
	 * The {@linkplain TextInterface text interface} for any {@linkplain A_Fiber
	 * fibers} started by this {@code AvailLoader}.
	 */
	@InnerAccess final TextInterface textInterface;

	/**
	 * The {@linkplain MessageBundleTreeDescriptor message bundle tree} that
	 * this {@code AvailLoader} is using to parse its {@linkplain
	 * ModuleDescriptor module}.
	 */
	private @Nullable A_BundleTree rootBundleTree;

	/**
	 * Answer the {@linkplain MessageBundleTreeDescriptor message bundle tree}
	 * that this {@code AvailLoader} is using to parse its
	 * {@linkplain ModuleDescriptor module}.
	 *
	 * @return A message bundle tree.
	 */
	public A_BundleTree rootBundleTree ()
	{
		return stripNull(rootBundleTree);
	}

	/**
	 * A flag that is cleared before executing each top-level statement of a
	 * module, and set whenever execution of the statement causes behavior that
	 * can't simply be summarized by a sequence of {@link LoadingEffect}s.
	 */
	private volatile boolean statementCanBeSummarized = true;

	/**
	 * A flag that indicates whether we are attempting to determine whether an
	 * expression can be summarized into a series of {@link LoadingEffect}s.
	 */
	private boolean determiningSummarizability = false;

	/**
	 * Replace the boolean that indicates whether the current statement can be
	 * summarized into a sequence of {@link LoadingEffect}s.  It is set to true
	 * before executing a top-level statement, and set to false if an activity
	 * is performed that cannot be summarized.
	 *
	 * @param summarizable The new value of the flag.
	 */
	public void statementCanBeSummarized (final boolean summarizable)
	{
		if (determiningSummarizability)
		{
			if (debugUnsummarizedStatements
				&& !summarizable
				&& statementCanBeSummarized)
			{
				// Here's a good place for a breakpoint, to see why an
				// expression couldn't be summarized.
				final Throwable e = new Throwable().fillInStackTrace();
				System.err.println("Disabled summary:\n" + trace(e));
			}
			statementCanBeSummarized = summarizable;
		}
	}

	/**
	 * Answer whether the current statement can be summarized into a sequence of
	 * {@link LoadingEffect}s.
	 *
	 * @return The current value of the flag.
	 */
	public boolean statementCanBeSummarized ()
	{
		return statementCanBeSummarized;
	}

	/**
	 * The sequence of effects performed by the current top-level statement of
	 * a module being compiled.
	 */
	private final List<LoadingEffect> effectsAddedByTopStatement =
		new ArrayList<>();

	/**
	 * Record a {@link LoadingEffect} to ensure it will be replayed when the
	 * module currently being compiled is later loaded.
	 *
	 * @param anEffect The effect to record.
	 */
	public synchronized void recordEffect (final LoadingEffect anEffect)
	{
		if (determiningSummarizability)
		{
			effectsAddedByTopStatement.add(anEffect);
		}
	}

	/**
	 * Set a flag that indicates we are determining if the effects of running
	 * a function can be summarized, and if so into what {@link LoadingEffect}s.
	 */
	public synchronized void startRecordingEffects ()
	{
		assert !determiningSummarizability;
		determiningSummarizability = true;
		statementCanBeSummarized = enableFastLoader;
		effectsAddedByTopStatement.clear();
	}

	/**
	 * Clear the flag that indicates whether we are determining if the effects
	 * of running a function can be summarized into {@link LoadingEffect}s.
	 */
	public synchronized void stopRecordingEffects ()
	{
		assert determiningSummarizability;
		determiningSummarizability = false;
	}

	/**
	 * Answer the list of {@link LoadingEffect}s.
	 *
	 * @return Answer the recorded {@link LoadingEffect}s.
	 */
	public synchronized List<LoadingEffect> recordedEffects ()
	{
		return new ArrayList<>(effectsAddedByTopStatement);
	}

	/**
	 * Construct a new {@code AvailLoader}.
	 *
	 * @param module
	 *        The Avail {@linkplain ModuleDescriptor module} undergoing loading
	 *        by this {@code AvailLoader}.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for any {@linkplain
	 *        A_Fiber fibers} started by this loader.
	 */
	public AvailLoader (
		final A_Module module,
		final TextInterface textInterface)
	{
		this.module = module;
		this.textInterface = textInterface;
		this.phase = INITIALIZING;
		// Start by using the module header lexical scanner, and replace it
		// after the header has been fully parsed.
		this.lexicalScanner = moduleHeaderLexicalScanner;
		// Do the same for the module header bundle tree.
		this.rootBundleTree = moduleHeaderBundleRoot;
	}

	/**
	 * Create an {@code AvailLoader} suitable for unloading the specified
	 * {@linkplain ModuleDescriptor module}.
	 *
	 * @param module
	 *        The module that will be unloaded.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for any {@linkplain
	 *        A_Fiber fibers} started by the new builder.
	 * @return An AvailLoader suitable for unloading the module.
	 */
	public static AvailLoader forUnloading (
		final A_Module module,
		final TextInterface textInterface)
	{
		final AvailLoader loader = new AvailLoader(module, textInterface);
		// We had better not be removing forward declarations from an already
		// fully-loaded module.
		loader.pendingForwards = nil;
		loader.phase = UNLOADING;
		return loader;
	}

	/**
	 * Create the {@link #rootBundleTree} now that the module's imports and new
	 * public names have been declared.
	 */
	public void createFilteredBundleTree ()
	{
		rootBundleTree = module.buildFilteredBundleTree();
	}

	/**
	 * Create the {@link #lexicalScanner} now that the module's imports and new
	 * public names have been declared.
	 */
	public void createLexicalScanner ()
	{
		lexicalScanner = module.createLexicalScanner();
	}

	/** The unresolved forward method declarations. */
	public A_Set pendingForwards = emptySet();

	/**
	 * The given forward is in the process of being resolved. A real definition
	 * is about to be added to the method tables, so remove the forward now.
	 *
	 * @param forwardDefinition A forward declaration.
	 */
	private void removeForward (
		final A_Definition forwardDefinition)
	{
		final A_Method method = forwardDefinition.definitionMethod();
		if (!pendingForwards.hasElement(forwardDefinition))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		if (!method.includesDefinition(forwardDefinition))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		pendingForwards = pendingForwards.setWithoutElementCanDestroy(
			forwardDefinition, true);
		method.removeDefinition(forwardDefinition);
		module.resolveForward(forwardDefinition);
	}

	/**
	 * This is a forward declaration of a method. Insert an appropriately
	 * stubbed definition in the module's method dictionary, and add it to
	 * the list of methods needing to be declared later in this module.
	 *
	 * @param methodName
	 *        A {@linkplain AtomDescriptor method name}.
	 * @param bodySignature
	 *        A {@linkplain MethodDefinitionDescriptor method signature}.
	 * @throws MalformedMessageException
	 *         If the message name is malformed.
	 * @throws SignatureException
	 *         If there is a problem with the signature.
	 */
	public void addForwardStub (
		final A_Atom methodName,
		final A_Type bodySignature)
	throws MalformedMessageException, SignatureException
	{
		methodName.makeShared();
		bodySignature.makeShared();
		final A_Bundle bundle = methodName.bundleOrCreate();
		final MessageSplitter splitter = bundle.messageSplitter();
		splitter.checkImplementationSignature(bodySignature);
		final A_Type bodyArgsTupleType = bodySignature.argsTupleType();
		// Add the stubbed method definition.
		final A_Method method = bundle.bundleMethod();
		for (final A_Definition definition : method.definitionsTuple())
		{
			final A_Type existingType = definition.bodySignature();
			if (existingType.argsTupleType().equals(bodyArgsTupleType))
			{
				throw new SignatureException(
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
			}
			if (existingType.acceptsArgTypesFromFunctionType(bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					bodySignature.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		final A_Definition newForward = newForwardDefinition(
			method, module, bodySignature);
		method.methodAddDefinition(newForward);
		recordEffect(new LoadingEffectToAddDefinition(newForward));
		final A_Module theModule = module;
		final A_BundleTree root = rootBundleTree();
		theModule.lock(() ->
		{
			theModule.moduleAddDefinition(newForward);
			pendingForwards = pendingForwards.setWithElementCanDestroy(
				newForward, true);
			final A_DefinitionParsingPlan plan =
				bundle.definitionParsingPlans().mapAt(newForward);
			final A_ParsingPlanInProgress planInProgress =
				newPlanInProgress(plan, 1);
			root.addPlanInProgress(planInProgress);
		});
	}

	/**
	 * Add the method definition. The precedence rules can change at any
	 * time.
	 *
	 * @param methodName
	 *        A {@linkplain AtomDescriptor method name}.
	 * @param bodyBlock
	 *        The {@linkplain FunctionDescriptor body block}.
	 * @throws MalformedMessageException
	 *         If the message name is malformed.
	 * @throws SignatureException
	 *         If the signature is invalid.
	 */
	public void addMethodBody (
		final A_Atom methodName,
		final A_Function bodyBlock)
	throws MalformedMessageException, SignatureException
	{
		assert methodName.isAtom();
		assert bodyBlock.isFunction();

		final A_Bundle bundle = methodName.bundleOrCreate();
		final MessageSplitter splitter = bundle.messageSplitter();
		splitter.checkImplementationSignature(bodyBlock.kind());
		final int numArgs = splitter.numberOfArguments();
		if (bodyBlock.code().numArgs() != numArgs)
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		// Make it so we can safely hold onto these things in the VM
		methodName.makeShared();
		bodyBlock.makeShared();
		addDefinition(
			newMethodDefinition(bundle.bundleMethod(), module, bodyBlock));
	}

	/**
	 * Add the abstract method signature. A class is considered abstract if
	 * there are any abstract methods that haven't been overridden with
	 * definitions for it.
	 *
	 * @param methodName
	 *        A {@linkplain AtomDescriptor method name}.
	 * @param bodySignature
	 *        The {@linkplain MethodDefinitionDescriptor method signature}.
	 * @throws MalformedMessageException
	 *         If the message name is malformed.
	 * @throws SignatureException
	 *         If there is a problem with the signature.
	 */
	public void addAbstractSignature (
		final A_Atom methodName,
		final A_Type bodySignature)
	throws MalformedMessageException, SignatureException
	{
		final A_Bundle bundle = methodName.bundleOrCreate();
		final MessageSplitter splitter = bundle.messageSplitter();
		final int numArgs = splitter.numberOfArguments();
		final A_Type bodyArgsSizes = bodySignature.argsTupleType().sizeRange();
		if (!bodyArgsSizes.lowerBound().equalsInt(numArgs)
			|| !bodyArgsSizes.upperBound().equalsInt(numArgs))
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		assert bodyArgsSizes.upperBound().equalsInt(numArgs)
			: "Wrong number of arguments in abstract method signature";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeShared();
		bodySignature.makeShared();
		addDefinition(
			newAbstractDefinition(
				bundle.bundleMethod(), module, bodySignature));
	}

	/**
	 * Add the new {@link A_Definition} to its {@link A_Method}.  Also update
	 * the methods {@link A_Bundle}s and this loader's {@link #rootBundleTree}
	 * as needed.
	 *
	 * @param newDefinition
	 *        The definition to add.
	 * @throws SignatureException
	 *         If the signature disagrees with existing definitions and
	 *         forwards.
	 */
	private void addDefinition (
		final A_Definition newDefinition)
	throws SignatureException
	{
		final A_Method method = newDefinition.definitionMethod();
		final A_Type bodySignature = newDefinition.bodySignature();
		@Nullable A_Definition forward = null;
		for (final A_Definition existingDefinition : method.definitionsTuple())
		{
			final A_Type existingType = existingDefinition.bodySignature();
			final boolean same = existingType.argsTupleType().equals(
				bodySignature.argsTupleType());
			if (same)
			{
				if (existingDefinition.isForwardDefinition())
				{
					if (existingType.returnType().equals(
						bodySignature.returnType()))
					{
						forward = existingDefinition;
					}
					else
					{
						throw new SignatureException(
							E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED);
					}
				}
				else
				{
					throw new SignatureException(
						E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
				}
			}
			if (existingType.acceptsArgTypesFromFunctionType(bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					bodySignature.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		final @Nullable A_Definition finalForward = forward;
		final A_Module theModule = module;
		theModule.lock(() ->
		{
			final A_Set ancestorModules = theModule.allAncestors();
			final A_BundleTree root = rootBundleTree();
			if (finalForward != null)
			{
				for (final A_Bundle bundle : method.bundles())
				{
					if (ancestorModules.hasElement(
						bundle.message().issuingModule()))
					{
						// Remove the appropriate forwarder plan from the
						// bundle tree.
						final A_DefinitionParsingPlan plan =
							bundle.definitionParsingPlans().mapAt(finalForward);
						final A_ParsingPlanInProgress planInProgress =
							newPlanInProgress(plan, 1);
						root.removePlanInProgress(planInProgress);
					}
				}
				removeForward(finalForward);
			}
			try
			{
				method.methodAddDefinition(newDefinition);
			}
			catch (final SignatureException e)
			{
				assert false : "Signature was already vetted";
				return;
			}
			recordEffect(new LoadingEffectToAddDefinition(newDefinition));
			for (final A_Bundle bundle : method.bundles())
			{
				if (ancestorModules.hasElement(
					bundle.message().issuingModule()))
				{
					final A_DefinitionParsingPlan plan =
						bundle.definitionParsingPlans().mapAt(newDefinition);
					final A_ParsingPlanInProgress planInProgress =
						newPlanInProgress(plan, 1);
					root.addPlanInProgress(planInProgress);
				}
			}
			theModule.moduleAddDefinition(newDefinition);
		});
	}

	/**
	 * Add the macro definition. The precedence rules can not change after
	 * the first definition is encountered, so set them to 'no restrictions'
	 * if they're not set already.
	 *
	 * @param methodName
	 *        The macro's name, an {@linkplain AtomDescriptor atom}.
	 * @param macroBody
	 *        A {@linkplain FunctionDescriptor function} that manipulates parse
	 *        nodes.
	 * @param prefixFunctions
	 *        The tuple of functions to run during macro parsing, corresponding
	 *        with occurrences of section checkpoints ("§") in the macro name.
	 * @throws MalformedMessageException
	 *         If the macro signature is malformed.
	 * @throws SignatureException
	 *         If the macro signature is invalid.
	 */
	public void addMacroBody (
		final A_Atom methodName,
		final A_Function macroBody,
		final A_Tuple prefixFunctions)
	throws MalformedMessageException, SignatureException
	{
		assert methodName.isAtom();
		assert macroBody.isFunction();

		final A_Bundle bundle = methodName.bundleOrCreate();
		final MessageSplitter splitter = bundle.messageSplitter();
		final int numArgs = splitter.numberOfArguments();
		if (macroBody.code().numArgs() != numArgs)
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		if (!macroBody.code().functionType().returnType().isSubtypeOf(
			ParseNodeKind.PARSE_NODE.mostGeneralType()))
		{
			throw new SignatureException(E_MACRO_MUST_RETURN_A_PARSE_NODE);
		}
		// Make it so we can safely hold onto these things in the VM.
		methodName.makeShared();
		macroBody.makeShared();
		// Add the macro definition.
		final A_Method method = bundle.bundleMethod();
		final AvailObject macroDefinition = newMacroDefinition(
			method, module, macroBody, prefixFunctions);
		module.moduleAddDefinition(macroDefinition);
		final A_Type macroBodyType = macroBody.kind();
		for (final A_Definition existingDefinition
			: method.macroDefinitionsTuple())
		{
			final A_Type existingType = existingDefinition.bodySignature();
			final boolean same = existingType.argsTupleType().equals(
				macroBodyType.argsTupleType());
			if (same)
			{
				throw new SignatureException(
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
			}
			// Note: Macro definitions don't have to satisfy a covariance
			// relationship with their result types, since they're static.
		}
		method.methodAddDefinition(macroDefinition);
		recordEffect(new LoadingEffectToAddDefinition(macroDefinition));
		final A_BundleTree root = rootBundleTree();
		module.lock(() ->
		{
			final A_DefinitionParsingPlan plan =
				bundle.definitionParsingPlans().mapAt(macroDefinition);
			final A_ParsingPlanInProgress planInProgress =
				newPlanInProgress(plan, 1);
			root.addPlanInProgress(planInProgress);
		});
	}

	/**
	 * Add a semantic restriction to its associated method.
	 *
	 * @param restriction
	 *        A {@linkplain SemanticRestrictionDescriptor semantic restriction}
	 *        that validates the static types of arguments at call sites.
	 *
	 * @throws SignatureException
	 *         If the signature is invalid.
	 */
	public void addSemanticRestriction (
		final A_SemanticRestriction restriction)
	throws SignatureException
	{
		final A_Method method = restriction.definitionMethod();
		final int numArgs = method.numArgs();
		if (restriction.function().code().numArgs() != numArgs)
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		runtime.addSemanticRestriction(restriction);
		recordEffect(
			new LoadingEffectToRunPrimitive(
				SpecialMethodAtom.SEMANTIC_RESTRICTION.bundle,
				method.chooseBundle(module).message(),
				restriction.function()));
		final A_Module theModule = module;
		theModule.lock(() -> theModule.moduleAddSemanticRestriction(restriction));
	}

	/**
	 * Add a seal to the method associated with the given method name.
	 *
	 * @param methodName
	 *        The method name, an {@linkplain AtomDescriptor atom}.
	 * @param seal
	 *        The signature at which to seal the method.
	 * @throws MalformedMessageException
	 *         If the macro signature is malformed.
	 * @throws SignatureException
	 *         If the macro signature is invalid.
	 */
	public void addSeal (
		final A_Atom methodName,
		final A_Tuple seal)
	throws MalformedMessageException, SignatureException
	{
		assert methodName.isAtom();
		assert seal.isTuple();
		final A_Bundle bundle = methodName.bundleOrCreate();
		final MessageSplitter splitter = bundle.messageSplitter();
		if (seal.tupleSize() != splitter.numberOfArguments())
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		methodName.makeShared();
		seal.makeShared();
		runtime.addSeal(methodName, seal);
		module.addSeal(methodName, seal);
		recordEffect(
			new LoadingEffectToRunPrimitive(
				SpecialMethodAtom.SEAL.bundle, methodName, seal));
	}

	/**
	 * The modularity scheme should prevent all intermodular method conflicts.
	 * Precedence is specified as an array of message sets that are not allowed
	 * to be messages generating the arguments of this message.  For example,
	 * &lt;&#123;'_+_'&#125; , &#123;'_+_' , '_*_'&#125;&gt; for the '_*_'
	 * operator makes * bind tighter than + and also groups multiple *'s
	 * left-to-right.
	 *
	 * Note that we don't have to prevent L2 code from running, since the
	 * grammatical restrictions only affect parsing.  We still have to latch
	 * access to the grammatical restrictions to avoid read/write conflicts.
	 *
	 * @param parentAtoms
	 *        A {@linkplain A_Set set} of {@linkplain AtomDescriptor atom}s that
	 *        name the message bundles that are to have their arguments
	 *        constrained.
	 * @param illegalArgMsgs
	 *        The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        SetDescriptor sets} of {@linkplain AtomDescriptor atoms} that name
	 *        methods.
	 * @throws MalformedMessageException
	 *         If one of the specified names is inappropriate as a method name.
	 * @throws SignatureException
	 *         If one of the specified names is inappropriate as a method name.
	 */
	public void addGrammaticalRestrictions (
		final A_Set parentAtoms,
		final A_Tuple illegalArgMsgs)
	throws MalformedMessageException, SignatureException
	{
		parentAtoms.makeShared();
		illegalArgMsgs.makeShared();
		final List<A_Set> bundleSetList =
			new ArrayList<>(illegalArgMsgs.tupleSize());
		for (final A_Set atomsSet : illegalArgMsgs)
		{
			A_Set bundleSet = emptySet();
			for (final A_Atom atom : atomsSet)
			{
				bundleSet = bundleSet.setWithElementCanDestroy(
					atom.bundleOrCreate(), true);
			}
			bundleSetList.add(bundleSet.makeShared());
		}
		final A_Tuple bundleSetTuple = tupleFromList(bundleSetList);
		for (final A_Atom parentAtom : parentAtoms)
		{
			final A_Bundle bundle = parentAtom.bundleOrCreate();
			final MessageSplitter splitter = bundle.messageSplitter();
			final int numArgs = splitter.numberOfLeafArguments();
			if (illegalArgMsgs.tupleSize() != numArgs)
			{
				throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
			}
			final A_GrammaticalRestriction grammaticalRestriction =
				newGrammaticalRestriction(bundleSetTuple, bundle, module);
			final A_BundleTree root = rootBundleTree();
			final A_Module theModule = module;
			theModule.lock(() ->
			{
				bundle.addGrammaticalRestriction(grammaticalRestriction);
				theModule.moduleAddGrammaticalRestriction(
					grammaticalRestriction);
				if (phase == EXECUTING_FOR_COMPILE)
				{
					// Update the message bundle tree to accommodate the new
					// grammatical restriction.
					final Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>>
						treesToVisit = new ArrayDeque<>();
					for (final Entry planEntry
						: bundle.definitionParsingPlans().mapIterable())
					{
						final A_DefinitionParsingPlan plan = planEntry.value();
						treesToVisit.addLast(
							new Pair<>(root, newPlanInProgress(plan, 1)));
						while (!treesToVisit.isEmpty())
						{
							final Pair<A_BundleTree, A_ParsingPlanInProgress>
								pair = treesToVisit.removeLast();
							final A_BundleTree tree = pair.first();
							final A_ParsingPlanInProgress planInProgress =
								pair.second();
							tree.updateForNewGrammaticalRestriction(
								planInProgress, treesToVisit);
						}
					}
				}
			});
		}
		recordEffect(
			new LoadingEffectToRunPrimitive(
				SpecialMethodAtom.GRAMMATICAL_RESTRICTION.bundle,
				parentAtoms,
				illegalArgMsgs));
	}

	/**
	 * Unbind the specified method definition from this loader and runtime.
	 *
	 * @param definition
	 *        A {@linkplain DefinitionDescriptor definition}.
	 */
	public void removeDefinition (final A_Definition definition)
	{
		if (definition.isForwardDefinition())
		{
			pendingForwards = pendingForwards.setWithoutElementCanDestroy(
				definition,
				true);
		}
		runtime.removeDefinition(definition);
	}

	/**
	 * Run the specified {@linkplain A_Tuple tuple} of {@linkplain A_Function
	 * functions} in parallel.
	 *
	 * @param unloadFunctions
	 *        A tuple of unload functions.
	 * @param afterRunning
	 *        What to do after every unload function has completed.
	 */
	public void runUnloadFunctions (
		final A_Tuple unloadFunctions,
		final Continuation0 afterRunning)
	{
		final int size = unloadFunctions.tupleSize();
		final MutableOrNull<Continuation0> onExit = new MutableOrNull<>();
		onExit.value = new Continuation0()
		{
			/** The index into the tuple of unload functions. */
			@InnerAccess int index = 1;

			@Override
			public void value ()
			{
				if (index <= size)
				{
					final A_Function unloadFunction =
						unloadFunctions.tupleAt(index);
					final A_Fiber fiber = newFiber(
						Types.TOP.o(),
						FiberDescriptor.loaderPriority,
						() -> formatString(
							"Unload function #%d for module %s",
							index,
							module().moduleName()));
					fiber.textInterface(textInterface);
					fiber.resultContinuation(
						unused ->
						{
							index++;
							onExit.value().value();
						});
					fiber.failureContinuation(
						unused ->
						{
							index++;
							onExit.value().value();
						});
					Interpreter.runOutermostFunction(
						runtime(),
						fiber,
						unloadFunction,
						Collections.emptyList());
				}
				else
				{
					afterRunning.value();
				}
			}
		};
		onExit.value().value();
	}

	/**
	 * Look up the given {@linkplain TupleDescriptor string} in the current
	 * {@linkplain ModuleDescriptor module}'s namespace. Answer the
	 * {@linkplain AtomDescriptor true name} associated with the string,
	 * creating the true name if necessary. A local true name always hides other
	 * true names.
	 *
	 * @param stringName
	 *        An Avail {@linkplain TupleDescriptor string}.
	 * @return A {@linkplain AtomDescriptor true name}.
	 * @throws AmbiguousNameException
	 *         If the string could represent several different true names.
	 */
	public A_Atom lookupName (
		final A_String stringName)
	throws AmbiguousNameException
	{
		return lookupName(stringName, false);
	}

	/**
	 * Look up the given {@linkplain TupleDescriptor string} in the current
	 * {@linkplain ModuleDescriptor module}'s namespace. Answer the
	 * {@linkplain AtomDescriptor true name} associated with the string,
	 * creating the true name if necessary. A local true name always hides other
	 * true names.  If #isExplicitSubclassAtom is true and we're creating a new
	 * atom, add the {@link SpecialAtom#EXPLICIT_SUBCLASSING_KEY} property.
	 *
	 * @param stringName
	 *        An Avail {@linkplain TupleDescriptor string}.
	 * @param isExplicitSubclassAtom
	 *        Whether to mark a new atom for creating an explicit subclass.
	 * @return A {@linkplain AtomDescriptor true name}.
	 * @throws AmbiguousNameException
	 *         If the string could represent several different true names.
	 */
	public A_Atom lookupName (
		final A_String stringName,
		final boolean isExplicitSubclassAtom)
	throws AmbiguousNameException
	{
		assert stringName.isString();
		//  Check if it's already defined somewhere...
		final MutableOrNull<A_Atom> atom = new MutableOrNull<>();
		module.lock(
			() ->
			{
				final A_Set who = module.trueNamesForStringName(stringName);
				if (who.setSize() == 0)
				{
					final A_Atom trueName = createAtom(stringName, module);
					if (isExplicitSubclassAtom)
					{
						trueName.setAtomProperty(
							EXPLICIT_SUBCLASSING_KEY.atom,
							EXPLICIT_SUBCLASSING_KEY.atom);
					}
					trueName.makeImmutable();
					module.addPrivateName(trueName);
					atom.value = trueName;
				}
				if (who.setSize() == 1)
				{
					atom.value = who.iterator().next();
				}
			});
		if (atom.value == null)
		{
			throw new AmbiguousNameException();
		}
		return atom.value();
	}

	/**
	 * Look up the given {@linkplain TupleDescriptor string} in the current
	 * {@linkplain ModuleDescriptor module}'s namespace. Answer every
	 * {@linkplain AtomDescriptor true name} associated with the string. Never
	 * create a true name.
	 *
	 * @param stringName
	 *        An Avail {@linkplain TupleDescriptor string}.
	 * @return Every {@linkplain AtomDescriptor true name} associated with the
	 *         name.
	 */
	public A_Set lookupAtomsForName (final A_String stringName)
	{
		assert stringName.isString();
		final MutableOrNull<A_Set> who = new MutableOrNull<>();
		module.lock(
			() ->
			{
				final A_Set newNames =
					module.newNames().hasKey(stringName)
						? emptySet().setWithElementCanDestroy(
						module.newNames().mapAt(stringName), true)
						: emptySet();
				final A_Set publics =
					module.importedNames().hasKey(stringName)
						? module.importedNames().mapAt(stringName)
						: emptySet();
				final A_Set privates =
					module.privateNames().hasKey(stringName)
						? module.privateNames().mapAt(stringName)
						: emptySet();
				who.value = newNames
					.setUnionCanDestroy(publics, true)
					.setUnionCanDestroy(privates, true);
			});
		return who.value();
	}

	/**
	 * The {@link A_BundleTree} used <em>only</em> for parsing module
	 * headers.
	 */
	private static final A_BundleTree moduleHeaderBundleRoot;

	/**
	 * The {@link LexicalScanner} used only</em> for parsing module
	 * headers.
	 */
	private static final LexicalScanner moduleHeaderLexicalScanner;

	/*
	 * Create the special {@link A_BundleTree} used for parsing module headers.
	 */
	static
	{
		// Define a special root bundle tree that's only capable of parsing
		// method headers.
		moduleHeaderBundleRoot = newBundleTree(nil);

		// Also define the LexicalScanner used for module headers.
		moduleHeaderLexicalScanner = new LexicalScanner();

		// Add the string literal lexer.
		createPrimitiveLexerForHeaderParsing(
			P_BootstrapLexerStringFilter.instance,
			P_BootstrapLexerStringBody.instance,
			"string token lexer");

		// The module header uses keywords, e.g. "Extends".
		createPrimitiveLexerForHeaderParsing(
			P_BootstrapLexerKeywordFilter.instance,
			P_BootstrapLexerKeywordBody.instance,
			"keyword token lexer");

		// There's also punctuation in there, like commas.
		createPrimitiveLexerForHeaderParsing(
			P_BootstrapLexerOperatorFilter.instance,
			P_BootstrapLexerOperatorBody.instance,
			"operator token lexer");

		// It would be tricky with no whitespace!
		createPrimitiveLexerForHeaderParsing(
			P_BootstrapLexerWhitespaceFilter.instance,
			P_BootstrapLexerWhitespaceBody.instance,
			"whitespace lexer");

		// Slash-star-star-slash comments are legal in the header.
		createPrimitiveLexerForHeaderParsing(
			P_BootstrapLexerSlashStarCommentFilter.instance,
			P_BootstrapLexerSlashStarCommentBody.instance,
			"comment lexer");

		moduleHeaderLexicalScanner.freezeFromChanges();

		// Now add the method that allows the header to be parsed.
		final A_Atom headerMethodName =
			SpecialMethodAtom.MODULE_HEADER_METHOD.atom;
		final A_Bundle headerMethodBundle;
		try
		{
			headerMethodBundle = headerMethodName.bundleOrCreate();
		}
		catch (final MalformedMessageException e)
		{
			assert false : "Malformed module header method name";
			throw new RuntimeException(e);
		}
		final A_DefinitionParsingPlan headerPlan =
			headerMethodBundle.definitionParsingPlans().mapIterable().next()
				.value();
		final A_ParsingPlanInProgress headerPlanInProgress =
			newPlanInProgress(headerPlan, 1);
		moduleHeaderBundleRoot.addPlanInProgress(headerPlanInProgress);
	}

	/**
	 * Create an {@link A_Lexer} from the given filter and body primitives, and
	 * install it in specified atom's bundle.  Add the lexer to the root {@link
	 * A_BundleTree} that is used for parsing module headers.
	 *
	 * @param filterPrimitive
	 *        A primitive for filtering the lexer by its first character.
	 * @param bodyPrimitive
	 *        A primitive for constructing a tuple of tokens at the current
	 *        position.  Typically the tuple has zero or one tokens, but more
	 *        can be produced to indicate ambiguity within the lexer.
	 * @param atomName
	 *        The {@link A_Atom} under which to record the new lexer.
	 */
	private static void createPrimitiveLexerForHeaderParsing (
		final Primitive filterPrimitive,
		final Primitive bodyPrimitive,
		final String atomName)
	{
		final A_Function stringLexerFilter =
			newPrimitiveFunction(filterPrimitive, nil, 0);
		final A_Function stringLexerBody =
			newPrimitiveFunction(bodyPrimitive, nil, 0);
		final A_Atom atom = createSpecialAtom(atomName);
		final A_Bundle bundle;
		try
		{
			bundle = atom.bundleOrCreate();
		}
		catch (final MalformedMessageException e)
		{
			assert false : "Invalid special lexer name: " + atomName;
			throw new RuntimeException(e);
		}
		final A_Method method = bundle.bundleMethod();
		final A_Lexer lexer = newLexer(
			stringLexerFilter, stringLexerBody, method, nil);
		moduleHeaderLexicalScanner.addLexer(lexer);
	}
}

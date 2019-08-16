/*
 * Primitive.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.FunctionTypeDescriptor;
import com.avail.descriptor.IntegerEnumSlotDescriptionEnum;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.TypeDescriptor;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE;
import com.avail.interpreter.primitive.privatehelpers.P_PushConstant;
import com.avail.optimizer.ExecutableChunk;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.optimizer.values.L2SemanticPrimitiveInvocation;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.serialization.Serializer;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * This abstraction represents the interface between Avail's Level One
 * nybblecode interpreter and the underlying interfaces of the built-in objects,
 * providing functionality that is (generally) inexpressible within Level One in
 * terms of other Level One operations.  A conforming Avail implementation must
 * provide these primitives with equivalent semantics and names.
 *
 * <p>The subclasses must define {@link #attempt(Interpreter)}, which expects
 * its arguments to be accessed via {@link Interpreter#argument(int)}.  Each
 * subclass operates on its arguments to produce a side-effect and/or produce a
 * result.  The primitive's {@link Flag}s indicate any special preparations that
 * must be made before the invocation, such as reifying the Java stack.</p>
 *
 * <p>Primitives may succeed or fail, or cause some other action like non-local
 * control flow.  This is handled via {@link
 * Interpreter#primitiveSuccess(A_BasicObject)} and {@link
 * Interpreter#primitiveFailure(A_BasicObject)} and similar methods.  If a
 * primitive fails, the statements in the containing function will be invoked,
 * as though the primitive had never been attempted.</p>
 *
 * <p>In addition, the {@code Primitive} subclasses collaborate with the {@link
 * L1Translator} and {@link L2Generator} to produce appropriate {@link
 * L2Instruction}s and ultimately JVM bytecode instructions within a calling
 * {@link ExecutableChunk}.  Again, the {@link Flag}s and some {@code Primitive}
 * methods indicate general properties of the primitive, like whether it can be
 * applied ahead of time ({@link Flag#CanFold}) to constant arguments, whether
 * it could fail, given particular types of arguments, and what type it
 * guarantees to provide, given particular argument types.</p>
 *
 * <p>The main hook for primitive-specific optimization is {@link
 * #tryToGenerateSpecialPrimitiveInvocation(L2ReadBoxedOperand, A_RawFunction,
 * List, List, L1Translator, CallSiteHelper)}.  Because of the way the L2
 * translation makes use of {@link L2SemanticValue}s, and {@link
 * L2SemanticPrimitiveInvocation}s in particular, a primitive can effectively
 * examine the history of its arguments and compose or cancel a chain of actions
 * in the L2 code.  For example, a primitive that extracts an element of a tuple
 * might notice that the tuple was created by a tuple-building primitive, and
 * then choose to directly use one of the inputs to the tuple-building
 * primitive, rather than decompose the tuple. If all such uses of the tuple
 * disappear, the invocation of the tuple-building primitive can be elided
 * entirely, since it has no side-effects.  Arithmetic provides similar rich
 * opportunities for these high-level optimizations.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class Primitive
implements IntegerEnumSlotDescriptionEnum
{
	/**
	 * The success state of a primitive attempt.
	 */
	public enum Result
	{
		/**
		 * The primitive succeeded, and the result, if any, has been stored for
		 * subsequent use in the {@link Interpreter#latestResult()}.
		 */
		@ReferencedInGeneratedCode
		SUCCESS,

		/**
		 * The primitive failed.  The backup Avail code should be executed
		 * instead.
		 */
		FAILURE,

		/**
		 * The continuation was replaced as a consequence of the primitive.
		 * This is a specific form of success, but no result can be produced due
		 * to the fact that the new continuation does not have a place to write
		 * it.
		 */
		CONTINUATION_CHANGED,

		/**
		 * A primitive with {@link Flag#CanInline} and {@link Flag#Invokes} has
		 * set up the {@link Interpreter#function} and {@link
		 * Interpreter#argsBuffer} for a call, but has not called it because
		 * that's not permitted from within a {@code Primitive}.
		 */
		READY_TO_INVOKE,

		/**
		 * The current fiber has been suspended as a consequence of this
		 * primitive executing, so the {@linkplain Interpreter interpreter}
		 * should switch processes now.
		 */
		FIBER_SUSPENDED
	}

	/**
	 * These flags are used by the execution machinery and optimizer to indicate
	 * the potential mischief that the corresponding primitives may get into.
	 */
	public enum Flag
	{
		/**
		 * The primitive can be attempted by the {@code L2Generator} at
		 * re-optimization time if the arguments are known constants. The result
		 * should be stable, such that invoking the primitive again with the
		 * same arguments should produce the same value. The primitive should
		 * not have side-effects.
		 */
		CanFold,

		/**
		 * The invocation of the primitive can be safely inlined. In particular,
		 * it simply computes a value or changes the state of something and does
		 * not replace the current continuation in unusual ways. Thus, something
		 * more specific than a general invocation can be embedded in the
		 * calling {@link L2Chunk}.  Since code for potential reification is
		 * still needed in the failure case, this flag is less useful than it
		 * used to be when a continuation had to be reified on <em>every</em>
		 * non-primitive call.
		 */
		CanInline,

		/**
		 * A primitive must have this flag if it might suspend the current
		 * fiber.  The L2 invocation machinery ensures the Java stack has been
		 * reified into a continuation chain <em>prior</em> to invoking the
		 * primitive.
		 */
		CanSuspend,

		/**
		 * The primitive has a side-effect, such as writing to a file, modifying
		 * a variable, or defining a new method.
		 */
		HasSideEffect,

		/**
		 * The primitive can invoke a function. If the function is a
		 * non-primitive (or a primitive that fails), the current continuation
		 * must be reified before the call.
		 */
		Invokes,

		/**
		 * The primitive can replace the current continuation, and care should
		 * be taken to ensure the current continuation is fully reified prior to
		 * attempting this primitive. Note that the primitive is not obligated
		 * to switch continuations.
		 */
		CanSwitchContinuations,

		/**
		 * The primitive is guaranteed to replace the current continuation, and
		 * care should be taken to ensure that the current continuation is fully
		 * reified prior to attempting this primitive.
		 */
		AlwaysSwitchesContinuation,

		/**
		 * The raw function has a particular form that qualifies it as a special
		 * primitive, such as immediately returning a constant or argument.  The
		 * raw function won't be displayed as a primitive, but it will execute
		 * and be inlineable as one.
		 */
		SpecialForm,

		/**
		 * The primitive cannot fail. Hence, there is no need for Avail code
		 * to run in the event of a primitive failure. Hence, such code is
		 * forbidden (because it would be unreachable).
		 */
		CannotFail,

		/**
		 * The primitive is not exposed to an Avail program. The compiler
		 * forbids direct compilation of primitive linkages to such primitives.
		 * {@link A_RawFunction}-creating primitives also forbid creation of
		 * code that links a {@code Private} primitive.
		 */
		Private,

		/**
		 * This is a bootstrap primitive. It must be made available to the
		 * origin module of an Avail system via a special pragma.
		 */
		Bootstrap,

		/**
		 * The primitive is the special exception catching primitive. Its sole
		 * purpose is to fail, causing an actual continuation to be built. The
		 * exception raising mechanism searches for such a continuation to find
		 * a suitable handler function.
		 */
		CatchException,

		/**
		 * The primitive failure variable should not be cleared after its last
		 * usage.
		 */
		PreserveFailureVariable,

		/**
		 * The primitive arguments should not be cleared after their last
		 * usages.
		 */
		PreserveArguments,

		/**
		 * The semantics of the primitive fall outside the usual capacity of the
		 * {@link L2Generator}. The current continuation should be reified prior
		 * to attempting the primitive. Do not attempt to fold or inline this
		 * primitive.
		 */
		Unknown
	}

	/**
	 * The actual fallibility of a fallible {@linkplain Primitive} when invoked
	 * with arguments of specific {@linkplain TypeDescriptor types}.
	 */
	public enum Fallibility
	{
		/**
		 * The fallible {@linkplain Primitive primitive} cannot fail when
		 * invoked with arguments of the specified {@linkplain TypeDescriptor
		 * types}.
		 */
		CallSiteCannotFail,

		/**
		 * The fallible {@linkplain Primitive primitive} can indeed fail when
		 * invoked with arguments of the specified {@linkplain TypeDescriptor
		 * types}.
		 */
		CallSiteCanFail,

		/**
		 * The fallible {@linkplain Primitive primitive} must fail when invoked
		 * with arguments of the specified {@linkplain TypeDescriptor types}.
		 */
		CallSiteMustFail
	}

	/**
	 * Attempt this primitive with the given {@link Interpreter}.  The
	 * interpreter's {@linkplain Interpreter#argsBuffer's argument list} must be
	 * set up prior to this call.  If the primitive fails, it should set the
	 * primitive failure code by calling {@link Interpreter#primitiveFailure(
	 * A_BasicObject)} and returning its result from the primitive.  Otherwise
	 * it should set the interpreter's primitive result by calling {@link
	 * Interpreter#primitiveSuccess(A_BasicObject)} and then return its result
	 * from the primitive.  For unusual primitives that replace the current
	 * continuation, {@link Result#CONTINUATION_CHANGED} is more appropriate,
	 * and the latestResult need not be set.  For primitives that need to cause
	 * a context switch, {@link Result#FIBER_SUSPENDED} should be returned.
	 *
	 * @param interpreter
	 *        The {@link Interpreter} that is executing.
	 * @return The {@link Result} code indicating success or failure (or special
	 *         circumstance).
	 */
	public abstract Result attempt (Interpreter interpreter);

	/**
	 * Return a function type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this function type's argument types, and the actual
	 * block's return type must be at least as general as this function type's
	 * return type.  That's equivalent to the condition that the actual block's
	 * type is a subtype of this function type.
	 *
	 * @return
	 *             A function type that restricts the type of a block that uses
	 *             this primitive.
	 */
	protected abstract A_Type privateBlockTypeRestriction ();

	/**
	 * A {@linkplain FunctionTypeDescriptor function type} that restricts the
	 * type of block that can use this primitive.  This is initialized lazily to
	 * the value provided by {@link #privateBlockTypeRestriction()}, to avoid
	 * having to compute this function type multiple times.
	 */
	private @Nullable A_Type cachedBlockTypeRestriction;

	/**
	 * Return a function type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this function type's argument types, and the actual
	 * block's return type must be at least as general as this function type's
	 * return type.  That's equivalent to the condition that the actual block's
	 * type is a subtype of this function type.
	 *
	 * <p>Cache the value in this {@code Primitive} so subsequent requests are
	 * fast.</p>
	 *
	 * @return
	 *            A function type that restricts the type of a block that uses
	 *            this primitive.
	 */
	public final A_Type blockTypeRestriction ()
	{
		@Nullable A_Type restriction = cachedBlockTypeRestriction;
		if (cachedBlockTypeRestriction == null)
		{
			restriction = privateBlockTypeRestriction().makeShared();
			cachedBlockTypeRestriction = restriction;
			final A_Type argsTupleType = restriction.argsTupleType();
			final A_Type sizeRange = argsTupleType.sizeRange();
			assert restriction.isBottom()
				|| argCount() == -1
				|| (sizeRange.lowerBound().extractInt() == argCount()
					&& sizeRange.upperBound().extractInt() == argCount());
		}
		return restriction;
	}

	/**
	 * Answer the type of the result that will be produced by a call site with
	 * the given argument types.  Don't include semantic restrictions defined
	 * in the Avail code, but if convenient answer something stronger than the
	 * return type in the primitive's basic function type.
	 *
	 * @param rawFunction
	 *        The {@link A_RawFunction} being invoked.
	 * @param argumentTypes
	 *        A {@link List} of argument {@linkplain TypeDescriptor types}.
	 * @return
	 *         The return type guaranteed by the VM at some call site.
	 */
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		assert rawFunction.primitive() == this;
		return blockTypeRestriction().returnType();
	}

	/**
	 * Return an Avail {@linkplain TypeDescriptor type} that a failure variable
	 * must accept in order to be compliant with this primitive.  A more general
	 * type is acceptable for the variable.  This type is cached upon first
	 * request and should be accessed via {@link #failureVariableType()}.
	 *
	 * <p>
	 * By default, expect the primitive to fail with a natural number.
	 * </p>
	 *
	 * @return
	 *         A type which is at least as specific as the type of the failure
	 *         variable declared in a block using this primitive.
	 */
	protected A_Type privateFailureVariableType ()
	{
		return naturalNumbers();
	}

	/**
	 * A {@linkplain TypeDescriptor type} to constrain the {@linkplain
	 * AvailObject#writeType() content type} of the variable declaration within
	 * the primitive declaration of a block.  The actual variable's inner type
	 * must be this or a supertype.
	 */
	@SuppressWarnings({
		"OverridableMethodCallDuringObjectConstruction",
		"OverriddenMethodCallDuringObjectConstruction"
	})
	private final A_Type failureVariableType =
		privateFailureVariableType().makeShared();

	/**
	 * Return an Avail {@linkplain TypeDescriptor type} that a failure variable
	 * must accept in order to be compliant with this primitive.  A more general
	 * type is acceptable for the variable.  The type is cached for performance.
	 *
	 * @return A type which is at least as specific as the type of the failure
	 *         variable declared in a block using this primitive.
	 */
	public final A_Type failureVariableType ()
	{
		return failureVariableType;
	}

	/**
	 * Answer the {@linkplain Fallibility fallibility} of the {@linkplain
	 * Primitive primitive} for a call site with the given argument {@linkplain
	 * TypeDescriptor types}.
	 *
	 * @param argumentTypes
	 *        A {@linkplain List list} of argument types.
	 * @return The fallibility of the call site.
	 */
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		return hasFlag(CannotFail)
			? CallSiteCannotFail
			: CallSiteCanFail;
	}

	/**
	 * This primitive's number.  The Avail source code refers to primitives by
	 * name, but it has a number associated with it by its position in the list
	 * within All_Primitives.txt.  {@link CompiledCodeDescriptor compiled code}
	 * stores the primitive number internally for speed.
	 */
	public int primitiveNumber;

	/**
	 * The number of arguments this primitive expects.  For {@link
	 * P_PushConstant} this is -1, but that primitive cannot be used
	 * explicitly in Avail code – it's plugged in automatically for functions
	 * that immediately return a constant.
	 */
	private int argCount;

	/**
	 * The flags that indicate to the {@link L2Generator} how an invocation of
	 * this primitive should be handled.
	 */
	private final EnumSet<Flag> primitiveFlags = EnumSet.noneOf(Flag.class);

	/**
	 * Test whether the specified {@link Flag} is set for this primitive.
	 *
	 * @param flag The {@code Flag} to test.
	 * @return Whether that {@code Flag} is set for this primitive.
	 */
	public final boolean hasFlag (final Flag flag)
	{
		return primitiveFlags.contains(flag);
	}

	/**
	 * The number of arguments this primitive expects.
	 *
	 * @return The count of arguments for this primitive.
	 */
	public final int argCount()
	{
		return argCount;
	}

	/** A helper class to assist with lazy loading of {@link Primitive}s. */
	private static final class PrimitiveHolder
	{
		/** The name by which a primitive function is declared in Avail code. */
		@InnerAccess final String name;

		/** The full name of the Java class implementing the primitive. */
		@InnerAccess final String className;

		/**
		 * The numeric index of the primitive in the {@link #holdersByNumber}
		 * array.  This ordering may be arbitrary, specific to the currently
		 * running Java VM instance.  The number should never be included in any
		 * serialization of primitive functions.
		 */
		@InnerAccess final int number;

		/**
		 * The sole instance of the specific subclass of {@link Primitive}.  It
		 * is initialized only when needed for the first time, since that causes
		 * Java class loading to happen, and we'd rather smear out that startup
		 * performance cost.
		 */
		@InnerAccess @Nullable volatile Primitive primitive = null;

		/**
		 * Get the {@link Primitive} from this {@code PrimitiveHolder}.  Load
		 * the appropriate class if necessary.
		 *
		 * @return The singleton instance of the requested subclass of {@link
		 *         Primitive}
		 */
		public Primitive primitive ()
		{
			final @Nullable Primitive p = primitive;
			if (p != null)
			{
				return p;
			}
			return fetchPrimitive();
		}

		/**
		 * While holding the monitor, double-check whether another thread has
		 * loaded the primitive, loading it if not.
		 *
		 * @return The loaded primitive.
		 */
		private synchronized Primitive fetchPrimitive ()
		{
			// The double-check pattern.  Make sure there's a write barrier
			// *before* and *after* writing the fully initialized primitive
			// into its slot.
			if (primitive == null)
			{
				final ClassLoader loader = Primitive.class.getClassLoader();
				try
				{
					final Class<?> primClass =
						loader.loadClass(className);
					// Trigger the linker.
					final Object instance =
						primClass.getField("instance").get(null);
					assert instance != null
						: "PrimitiveHolder.primitive field should have "
						+ "been set during class loading";
				}
				catch (
					final ClassNotFoundException
						| NoSuchFieldException
						| IllegalAccessException e)
				{
					throw new RuntimeException(e);
				}
			}
			return stripNull(primitive);
		}

		/**
		 * Construct a new {@code PrimitiveHolder}.
		 *
		 * @param name The primitive's textual name.
		 * @param className The fully qualified name of the Primitive subclass.
		 * @param number The primitive's assigned index in the
		 */
		@InnerAccess PrimitiveHolder (
			final String name,
			final String className,
			final int number)
		{
			this.name = name;
			this.className = className;
			this.number = number;
		}
	}

	/** A map of all {@link PrimitiveHolder}s, by name. */
	private static final Map<String, PrimitiveHolder> holdersByName;

	/** A map of all {@link PrimitiveHolder}s, by class name. */
	private static final Map<String, PrimitiveHolder> holdersByClassName;

	/**
	 * An array of all {@link Primitive}s encountered so far, indexed by
	 * primitive number.  Note that the primitive numbers may get assigned
	 * differently on different runs, so the {@link Serializer} always uses the
	 * primitive's textual name.
	 *
	 * <p>If this array is insufficient to hold all the primitives, it can be
	 * replaced with a larger one as needed.  However, be careful of the lack of
	 * write barrier for array elements.  Reads should always be safe, as they
	 * couldn't be caching stale data from the replacement array, because they
	 * can't see it until after it has been populated at least to the extent
	 * that the previous array was.  Writing new elements to the array has to be
	 * protected </p>
	 */
	private static final PrimitiveHolder[] holdersByNumber;

	/**
	 * The {@link Statistic} for abandoning the stack due to a primitive attempt
	 * answering {@link Result#CONTINUATION_CHANGED}.
	 */
	private @Nullable Statistic reificationAbandonmentStat;

	/**
	 * Answer the {@link Statistic} for abandoning the stack due to a primitive
	 * attempt answering {@link Result#CONTINUATION_CHANGED}.  The primitive
	 * must have {@link Flag#CanSwitchContinuations} set.
	 *
	 * @return The {@link Statistic}.
	 */
	public Statistic reificationAbandonmentStat()
	{
//		assert hasFlag(CanSwitchContinuations);
		return stripNull(reificationAbandonmentStat);
	}

	/**
	 * The {@link Statistic} for reification prior to invoking a primitive that
	 * <em>does not</em> have {@link Flag#CanInline} set.
	 */
	private @Nullable Statistic reificationForNoninlineStat;

	/**
	 * Answer the {@link Statistic} for reification prior to invoking a
	 * primitive that <em>does not</em> have {@link Flag#CanInline} set.
	 *
	 * @return The {@link Statistic}.
	 */
	public Statistic reificationForNoninlineStat()
	{
//		assert !hasFlag(CanInline);
		return stripNull(reificationForNoninlineStat);
	}

	/**
	 * The name of a generated file which lists all primitive classes.  The file
	 * is generated by the build process and is included in build products as
	 * necessary.
	 */
	private static final String allPrimitivesFileName = "All_Primitives.txt";

	/**
	 * The pattern of the simple names of {@link Primitive} classes.
	 */
	private static final Pattern primitiveNamePattern =
		Pattern.compile("P_(\\w+)");

	/*
	 * Read from allPrimitivesFileName to get a complete manifest of accessible
	 * primitives.  Don't actually load the primitives yet.
	 */
	static
	{
		final List<PrimitiveHolder> byNumbers = new ArrayList<>();
		byNumbers.add(null);  // Entry zero is reserved for not-a-primitive.
		int counter = 1;
		final Map<String, PrimitiveHolder> byNames = new HashMap<>();
		final Map<String, PrimitiveHolder> byClassNames = new HashMap<>();
		try
		{
			final URL resource =
				Primitive.class.getResource(allPrimitivesFileName);
			try (final BufferedReader input = new BufferedReader(
				new InputStreamReader(resource.openStream(), UTF_8)))
			{
				while (true)
				{
					final @Nullable String className = input.readLine();
					if (className == null)
					{
						break;
					}
					final String[] parts = className.split("\\.");
					final String lastPart = parts[parts.length - 1];
					final Matcher matcher =
						primitiveNamePattern.matcher(lastPart);
					if (matcher.matches())
					{
						final String name = matcher.group(1);
						assert !byNames.containsKey(name);
						final PrimitiveHolder holder = new PrimitiveHolder(
							name, className, counter);
						byNames.put(name, holder);
						byClassNames.put(className, holder);
						byNumbers.add(holder);
						counter++;
					}
				}
			}
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
		holdersByName = byNames;
		holdersByClassName = byClassNames;
		holdersByNumber = byNumbers.toArray(new PrimitiveHolder[counter]);
	}

	/**
	 * Initialize a newly constructed {@code Primitive}.  The first argument is
	 * a primitive number, the second is the number of arguments with which the
	 * primitive expects to be invoked, and the remaining arguments are
	 * {@linkplain Flag flags}.
	 *
	 * <p>Note that it's essential that this method, invoked during static
	 * initialization of each Primitive subclass, install this new instance into
	 * this primitive's {@link PrimitiveHolder} in {@link #holdersByClassName}.
	 *
	 * @param theArgCount
	 *        The number of arguments the primitive expects.  The value -1 is
	 *        used by the special primitive {@link P_PushConstant} to
	 *        indicate it may have any number of arguments.  However, note that
	 *        that primitive cannot be used explicitly in Avail code.
	 * @param flags
	 *        The flags that describe how the {@link L2Generator} should deal
	 *        with this primitive.
	 * @return The initialized primitive.
	 */
	protected Primitive init (
		final int theArgCount,
		final Flag... flags)
	{
		final PrimitiveHolder holder =
			holdersByClassName.get(getClass().getName());
		primitiveNumber = holder.number;
		name = holder.name;
		argCount = theArgCount;
		assert primitiveFlags.isEmpty();
		for (final Flag flag : flags)
		{
			assert !primitiveFlags.contains(flag)
				: "Duplicate flag in " + getClass().getSimpleName();
			primitiveFlags.add(flag);
		}
		// Sanity check certain conditions.
		assert !primitiveFlags.contains(CanFold)
				|| primitiveFlags.contains(CanInline)
			: "Primitive " + getClass().getSimpleName()
				+ " has CanFold without CanInline";
		assert !primitiveFlags.contains(Invokes)
				|| primitiveFlags.contains(CanInline)
			: "Primitive " + getClass().getSimpleName()
				+ " has Invokes without CanInline";
		// Register this instance.
		assert holder.primitive == null;
		holder.primitive = this;
		runningNanos = new Statistic(
			(hasFlag(CanInline) ? "" : "[NOT INLINE]")
				+ getClass().getSimpleName()
				+ " (running)",
			StatisticReport.PRIMITIVES);
		if (hasFlag(CanSwitchContinuations))
		{
			reificationAbandonmentStat = new Statistic(
				"Abandoned for CONTINUATION_CHANGED from " + name(),
				StatisticReport.REIFICATIONS);
		}
		if (!hasFlag(CanInline))
		{
			reificationForNoninlineStat = new Statistic(
				"Reification for non-inline " + name(),
				StatisticReport.REIFICATIONS);
		}
		return this;
	}

	/**
	 * Answer the largest primitive number.
	 *
	 * @return The largest primitive number.
	 */
	public static int maxPrimitiveNumber ()
	{
		return holdersByNumber.length - 1;
	}

	/**
	 * Given a primitive name, look it up and answer the {@code Primitive} if
	 * found, or {@code null} if not found.
	 *
	 * @param name The primitive name to look up.
	 * @return The primitive, or null if the name is not a valid primitive.
	 */
	public static @Nullable Primitive primitiveByName (final String name)
	{
		final PrimitiveHolder holder =  holdersByName.get(name);
		return holder == null ? null : holder.primitive();
	}

	/**
	 * Given a primitive number, look it up and answer the {@code Primitive} if
	 * found, or {@code null} if not found.
	 *
	 * @param primitiveNumber The primitive number to look up.
	 * @return The primitive, or null if the name is not a valid primitive.
	 */
	public static @Nullable Primitive byNumber (final int primitiveNumber)
	{
		assert primitiveNumber >= 0 && primitiveNumber <= maxPrimitiveNumber();
		final PrimitiveHolder holder =  holdersByNumber[primitiveNumber];
		return holder == null ? null : holder.primitive();
	}

	/**
	 * Locate the primitive that has the specified primitive number.
	 *
	 * @param primitiveNumber The primitive number for which to search.
	 * @return The primitive with the specified primitive number.
	 */
	public static @Nullable Primitive byPrimitiveNumberOrNull (
		final int primitiveNumber)
	{
		if (primitiveNumber == 0)
		{
			return null;
		}
		return holdersByNumber[primitiveNumber].primitive();
	}

	/**
	 * Answer whether a raw function using this primitive can/should have
	 * nybblecode instructions.
	 *
	 * @return Whether this primitive has failure/alternative code.
	 */
	public boolean canHaveNybblecodes ()
	{
		return !hasFlag(CannotFail) || hasFlag(SpecialForm);
	}

	/**
	 * Determine whether the specified primitive declaration is acceptable to be
	 * used with the given list of parameter declarations.  Answer null if they
	 * are acceptable, otherwise answer a suitable {@code String} that is
	 * expected to appear after the prefix "Expecting...".
	 *
	 * @param primitiveNumber Which primitive.
	 * @param arguments The argument declarations that we should check are legal
	 *                  for this primitive.
	 * @return Whether the primitive accepts arguments with types that conform
	 *         to the given argument declarations.
	 */
	public static @Nullable String validatePrimitiveAcceptsArguments (
		final int primitiveNumber,
		final List<A_Phrase> arguments)
	{
		final Primitive primitive =
			stripNull(byPrimitiveNumberOrNull(primitiveNumber));
		final int expected = primitive.argCount();
		if (expected == -1)
		{
			return null;
		}
		if (arguments.size() != expected)
		{
			return format(
				"number of declared arguments (%d) to agree with primitive's"
					+ " required number of arguments (%d).",
				arguments.size(),
				expected);
		}
		final A_Type expectedTypes =
			primitive.blockTypeRestriction().argsTupleType();
		assert expectedTypes.sizeRange().upperBound().extractInt() == expected;
		final StringBuilder builder = new StringBuilder();
		for (int i = 1; i <= expected; i++)
		{
			final A_Type declaredType = arguments.get(i - 1).declaredType();
			final A_Type expectedType = expectedTypes.typeAtIndex(i);
			if (!declaredType.isSubtypeOf(expectedType))
			{
				if (builder.length() > 0)
				{
					builder.append("\n");
				}
				builder.append(
					format(
						"argument #%d (%s) of primitive %s to be a subtype"
							+ " of %s, not %s.",
						i,
						arguments.get(i - 1).token().string(),
						primitive.name(),
						expectedType,
						declaredType));
			}
		}
		if (builder.length() == 0)
		{
			return null;
		}
		return builder.toString();
	}

	/**
	 * Generate suitable primitive failure code on the given {@link
	 * L1InstructionWriter}. Some primitives may have special requirements, but
	 * most (fallible) primitives follow the same pattern.
	 *
	 * @param lineNumber
	 *        The line number at which to consider these
	 * @param writer
	 *        Where to write the failure code.
	 * @param numArgs
	 *        The number of arguments that the function will accept.
	 */
	public void writeDefaultFailureCode (
		final int lineNumber,
		final L1InstructionWriter writer,
		final int numArgs)
	{
		if (!hasFlag(CannotFail))
		{
			// Produce failure code.  First declare the local that holds
			// primitive failure information.
			final int failureLocal = writer.createLocal(
				variableTypeFor(failureVariableType()));
			for (int i = 1; i <= numArgs; i++)
			{
				writer.write(lineNumber, L1Operation.L1_doPushLastLocal, i);
			}
			// Get the failure code.
			writer.write(lineNumber, L1Operation.L1_doGetLocal, failureLocal);
			// Put the arguments and failure code into a tuple.
			writer.write(lineNumber, L1Operation.L1_doMakeTuple, numArgs + 1);
			writer.write(
				lineNumber,
				L1Operation.L1_doCall,
				writer.addLiteral(SpecialMethodAtom.CRASH.bundle),
				writer.addLiteral(bottom()));
		}
	}

	/** Capture the name of the primitive class once for performance. */
	private String name = "";

	/**
	 * Answer the name of this primitive, which is just the class's simple name,
	 * as previously captured by the {@link #name} field during {@link #init(
	 * int, Flag...)}.
	 *
	 * @return The name of this primitive.
	 */
	@Override
	public String name ()
	{
		return name;
	}

	/**
	 * Be compliant with {@link IntegerEnumSlotDescriptionEnum} – although it
	 * shouldn't really be used, since we want to index by primitive number
	 * instead of ordinal.
	 *
	 * @return The ordinal of this primitive, in theory.
	 */
	@Deprecated
	@Override
	public int ordinal ()
	{
		throw new UnsupportedOperationException(
			"Primitive ordinal() should not be used.");
	}

	/**
	 * A performance metric indicating how long was spent executing each
	 * primitive.
	 */
	private @Nullable Statistic runningNanos = null;

	/**
	 * Record that some number of nanoseconds were just expended running this
	 * primitive.
	 *
	 * @param deltaNanoseconds The sample to add, in nanoseconds.
	 * @param interpreterIndex The contention bin in which to add the sample.
	 */
	void addNanosecondsRunning (
		final long deltaNanoseconds,
		final int interpreterIndex)
	{
		stripNull(runningNanos).record(deltaNanoseconds, interpreterIndex);
	}

	/**
	 * A performance metric indicating how long was spent checking the return
	 * result for all invocations of this primitive in level two code.  An
	 * excessively large value indicates a profitable opportunity for {@link
	 * #returnTypeGuaranteedByVM(A_RawFunction, List)} to return a stronger
	 * type, perhaps allowing the level two optimizer to skip more checks.
	 */
	private final Statistic resultTypeCheckingNanos = new Statistic(
		getClass().getSimpleName() + " (checking result)",
		StatisticReport.PRIMITIVE_RETURNER_TYPE_CHECKS);

	/**
	 * Record that some number of nanoseconds were just expended checking the
	 * type of the value returned by this primitive.
	 *
	 * @param deltaNanoseconds
	 *        The amount of time just spent checking the result type.
	 * @param interpreterIndex
	 *        The interpreterIndex of the current thread's interpreter.
	 */
	public void addNanosecondsCheckingResultType (
		final long deltaNanoseconds,
		final int interpreterIndex)
	{
		resultTypeCheckingNanos.record(deltaNanoseconds, interpreterIndex);
	}

	/**
	 * The primitive couldn't be folded out, so see if alternative instructions
	 * can be generated for its invocation.  If so, answer {@code true}, ensure
	 * control flow will go to the appropriate {@link CallSiteHelper} exit
	 * point, and leave the translator NOT at a currentReachable() point.  If
	 * the alternative instructions could not be generated for this primitive,
	 * answer {@code false}, and generate nothing.
	 *
	 * @param functionToCallReg
	 *        The {@link L2ReadBoxedOperand} register that holds the function
	 *        being invoked.  The function's primitive is known to be the
	 *        receiver.
	 * @param rawFunction
	 *        The primitive raw function whose invocation is being generated.
	 * @param arguments
	 *        The argument {@link L2ReadBoxedOperand}s supplied to the
	 *        function.
	 * @param argumentTypes
	 *        The list of {@link A_Type}s of the arguments.
	 * @param translator
	 *        The {@link L1Translator} on which to emit code, if possible.
	 * @param callSiteHelper
	 *        Information about the call site being generated.
	 * @return {@code true} if a specialized {@link L2Instruction} sequence was
	 *         generated, {@code false} if nothing was emitted and the general
	 *         mechanism should be used instead.
	 */
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadBoxedOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadBoxedOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		// In the general case, avoid producing failure and reification code if
		// the primitive is infallible.  However, if the primitive can suspend
		// the fiber (which can happen even if it's infallible), be careful not
		// to inline it.
		if (hasFlag(CanSuspend)
			|| fallibilityForArgumentTypes(argumentTypes) != CallSiteCannotFail)
		{
			return false;
		}
		// The primitive cannot fail at this site.  Output code to run the
		// primitive as simply as possible, feeding a register with as strong a
		// type as possible.
		final L2Generator generator = translator.generator;
		final A_Type guaranteedType =
			returnTypeGuaranteedByVM(rawFunction, argumentTypes);
		final TypeRestriction restriction = restrictionForType(
			guaranteedType.isBottom() ? TOP.o() : guaranteedType, BOXED);
		final L2SemanticValue semanticValue;
		if (hasFlag(CanFold) && !guaranteedType.isBottom())
		{
			semanticValue = generator.primitiveInvocation(this, arguments);
			// See if we already have a value for an equivalent invocation.
			final @Nullable L2SemanticValue equivalent =
				generator.currentManifest().equivalentSemanticValue(
					semanticValue);
			if (equivalent != null)
			{
				// Reuse the previously computed result.
				generator.currentManifest().setRestriction(
					equivalent,
					generator.currentManifest().restrictionFor(equivalent)
						.intersectionWithType(guaranteedType));
				callSiteHelper.useAnswer(generator.readBoxed(equivalent));
				return true;
			}
		}
		else
		{
			semanticValue = generator.topFrame.temp(generator.nextUnique());
		}
		final L2WriteBoxedOperand writer =
			generator.boxedWrite(semanticValue, restriction);
		translator.addInstruction(
			L2_RUN_INFALLIBLE_PRIMITIVE.instance,
			new L2ConstantOperand(rawFunction),
			new L2PrimitiveOperand(this),
			new L2ReadBoxedVectorOperand(arguments),
			writer);
		if (guaranteedType.isBottom())
		{
			generator.addUnreachableCode();
		}
		else
		{
			callSiteHelper.useAnswer(translator.readBoxed(writer));
		}
		return true;
	}
}

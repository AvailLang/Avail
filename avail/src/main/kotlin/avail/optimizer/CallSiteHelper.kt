/*
 * CallSiteHelper.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.optimizer

import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.functions.A_Function
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.dispatch.LookupTree
import avail.interpreter.levelTwo.L2JVMChunk.ChunkEntryPoint
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.CallSiteHelper.JunctionType.AfterCallNoCheckNoEscapes
import avail.optimizer.CallSiteHelper.JunctionType.AfterCallNoCheckTestEscapes
import avail.optimizer.CallSiteHelper.JunctionType.AfterCallWithCheckNoEscapes
import avail.optimizer.CallSiteHelper.JunctionType.AfterCallWithCheckTestEscapes
import avail.optimizer.L2ControlFlowGraph.ZoneType.PROPAGATE_REIFICATION_FOR_INVOKE
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticValue
import avail.utility.structures.EnumMap.Companion.enumMap

/**
 * A helper that aggregates parameters for polymorphic dispatch inlining.
 *
 * @property bundle
 *   The [A_Bundle] being dispatched
 * @property superUnionType
 *   Bottom in the normal case, but for a super-call this is a tuple type with
 *   the same size as the number of arguments.  For the purpose of looking up
 *   the appropriate [A_Definition], the type union of each argument's dynamic
 *   type and the corresponding entry type from this field is computed, and
 *   that's used for the lookup.
 * @property expectedType
 *   The type expected to be returned by invoking the function.  This may be
 *   stronger than the type guaranteed by the VM, which requires a runtime
 *   check.
 *
 * @constructor
 * Create the helper, constructing basic blocks that may or may not be
 * ultimately generated, depending on whether they're reachable.
 *
 * @param translator
 *   The [L1Translator] in which this is a call site.
 * @param bundle
 *   The [A_Bundle] being invoked.
 * @param semanticArguments
 *   The list of [L2SemanticValue]s supplying argument values. These become
 *   strengthened by type tests in the current manifest.
 * @param superUnionType
 *   The type whose union with the arguments tuple type is used for lookup. This
 *   is ⊥ for ordinary calls, and other types for super calls.
 * @param expectedType
 *   The expected result type that has been strengthened by
 *   [A_SemanticRestriction]s at this call site.  The VM does not always
 *   guarantee this type will be returned, but it inserts runtime checks in the
 *   case that it can't prove it.
 * @param unionOfPossibleResults
 *   The type union of the possible return values of all method definitions that
 *   could be invoked and run to completion.
 */
class CallSiteHelper internal constructor(
	val translator: L1Translator,
	val bundle: A_Bundle,
	val semanticArguments: List<L2SemanticBoxedValue>,
	val superUnionType: A_Type,
	val expectedType: A_Type,
	val unionOfPossibleResults: A_Type)
{
	/** A Java [String] naming the [A_Bundle]. */
	val quotedBundleName = bundle.message.atomName.asNativeString()

	/** A counter for generating unique branch names for this dispatch. */
	var branchLabelCounter = 1

	/**
	 * If this flag gets set to true, the fallback slow lookup will avoid
	 * triggering a reoptimization.  If it stays false, slow lookups will
	 * eventually cause the current chunk to be reoptimized.
	 */
	var tooComplexToInline = false

	/** Whether this call site is a super lookup. */
	val isSuper = !superUnionType.isBottom

	/** Either the string "" or the string " super", depending on [isSuper]. */
	val superString: String get() = if (isSuper) " super" else ""

	/**
	 * An enumeration of the schematic blocks that can be reached by individual
	 * invocation sites of this potentially polymorphic call site.  They take
	 * into account whether the invocation can complete, whether it produces a
	 * value provable strong enough to satisfy semantic restrictions of the
	 * call, whether reification is taking place, and whether the call might
	 * cause escaped local variables to become shared or have reactors.
	 */
	enum class JunctionType
	private constructor(
		private val namePattern: JunctionNameHelper.()->String,
		private val isCold: Boolean = false,
		private val zoneType: L2ControlFlowGraph.ZoneType? = null,
		private val zoneName: String? = null,
		val generate: CallSiteHelper.()->Unit)
	{
		/**
		 * If this junction is reached, generate a slow lookup using the
		 * [LookupTree] mechanism, followed by a generic invocation.  Then jump
		 * to a suitable [JunctionType], based on whether *any* of the looked-up
		 * functions might require return type checking and local escape checks.
		 */
		FallBackToSlowLookup(
			namePattern = {"fall back to slow lookup during $bundle"},
			isCold = true,
			generate = {
				translator.generateSlowPolymorphicCall(this)
			}),

		/**
		 * Generate reification code that eventually resumes at a point where
		 * the returned value will be type checked.
		 */
		ReificationWithCheck(
			{"reify with check during $bundle"},
			zoneType = PROPAGATE_REIFICATION_FOR_INVOKE,
			zoneName = "Continue reification leading to return check",
			isCold = true,
			generate = {
				translator.reify(expectedType, ChunkEntryPoint.TO_RETURN_INTO)
				if (generator.currentlyReachable())
				{
					// Capture the value being returned into the on-ramp.
					if (unionOfPossibleResults.isVacuousType)
					{
						generator.addUnreachableCode()
					}
					else
					{
						translator.forceSlotRegister(
							translator.stackp,
							translator.pc - 1,
							translator.getLatestReturnValue(
								"unchecked result",
								unionOfPossibleResults))
						generator.jumpTo(this[AfterCallWithCheckTestEscapes])
					}
				}
			}),

		/**
		 * Where to jump to perform reification without the need for an eventual
		 * return type check.
		 */
		ReificationNoCheck(
			{"reify no check during $bundle"},
			zoneType = PROPAGATE_REIFICATION_FOR_INVOKE,
			zoneName = "Continue reification without return check",
			isCold = true,
			generate = {
				translator.reify(expectedType, ChunkEntryPoint.TO_RETURN_INTO)
				if (generator.currentlyReachable())
				{
					// Capture the value being returned into the on-ramp.
					val guaranteedType = unionOfPossibleResults
						.typeIntersection(expectedType)
					if (guaranteedType.isVacuousType)
					{
						generator.addUnreachableCode()
					}
					else
					{
						translator.forceSlotRegister(
							translator.stackp,
							translator.pc,
							translator.getLatestReturnValue(
								"no-check result",
								guaranteedType))
						generator.jumpTo(this[AfterCallNoCheckTestEscapes])
					}
				}
			}),

		/**
		 * Where to jump to perform reification during a call that cannot ever
		 * return.
		 */
		ReificationUnreturnable(
			{"reify unreturnable $bundle"},
			zoneType = PROPAGATE_REIFICATION_FOR_INVOKE,
			zoneName = "Continue reification for unreturnable",
			isCold = true,
			generate = {
				translator.reify(expectedType, ChunkEntryPoint.TO_RETURN_INTO)
				if (generator.currentlyReachable())
				{
					generator.addUnreachableCode()
				}
			}),

		/**
		 * Where to jump after a completed call to perform a return type check.
		 */
		AfterCallWithCheckNoEscapes(
			{"after$superString call with check of $bundle"},
			generate = {
				// The unchecked return value will have been put into the
				// register bound to the L2SemanticSlot for the stackp and pc
				// just after the call MINUS ONE.  Check it, moving it to a
				// register that's bound to the L2SemanticSlot for the stackp
				// and pc just after the call.
				translator.generateReturnTypeCheck(expectedType)
				generator.jumpTo(this[AfterCallNoCheckNoEscapes])
			}),

		/**
		 * Where to jump after a completed call if a return type check isn't
		 * needed.
		 */
		AfterCallNoCheckNoEscapes(
			{"after$superString no-check call of $bundle"},
			generate = {
				// Make the version of the stack with the unchecked value
				// available. The value will have been put into a register bound
				// to the L2SemanticSlot for the stackp and pc just after the
				// call.
				translator.emitCheckLocals(false, "unused comment")
				generator.jumpTo(this[AfterEverything])
			}),

		/**
		 * Where to jump after a completed call to perform a return type check,
		 * if we also have to test for escaped locals becoming shared or having
		 * reactors.
		 */
		AfterCallWithCheckTestEscapes(
			{"after$superString call with check of $bundle"},
			generate = {
				// The unchecked return value will have been put into the
				// register bound to the L2SemanticSlot for the stackp and pc
				// just after the call MINUS ONE.  Check it, moving it to a
				// register that's bound to the L2SemanticSlot for the stackp
				// and pc just after the call.
				translator.generateReturnTypeCheck(expectedType)
				generator.jumpTo(this[AfterCallNoCheckTestEscapes])
			}),

		/**
		 * Where to jump after a completed call if a return type check isn't
		 * needed.
		 */
		AfterCallNoCheckTestEscapes(
			{"after$superString no-check call of $bundle"},
			generate = {
				// Make the version of the stack with the unchecked value
				// available. The value will have been put into a register bound
				// to the L2SemanticSlot for the stackp and pc just after the
				// call.
				translator.emitCheckLocals(true, "after call no check $bundle")
				generator.jumpTo(this[AfterEverything])
			}),

		/**
		 * Where it ends up after the entire call, regardless of whether the
		 * returned value had to be checked or not.
		 */
		AfterEverything(
			{"after entire$superString call of $bundle"},
			generate = {
				// If it's possible to return a valid value from the call, this
				// will be reachable.  Do nothing here, as we've already made
				// this final junction reachable (if possible).
			})

		;

		init
		{
			assert((zoneType == null) == (zoneName == null))
		}

		/** A helper class to make name patterns easier to write. */
		data class JunctionNameHelper(
			val superString: String,
			val bundle: String)

		fun createBlock(helper: CallSiteHelper) =
			helper.generator.createBasicBlock(
				name = JunctionNameHelper(
					helper.superString,
					helper.quotedBundleName
				).namePattern(),
				isCold = isCold,
				zone = zoneType?.let { zoneType.createZone(zoneName!!)})
	}

	private val junctions = enumMap<JunctionType, L2BasicBlock>()

	operator fun get(junctionType: JunctionType): L2BasicBlock =
		junctions.getOrPut(junctionType) { it.createBlock(this) }

	fun generateReachableJunctions()
	{
		// Evaluaute in JunctionType enum order, but without using an iterator
		// over junctionType, since it may change during generation.
		JunctionType.entries.forEach { junctionType ->
			junctions[junctionType]?.let { block ->
				assert(!generator.currentlyReachable())
				generator.startBlock(block)
				if (generator.currentlyReachable())
				{
					junctionType.generate(this)
				}
			}
		}
	}

	/**
	 * A map from each reachable looked-up [A_Function] to a [Pair] containing
	 * an [L2BasicBlock] in which code generation for invocation of this
	 * function should/did take place, and a lambda which will cause that code
	 * generation to happen.
	 *
	 * This construct theoretically deals with method lookups that lead to the
	 * same function multiple ways (it's unclear if the lookup tree mechanism
	 * will ever evolve to produce this situation), but more practically, it can
	 * de-duplicate a successful inlined lookup and the success path of the
	 * fall-back slow lookup *when it knows there is only one particular
	 * definition that a successful slow lookup could produce*.
	 */
	val invocationSitesToCreate =
		mutableMapOf<A_Function, L1Translator.InvocationSite>()

	/**
	 * Answer the [L2Generator] that this [CallSiteHelper] is within.
	 */
	val generator: L2Generator get() = translator.generator

	/**
	 * Record the fact that this call has produced a value in a particular
	 * [answerReg] which is to represent the new top-of-stack value.  If the
	 * call has the potential to have made one or more local variables of this
	 * frame shared or to give them recators, [mightEndangerEscapedLocals] must
	 * be true. That will lead to extra checks for those cases, falling out to
	 * the L1 interpreter if so.
	 *
	 * @param answerReg
	 *   The register which will already hold the return value at this point.
	 *   The value has not yet been type checked against the expectedType at
	 *   this point, but it should comply with the type guarantees of the VM.
	 * @param mightEndangerEscapedLocals
	 *   If true, after the call we should check whether any escaped variables
	 *   might have become shared or had write reactors added to them.
	 *   Otherwise, don't bother checking.
	 */
	fun useAnswer(
		answerReg: L2ReadBoxedOperand,
		mightEndangerEscapedLocals: Boolean)
	{
		val answerType = answerReg.type()
		when
		{
			answerType.isBottom ->
			{
				// The VM says we can't actually get here.  Don't bother
				// associating the return value with either the checked or
				// unchecked return result L2SemanticSlot.
				generator.addUnreachableCode()
			}
			answerType.isSubtypeOf(expectedType) ->
			{
				// Capture it as the checked value L2SemanticSlot.
				translator.forceSlotRegister(
					translator.stackp, translator.pc, answerReg)
				generator.jumpTo(
					when (mightEndangerEscapedLocals)
					{
						true -> this[AfterCallNoCheckTestEscapes]
						else -> this[AfterCallNoCheckNoEscapes]
					})
			}
			else ->
			{
				// Capture it as the unchecked return value SemanticSlot by
				// using pc - 1.
				translator.forceSlotRegister(
					translator.stackp, translator.pc - 1, answerReg)
				generator.jumpTo(
					when (mightEndangerEscapedLocals)
					{
						true -> this[AfterCallWithCheckTestEscapes]
						else -> this[AfterCallWithCheckNoEscapes]
					})
			}
		}
		assert(!generator.currentlyReachable())
	}

	/**
	 * Record the fact that this call is producing a particular *constant*
	 * value.
	 *
	 * @param constantResult
	 *   The [A_BasicObject] to use as the result of the call.
	 */
	fun useConstantAnswer(
		constantResult: A_BasicObject)
	{
		if (!generator.currentlyReachable()) return
		useAnswer(generator.boxedConstant(constantResult), false)
	}

	/**
	 * For every [L2BasicBlock] in my [invocationSitesToCreate] that is
	 * reachable, generate an invocation of the corresponding [A_Function].
	 * [A_Bundle.message]
	 */
	fun generateAllInvocationSites()
	{
		invocationSitesToCreate.values.forEach { invocationSite ->
			invocationSite.run { generateAction() }
		}
	}
}

/*
 * L2_BIT_LOGIC_OP.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.Primitive
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.BitOperation
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.BinaryOp
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.C
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Constant
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Constant.K1
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Constant.K2
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Constant.K3
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Constant.K4
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Variable
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Variable.W
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Variable.X
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Variable.Y
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.Companion.Pattern.Variable.Z
import avail.optimizer.L1Translator
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.unboxedIntCondition
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * My instances are logic operations that take two [Int]s and produce an [Int].
 * They must not overflow.
 *
 * @constructor
 *   Instantiate a two-argument [i32] logic operation for a particular purpose,
 *   such as addition or exclusive-or.  Some operations require stronger
 *   restrictions on the inputs.
 * @property bitOperation
 *   The [BitOperation] to perform on the [i32]s.
 * @property input1
 *   The source of the first operand.
 * @property input2
 *   The source of the second operand.
 * @property output
 *   Where to write the result of the logical operation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_BIT_LOGIC_OP(
	val bitOperation: BitOperation,
	var input1: L2ReadIntOperand,
	var input2: L2ReadIntOperand,
	var output: L2WriteIntOperand
): L2Instruction()
{
	override val name get() = "${super.name}(${bitOperation.name})"

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(output.registerString())
		builder.append(" ← ")
		builder.append(input1.registerString())
		builder.append(" $bitOperation ")
		builder.append(input2.registerString())
	}

	override fun isBitLogicOperation(op: BitOperation) = bitOperation == op

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		return listOf(
			unboxedIntCondition(
				listOf(
					input1.register(), input2.register(), output.register())))
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: output = input1 op input2;
		translator.load(method, input1.register())
		translator.load(method, input2.register())
		method.visitInsn(bitOperation.jvmOpcode)
		translator.store(method, output.register())
	}

	class RulesSyntax constructor(
		val thisOperation: BitOperation,
		val rules: MutableList<Rule> = mutableListOf())
	{
		fun rule(
			left: Pattern,
			right: Pattern,
			producer: RuleEvaluationContext.() -> Pattern?)
		{
			val pattern = thisOperation(left, right)
			rules.add(Rule(pattern, producer))
		}
	}

	/**
	 * A [RuleEvaluationContext] is created when attempting to apply rules to an
	 * actual control flow graph.  The corresponding matched [Pattern] is used
	 * to determine the sources ([L2ReadIntOperand]s) of [Variable]s, and the
	 * [Int] values of [Constant]s.  This context is then used as an implied
	 * receiver during the rule evaluation, allowing access to [k1]..[k4] for
	 * the int values that were captured due to appearing as [K1]..[K4] in the
	 * match [Pattern], as well as the analagous [L2ReadIntOperand]s in [w]..[z]
	 * corresponding with variables [W]..[Z] in the pattern.  The rule lambda
	 * then produces a new transformed [Pattern], or `null` if it was determined
	 * by the lambda to be inapplicable.
	 *
	 * @constructor
	 *   Constructed by an optimizer performing transformations on the
	 *   [L2ControlFlowGraph], for the purpose of rewriting [Int] calculations
	 *   to be more efficient.
	 *
	 * @property variablesMap
	 *   A [Map] from [Variable] to [L2ReadIntOperand], populated for all
	 *   [Variable]s that occured in the match [Pattern].
	 * @property constantsMap
	 *   A [Map] from [Constant] to [Int] from the actual [L2ControlFlowGraph],
	 *   populated for each [Constant] that occured in the match [Pattern].
	 */
	class RuleEvaluationContext(
		private val variablesMap: Map<Variable, L2ReadIntOperand>,
		private val constantsMap: Map<Constant, Int>)
	{
		val k1: Int get() = constantsMap[K1]!!
		val k2: Int get() = constantsMap[K2]!!
		val k3: Int get() = constantsMap[K3]!!
		val k4: Int get() = constantsMap[K4]!!
		val w: L2ReadIntOperand get() = variablesMap[W]!!
		val x: L2ReadIntOperand get() = variablesMap[X]!!
		val y: L2ReadIntOperand get() = variablesMap[Y]!!
		val z: L2ReadIntOperand get() = variablesMap[Z]!!
	}

	/**
	 * The meat of an [L2_BIT_LOGIC_OP], controlling what JVM code gets
	 * generated, and what reductions are allowed.
	 *
	 * @constructor
	 *   Create a new [BitOperation] enumeration value.
	 * @property jvmOpcode
	 *   An Int opcode to emit for this logic operation, once the two int values
	 *   have been pushed.  It should have the effect of removing them from the
	 *   JVM operand stack and leaving a single int as the result.
	 * @property ruleCreator
	 *   A lambda that is able to produce rules, via [RulesSyntax], once all of
	 *   the enum values have been created, since rules for an operation refer
	 *   to other operations, sometimes cyclically.
	 */
	enum class BitOperation private constructor(
		val jvmOpcode: Int,
		private val ruleCreator: RulesSyntax.()->Unit)
	{
		/**
		 * Compute the bit-wise [Int.and] of two [Int]s.
		 */
		And(Opcodes.IAND, {
			rule(C(0), X) { C(0) }
			rule(X, C(0)) { C(0) }
			rule(C(-1), X) { X }
			rule(X, C(-1)) { X }
			rule(X, X) { X }
			rule(And(K1, X), K2) { And(X, C(k1 and k2)) }
			rule(And(X, K1), K2) { And(X, C(k1 and k2)) }
			rule(K1, And(K2, X)) { And(X, C(k1 and k2)) }
			rule(K1, And(X, K2)) { And(X, C(k1 and k2)) }
		}),

		/**
		 * Compute the bit-wise [Int.or] of two [Int]s.
		 */
		Or(Opcodes.IOR, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			rule(C(-1), X) { C(-1) }
			rule(X, C(-1)) { C(-1) }
			rule(X, X) { X }
			rule(Or(K1, X), K2) { Or(X, C(k1 or k2)) }
			rule(Or(X, K1), K2) { Or(X, C(k1 or k2)) }
			rule(K1, Or(K2, X)) { Or(X, C(k1 or k2)) }
			rule(K1, Or(X, K2)) { Or(X, C(k1 or k2)) }
		}),

		/**
		 * Compute the bit-wise [Int.xor] of two [Int]s.
		 */
		Xor(Opcodes.IXOR, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			rule(X, X) { C(0) }
			rule(X, Xor(X, Y)) { Y }
			rule(X, Xor(Y, X)) { Y }
			rule(Xor(X, Y), X) { Y }
			rule(Xor(Y, X), X) { Y }
		}),

		/**
		 * Compute the bit-wise sum of two [Int]s, wrapping around with 2's
		 * complement semantics as needed.
		 */
		Add(Opcodes.IADD, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			// Bring together constants.
			rule(K1, Add(X, K2)) { Add(X, C(k1 + k2)) }
			rule(Add(X, K1), K2) { Add(X, C(k1 + k2)) }
			rule(X, X) { Shl(X, C(1)) }
			rule(X, Mul(X, K1)) { Mul(X, C(k1 + 1)) }
			rule(Mul(X, K1), X) { Mul(X, C(k1 + 1)) }
			rule(Mul(X, K1), Mul(X, K2)) { Mul(X, C(k1 + k2)) }
		}),

		/**
		 * Compute the difference of two [Int]s (the first minus the second),
		 * wrapping around with 2's complement semantics as needed.
		 */
		Sub(Opcodes.ISUB, {
			rule(X, C(0)) { X }
			rule(X, K1) { Add(X, C(-k1)) }
			rule(Sub(X, K1), K2) { Add(X, C(-k1 - k2)) }
			rule(Add(X, K1), K2) { Add(X, C(k1 - k2)) }
			rule(K1, Sub(X, K2)) { Sub(C(k1 + k2), X) }
			rule(K1, Add(X, K2)) { Sub(C(k1 - k2), X) }
			rule(X, Sub(Y, Z)) { Sub(Add(X, Z), Y) }
			// x - (x * (x / y))  --> x mod y
			rule(X, Mul(X, Div(X, Y))) { Mod(X, Y) }
			rule(X, Mul(Div(X, Y), X)) { Mod(X, Y) }
		}),

		/**
		 * Compute the product of two [Int]s, wrapping around with 2's
		 * complement semantics as needed.
		 */
		Mul(Opcodes.IMUL, {
			rule(X, C(-1)) { Sub(C(0), X) }
			rule(C(-1), X) { Sub(C(0), X) }
			rule(X, C(0)) { C(0) }
			rule(C(0), X) { C(0) }
			rule(X, C(1)) { X }
			rule(C(1), X) { X }
			(1..31).forEach { shift ->
				val multiplier = 1 shl shift
				rule(X, C(multiplier)) { Shl(X, C(shift)) }
				rule(C(multiplier), X) { Shl(X, C(shift)) }
			}
		}),

		/**
		 * Compute the ratio of two [Int]s (i.e., intA / intB), wrapping around
		 * with 2's complement semantics as needed.  The numerator must be ≥ 0,
		 * and the denominator must be > 0.
		 */
		Div(Opcodes.IDIV, {
			rule(C(0), X) { C(0) }
			rule(X, C(1)) { X }
			(1..30).forEach { shift ->
				val divisor = 1 shl shift
				rule(X, C(divisor)) { Shr(X, C(shift)) }
			}
		}),

		/**
		 * Compute the remainder after dividng two [Int]s (i.e., `intA - (intA /
		 * intB)`), wrapping around with 2's complement semantics as needed.
		 * The numerator must be ≥ 0, and the denominator must be > 0.
		 */
		Mod(Opcodes.IREM, {
			rule(C(0), X) { C(0) }
			rule(C(1), X) { C(1) }
			rule(X, X) { C(0) }
			rule(Mul(X, Y), X) { C(0) }
			rule(Mul(Y, X), X) { C(0) }
			(1..30).forEach { shift ->
				val divisor = 1 shl shift
				rule(X, C(divisor)) { And(X, C(divisor - 1)) }
			}
		}),

		/**
		 * Shift an [Int] rightward by the specified number of bit positions,
		 * treating it as unsigned.  The second operand should be between 0 and
		 * 31, otherwise only the bottom five bits will be used.  The result can
		 * be negative if the first argument is negative and the shift is zero.
		 */
		Ushr(Opcodes.IUSHR, {
			rule(X, C(0)) { X }
			rule(Ushr(X, K1), K2) {
				if (k1 + k2 <= 31) Ushr(X, C(k1 + k2))
				else null
			}
		}),

		/**
		 * Shift an [Int] rightward by the specified number of bit positions,
		 * respecting its sign.  The second operand should be between 0 and 31,
		 * otherwise only the bottom five bits will be used.
		 */
		Shr(Opcodes.ISHR, {
			rule(X, C(0)) { X }
			rule(Shr(X, K1), K2) {
				if (k1 + k2 <= 31) Shr(X, C(k1 + k2))
				else null
			}
			rule(X, Y) {
				if (x.type().isSubtypeOf(inclusive(-1, 0))) X
				else null
			}
		}),

		/**
		 * Shift an [Int] leftward by the specified number of bit positions,
		 * respecting its sign.  The second operand should be between 0 and 31,
		 * otherwise only the bottom five bits will be used.
		 */
		Shl(Opcodes.ISHL, {
			rule(X, C(0)) { X }
			rule(Shl(X, K1), K2) {
				if (k1 + k2 <= 31) Shl(X, C(k1 + k2))
				else null
			}
			rule(Shr(X, K1), K1) { And(X, C(-1 shl k1)) }
			rule(Ushr(X, K1), K1) { And(X, C(-1 shl k1)) }
			rule(X, Y) {
				if (x.type().isSubtypeOf(inclusive(-1, 0))) X
				else null
			}
		});

		/**
		 * The [List] of [Rule]s by which an [L2ControlFlowGraph] can be
		 * transformed.  This happens when an [L2_BIT_LOGIC_OP] is asked to
		 * optimize itself, visiting its [L2_BIT_LOGIC_OP.bitOperation] and
		 * pattern matching its [BitOperation.rules].
		 */
		val rules: List<Rule> by lazy {
			val syntax = RulesSyntax(this)
			syntax.ruleCreator()
			syntax.rules
		}

		/**
		 * A syntactic convenience within pattern and rule definitions, allowing
		 * the [BitOperation] itself to appear to be a function applied to two
		 * arguments to construct a [Pattern] that's a binary [BinaryOp].
		 */
		operator fun invoke(left: Pattern, right: Pattern): BinaryOp =
			BinaryOp(this, left, right)

		/**
		 * Generate code (via the [callSiteHelper] that performs this operation,
		 * and ensures the boxed form of the result is available as the result
		 * of the call site.
		 *
		 * @param primitive
		 *   The [Primitive] being effectively invoked.
		 * @param arguments
		 *   The two boxed input arguments.
		 * @param argumentTypes
		 *   The [List] of [A_Type]s of the arguments.
		 * @param callSiteHelper
		 *   The [CallSiteHelper] that assists creation of the current method
		 *   call site.
		 * @param typeGuaranteeFunction
		 *   A lambda that produces an output type bound, given two input types.
		 * @param fallbackBody
		 *   How to generate the boxed form of this call along a path where the
		 *   inputs could not both be unboxed to [i32] values.
		 * @return
		 *   Whether any code was output.  If no code was output, the caller is
		 *   responsible for producing a more general primitive invocation or
		 *   call.
		 */
		fun generateBinaryIntOperation(
			primitive: Primitive,
			arguments: List<L2ReadBoxedOperand>,
			argumentTypes: List<A_Type>,
			callSiteHelper: CallSiteHelper,
			typeGuaranteeFunction: (List<A_Type>) -> A_Type,
			fallbackBody: L1Translator.() -> Unit
		): Boolean
		{
			val (a, b) = arguments
			val (aType, bType) = argumentTypes
			// If either of the argument types does not intersect with int32,
			// then fall back to the primitive invocation.
			if (aType.typeIntersection(i32).isBottom
				|| bType.typeIntersection(i32).isBottom)
			{
				return false
			}

			// Attempt to unbox the arguments.
			val generator = callSiteHelper.generator
			val fallback =
				generator.createBasicBlock("fall back to boxed logic")
			val intA = generator.readInt(a.semanticValue().unboxedInt, fallback)
			val intB = generator.readInt(b.semanticValue().unboxedInt, fallback)
			if (generator.currentlyReachable())
			{
				// The happy path is reachable.  In this region, the output is
				// guaranteed to be an Int.
				val semanticPrimitive = primitive.semanticInvocation(
					a.semanticValue(), b.semanticValue())
				val intSemanticPrimitive = semanticPrimitive.unboxedInt
				val typeGuarantee = typeGuaranteeFunction(
					listOf(
						aType.typeIntersection(i32),
						bType.typeIntersection(i32)))
				val manifest = generator.currentManifest
				// See if we've already computed an equivalent value in either
				// the boxed or unboxed form.
				manifest.equivalentSemanticValue(semanticPrimitive)?.let {
					generator.moveRegister(it, listOf(semanticPrimitive))
					manifest.updateRestriction(semanticPrimitive) {
						intersectionWithType(typeGuarantee)
					}
					callSiteHelper.useAnswer(
						generator.readBoxed(semanticPrimitive))
					return true
				}
				manifest.equivalentSemanticValue(intSemanticPrimitive)?.let {
					generator.moveRegister(it, listOf(intSemanticPrimitive))
					manifest.updateRestriction(intSemanticPrimitive) {
						intersectionWithType(typeGuarantee)
					}
					callSiteHelper.useAnswer(
						generator.readBoxed(intSemanticPrimitive.boxed))
					return true
				}
				val tempWriter =
					generator.intWrite(
						setOf(intSemanticPrimitive),
						intRestrictionForType(typeGuarantee))
				// Note that both the unboxed and boxed registers end up in the
				// same synonym, so subsequent uses of the result might use
				// either register, depending whether an unboxed value is
				// desired.
				generator.addInstruction(
					L2_BIT_LOGIC_OP(this, intA, intB, tempWriter))
				// Even though we're just using the boxed value again, the
				// unboxed form is also still available for use by subsequent
				// primitives, which could allow the boxing instruction to
				// evaporate.
				callSiteHelper.useAnswer(generator.readBoxed(semanticPrimitive))
			}
			if (fallback.predecessorEdges().isNotEmpty())
			{
				// The fallback block is reachable, so generate the slow case
				// within it.  Fallback may happen from conversion of non-int32
				// arguments, or from int32 overflow calculating the sum.
				generator.startBlock(fallback)
				callSiteHelper.translator.fallbackBody()
			}
			return true
		}
	}

	companion object
	{
		class Rule(
			val pattern: Pattern,
			val producer: RuleEvaluationContext.()->Pattern?)

		sealed class Pattern
		{
			open fun subString(): String = toString()

			open val allVariables: List<Variable> get() = emptyList()

			open val allConstants: List<Constant> get() = emptyList()

			abstract class Constant(): Pattern()
			{
				override fun toString(): String = this::class.simpleName!!

				override val allConstants get() = listOf(this)

				object K1 : Constant()
				object K2 : Constant()
				object K3 : Constant()
				object K4 : Constant()
			}

			/** A *specific* constant value. */
			class C(val n: Int): Pattern()
			{
				override fun toString() = n.toString()
			}

			abstract class Variable(): Pattern()
			{
				override fun toString(): String = this::class.simpleName!!

				override val allVariables get() = listOf(this)

				object W : Variable()
				object X : Variable()
				object Y : Variable()
				object Z : Variable()
			}

			class BinaryOp(
				val operation: BitOperation,
				val arg1: Pattern,
				val arg2: Pattern
			): Pattern()
			{
				override fun subString(): String = "(${toString()})"

				override fun toString() =
					"${arg1.subString()} $operation ${arg2.subString()}"

				override val allVariables
					get() = arg1.allVariables + arg2.allVariables

				override val allConstants
					get() = arg1.allConstants + arg2.allConstants
			}
		}
	}
}

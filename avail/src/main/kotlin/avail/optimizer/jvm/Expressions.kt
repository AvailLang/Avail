/*
 * Expressions.kt
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
package avail.optimizer.jvm

import kotlin.reflect.KFunction

/**
 * DSL syntax classes for generating depth-safe expressions to the objectweb
 * JVM code generator.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@DslMarker
annotation class StatementDsl

@DslMarker
annotation class ExpressionDsl

// Inner DSL for expressions
@ExpressionDsl
class ExpressionBuilder {
	private val instructions = mutableListOf<String>()

	// Push a literal value
	fun literal(value: Any) {
		instructions.add("push $value")
	}

	// Nested function call with subexpression arguments
	fun <R> call(func: KFunction<R>, buildArgs: ExpressionBuilder.() -> Unit) {
		val argBuilder = ExpressionBuilder().apply(buildArgs)
		instructions.addAll(argBuilder.build())

		instructions.add("call ${func.name}")
	}

	fun build(): List<String> = instructions
}

// Outer DSL for statements
@StatementDsl
class StatementBuilder {
	private val instructions = mutableListOf<String>()

	// Create a statement with optional result discarding
	fun <R> statement(func: KFunction<R>, buildArgs: ExpressionBuilder.() -> Unit) {
		val argBuilder = ExpressionBuilder().apply(buildArgs)
		instructions.addAll(argBuilder.build())

		instructions.add("call ${func.name}")

		// Discard result if function has a non-Unit return type
		if (func.returnType.classifier != Unit::class) {
			instructions.add("pop")
		}
	}

	// Use the inner DSL for expressions within a statement
	fun expression(build: ExpressionBuilder.() -> Unit) {
		val expressionBuilder = ExpressionBuilder().apply(build)
		instructions.addAll(expressionBuilder.build())
	}

	fun build(): List<String> = instructions
}

// Entry function for the outer DSL
fun buildStatements(build: StatementBuilder.() -> Unit): List<String> {
	val statementBuilder = StatementBuilder()
	statementBuilder.build()
	return statementBuilder.build()
}

// Example functions to be used in the DSL
fun compute(a: Int, b: Int): Int = a + b
fun printValue(value: Any) = println(value)

fun main() {
	val bytecode = buildStatements {
		// Call printValue as a statement with arguments as subexpressions
		statement(::printValue) {
			literal("Hello, World!")
		}

		// Inline expression with literals and nested function calls
		expression {
			literal(42)
			call(::printValue) {
				literal("The answer is")
				literal(42)
			}
		}

		// Use statement with subexpression arguments to compute a result
		statement(::compute) {
			literal(10)
			literal(20)
		}
	}

	bytecode.forEach { println(it) }
}

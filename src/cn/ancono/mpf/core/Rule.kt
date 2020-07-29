package cn.ancono.mpf.core

import cn.ancono.mpf.builder.RefFormulaContext
import cn.ancono.mpf.matcher.FormulaMatcher

/*
 * Created by liyicheng at 2020-04-05 19:10
 */




/**
 * Describes the transformation rule in a structure.
 * @author liyicheng
 */
interface Rule {
    //    val arguments : List<ArgumentType>
    val name: QualifiedName


    val description: String


    /**
     * Applies this rule to the given context, formulas and terms, and return the result of applying
     * this rule. If this rule is not applicable or the desired result can not be reached, `null` may be returned.
     * @param context formulas that are obtained
     * @param formulas optional formula parameters, these formulas may or may not be obtained
     * @param terms optional term parameters
     * @param desiredResult the desired result, it may not be reachable with only this rule
     */
    fun applyToward(
        context: FormulaContext,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult

    /**
     * Applies this rule to the given context, formulas and terms, and return a list of possible results of applying
     * this rule. An empty list can be returned if this rule is not suitable.
     * @param context formulas that are obtained
     * @param formulas optional formula parameters, these formulas may or may not be obtained
     * @param terms optional term parameters
     *
     */
    fun apply(
        context: FormulaContext,
        formulas: List<Formula>,
        terms: List<Term>
    ): List<Deduction>


//    /**
//     * A matcher to determine whether the input
//     */
//    val inputMatcher: FormulaMatcher


}


/**
 * Describes a result of applying a rule toward a desired formula.
 *
 * @see MatcherRule.applyToward
 */
sealed class TowardResult


data class Reached(val result: Deduction) : TowardResult() {
    constructor(r: Rule,f: Formula, dependencies: List<Formula>, moreInfo: Map<String, Any> = emptyMap()) : this(
        Deduction(
            r,
            f,
            dependencies,
            moreInfo
        )
    )

}

data class NotReached(val results: List<Deduction>) : TowardResult()


open class MatcherRule(
    override val name: QualifiedName,
    override val description: String,
    val matcher: FormulaMatcher, protected val replacer: RefFormulaContext.() -> Formula
) : Rule {
    override fun applyToward(
        context: FormulaContext,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult {
        val allResults = arrayListOf<Deduction>()
        val fs = context.formulas
        for (i in fs.lastIndex downTo 0) {
            val f = fs[i]
            val replaced = applyOne(f)
            for (r in replaced) {
                val re = Deduction(this,desiredResult, listOf(f))
                if (r.isIdentityTo(desiredResult)) {
                    return Reached(re)
                }
                allResults.add(re)
            }
        }
        return NotReached(allResults)
    }

    protected open fun applyOne(f: Formula): List<Formula> {
        val replace1 = matcher.replaceOne(f, replacer)
        val results = ArrayList<Formula>(replace1.size + 1)
        results.addAll(replace1)
        val r = matcher.replaceAll(f, replacer)
        if (!f.isIdentityTo(r) && results.all { !it.isIdentityTo(r) }) {
            results.add(r)
        }
        return results
    }

    override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
        return context.formulas.flatMap {
            val ctx = listOf(it)
            applyOne(it).asIterable().map { r ->
                Deduction(this,r, ctx)
            }
        }
    }
}


open class MatcherDefRule(
    name: QualifiedName,
    description: String,
    m1: FormulaMatcher, r1: RefFormulaContext.() -> Formula,
    val m2: FormulaMatcher, private val r2: RefFormulaContext.() -> Formula
) : MatcherRule(name, description, m1, r1) {


    override fun applyOne(f: Formula): List<Formula> {
        val re = arrayListOf<Formula>()

        fun replaceAndAdd(m: FormulaMatcher, r: RefFormulaContext.() -> Formula) {
            for (t in m.replaceOne(f, r)) {
                if (re.all { !it.isIdentityTo(t) }) {
                    re.add(t)
                }
            }
            val t = matcher.replaceAll(f, r)

            if (!t.isIdentityTo(f)) {
                if (re.all { !it.isIdentityTo(t) }) {
                    re.add(t)
                }
                // found
            }
        }
        replaceAndAdd(matcher, replacer)
        replaceAndAdd(m2, r2)
        return re
    }

}
package cn.ancono.mpf.core


/**
 * Describes a term in the
 * Created by liyicheng at 2020-04-04 18:53
 */
abstract class Term : Node<Term> {
    abstract val variables: Set<Variable>

    abstract override val childCount: Int


    abstract fun isIdentityTo(t: Term): Boolean

    /**
     * Applies the function recursively to each term nodes in this term. The order of
     * iteration is pre-order.
     */
    abstract fun recurApply(f: (Term) -> Unit): Unit

    /**
     * Applies the mapping function recursively to each term nodes in this term to build a new term.
     *
     *
     * @param m a mapping function that takes the original term and the term whose sub-terms are mapped
     * as a parameter. If the term has no sub-term, then the two parameters will be the same.
     *
     */
    abstract fun recurMap(m: (origin: Term, mapped: Term) -> Term): Term

    /**
     * Recursively maps the term and all it sub-terms and build a new term. The function [before] will be
     * invoked first before mapping the sub-nodes, and the function [after] will be invoked to a term with all sub-terms
     * mapped. If [before] returns a non-null value, then [after] will not be invoked for the term.
     *
     *
     *
     *
     */
    abstract fun recurMap(before: (Term) -> Term?, after: (Term) -> Term): Term

    abstract fun renameVar(nameMap: Map<Variable, Variable>): Term

    abstract fun regularizeVarName(
        nameMap: MutableMap<Variable, Variable>,
        nameProvider: Iterator<Variable>
    ): Term
}

abstract class AtomicTerm : Term(), AtomicNode<Term> {

    override val childCount: Int
        get() = 0

    override val children: List<Term>
        get() = emptyList()

    override fun recurApply(f: (Term) -> Unit) {
        f(this)
    }

    override fun recurMap(m: (origin: Term, mapped: Term) -> Term): Term {
        return m(this, this)
    }

    override fun recurMap(before: (Term) -> Term?, after: (Term) -> Term): Term {
        return before(this) ?: after(this)
    }
}

/**
 * A term consists of only a variable.
 */
class VarTerm(val v: Variable) : AtomicTerm() {
    override val variables: Set<Variable> = setOf(v)

    override fun isIdentityTo(t: Term): Boolean {
        return t is VarTerm && v == t.v
    }

    override fun toString(): String {
        return v.name
    }

    override fun renameVar(nameMap: Map<Variable, Variable>): Term {
        val nv = nameMap[v] ?: return this
        return VarTerm(nv)
    }

    override fun regularizeVarName(nameMap: MutableMap<Variable, Variable>, nameProvider: Iterator<Variable>): Term {
        var nv = nameMap[v]
        return if (nv != null) {
            VarTerm(nv)
        } else {
            nv = nameProvider.next()
            nameMap[v] = nv
            VarTerm(nv)
        }
    }
}

class ConstTerm(val c: Constance) : AtomicTerm() {
    override val variables: Set<Variable>
        get() = emptySet()

    override fun isIdentityTo(t: Term): Boolean {
        return t is ConstTerm && c == t.c
    }

    override fun toString(): String {
        return c.name.displayName
    }

    override fun renameVar(nameMap: Map<Variable, Variable>): Term {
        return this
    }

    override fun regularizeVarName(nameMap: MutableMap<Variable, Variable>, nameProvider: Iterator<Variable>): Term {
        return this
    }
}

class NamedTerm(val name: QualifiedName, val parameters: List<Variable>) : AtomicTerm() {
    override val variables: Set<Variable> = parameters.toSet()
    override fun isIdentityTo(t: Term): Boolean {
        return t is NamedTerm && name == t.name && parameters == t.parameters
    }

    override fun toString(): String {
        if (parameters.isEmpty()) {
            return name.displayName
        }
        return name.displayName + parameters.joinToString(",", prefix = "(", postfix = ")") { it.name }
    }

    override fun renameVar(nameMap: Map<Variable, Variable>): Term {
        if (variables.any { it in nameMap }) {
            return NamedTerm(name, parameters.map { nameMap.getOrDefault(it, it) })
        }
        return this
    }

    override fun regularizeVarName(nameMap: MutableMap<Variable, Variable>, nameProvider: Iterator<Variable>): Term {
        val nParameters = parameters.map<Variable,Variable> {v ->
            var nv = nameMap[v]
            if (nv == null) {
                nv = nameProvider.next()
                nameMap[v] = nv
            }
            nv
        }
        return NamedTerm(name,nParameters)
    }
}

abstract class CombinedTerm(override val children: List<Term>) : Term(), CombinedNode<Term> {
    override val childCount: Int
        get() = children.size

    abstract override fun copyOf(newChildren: List<Term>): CombinedTerm

    override fun renameVar(nameMap: Map<Variable, Variable>): Term {
        if (variables.any { it in nameMap }) {
            return copyOf(children.map { it.renameVar(nameMap) })
        }
        return this
    }

    override fun regularizeVarName(nameMap: MutableMap<Variable, Variable>, nameProvider: Iterator<Variable>): Term {
        val newChildren = children.map { it.regularizeVarName(nameMap, nameProvider) }
        return copyOf(newChildren)
    }
}

/**
 * A function term, such as `f(a,b)`, `a + b`.
 */
class FunTerm(val f: Function, args: List<Term>) : CombinedTerm(args) {
    override val variables: Set<Variable> by lazy { args.flatMapTo(hashSetOf()) { it.variables } }

    override fun isIdentityTo(t: Term): Boolean {
        return t is FunTerm && f == t.f &&
                Utils.collectionEquals(children, t.children,Term::isIdentityTo)
    }

    override fun recurApply(f: (Term) -> Unit) {
        f(this)
        children.forEach { it.recurApply(f) }
    }

    override fun recurMap(m: (origin: Term, mapped: Term) -> Term): Term {
        val nArgs = children.map { it.recurMap(m) }
        val nTerm = FunTerm(f, nArgs)
        return m(this, nTerm)
    }

    override fun recurMap(before: (Term) -> Term?, after: (Term) -> Term): Term {
        val t = before(this)
        if (t != null) {
            return t
        }
        val nArgs = children.map { it.recurMap(before, after) }
        val nTerm = FunTerm(f, nArgs)
        return after(nTerm)
    }

    override fun copyOf(newChildren: List<Term>): CombinedTerm {
        return FunTerm(f, newChildren)
    }

    override fun toString(): String {
        if (children.isEmpty()) {
            return f.name.displayName
        }
        return f.name.displayName + children.joinToString(",", prefix = "(", postfix = ")")
    }


}

package com.strumenta.kolasu.languageserver.library

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.PossiblyNamed
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.traversing.walk
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.typeOf

class ScopelessSymbolResolver : SymbolResolver {

    override fun resolveSymbols(tree: Node?) {
        if (tree == null) return
        val symbols = mutableMapOf<String, PossiblyNamed>()
        tree.walk().forEach { node ->
            if (node is PossiblyNamed) {
                node.name?.let {
                    symbols.put(it, node)
                }
            }
        }
        tree.walk().forEach { node ->
            val referenceField = node::class.declaredMemberProperties.find { it.returnType.isSubtypeOf(typeOf<ReferenceByName<*>>()) }
            referenceField?.javaField?.let { field ->
                field.isAccessible = true
                val value = field.get(node) as ReferenceByName<*>
                val name = value.name
                field.set(node, ReferenceByName(name, symbols[name]))
            }
        }
    }
}

package com.daveme.chocolateCakePHP.behavior

import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * A method or finder a behavior attached to a table mixes into it.
 */
class MixedInMember(
    val alias: String,
    val method: Method,
    val behaviorClass: PhpClass,
)

/**
 * The methods the behaviors attached to [tableFqns] mix in, leaving out any name a class in
 * the table's chain declares, since `Table::__call()` never runs for those.
 */
fun behaviorMethodsOnTables(project: Project, tableFqns: Collection<String>): List<MixedInMember> =
    mixedIn(project, tableFqns, { it.methods }) { table, alias -> table.findMethodByName(alias) != null }

/**
 * The finders the behaviors attached to [tableFqns] mix in, leaving out any the table's chain
 * declares as a `find` method, since `Table::callFinder()` looks for those first.
 */
fun behaviorFindersOnTables(project: Project, tableFqns: Collection<String>): List<MixedInMember> =
    mixedIn(project, tableFqns, { it.finders }) { table, alias -> table.findMethodByName("find$alias") != null }

private fun mixedIn(
    project: Project,
    tableFqns: Collection<String>,
    members: (BehaviorMemberMap) -> Map<String, BehaviorMember>,
    isShadowed: (PhpClass, String) -> Boolean,
): List<MixedInMember> {
    val phpIndex = PhpIndex.getInstance(project)
    val result = mutableListOf<MixedInMember>()
    for (tableFqn in tableFqns.distinct()) {
        val tables = phpIndex.getClassesByFQN(tableFqn)
        for (attached in ConfiguredMemberService.behaviors(project, tableFqn)) {
            for (behaviorClass in attached.classes) {
                for (member in members(BehaviorMembers.of(project, behaviorClass, attached.load)).values) {
                    if (tables.any { isShadowed(it, member.alias) }) {
                        continue
                    }
                    val isDuplicate = result.any {
                        it.alias.equals(member.alias, ignoreCase = true) && it.method == member.method
                    }
                    if (!isDuplicate) {
                        result += MixedInMember(member.alias, member.method, behaviorClass)
                    }
                }
            }
        }
    }
    return result
}

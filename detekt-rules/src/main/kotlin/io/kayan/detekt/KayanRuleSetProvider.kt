package io.kayan.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

public class KayanRuleSetProvider : RuleSetProvider {
    public override val ruleSetId: RuleSetId = RuleSetId("kayan")

    public override fun instance(): RuleSet = RuleSet(ruleSetId, listOf(::NoEmDashes))
}

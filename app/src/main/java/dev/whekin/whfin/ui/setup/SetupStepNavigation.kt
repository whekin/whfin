package dev.whekin.whfin.ui.setup

import dev.whekin.whfin.R

/** Navigation never claims that an optional configuration was saved. */
internal fun setupNextLabel(
    stage: SetupStage,
    bankConnected: Boolean,
    smsEnabled: Boolean,
    hasAccounts: Boolean,
    hasIncome: Boolean,
    hasPlan: Boolean,
): Int = when (stage) {
    SetupStage.Banks -> if (bankConnected) R.string.setup_to_sms else R.string.setup_skip_banks
    SetupStage.Sms -> if (smsEnabled) R.string.setup_to_accounts else R.string.setup_skip_sms
    SetupStage.Accounts -> if (hasAccounts) R.string.setup_to_categories else R.string.setup_skip_accounts
    SetupStage.Categories -> R.string.setup_skip_categories
    SetupStage.Income -> if (hasIncome) R.string.setup_to_plans else R.string.setup_skip_income
    SetupStage.Plans -> if (hasPlan) R.string.setup_to_preferences else R.string.setup_skip_plans
    SetupStage.Preferences -> R.string.setup_use_preferences
    SetupStage.Ready -> R.string.personal_setup_continue_action
}

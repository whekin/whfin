package dev.whekin.whfin.data.integrity

/** A zero count is evidence only after a complete, successful pass. */
internal sealed interface IntegrityCheckState {
    data object NotChecked : IntegrityCheckState
    data object Checking : IntegrityCheckState
    data class Complete(val issueCount: Int, val checkedAt: Long) : IntegrityCheckState
    data object Failed : IntegrityCheckState
}

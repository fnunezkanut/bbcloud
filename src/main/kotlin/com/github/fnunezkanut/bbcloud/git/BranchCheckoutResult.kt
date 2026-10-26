package com.github.fnunezkanut.bbcloud.git

sealed class BranchCheckoutResult {
    data class Success(val branchName: String) : BranchCheckoutResult()

    data class Failed(val branchName: String, val output: String, val cause: Throwable? = null) : BranchCheckoutResult()
}
package com.mrndstvndv.search.util

object VersionComparator {
    /**
     * Returns true if candidate is newer than current.
     */
    fun isNewer(current: String, candidate: String): Boolean {
        val cleanCurrent = current.trim().removePrefix("v")
        val cleanCandidate = candidate.trim().removePrefix("v")

        val currentParts = cleanCurrent.split('.', '-')
        val candidateParts = cleanCandidate.split('.', '-')

        val maxLen = maxOf(currentParts.size, candidateParts.size)
        for (i in 0 until maxLen) {
            val currPart = currentParts.getOrNull(i)
            val candPart = candidateParts.getOrNull(i)

            if (currPart == candPart) continue

            if (currPart == null) {
                val candNum = candPart?.toIntOrNull()
                return candNum != null && candNum > 0
            }
            if (candPart == null) {
                return false
            }

            val currNum = currPart.toIntOrNull()
            val candNum = candPart.toIntOrNull()

            if (currNum != null && candNum != null) {
                if (currNum != candNum) {
                    return candNum > currNum
                }
            } else {
                val cmp = candPart.compareTo(currPart, ignoreCase = true)
                if (cmp != 0) {
                    return cmp > 0
                }
            }
        }
        return false
    }
}

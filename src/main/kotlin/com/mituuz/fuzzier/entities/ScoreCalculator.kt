package com.mituuz.fuzzier.entities

import com.intellij.openapi.components.service
import com.mituuz.fuzzier.entities.FuzzyMatchContainer.FuzzyScore
import com.mituuz.fuzzier.settings.FuzzierSettingsService
import org.apache.commons.lang3.StringUtils

class ScoreCalculator(searchString: String) {
    private val lowerSearchString: String = searchString.lowercase()
    private val searchStringParts = lowerSearchString.split(" ")
    private lateinit var fuzzyScore: FuzzyScore
    private val uniqueLetters = lowerSearchString.toSet()

    var searchStringIndex: Int = 0
    var searchStringLength: Int = 0
    var filePathIndex: Int = 0
    private var filenameIndex: Int = 0

    // Set up the settings
    private val settings = service<FuzzierSettingsService>().state
    private var multiMatch = settings.multiMatch
    private var matchWeightSingleChar = settings.matchWeightSingleChar
    private var matchWeightStreakModifier = settings.matchWeightStreakModifier
    private var matchWeightPartialPath = settings.matchWeightPartialPath
    private var matchWeightFilename = settings.matchWeightFilename

    var currentFilePath = ""
    private var longestStreak: Int = 0
    private var currentStreak: Int = 0
    private var longestFilenameStreak: Int = 0
    private var currentFilenameStreak: Int = 0

    /**
     * Returns null if no match can be found
     */
    fun calculateScore(filePath: String): FuzzyScore? {
        currentFilePath = filePath.lowercase()
        filenameIndex = currentFilePath.lastIndexOf("/") + 1
        longestStreak = 0
        fuzzyScore = FuzzyScore()

        // Check if the search string is longer than the file path, which results in no match
        if (lowerSearchString.length > currentFilePath.length) { // TODO: + tolerance when it is implemented
            return null
        }

        for (part in searchStringParts) {
            // Reset the index and length for the new part
            searchStringIndex = 0
            searchStringLength = part.length
            filePathIndex = 0

            if (!processString(part)) {
                return null
            }
        }

        if (multiMatch) {
            calculateMultiMatchScore()
        }
        calculateFilenameScore()

        fuzzyScore.streakScore = (longestStreak * matchWeightStreakModifier) / 10
        fuzzyScore.filenameScore = (longestFilenameStreak * matchWeightFilename) / 10

        return fuzzyScore
    }

    /**
     * Returns false if no match can be found, this stops the search
     */
    private fun processString(searchStringPart: String): Boolean {
        while (searchStringIndex < searchStringLength) {
            if (!canSearchStringBeContained()) {
                return false
            }

            val currentChar = searchStringPart[searchStringIndex]
            if (!processChar(currentChar)) {
                return false
            }
        }

        calculatePartialPathScore(searchStringPart)

        return true
    }

    private fun calculateMultiMatchScore() {
        fuzzyScore.multiMatchScore += (currentFilePath.count { it in uniqueLetters } * matchWeightSingleChar) / 10
    }

    private fun calculatePartialPathScore(searchStringPart: String) {
        StringUtils.split(currentFilePath, "/.").forEach {
            if (it == searchStringPart) {
                fuzzyScore.partialPathScore += matchWeightPartialPath
            }
        }
    }

    private fun processChar(searchStringPartChar: Char): Boolean {
        val filePathPartChar = currentFilePath[filePathIndex]
        if (searchStringPartChar == filePathPartChar) {
            searchStringIndex++
            updateStreak(true)
        } else {
            updateStreak(false)
        }
        filePathIndex++
        return true
    }

    /**
     * Go through the search string one more time and calculate the longest streak present in the filename
     */
    private fun calculateFilenameScore() {
        searchStringIndex = 0
        currentFilenameStreak = 0
        longestFilenameStreak = 0

        filePathIndex = filenameIndex
        while (searchStringIndex < searchStringLength && filePathIndex < currentFilePath.length) {
            processFilenameChar(lowerSearchString[searchStringIndex])
        }
    }

    private fun processFilenameChar(searchStringPartChar: Char) {
        val filePathPartChar = currentFilePath[filePathIndex]
        if (searchStringPartChar == filePathPartChar) {
            searchStringIndex++
            currentFilenameStreak++
            if (currentFilenameStreak > longestFilenameStreak) {
                longestFilenameStreak = currentFilenameStreak
            }
        } else {
            currentFilenameStreak = 0
        }
        filePathIndex++
    }

    private fun updateStreak(match: Boolean) {
        if (match) {
            currentStreak++
            if (currentStreak > longestStreak) {
                longestStreak = currentStreak
            }
        } else {
            currentStreak = 0
        }
    }

    /**
     * Checks if the remaining search string can be contained in the remaining file path based on the length
     * e.g. if the remaining search string is "abc" and the remaining file path is "abcdef", it can be contained
     * e.g. if the remaining search string is "abc" and the remaining file path is "def", it can't be contained
     * e.g. if the remaining search string is "abc" and the remaining file path is "ab", it can't be contained
     */
    fun canSearchStringBeContained(): Boolean {
        val remainingSearchStringLength = searchStringLength - searchStringIndex
        val remainingFilePathLength = currentFilePath.length - filePathIndex
        return remainingSearchStringLength <= remainingFilePathLength // TODO: + tolerance when it is implemented
    }

    fun setMatchWeightStreakModifier(value: Int) {
        matchWeightStreakModifier = value
    }

    fun setMatchWeightSingleChar(value: Int) {
        matchWeightSingleChar = value
    }

    fun setMatchWeightPartialPath(value: Int) {
        matchWeightPartialPath = value
    }

    fun setFilenameMatchWeight(value: Int) {
        matchWeightFilename = value
    }

    fun setMultiMatch(value: Boolean) {
        multiMatch = value
    }
}
package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.SpendingInsightsCache
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.DetectedInsightDto
import com.pledgerio.app.data.remote.dto.DetectedPatternDto
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.model.InsightSeverity
import com.pledgerio.app.domain.model.InsightType
import com.pledgerio.app.domain.model.PatternType
import com.pledgerio.app.domain.model.SpendingInsight
import com.pledgerio.app.domain.model.SpendingInsights
import com.pledgerio.app.domain.model.SpendingPattern
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.SpendingInsightRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpendingInsightRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val categoryRepository: CategoryRepository,
    private val cache: SpendingInsightsCache,
) : SpendingInsightRepository {

    override suspend fun getInsights(
        month: YearMonth,
        forceRefresh: Boolean,
    ): Resource<SpendingInsights> {
        if (!forceRefresh) {
            val cached = cache.get(month)
            if (cached != null && cached.isFresh(month)) {
                return Resource.Success(cached.insights)
            }
        }
        return try {
            val insightsResponse = apiService.getDetectedInsights(month.year, month.monthValue)
            if (!insightsResponse.isSuccessful) {
                return Resource.Error("Failed to load insights: HTTP ${insightsResponse.code()}")
            }
            val patternsResponse = apiService.getDetectedPatterns(month.year, month.monthValue)
            if (!patternsResponse.isSuccessful) {
                return Resource.Error("Failed to load patterns: HTTP ${patternsResponse.code()}")
            }
            val insightDtos = insightsResponse.body().orEmpty()
            val patternDtos = patternsResponse.body().orEmpty()
            val categoryIds = if (insightDtos.isEmpty() && patternDtos.isEmpty()) {
                emptyMap()
            } else {
                categoryIdsByName()
            }
            val insights = SpendingInsights(
                insights = insightDtos.map { it.toDomain(categoryIds) },
                patterns = patternDtos.map { it.toDomain(categoryIds) },
            )
            cache.put(month, insights)
            Resource.Success(insights)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not load insights")
        }
    }

    private suspend fun categoryIdsByName(): Map<String, Long> =
        loadCategories().associate { it.name.normalizedCategoryKey() to it.id }

    private suspend fun loadCategories(): List<Category> {
        val cached = try {
            categoryRepository.observeCategories().first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (cached.isNotEmpty()) return cached
        return when (val refreshed = categoryRepository.refreshCategories()) {
            is Resource.Success -> refreshed.data
            else -> emptyList()
        }
    }
}

private fun DetectedInsightDto.toDomain(categoryIds: Map<String, Long>): SpendingInsight {
    val categoryName = category.orEmpty().trim()
    return SpendingInsight(
        type = InsightType.fromString(type),
        severity = InsightSeverity.fromString(severity),
        category = categoryName,
        message = message.orEmpty(),
        score = score,
        detectedDate = detectedDate.toLocalDateOrNull(),
        transactionId = transactionId,
        categoryId = categoryIds[categoryName.normalizedCategoryKey()],
    )
}

private fun DetectedPatternDto.toDomain(categoryIds: Map<String, Long>): SpendingPattern {
    val categoryName = category.orEmpty().trim()
    return SpendingPattern(
        type = PatternType.fromString(type),
        category = categoryName,
        confidence = confidence,
        detectedDate = detectedDate.toLocalDateOrNull(),
        categoryId = categoryIds[categoryName.normalizedCategoryKey()],
    )
}

private fun String.normalizedCategoryKey(): String = trim().lowercase(Locale.ROOT)

private fun String?.toLocalDateOrNull(): LocalDate? =
    this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

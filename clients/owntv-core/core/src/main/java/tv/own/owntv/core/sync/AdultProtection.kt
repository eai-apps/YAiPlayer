package tv.own.owntv.core.sync

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * "Protect adult channels with a PIN", chosen when a playlist or portal is added.
 *
 * The categories do not exist yet at that moment, so the choice is remembered per playlist and profile
 * and carried out here after each import pass (a staged import brings live, movies and series in
 * separately). Every category whose name reads as adult is put behind the profile's Customize PIN.
 * A lock is applied once per category: one the user removes later is not put back by the next sync.
 */
class AdultProtection(
    private val settings: SettingsRepository,
    private val categoryDao: CategoryDao,
    private val customize: CustomizationStore,
) {
    suspend fun applyIfRequested(sourceId: Long) {
        val requests = settings.adultProtectRequests.first()
            .filter { it.substringBefore(':').toLongOrNull() == sourceId }
        if (requests.isEmpty()) return
        val adult = categoryDao.forSource(sourceId).filter {
            it.mediaType in LOCKABLE && AdultCategoryClassifier.isAdult(it.name)
        }
        if (adult.isEmpty()) return
        val applied = settings.adultProtectApplied.first()
        val fresh = ArrayList<String>()
        for (request in requests) {
            val profileId = request.substringAfter(':').toLongOrNull() ?: continue
            for (category in adult) {
                val key = CustomizeKeys.category(category)
                val mark = "$request:$key"
                if (mark in applied) continue
                customize.setCategoryLocked(profileId, category.mediaType, key, true)
                fresh.add(mark)
            }
        }
        if (fresh.isNotEmpty()) settings.markAdultProtectApplied(fresh)
    }

    private companion object {
        val LOCKABLE = setOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES)
    }
}

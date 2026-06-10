package com.mrndstvndv.search.di

import android.content.Context
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.provider.apps.RecentAppsRepository
import com.mrndstvndv.search.provider.apps.PinnedAppsRepository
import com.mrndstvndv.search.provider.apps.createAppSearchSettingsRepository
import com.mrndstvndv.search.provider.contacts.ContactsRepository
import com.mrndstvndv.search.provider.contacts.createContactsSettingsRepository
import com.mrndstvndv.search.provider.files.FileSearchRepository
import com.mrndstvndv.search.provider.files.FileThumbnailRepository
import com.mrndstvndv.search.provider.files.createFileSearchSettingsRepository
import com.mrndstvndv.search.provider.intent.createIntentSettingsRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.system.DeveloperSettingsManager
import com.mrndstvndv.search.provider.system.createSystemSettingsSettingsRepository
import com.mrndstvndv.search.provider.termux.createTermuxSettingsRepository
import com.mrndstvndv.search.provider.text.createTextUtilitiesSettingsRepository
import com.mrndstvndv.search.provider.web.createWebSearchSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(private val context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository by lazy { ProviderSettingsRepository(context, applicationScope) }
    val aliasRepository by lazy { AliasRepository(context, applicationScope) }

    val webSearchSettingsRepo by lazy { createWebSearchSettingsRepository(context) }
    val appSearchSettingsRepo by lazy { createAppSearchSettingsRepository(context) }
    val textUtilitiesSettingsRepo by lazy { createTextUtilitiesSettingsRepository(context) }
    val fileSearchSettingsRepo by lazy { createFileSearchSettingsRepository(context) }
    val systemSettingsSettingsRepo by lazy { createSystemSettingsSettingsRepository(context) }
    val contactsSettingsRepo by lazy { createContactsSettingsRepository(context) }
    val termuxSettingsRepo by lazy { createTermuxSettingsRepository(context) }
    val intentSettingsRepo by lazy { createIntentSettingsRepository(context) }

    val fileSearchRepository by lazy { FileSearchRepository.getInstance(context) }
    val fileThumbnailRepository by lazy { FileThumbnailRepository.getInstance(context) }
    val contactsRepository by lazy { ContactsRepository.getInstance(context) }
    val rankingRepository by lazy { ProviderRankingRepository.getInstance(context, applicationScope) }
    
    val defaultAppIconSize by lazy { context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size) }
    val appListRepository by lazy { AppListRepository.getInstance(context, defaultAppIconSize) }
    val recentAppsRepository by lazy { RecentAppsRepository(context, appListRepository) }
    val pinnedAppsRepository by lazy { PinnedAppsRepository(context, appSearchSettingsRepo, appListRepository) }
    val developerSettingsManager by lazy { DeveloperSettingsManager.getInstance(context) }
}

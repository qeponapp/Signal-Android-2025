package org.thoughtcrime.securesms.components.settings.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.DynamicTheme

class SettingsHostFragment : Fragment(R.layout.settings_host_fragment) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (childFragmentManager.findFragmentById(R.id.settings_nav_host_fragment) == null) {
      val startArgs = Bundle().apply {
        putBoolean(AppSettingsFragment.ARG_SHOW_TOP_BAR, false)
      }
      val navHost = NavHostFragment.create(R.navigation.app_settings_with_change_number, startArgs)
      childFragmentManager.beginTransaction()
        .replace(R.id.settings_nav_host_fragment, navHost)
        .setPrimaryNavigationFragment(navHost)
        .commitNow()
    }

    SignalStore.settings.onConfigurationSettingChanged.observe(viewLifecycleOwner) { key ->
      when (key) {
        SettingsValues.THEME -> {
          DynamicTheme.setDefaultDayNightMode(requireContext())
          requireActivity().recreate()
        }
        SettingsValues.LANGUAGE -> {
          CachedInflater.from(requireContext()).clear()
          requireActivity().recreate()
          val intent = Intent(requireContext(), KeyCachingService::class.java).apply {
            action = KeyCachingService.LOCALE_CHANGE_EVENT
          }
          requireContext().startService(intent)
        }
      }
    }
  }
}

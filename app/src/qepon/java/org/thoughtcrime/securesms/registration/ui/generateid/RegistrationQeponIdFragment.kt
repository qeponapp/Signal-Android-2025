/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.generateid

import android.os.Bundle
import android.view.View
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R


class RegistrationQeponIdFragment: LoggingFragment(R.layout.fragment_registration_qepon_id) {

  private var qeponIds = arrayListOf<String>()
  private var qeponId = ""

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    arguments?.let {
      //qeponIds = RegistrationQeponIdFragmentArgs.fromBundle(it).qeponIds
      //Log.e(TAG, "list calon id: $qeponIds")
      //Log.e(TAG, "list calon id ada")
    }

  }

  companion object {
    private val TAG = Log.tag(RegistrationQeponIdFragment::class.java)

  }
}
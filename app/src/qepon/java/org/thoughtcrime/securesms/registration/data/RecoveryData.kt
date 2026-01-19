/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data

import org.signal.libsignal.zkgroup.profiles.ProfileKey

data class RecoveryData(
  val code: String,
  val e164: String,
  val password: String,
  val registrationId: Int,
  val profileKey: ByteArray?,
  val fcmToken: String?,
  val pniRegistrationId: Int,
  val recoveryPassword: String?,
  val pni: String?,
  val aci: String?
) {
  val isNotFcm: Boolean = fcmToken.isNullOrBlank()
  val isFcm: Boolean = !isNotFcm
}
/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data



data class QeponAccountRegistrationResult(
  val uuid: String,
  val number: String,
  val pni: String,
  val usernameHash: String,
  val usernameLinkHandle: String?,
  val storageCapable: Boolean

)
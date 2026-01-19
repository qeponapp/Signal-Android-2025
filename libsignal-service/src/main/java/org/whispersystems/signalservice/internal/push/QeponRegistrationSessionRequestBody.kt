/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity

@JsonInclude(JsonInclude.Include.NON_NULL)
data class QeponRegistrationSessionRequestBody(
  @JsonProperty val usernameHashList: List<String>? = null,
  @JsonProperty val accountAttributes: AccountAttributes,
  @JsonProperty val aciIdentityKey: String,
  @JsonProperty val pniIdentityKey: String,
  @JsonProperty val aciSignedPreKey: SignedPreKeyEntity,
  @JsonProperty val pniSignedPreKey: SignedPreKeyEntity,
  @JsonProperty val aciPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty val pniPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty val gcmToken: GcmRegistrationId?,
  @JsonProperty val requireAtomic: Boolean = true
)
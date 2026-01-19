/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data.network

import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.data.AccountRegistrationResult
import org.thoughtcrime.securesms.registration.data.QeponAccountRegistrationResult
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException
import org.whispersystems.signalservice.api.push.exceptions.IncorrectRegistrationRecoveryPasswordException
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.LockedException
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse

/**
 * This is a processor to map a [VerifyAccountResponse] to all the known outcomes.
 */
sealed class QeponRegisterAccountResult(cause: Throwable?) : RegistrationResult(cause) {
  companion object {
    fun from(networkResult: NetworkResult<QeponAccountRegistrationResult>): QeponRegisterAccountResult {
      return when (networkResult) {
        is NetworkResult.Success -> Success(networkResult.result)
        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> {
          when (val cause = networkResult.exception) {
            is IncorrectRegistrationRecoveryPasswordException -> IncorrectRecoveryPassword(cause)
            is AuthorizationFailedException -> AuthorizationFailed(cause)
            is MalformedRequestException -> MalformedRequest(cause)
            is RateLimitException -> createRateLimitProcessor(cause)
            is LockedException -> RegistrationLocked(cause = cause, timeRemaining = cause.timeRemaining, svr2Credentials = cause.svr2Credentials, svr3Credentials = cause.svr3Credentials)
            else -> {
              if (networkResult.code == 422) {
                ValidationError(cause)
              } else {
                UnknownError(cause)
              }
            }
          }
        }
      }
    }

    private fun createRateLimitProcessor(exception: RateLimitException): QeponRegisterAccountResult {
      return if (exception.retryAfterMilliseconds.isPresent) {
        RateLimited(exception, exception.retryAfterMilliseconds.get())
      } else {
        AttemptsExhausted(exception)
      }
    }
  }
  class Success(val accountRegistrationResult: QeponAccountRegistrationResult) : QeponRegisterAccountResult(null)
  class IncorrectRecoveryPassword(cause: Throwable) : QeponRegisterAccountResult(cause)
  class AuthorizationFailed(cause: Throwable) : QeponRegisterAccountResult(cause)
  class MalformedRequest(cause: Throwable) : QeponRegisterAccountResult(cause)
  class ValidationError(cause: Throwable) : QeponRegisterAccountResult(cause)
  class RateLimited(cause: Throwable, val timeRemaining: Long) : QeponRegisterAccountResult(cause)
  class AttemptsExhausted(cause: Throwable) : QeponRegisterAccountResult(cause)
  class RegistrationLocked(cause: Throwable, val timeRemaining: Long, val svr2Credentials: AuthCredentials?, val svr3Credentials: Svr3Credentials?) : QeponRegisterAccountResult(cause)
  class UnknownError(cause: Throwable) : QeponRegisterAccountResult(cause)

  class SvrNoData(cause: SvrNoDataException) : QeponRegisterAccountResult(cause)
  class SvrWrongPin(cause: SvrWrongPinException) : QeponRegisterAccountResult(cause) {
    val triesRemaining = cause.triesRemaining
  }
}

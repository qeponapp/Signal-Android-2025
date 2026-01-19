package org.thoughtcrime.securesms.registration.ui.welcome

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationWelcomeBinding
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.WelcomePermissions
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.grantpermissions.GrantPermissionsFragment
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Qepon flavor-specific welcome: routes restore to ImportRecovery flow.
 */
class QeponWelcomeFragment : LoggingFragment(R.layout.fragment_registration_welcome) {
  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val binding: FragmentRegistrationWelcomeBinding by ViewBinderDelegate(FragmentRegistrationWelcomeBinding::bind)
  private var pendingAction: NextAction? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(binding.image)
    setDebugLogSubmitMultiTapView(binding.title)
    binding.welcomeContinueButton.setOnClickListener { onContinueClicked() }
    binding.welcomeTermsButton.setOnClickListener { onTermsClicked() }
    binding.welcomeTransferOrRestore.setOnClickListener { onTransferOrRestoreClicked() }
  }

  private fun onContinueClicked() {
    if (Permissions.isRuntimePermissionsRequired() && !hasAllPermissions()) {
      pendingAction = NextAction.REGISTER
      findNavController().safeNavigate(
        R.id.action_welcomeFragment_to_grantPermissionsFragment,
        bundleOf("welcomeAction" to GrantPermissionsFragment.WelcomeAction.CONTINUE)
      )
    } else {
      sharedViewModel.maybePrefillE164(requireContext())
      findNavController().safeNavigate(R.id.action_skip_restore)
    }
  }

  private fun hasAllPermissions(): Boolean {
    val isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext())
    return WelcomePermissions.getWelcomePermissions(isUserSelectionRequired).all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
  }

  private fun onTermsClicked() {
    CommunicationActions.openBrowserLink(requireContext(), TERMS_AND_CONDITIONS_URL)
  }

  private fun onTransferOrRestoreClicked() {
    if (Permissions.isRuntimePermissionsRequired() && !hasAllPermissions()) {
      pendingAction = NextAction.RESTORE
      findNavController().safeNavigate(
        R.id.action_welcomeFragment_to_grantPermissionsFragment,
        bundleOf("welcomeAction" to GrantPermissionsFragment.WelcomeAction.RESTORE_BACKUP)
      )
    } else {
      sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PERMISSIONS_GRANTED)
      findNavController().safeNavigate(R.id.action_welcome_to_importRecovery)
    }
  }

  override fun onResume() {
    super.onResume()
    val action = pendingAction
    if (action != null && (!Permissions.isRuntimePermissionsRequired() || hasAllPermissions())) {
      pendingAction = null
      when (action) {
        NextAction.REGISTER -> {
          sharedViewModel.maybePrefillE164(requireContext())
          findNavController().safeNavigate(R.id.action_skip_restore)
        }
        NextAction.RESTORE -> {
          sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PERMISSIONS_GRANTED)
          findNavController().safeNavigate(R.id.action_welcome_to_importRecovery)
        }
      }
    }
  }

  companion object {
    private val TAG = Log.tag(QeponWelcomeFragment::class.java)
    private const val TERMS_AND_CONDITIONS_URL = "https://signal.org/legal"
  }

  private enum class NextAction {
    REGISTER,
    RESTORE
  }
}

package org.thoughtcrime.securesms.registration.ui.recovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.databinding.FragmentImportRecoveryBinding



class ImportRecoveryFragment : Fragment() {

  private var binding: FragmentImportRecoveryBinding? = null

  private val pickZipLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      binding?.zipPathEditText?.setText(uri.toString())
      try {
        requireContext().contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: SecurityException) {
        // Best effort; SAF may not support persistable permission.
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val fragmentBinding = FragmentImportRecoveryBinding.inflate(inflater, container, false)
    binding = fragmentBinding

    fragmentBinding.browseButton.setOnClickListener {
      pickZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    fragmentBinding.importButton.setOnClickListener {
      // TODO: hook up import + recovery logic
    }

    return fragmentBinding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }
}

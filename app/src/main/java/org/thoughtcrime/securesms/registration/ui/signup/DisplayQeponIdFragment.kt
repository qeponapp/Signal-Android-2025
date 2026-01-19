package org.thoughtcrime.securesms.registration.ui.signup

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.FragmentDisplayQeponIdBinding
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel


class DisplayQeponIdFragment : Fragment(R.layout.fragment_display_qepon_id) {

  private val sharedViewModel: RegistrationViewModel by activityViewModels()
  private val args: DisplayQeponIdFragmentArgs by navArgs()
  private var _binding: FragmentDisplayQeponIdBinding? = null
  private val binding get() = _binding!!

  // --- Lifecycle ------------------------------------------------------------
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentDisplayQeponIdBinding.inflate(inflater, container, false)
    return binding.root
  }


  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    /*val argId = arguments?.getString("qeponId").orEmpty()
    if (!argId.isNullOrBlank()) {
      binding.yourQeponId.text = argId
      sharedViewModel.clearMatchedQeponId()
    }*/

    sharedViewModel.uiState.observe(viewLifecycleOwner) { state ->
      val id = state.matchedQeponId
      if (id != null) {
        binding.yourQeponId.text = id
        sharedViewModel.clearMatchedQeponId()
      }
    }

    binding.nextButton.setOnClickListener {
      //findNavController().navigate(R.id.action_displayQeponIdFragment_to_generateIdFragment)
      sharedViewModel.restoreStateRecovery()
      sharedViewModel.forceCheckpointToLocalComplete()

      /*val intent = CreateProfileActivity.getIntentForUserProfile(requireContext())
      startActivity(intent)
      requireActivity().finish()*/
    }


  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}

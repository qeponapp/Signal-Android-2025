/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.generateid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentSignUpBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.data.network.QeponRegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import java.security.SecureRandom

class GenerateIdFragment: LoggingFragment(R.layout.fragment_sign_up) {

  // --- Dependencies ---------------------------------------------------------
  private val secureRandom: SecureRandom = SecureRandom()
  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val binding: FragmentSignUpBinding by ViewBinderDelegate(FragmentSignUpBinding::bind)

  // --- Collaborators --------------------------------------------------------
  private lateinit var gridRenderer: SeedGridRenderer
  private lateinit var idGenerator: QeponIdGenerator

  private var lastSubmittedIds: List<String>? = null

  // --- Lifecycle ------------------------------------------------------------
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Initialize collaborators
    idGenerator = QeponIdGenerator(CHARS, secureRandom)
    gridRenderer = SeedGridRenderer(
      rootViewProvider = { requireView() },
      tableId = R.id.seedCharsTable,
      gridSize = GRID_SIZE,
      initialCharProvider = { row, _ -> CHARS[row].toString() }
    )

    gridRenderer.renderInitialGrid()

    // Input handling is minimal here: delegate to generator + renderer
    view.setOnTouchListener { _, event ->
      if (event.action == MotionEvent.ACTION_MOVE) {
        handleTouchMove(event)
        true
      } else true
    }

    initViewModel()

  }

  // --- Input Handling -------------------------------------------------------
  private fun handleTouchMove(event: MotionEvent) {
    idGenerator.step()
    if (idGenerator.hasEnoughIds(MIN_IDS)) {
      // Once we have enough, trigger the next flow and stop listening
      view?.setOnTouchListener(null)
      goToRegistrationQeponIdFragment(idGenerator.drainIds())
      return
    }
    val x = event.x.toInt()
    val y = event.y.toInt()
    gridRenderer.updateGrid { row, col ->
      val index = (x + y + row + col + secureRandom.nextInt(CHARS.size)) % CHARS.size
      CHARS[index].toString()
    }
  }

  // --- Navigation/Next Step -------------------------------------------------
  private fun goToRegistrationQeponIdFragment(qeponIds: List<String>) {
    try {
      lastSubmittedIds = qeponIds // simpan sebelum di-drain
      val usernameCandidates: List<Username> = qeponIds
        .map { "$it.2025" }
        .map { Username(it) }
      val hashes: List<String> = usernameCandidates
        .map { Base64.encodeUrlSafeWithoutPadding(it.hash) }
      Log.i(TAG, "QEpon IDs: $qeponIds")
      Log.i(TAG, "Username candidates: $usernameCandidates")
      Log.i(TAG, "Hashes: $hashes")
      sharedViewModel.registerQeponId(requireContext(), hashes)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to prepare qepon IDs: ${e.message}")
    }
  }

  // --- Helpers --------------------------------------------------------------
  private class SeedGridRenderer(
    private val rootViewProvider: () -> View,
    private val tableId: Int,
    private val gridSize: Int,
    private val initialCharProvider: (row: Int, col: Int) -> String
  ) {
    private val textViewIds: Array<IntArray> = Array(gridSize) { IntArray(gridSize) }
    fun renderInitialGrid() {
      val table = rootViewProvider().findViewById<TableLayout>(tableId)
      table.removeAllViews()

      for (rowIdx in 0 until gridSize) {
        val row = TableRow(table.context).apply {
          layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT)
        }
        for (colIdx in 0 until gridSize) {
          val id = View.generateViewId()
          textViewIds[rowIdx][colIdx] = id

          val cell = TextView(table.context).apply {
            this.id = id
            text = initialCharProvider(rowIdx, colIdx)
            setTextColor(ContextCompat.getColor(table.context, R.color.white))
            gravity = Gravity.CENTER
            setPadding(2, 2, 2, 2)
            width = 40
          }
          row.addView(cell, colIdx)
        }
        table.addView(row)
      }
    }

    fun updateGrid(charProvider: (row: Int, col: Int) -> String) {
      val root = rootViewProvider()
      for (rowIdx in 0 until gridSize) {
        for (colIdx in 0 until gridSize) {
          val tv: TextView? = root.findViewById(textViewIds[rowIdx][colIdx])
          tv?.text = charProvider(rowIdx, colIdx)
        }
      }
    }
  }

  private class QeponIdGenerator(
    private val candidateChars: CharArray,
    private val rng: SecureRandom
  ) {
    private val buffer = StringBuilder(MAX_ID_LENGTH)
    private val collected = mutableListOf<String>()

    fun step() {
      if (buffer.length < MAX_ID_LENGTH) {
        val next = candidateChars[rng.nextInt(candidateChars.size)]
        buffer.append(next)
      }

      if (buffer.length >= MAX_ID_LENGTH) {
        val candidate = buffer.toString()
        // Rule: only accept if first char is not a digit
        if (candidate.firstOrNull()?.isDigit() == false) {
          collected.add(candidate)
        }
        buffer.clear()
      }
    }


  fun hasEnoughIds(target: Int): Boolean = collected.size >= target

  fun drainIds(): List<String> {
    val out = collected.toList()
    collected.clear()
    buffer.clear()
    return out
  }
}

  private fun genericErrorDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setMessage(R.string.RegistrationActivity_error_connecting_to_service)
      .setPositiveButton(android.R.string.ok, null)
      .create()
      .show()
  }

  private fun initViewModel(){
    LiveDataUtil
      .combineLatest(sharedViewModel.uiState, sharedViewModel.uiState) { reg, qeponReg -> reg to qeponReg }
      .observe(viewLifecycleOwner) { (registrationState) -> updateViewState(registrationState) }
  }



  private fun updateViewState(state: RegistrationState) {
    showLoading(state)

    val result = state.qeponRegistrationResult

    if (result != null) {
      when (result) {
        is QeponRegisterAccountResult.Success -> {
          Toast.makeText(requireContext(), "✅ Success: Qepon ID registered", Toast.LENGTH_SHORT).show()
          Log.d("QeponResult", "QEPON HASH: ${result.accountRegistrationResult.usernameHash}")

          val returnedHash = result.accountRegistrationResult.usernameHash
          val matched = lastSubmittedIds?.firstOrNull { submittedId ->
            val submittedHash = Base64.encodeUrlSafeWithoutPadding(Username("$submittedId.2025").hash)
            submittedHash == returnedHash
          }
          Log.d("QeponResult", "✅ Matched ID from submitted list: $matched")
          SignalStore.account.username = "$matched.2025"

          matched?.let {
            sharedViewModel.setMatchedQeponId(it)
            findNavController().navigate(R.id.action_generateIdFragment_to_displayQeponIdFragment)
            Log.d(TAG, "Current checkpoint: ${sharedViewModel.checkpoint.value}")
          }
        }

        else -> {
          Log.e("QeponResult", "Unhandled result: ${result::class.simpleName}")
          genericErrorDialog()
        }
      }
      // ✅ Reset agar tidak diproses berulang
      sharedViewModel.clearQeponRegistrationResult()
    }
    // Tangani hal lain seperti progress bar, error biasa, dsb.
    //binding.progressBar.isVisible = state.inProgress
  }

  private fun showLoading(state: RegistrationState) {
    val shouldShow = state.inProgress
    val currentAlpha = binding.loadingOverlay.alpha

    // 1️⃣ Kalau sedang loading dan belum kelihatan → fade-in
    if (shouldShow && currentAlpha == 0f) {
      binding.loadingOverlay.apply {
        isVisible = true
        animate()
          .alpha(1f)
          .setDuration(200)
          .setListener(null)
      }
    }

    // 2️⃣ Kalau loading selesai dan masih kelihatan → fade-out
    if (!shouldShow && currentAlpha == 1f) {
      binding.loadingOverlay.animate()
        .alpha(0f)
        .setDuration(200)
        .setListener(object : AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: Animator) {
            binding.loadingOverlay.isVisible = false
          }
        })
    }

    binding.root.isEnabled = !shouldShow
  }


  companion object {
    private val TAG = Log.tag(GenerateIdFragment::class.java)

    // Configuration constants
    private const val GRID_SIZE = 16
    private const val MAX_ID_LENGTH = 10
    private const val MIN_IDS = 10

    // Excludes easily confusable chars like 0, O, I per original design intent
    private val CHARS: CharArray = charArrayOf(
      '1','2','3','4','5','6','7','8','9',
      'A','B','C','D','E','F','G','H',
      'J','K','L','M','N','P','Q','R','S',
      'T','U','V','W','X','Y','Z'
    )
  }
}



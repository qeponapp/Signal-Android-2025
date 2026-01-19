package org.thoughtcrime.securesms.registration.ui.recovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.thoughtcrime.securesms.databinding.FragmentImportRecoveryBinding
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.util.Medium
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.AccountEntropyPool
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.signal.libsignal.usernames.Username
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.jobs.RotateCertificateJob
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.data.network.QeponRegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.generateid.GenerateIdFragment
import org.thoughtcrime.securesms.service.DirectoryRefreshListener
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener
import org.thoughtcrime.securesms.util.SignalE164Util
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import kotlin.getValue


class ImportRecoveryFragment : Fragment() {

  private val sharedViewModel: RegistrationViewModel by activityViewModels()
  private var binding: FragmentImportRecoveryBinding? = null
  private var selectedUri: Uri? = null

  private val pickZipLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      selectedUri = uri
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
      importRecovery()
    }

    return fragmentBinding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }

  private fun importRecovery() {
    val password = binding?.passwordEditText?.text?.toString().orEmpty()
    val uri = selectedUri

    if (uri == null) {
      Toast.makeText(requireContext(), "Please select a recovery zip file.", Toast.LENGTH_SHORT).show()
      return
    }

    if (password.isBlank()) {
      Toast.makeText(requireContext(), "Password required.", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch {
      try {
        val payload = withContext(Dispatchers.IO) {
          readRecoveryPayload(uri, password)
        }
        Log.e("IMPORT", "Payload: $payload")
        //testNumber(payload)
        applyPayload(payload)
        Toast.makeText(requireContext(), "Recovery data imported.", Toast.LENGTH_LONG).show()
        navigateToDisplayQepon(payload)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to import recovery", e)
        Toast.makeText(requireContext(), "Failed to import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }

  fun normalizeE164(raw: String): String =
    raw.trim().replace("\\s+".toRegex(), "")

  fun fixLeadingZeroCountryCode(e164: String): String {
    return if (e164.startsWith("+0")) {
      "+628" + e164.drop(2)
    } else {
      e164
    }
  }
  private fun readRecoveryPayload(uri: Uri, password: String): RecoveryPayload {
    val context = requireContext()
    val tempFile = File.createTempFile("recovery", ".zip", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
      tempFile.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("Unable to open selected file")

    val zipFile = ZipFile(tempFile, password.toCharArray())
    val header: FileHeader = zipFile.fileHeaders.firstOrNull { it.fileName == "recovery.json" }
      ?: throw IllegalStateException("recovery.json not found in zip")

    val json = zipFile.getInputStream(header).use { stream ->
      stream.readBytes().toString(StandardCharsets.UTF_8)
    }

    val jsonObject = JSONObject(json)
    return RecoveryPayload.fromJson(jsonObject)
  }

  private fun fixNumber(jsonNumber:String): String{
    val phoneUtil = PhoneNumberUtil.getInstance()

    //val raw = jsonNumber.e164
    val normalized = jsonNumber?.let { normalizeE164(it) }
    val fixed = normalized?.let { fixLeadingZeroCountryCode(it) }

    android.util.Log.e("IMPORT", "Original: $jsonNumber")
    android.util.Log.e("IMPORT", "Fixed: $fixed")

    val phoneNumber = try {
      phoneUtil.parse(fixed, null)
    } catch (e: Exception) {
      Log.e("IMPORT", "Failed to parse after fix: $fixed", e)
      return ""
    }

    if (!phoneUtil.isValidNumber(phoneNumber)) {
      Log.e("IMPORT", "Invalid number after fix: $fixed")
      return ""
    }

    val e164Final = phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
    Log.d("IMPORT", "Trusted E164: $e164Final")
    //Log.e("IMPORT", "Trusted E164: ${phoneUtil.format(trustedE164, PhoneNumberUtil.PhoneNumberFormat.E164)}")
    return e164Final
  }
  private fun applyPayload(payload: RecoveryPayload) {
    val account = SignalStore.account
    val svr = SignalStore.svr

    val aci = payload.aci ?: return
    val pni = payload.pni
    account.setAci(aci)
    pni?.let { account.setPni(it) }

    payload.username?.takeIf { it.isNotBlank() }?.let { account.username = it }
    payload.qeponId?.takeIf { it.isNotBlank() }?.let { account.username = it }
    payload.servicePassword?.takeIf { it.isNotBlank() }?.let { account.setServicePassword(it) }
    payload.registrationId?.let { account.registrationId = it }
    payload.pniRegistrationId?.let { account.pniRegistrationId = it }
    payload.accountEntropyPool?.let { account.restoreAccountEntropyPool(it) }

    /*val trustedE164: String? = payload.e164?.takeIf { it.isNotBlank() && (it.startsWith("+0") || SignalE164Util.isPotentialE164(it)) }
    trustedE164?.let { account.setE164(it) }*/

    val trustedE164 = fixNumber(payload.e164)
    account.setE164(trustedE164)

    runCatching {
      val recipientTable = SignalDatabase.recipients
      val pniForLink = pni ?: throw IllegalStateException("PNI missing; cannot link self recipient")
      val self = Recipient.trustedPush(aci, pniForLink, trustedE164)
      val selfId = self.id
      recipientTable.setProfileSharing(selfId, true)
      recipientTable.markRegisteredOrThrow(selfId, aci)
      recipientTable.linkIdsForSelf(aci, pniForLink, trustedE164)

      payload.profileKey?.let { keyBytes ->
        runCatching { ProfileKey(keyBytes) }.getOrNull()?.let { pk ->
          recipientTable.setProfileKey(selfId, pk)
        }
      }
      AppDependencies.recipientCache.clearSelf()
    }.onFailure { e -> Log.w(TAG, "Failed to set up self recipient during import", e) }

    if (payload.svr2AuthTokens.isNotEmpty()) {
      svr.putSvr2AuthTokens(payload.svr2AuthTokens)
    }
    if (payload.svr3AuthTokens.isNotEmpty()) {
      svr.putSvr3AuthTokens(payload.svr3AuthTokens)
    }

    /*payload.profileKey?.let { keyBytes ->
      runCatching { ProfileKey(keyBytes) }.getOrNull()?.let { pk ->
        runCatching { Recipient.self().id }.getOrNull()?.let { selfId ->
          SignalDatabase.recipients.setProfileKey(selfId, pk)
        } ?: Log.w(TAG, "Unable to resolve self recipient for profile key apply.")
      }
    }*/


    TextSecurePreferences.setPromptedPushRegistration(context, true)
    TextSecurePreferences.setUnauthorizedReceived(context, false)
    NotificationManagerCompat.from(requireContext()).cancel(NotificationIds.UNREGISTERED_NOTIFICATION_ID)

    AppDependencies.resetNetwork()
    AppDependencies.startNetwork()
    PreKeysSyncJob.enqueue()

    val jobManager = AppDependencies.jobManager
    jobManager.add(DirectoryRefreshJob(false))
    jobManager.add(RotateCertificateJob())

    DirectoryRefreshListener.schedule(context)
    RotateSignedPreKeyListener.schedule(context)
  }

  private fun navigateToDisplayQepon(payload: RecoveryPayload) {
    // Mark registration as complete to satisfy profile uploads and jobs.
    //SignalStore.registration.markRegistrationComplete()
    //SignalStore.registration.setLocalRegistrationMetadata(null)
    //SignalStore.registration.setRestoreMethodToken(null)
    /*if (!SignalStore.account.isRegistered) {
      SignalStore.account.setRegistered(true)

    }*/
    runCatching { Recipient.self().live().refresh() }.onFailure {
      Log.w(TAG, "Recipient self refresh failed", it)
    }


    val qeponToShow = payload.username.orEmpty()
    SignalStore.account.username = qeponToShow
    sharedViewModel.setMatchedQeponId(qeponToShow)
    SignalStore.account.setRegistered(true)
    sharedViewModel.completeRegistration()
    Log.e("IMPORT", "Qepon to show: $qeponToShow")
    findNavController().navigate(R.id.action_importRecoveryFragment_to_displayQeponIdFragment)
    //sharedViewModel.clearQeponRegistrationResult()
  }

  companion object {
    private val TAG = Log.tag(ImportRecoveryFragment::class.java)
  }
}

private data class RecoveryPayload(
  val username: String?,
  val qeponId: String?,
  val e164: String,
  val aci: ACI?,
  val pni: PNI?,
  val servicePassword: String?,
  val registrationId: Int?,
  val pniRegistrationId: Int?,
  val aciIdentityKey: IdentityKeyPair?,
  val pniIdentityKey: IdentityKeyPair?,
  val profileKey: ByteArray?,
  val accountEntropyPool: AccountEntropyPool?,
  val svr2AuthTokens: List<String>,
  val svr3AuthTokens: List<String>,
  val registrationLockToken: String?,
  val recoveryPassword: String?,
  val registeredAndUpToDate: Boolean
) {
  companion object {
    fun fromJson(json: JSONObject): RecoveryPayload {
      val decode = { key: String -> decodeBase64Flexible(json.optString(key, null)) }

      val aci = json.optString("aci", null)?.takeIf { it.isNotBlank() }?.let { ACI.parseOrNull(it) }
      val pni = json.optString("pni", null)?.takeIf { it.isNotBlank() }?.let { PNI.parseOrNull(it) }

      val aciIdentity = buildIdentityKeyPair(
        decode("identityAciPublic"),
        decode("identityAciPrivate")
      )
      val pniIdentity = buildIdentityKeyPair(
        decode("identityPniPublic"),
        decode("identityPniPrivate")
      )

      val entropy = json.optString("accountEntropyPool", null)?.takeIf { it.isNotBlank() }?.let { AccountEntropyPool(it) }

      return RecoveryPayload(
        username = json.optString("username", null),
        qeponId = json.optString("qeponId", null),
        e164 = json.optString("e164", null),
        aci = aci,
        pni = pni,
        servicePassword = json.optString("servicePassword", null),
        registrationId = json.optInt("registrationId", Medium.MAX_VALUE).takeIf { json.has("registrationId") },
        pniRegistrationId = json.optInt("pniRegistrationId", Medium.MAX_VALUE).takeIf { json.has("pniRegistrationId") },
        aciIdentityKey = aciIdentity,
        pniIdentityKey = pniIdentity,
        profileKey = decode("profileKey"),
        accountEntropyPool = entropy,
        svr2AuthTokens = parseStringArray(json, "svr2AuthTokens"),
        svr3AuthTokens = parseStringArray(json, "svr3AuthTokens"),
        registrationLockToken = json.optString("registrationLockToken", null),
        recoveryPassword = json.optString("recoveryPassword", null),
        registeredAndUpToDate = json.optBoolean("registeredAndUpToDate", false)
      )
    }

    private fun buildIdentityKeyPair(publicBytes: ByteArray?, privateBytes: ByteArray?): IdentityKeyPair? {
      if (publicBytes == null || privateBytes == null) return null
      return IdentityKeyPair(
        IdentityKey(publicBytes, 0),
        Curve.decodePrivatePoint(privateBytes)
      )
    }

    private fun parseStringArray(json: JSONObject, key: String): List<String> {
      val array = json.optJSONArray(key) ?: return emptyList()
      val result = ArrayList<String>(array.length())
      for (i in 0 until array.length()) {
        array.optString(i)?.takeIf { it.isNotBlank() }?.let { result.add(it) }
      }
      return result
    }

    private fun decodeBase64Flexible(value: String?): ByteArray? {
      if (value.isNullOrBlank()) return null
      val padded = if (value.length % 4 == 0) value else value.padEnd(value.length + (4 - value.length % 4), '=')
      return runCatching { Base64.decode(padded) }.getOrNull()
    }
  }
}

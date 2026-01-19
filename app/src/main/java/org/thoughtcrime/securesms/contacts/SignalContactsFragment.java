/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode;
import org.thoughtcrime.securesms.contacts.paged.ChatType;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientRepository;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.Optional;
import java.util.function.Consumer;

public class SignalContactsFragment extends LoggingFragment implements ContactSelectionListFragment.OnContactSelectedListener {

  private ContactSelectionListFragment contactsFragment;
  private ContactFilterView            contactFilterView;

  public SignalContactsFragment() {
    super(R.layout.signal_contacts_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text);
    contactFilterView.setOnFilterChangedListener(filter -> {
      if (contactsFragment != null) {
        contactsFragment.setQueryFilter(filter);
      }
    });

    FragmentManager fragmentManager = getChildFragmentManager();
    ContactSelectionListFragment existing = (ContactSelectionListFragment) fragmentManager.findFragmentById(R.id.contact_selection_list_container);
    if (existing != null) {
      contactsFragment = existing;
      return;
    }

    Bundle args = new Bundle();
    int displayMode = ContactSelectionDisplayMode.FLAG_PUSH | ContactSelectionDisplayMode.FLAG_HIDE_NEW;
    args.putInt(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
    args.putBoolean(ContactSelectionListFragment.REFRESHABLE, false);

    contactsFragment = new ContactSelectionListFragment();
    contactsFragment.setArguments(args);

    fragmentManager
        .beginTransaction()
        .replace(R.id.contact_selection_list_container, contactsFragment)
        .commitNow();
  }

  @Override
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey,
                                      @NonNull Optional<RecipientId> recipientId,
                                      @Nullable String number,
                                      @NonNull Optional<ChatType> chatType,
                                      @NonNull Consumer<Boolean> callback) {
    if (recipientId.isPresent()) {
      launch(recipientId.get());
      callback.accept(true);
      return;
    }

    if (number != null && SignalStore.account().isRegistered()) {
      AlertDialog progress = SimpleProgressDialog.show(requireContext());

      SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> RecipientRepository.lookupNewE164(number), result -> {
        progress.dismiss();

        if (result instanceof RecipientRepository.LookupResult.Success) {
          Recipient resolved = Recipient.resolved(((RecipientRepository.LookupResult.Success) result).getRecipientId());
          if (resolved.isRegistered() && resolved.getHasServiceId()) {
            launch(resolved.getId());
          }
        } else if (result instanceof RecipientRepository.LookupResult.NotFound || result instanceof RecipientRepository.LookupResult.InvalidEntry) {
          new MaterialAlertDialogBuilder(requireContext())
              .setMessage(getString(R.string.NewConversationActivity__s_is_not_a_signal_user, number))
              .setPositiveButton(android.R.string.ok, null)
              .show();
        } else {
          new MaterialAlertDialogBuilder(requireContext())
              .setMessage(R.string.NetworkFailure__network_error_check_your_connection_and_try_again)
              .setPositiveButton(android.R.string.ok, null)
              .show();
        }
      });
    }

    callback.accept(true);
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType) {
  }

  @Override
  public void onSelectionChanged() {
  }

  private void launch(@NonNull RecipientId recipientId) {
    startActivity(ConversationIntents.createBuilderSync(requireContext(), recipientId, -1L).build());

    if (contactsFragment != null) {
      contactsFragment.reset();
    }
  }
}

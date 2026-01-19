/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist;

import androidx.lifecycle.ViewModelStoreOwner;

import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter;
import org.thoughtcrime.securesms.main.MainNavigationListLocation;

public class GroupConversationListFragment extends ConversationListFragment {

  @Override
  protected ConversationFilter getInitialConversationFilter() {
    return ConversationFilter.GROUPS;
  }

  @Override
  protected MainNavigationListLocation getListLocationForTabClick() {
    return MainNavigationListLocation.GROUPS;
  }

  @Override
  protected ViewModelStoreOwner getViewModelStoreOwner() {
    return this;
  }
}

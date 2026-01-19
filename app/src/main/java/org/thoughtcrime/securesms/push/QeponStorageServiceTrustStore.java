package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.InputStream;

public class QeponStorageServiceTrustStore implements TrustStore {

  private final Context context;

  public QeponStorageServiceTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.storage_service);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }
}

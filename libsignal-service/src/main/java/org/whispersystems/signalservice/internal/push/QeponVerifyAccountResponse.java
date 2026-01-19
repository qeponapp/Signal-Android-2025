package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class QeponVerifyAccountResponse {
  @JsonProperty
  public String uuid;

  @JsonProperty
  public String pni;

  @JsonProperty
  public boolean storageCapable;

  @JsonProperty
  public String number;

  @JsonProperty
  public String usernameHash;

  @JsonProperty
  public String usernameLinkHandle;

  @JsonCreator
  public QeponVerifyAccountResponse() {}

  public QeponVerifyAccountResponse(String uuid, String pni, String usernameHash,String usernameLinkHandle, boolean storageCapable) {
    this.uuid           = uuid;
    this.pni            = pni;
    this.usernameHash   = usernameHash;
    this.storageCapable = storageCapable;
  }

  public String getUuid() {
    return uuid;
  }

  public boolean isStorageCapable() {
    return storageCapable;
  }

  public String getPni() {
    return pni;
  }
}

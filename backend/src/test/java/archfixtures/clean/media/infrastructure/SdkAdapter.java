package archfixtures.clean.media.infrastructure;

import archfixtures.sdk.SdkClient;

public class SdkAdapter {
  private final SdkClient client = new SdkClient();

  public SdkClient client() {
    return client;
  }
}

package outboxfixtures;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the recording listener; imported explicitly by the tests that need it. */
@TestConfiguration(proxyBeanMethods = false)
public class OutboxProbeConfiguration {

  @Bean
  RecordingOutboxListener recordingOutboxListener() {
    return new RecordingOutboxListener();
  }
}

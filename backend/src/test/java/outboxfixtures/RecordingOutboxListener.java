package outboxfixtures;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.modulith.events.ApplicationModuleListener;

/** Records the events it handles and can be told to fail, to exercise redelivery. */
public class RecordingOutboxListener {

  private final Set<String> handled = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final AtomicBoolean failing = new AtomicBoolean(false);

  @ApplicationModuleListener
  public void on(OutboxProbeEvent event) {
    if (failing.get()) {
      throw new IllegalStateException("simulated consumer failure");
    }
    handled.add(event.id());
  }

  public Set<String> handled() {
    return handled;
  }

  public void failing(boolean value) {
    failing.set(value);
  }
}

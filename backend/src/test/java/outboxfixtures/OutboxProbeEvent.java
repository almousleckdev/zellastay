package outboxfixtures;

/** An event published in a transaction to observe how the outbox delivers it. */
public record OutboxProbeEvent(String id) {}

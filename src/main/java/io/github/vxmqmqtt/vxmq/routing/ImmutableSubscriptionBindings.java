package io.github.vxmqmqtt.vxmq.routing;

import java.util.HashMap;
import java.util.Map;

/**
 * Compact immutable binding container optimized for the common empty and single-binding cases.
 */
final class ImmutableSubscriptionBindings {

    private static final ImmutableSubscriptionBindings EMPTY =
            new ImmutableSubscriptionBindings(0, null, null, null);

    private final int size;
    private final String singleClientId;
    private final SubscriptionBinding singleBinding;
    private final Map<String, SubscriptionBinding> manyBindings;

    private ImmutableSubscriptionBindings(
            int size,
            String singleClientId,
            SubscriptionBinding singleBinding,
            Map<String, SubscriptionBinding> manyBindings) {
        this.size = size;
        this.singleClientId = singleClientId;
        this.singleBinding = singleBinding;
        this.manyBindings = manyBindings;
    }

    static ImmutableSubscriptionBindings empty() {
        return EMPTY;
    }

    static ImmutableSubscriptionBindings from(Map<String, SubscriptionBinding> bindings) {
        if (bindings.isEmpty()) {
            return empty();
        }
        if (bindings.size() == 1) {
            Map.Entry<String, SubscriptionBinding> entry = bindings.entrySet().iterator().next();
            return singleton(entry.getKey(), entry.getValue());
        }
        return new ImmutableSubscriptionBindings(bindings.size(), null, null, new HashMap<>(bindings));
    }

    boolean isEmpty() {
        return size == 0;
    }

    SubscriptionBinding get(String clientId) {
        return switch (size) {
            case 0 -> null;
            case 1 -> singleClientId.equals(clientId) ? singleBinding : null;
            default -> manyBindings.get(clientId);
        };
    }

    ImmutableSubscriptionBindings put(SubscriptionBinding binding) {
        return switch (size) {
            case 0 -> singleton(binding.clientId(), binding);
            case 1 -> putIntoSingleton(binding);
            default -> putIntoMany(binding);
        };
    }

    RemoveResult remove(String clientId) {
        return switch (size) {
            case 0 -> new RemoveResult(this, false);
            case 1 -> removeFromSingleton(clientId);
            default -> removeFromMany(clientId);
        };
    }

    void mergeInto(Map<String, SubscriptionBinding> deduplicated) {
        switch (size) {
            case 0 -> {
            }
            case 1 -> mergeBinding(singleBinding, deduplicated);
            default -> manyBindings.values().forEach(binding -> mergeBinding(binding, deduplicated));
        }
    }

    private ImmutableSubscriptionBindings putIntoSingleton(SubscriptionBinding binding) {
        if (singleClientId.equals(binding.clientId())) {
            return binding.equals(singleBinding) ? this : singleton(binding.clientId(), binding);
        }

        Map<String, SubscriptionBinding> bindings = new HashMap<>(4);
        bindings.put(singleClientId, singleBinding);
        bindings.put(binding.clientId(), binding);
        return new ImmutableSubscriptionBindings(2, null, null, bindings);
    }

    private ImmutableSubscriptionBindings putIntoMany(SubscriptionBinding binding) {
        SubscriptionBinding existing = manyBindings.get(binding.clientId());
        if (binding.equals(existing)) {
            return this;
        }

        Map<String, SubscriptionBinding> bindings = new HashMap<>(manyBindings);
        bindings.put(binding.clientId(), binding);
        return new ImmutableSubscriptionBindings(bindings.size(), null, null, bindings);
    }

    private RemoveResult removeFromSingleton(String clientId) {
        if (!singleClientId.equals(clientId)) {
            return new RemoveResult(this, false);
        }
        return new RemoveResult(empty(), true);
    }

    private RemoveResult removeFromMany(String clientId) {
        if (!manyBindings.containsKey(clientId)) {
            return new RemoveResult(this, false);
        }

        Map<String, SubscriptionBinding> bindings = new HashMap<>(manyBindings);
        bindings.remove(clientId);
        return new RemoveResult(from(bindings), true);
    }

    private static ImmutableSubscriptionBindings singleton(String clientId, SubscriptionBinding binding) {
        return new ImmutableSubscriptionBindings(1, clientId, binding, null);
    }

    private static void mergeBinding(
            SubscriptionBinding binding,
            Map<String, SubscriptionBinding> deduplicated) {
        deduplicated.merge(binding.clientId(), binding, (left, right) ->
                left.grantedQos().value() >= right.grantedQos().value() ? left : right);
    }

    record RemoveResult(ImmutableSubscriptionBindings bindings, boolean removed) {
    }
}

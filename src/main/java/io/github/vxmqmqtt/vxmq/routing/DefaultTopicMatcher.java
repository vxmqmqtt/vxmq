package io.github.vxmqmqtt.vxmq.routing;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Minimal MQTT topic matcher covering wildcard validation and matching rules for M1.
 */
@ApplicationScoped
public class DefaultTopicMatcher implements TopicMatcher {

    @Override
    public boolean isValidFilter(String topicFilter) {
        if (topicFilter == null || topicFilter.isEmpty()) {
            return false;
        }

        // MQTT wildcards must occupy the whole topic level, and '#' can only appear at the end.
        String[] levels = topicFilter.split("/", -1);
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            if (level.contains("#") && (!"#".equals(level) || i != levels.length - 1)) {
                return false;
            }
            if (level.contains("+") && !"+".equals(level)) {
                return false;
            }
        }
        return !topicFilter.startsWith("$share/");
    }

    @Override
    public boolean isValidTopicName(String topicName) {
        return topicName != null && !topicName.isEmpty() && !topicName.contains("+") && !topicName.contains("#");
    }

    @Override
    public boolean matches(String topicFilter, String topicName) {
        if (!isValidFilter(topicFilter) || !isValidTopicName(topicName)) {
            return false;
        }

        String[] filterLevels = topicFilter.split("/", -1);
        String[] topicLevels = topicName.split("/", -1);
        int filterIndex = 0;
        int topicIndex = 0;

        // Walk both topic paths together and stop early on mismatch or multi-level wildcard.
        while (filterIndex < filterLevels.length && topicIndex < topicLevels.length) {
            String filterLevel = filterLevels[filterIndex];
            if ("#".equals(filterLevel)) {
                return true;
            }
            if (!"+".equals(filterLevel) && !filterLevel.equals(topicLevels[topicIndex])) {
                return false;
            }
            filterIndex++;
            topicIndex++;
        }

        if (filterIndex == filterLevels.length && topicIndex == topicLevels.length) {
            return true;
        }

        return filterIndex == filterLevels.length - 1 && "#".equals(filterLevels[filterIndex]);
    }
}

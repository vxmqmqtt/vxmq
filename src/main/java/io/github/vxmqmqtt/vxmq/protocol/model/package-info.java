/**
 * Transport-neutral MQTT protocol models.
 *
 * <p>Model naming rules:
 * <ul>
 *   <li>{@code *Request}: normalized inbound wire input without side effects</li>
 *   <li>{@code *Response}/{@code *Acknowledgement}: fields written directly to the wire</li>
 *   <li>{@code *Plan}/{@code *Action}: transport follow-up work triggered by a protocol decision</li>
 *   <li>{@code *Outcome}: protocol result composed from response/acknowledgement and plan/action objects</li>
 * </ul>
 */
package io.github.vxmqmqtt.vxmq.protocol.model;

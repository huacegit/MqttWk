package cn.wizzer.iot.mqtt.server.broker.cluster;

import cn.wizzer.iot.mqtt.server.broker.config.BrokerProperties;
import cn.wizzer.iot.mqtt.server.broker.internal.InternalMessage;
import cn.wizzer.iot.mqtt.server.common.subscribe.SubscribeStore;
import cn.wizzer.iot.mqtt.server.store.message.MessageIdService;
import cn.wizzer.iot.mqtt.server.store.session.SessionStoreService;
import cn.wizzer.iot.mqtt.server.store.subscribe.SubscribeStoreService;
import com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.mqtt.*;
import org.nutz.aop.interceptor.async.Async;
import org.nutz.integration.jedis.pubsub.PubSub;
import org.nutz.integration.jedis.pubsub.PubSubService;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Created by wizzer on 2018
 */
@IocBean(create = "init")
public class RedisCluster implements PubSub {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCluster.class);
    private static final String CLUSTER_TOPIC = "mqttwk:cluster";
    @Inject
    private PubSubService pubSubService;
    @Inject
    private SubscribeStoreService subscribeStoreService;
    @Inject
    private SessionStoreService sessionStoreService;
    @Inject
    private MessageIdService messageIdService;
    @Inject
    private BrokerProperties brokerProperties;
    @Inject
    private ChannelGroup channelGroup;
    @Inject
    private Map<String, ChannelId> channelIdMap;

    public void init() {
        pubSubService.reg(CLUSTER_TOPIC, this);
    }

    @Override
    public void onMessage(String channel, String message) {
        InternalMessage internalMessage = JSONObject.parseObject(message, InternalMessage.class);
        this.sendPublishMessage(internalMessage.getClientId(), internalMessage.getTopic(), MqttQoS.valueOf(internalMessage.getMqttQoS()), internalMessage.getMessageBytes(), internalMessage.isRetain(), internalMessage.isDup());
    }

    @Async
    public void sendMessage(InternalMessage internalMessage) {
        pubSubService.fire(CLUSTER_TOPIC, JSONObject.toJSONString(internalMessage));
    }

    private void sendPublishMessage(String clientId, String topic, MqttQoS mqttQoS, byte[] messageBytes, boolean retain, boolean dup) {
        List<SubscribeStore> subscribeStores = subscribeStoreService.search(topic);
        subscribeStores.forEach(subscribeStore -> {
            if (!clientId.equals(subscribeStore.getClientId()) && sessionStoreService.containsKey(subscribeStore.getClientId())) {
                // 订阅者收到MQTT消息的QoS级别, 最终取决于发布消息的QoS和主题订阅的QoS
                MqttQoS respQoS = mqttQoS.value() > subscribeStore.getMqttQoS() ? MqttQoS.valueOf(subscribeStore.getMqttQoS()) : mqttQoS;
                if (respQoS == MqttQoS.AT_MOST_ONCE) {
                    MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
                            new MqttPublishVariableHeader(topic, 0), ByteBuffer.wrap(messageBytes));
                    LOGGER.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}", subscribeStore.getClientId(), topic, respQoS.value());
                    ChannelId channelId = channelIdMap.get(sessionStoreService.get(subscribeStore.getClientId()).getChannelId());
                    if (channelId != null) {
                        Channel channel = channelGroup.find(channelId);
                        if (channel != null) channel.writeAndFlush(publishMessage);
                    }
                }
                if (respQoS == MqttQoS.AT_LEAST_ONCE) {
                    int messageId = messageIdService.getNextMessageId();
                    MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
                            new MqttPublishVariableHeader(topic, messageId), ByteBuffer.wrap(messageBytes));
                    LOGGER.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", subscribeStore.getClientId(), topic, respQoS.value(), messageId);
                    ChannelId channelId = channelIdMap.get(sessionStoreService.get(subscribeStore.getClientId()).getChannelId());
                    if (channelId != null) {
                        Channel channel = channelGroup.find(channelId);
                        if (channel != null) channel.writeAndFlush(publishMessage);
                    }
                }
                if (respQoS == MqttQoS.EXACTLY_ONCE) {
                    int messageId = messageIdService.getNextMessageId();
                    MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
                            new MqttPublishVariableHeader(topic, messageId), ByteBuffer.wrap(messageBytes));
                    LOGGER.debug("PUBLISH - clientId: {}, topic: {}, Qos: {}, messageId: {}", subscribeStore.getClientId(), topic, respQoS.value(), messageId);
                    ChannelId channelId = channelIdMap.get(sessionStoreService.get(subscribeStore.getClientId()).getChannelId());
                    if (channelId != null) {
                        Channel channel = channelGroup.find(channelId);
                        if (channel != null) channel.writeAndFlush(publishMessage);
                    }
                }
            }
        });
    }


}

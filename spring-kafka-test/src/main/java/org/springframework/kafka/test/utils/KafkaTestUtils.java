/*
 * Copyright 2016-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.kafka.test.utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.util.Assert;

/**
 * Kafka testing utilities.
 *
 * @author Gary Russell
 * @author Hugo Wood
 * @author Artem Bilan
 * @author Sanghyeok An
 * @author Mikhail Polivakha
 */
public final class KafkaTestUtils {

	private static final int TEN = 10;

	private static final LogAccessor logger = new LogAccessor(LogFactory.getLog(KafkaTestUtils.class)); // NOSONAR

	private static final Properties defaults = new Properties();

	static {
		defaults.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
	}

	private KafkaTestUtils() {
	}

	/**
	 * Set up test properties for an {@code <Integer, String>} consumer.
	 * @param group the group id.
	 * @param autoCommit the auto commit.
	 * @param embeddedKafka a {@link EmbeddedKafkaBroker} instance.
	 * @return the properties.
	 * @deprecated please, use {@link #consumerProps(EmbeddedKafkaBroker, String, boolean)} instead
	 */
	@Deprecated(forRemoval = true, since = "4.0.0")
	public static Map<String, Object> consumerProps(String group, String autoCommit,
			EmbeddedKafkaBroker embeddedKafka) {

		return consumerProps(embeddedKafka.getBrokersAsString(), group, autoCommit);
	}

	/**
	 * Set up test properties for an {@code <Integer, String>} consumer.
	 *
	 * @param group the group id.
	 * @param autoCommit the auto commit.
	 * @param embeddedKafka a {@link EmbeddedKafkaBroker} instance.
	 * @return the properties.
	 * @since 4.0
	 */
	public static Map<String, Object> consumerProps(EmbeddedKafkaBroker embeddedKafka, String group,
			boolean autoCommit) {

		return consumerProps(embeddedKafka.getBrokersAsString(), group, autoCommit);
	}

	/**
	 * Set up test properties for an {@code <Integer, String>} consumer.
	 * @param brokers the bootstrapServers property.
	 * @param group the group id.
	 * @return the properties.
	 * @since 3.3
	 */
	public static Map<String, Object> consumerProps(String brokers, String group) {
		return consumerProps(brokers, group, false);
	}

	/**
	 * Set up test properties for an {@code <Integer, String>} producer.
	 * @param embeddedKafka a {@link EmbeddedKafkaBroker} instance.
	 * @return the properties.
	 */
	public static Map<String, Object> producerProps(EmbeddedKafkaBroker embeddedKafka) {
		return producerProps(embeddedKafka.getBrokersAsString());
	}

	/**
	 * Set up test properties for an {@code <Integer, String>} consumer.
	 * @param brokers the bootstrapServers property.
	 * @param group the group id.
	 * @param autoCommit the auto commit.
	 * @return the properties.
	 * @deprecated Please, use {@link #consumerProps(String, String, boolean)} instead.
 	 */
	@Deprecated(forRemoval = true, since = "4.0.0")
	public static Map<String, Object> consumerProps(String brokers, String group, String autoCommit) {
		return consumerProps(brokers, group, Boolean.parseBoolean(autoCommit));
	}

	/**
	 * Set up test properties for an {@code <Integer, String>} consumer.
	 * @param brokers the bootstrapServers property.
	 * @param group the group id.
	 * @param autoCommit the auto commit.
	 * @return the properties.
	 * @since 4.0
	 */
	public static Map<String, Object> consumerProps(String brokers, String group, boolean autoCommit) {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit);
		props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "10");
		props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "60000");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		return props;
	}

	/**
	 * Set up test properties for an {@code <Integer, String>} producer.
	 * @param brokers the bootstrapServers property.
	 * @return the properties.
	 * @since 2.3.5
	 */
	public static Map<String, Object> producerProps(String brokers) {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
		props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
		props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
		props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		return props;
	}

	/**
	 * Set up test properties for the Kafka Streams.
	 * @param applicationId the applicationId for the Kafka Streams.
	 * @param brokers the bootstrapServers property.
	 * @return the properties.
	 * @since 3.3
	 */
	public static Map<String, Object> streamsProps(String applicationId, String brokers) {
		Map<String, Object> props = new HashMap<>();
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
		return props;
	}

	/**
	 * Poll the consumer, expecting a single record for the specified topic.
	 * @param consumer the consumer.
	 * @param topic the topic.
	 * @param <K> the key type.
	 * @param <V> the value type.
	 * @return the record.
	 * @throws IllegalStateException if exactly one record is not received.
	 * @see #getSingleRecord(Consumer, String, Duration)
	 */
	public static <K, V> ConsumerRecord<K, V> getSingleRecord(Consumer<K, V> consumer, String topic) {
		return getSingleRecord(consumer, topic, Duration.ofSeconds(60)); // NOSONAR magic #
	}

	/**
	 * Poll the consumer, expecting a single record for the specified topic.
	 * @param consumer the consumer.
	 * @param topic the topic.
	 * @param timeout max duration to wait for records; forwarded to {@link Consumer#poll(Duration)}.
	 * @param <K> the key type.
	 * @param <V> the value type.
	 * @return the record.
	 * @throws IllegalStateException if exactly one record is not received.
	 * @since 2.9.3
	 */
	public static <K, V> ConsumerRecord<K, V> getSingleRecord(Consumer<K, V> consumer, String topic, Duration timeout) {
		long expire = System.currentTimeMillis() + timeout.toMillis();
		ConsumerRecords<K, V> received;
		Iterator<ConsumerRecord<K, V>> iterator;
		long remaining = timeout.toMillis();
		do {
			received = getRecords(consumer, Duration.ofMillis(remaining));
			iterator = received.records(topic).iterator();
			Map<TopicPartition, Long> reset = new HashMap<>();
			received.forEach(rec -> {
				if (!rec.topic().equals(topic)) {
					reset.computeIfAbsent(new TopicPartition(rec.topic(), rec.partition()), tp -> rec.offset());
				}
			});
			reset.forEach(consumer::seek);
			try {
				Thread.sleep(50); // NOSONAR magic#
			}
			catch (@SuppressWarnings("unused") InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			remaining = expire - System.currentTimeMillis();
		}
		while (!iterator.hasNext() && remaining > 0);
		if (!iterator.hasNext()) {
			throw new IllegalStateException("No records found for topic");
		}
		iterator.next();
		if (iterator.hasNext()) {
			throw new IllegalStateException("More than one record for topic found");
		}
		return received.records(topic).iterator().next();
	}

	/**
	 * Get a single record for the group from the topic/partition. Optionally, seeking to the current last record.
	 * @param brokerAddresses the broker address(es).
	 * @param group the group.
	 * @param topic the topic.
	 * @param partition the partition.
	 * @param seekToLast true to fetch an existing last record, if present.
	 * @param commit commit offset after polling or not.
	 * @param timeout the timeout.
	 * @return the record or null if no record received.
	 * @since 2.9.3
	 */
	@Nullable
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static ConsumerRecord<?, ?> getOneRecord(String brokerAddresses, String group, String topic, int partition,
			boolean seekToLast, boolean commit, Duration timeout) {

		Map<String, Object> consumerConfig = consumerProps(brokerAddresses, group, false);
		consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
		try (KafkaConsumer consumer = new KafkaConsumer(consumerConfig)) {
			TopicPartition topicPart = new TopicPartition(topic, partition);
			consumer.assign(Collections.singletonList(topicPart));
			if (seekToLast) {
				consumer.seekToEnd(Collections.singletonList(topicPart));
				if (consumer.position(topicPart) > 0) {
					consumer.seek(topicPart, consumer.position(topicPart) - 1);
				}
			}
			ConsumerRecords<?, ?> records = consumer.poll(timeout);
			ConsumerRecord<?, ?> record = records.count() == 1 ? records.iterator().next() : null;
			if (record != null && commit) {
				consumer.commitSync();
			}
			return record;
		}
	}

	/**
	 * Get the current offset and metadata for the provided group/topic/partition.
	 * @param brokerAddresses the broker address(es).
	 * @param group the group.
	 * @param topic the topic.
	 * @param partition the partition.
	 * @return the offset and metadata.
	 * @throws Exception if an exception occurs.
	 * @since 2.3
	 */
	public static @Nullable OffsetAndMetadata getCurrentOffset(String brokerAddresses, String group, String topic, int partition)
			throws Exception { // NOSONAR

		try (AdminClient client = AdminClient
				.create(Collections.singletonMap(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokerAddresses))) {
			return client.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get() // NOSONAR false positive
					.get(new TopicPartition(topic, partition));
		}
	}

	/**
	 * Get the current offset and metadata for the provided group/topic/partition.
	 * @param adminClient the AdminClient instance.
	 * @param group the group.
	 * @param topic the topic.
	 * @param partition the partition.
	 * @return the offset and metadata.
	 * @throws Exception if an exception occurs.
	 * @since 3.0
	 */
	public static @Nullable OffsetAndMetadata getCurrentOffset(AdminClient adminClient, String group, String topic, int partition)
			throws Exception { // NOSONAR

		return adminClient.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get() // NOSONAR false positive
				.get(new TopicPartition(topic, partition));
	}

	/**
	 * Return the end offsets of the requested topic/partitions
	 * @param consumer the consumer.
	 * @param topic the topic.
	 * @param partitions the partitions, or null for all partitions.
	 * @return the map of end offsets.
	 * @since 2.6.5
	 * @see Consumer#endOffsets(Collection, Duration)
	 */
	public static Map<TopicPartition, Long> getEndOffsets(Consumer<?, ?> consumer, String topic,
			Integer @Nullable ... partitions) {

		Collection<TopicPartition> tps;
		if (partitions == null || partitions.length == 0) {
			Map<String, List<PartitionInfo>> parts = consumer.listTopics(Duration.ofSeconds(TEN));
			tps = parts.entrySet()
					.stream()
					.filter(entry -> entry.getKey().equals(topic))
					.flatMap(entry -> entry.getValue().stream())
					.map(pi -> new TopicPartition(topic, pi.partition()))
					.collect(Collectors.toList());
		}
		else {
			Assert.noNullElements(partitions, "'partitions' cannot have null elements");
			tps = Arrays.stream(partitions)
					.map(part -> new TopicPartition(topic, part))
					.collect(Collectors.toList());
		}
		return consumer.endOffsets(tps, Duration.ofSeconds(TEN));
	}

	/**
	 * Poll the consumer for records.
	 * @param consumer the consumer.
	 * @param <K> the key type.
	 * @param <V> the value type.
	 * @return the records.
	 * @see #getRecords(Consumer, Duration)
	 */
	public static <K, V> ConsumerRecords<K, V> getRecords(Consumer<K, V> consumer) {
		return getRecords(consumer, Duration.ofSeconds(60)); // NOSONAR magic #
	}

	/**
	 * Poll the consumer for records.
	 * @param consumer the consumer.
	 * @param timeout max time in milliseconds to wait for records; forwarded to {@link Consumer#poll(Duration)}.
	 * @param <K> the key type.
	 * @param <V> the value type.
	 * @return the records.
	 * @throws IllegalStateException if the poll returns null (since 2.3.4).
	 * @since 2.9.3
	 */
	public static <K, V> ConsumerRecords<K, V> getRecords(Consumer<K, V> consumer, Duration timeout) {
		return getRecords(consumer, timeout, -1);
	}

	/**
	 * Poll the consumer for records.
	 * @param consumer the consumer.
	 * @param timeout max time in milliseconds to wait for records; forwarded to {@link Consumer#poll(Duration)}.
	 * @param <K> the key type.
	 * @param <V> the value type.
	 * @param minRecords wait until the timeout or at least this number of records are received.
	 * @return the records.
	 * @throws IllegalStateException if the poll returns null.
	 * @since 2.9.3
	 */
	public static <K, V> ConsumerRecords<K, V> getRecords(Consumer<K, V> consumer, Duration timeout, int minRecords) {
		logger.debug("Polling...");
		Map<TopicPartition, List<ConsumerRecord<K, V>>> records = new HashMap<>();
		long remaining = timeout.toMillis();
		int count = 0;
		do {
			long t1 = System.currentTimeMillis();
			ConsumerRecords<K, V> received = consumer.poll(Duration.ofMillis(remaining));
			if (received == null) {
				throw new IllegalStateException("null received from consumer.poll()");
			}
			logger.debug(() -> "Received: " + received.count() + ", "
					+ received.partitions().stream()
					.flatMap(p -> received.records(p).stream())
					// map to same format as send metadata toString()
					.map(r -> r.topic() + "-" + r.partition() + "@" + r.offset()).toList());
			if (minRecords < 0) {
				return received;
			}
			else {
				count += received.count();
				received.partitions().forEach(tp -> {
					List<ConsumerRecord<K, V>> recs = records.computeIfAbsent(tp, part -> new ArrayList<>());
					recs.addAll(received.records(tp));
				});
				remaining -= System.currentTimeMillis() - t1;
			}
		}
		while (count < minRecords && remaining > 0);
		return new ConsumerRecords<>(records, Map.of());
	}

	/**
	 * Uses nested {@link DirectFieldAccessor}s to obtain a property using dotted notation to traverse fields; e.g.
	 * "foo.bar.baz" will obtain a reference to the baz field of the bar field of foo. Adopted from Spring Integration.
	 * @param root The object.
	 * @param propertyPath The path.
	 * @return The field.
	 */
	public static @Nullable Object getPropertyValue(Object root, String propertyPath) {
		Object value = null;
		DirectFieldAccessor accessor = new DirectFieldAccessor(root);
		String[] tokens = propertyPath.split("\\.");
		for (int i = 0; i < tokens.length; i++) {
			value = accessor.getPropertyValue(tokens[i]);
			if (value != null) {
				accessor = new DirectFieldAccessor(value);
			}
			else if (i == tokens.length - 1) {
				return null;
			}
			else {
				throw new IllegalArgumentException("intermediate property '" + tokens[i] + "' is null");
			}
		}
		return value;
	}

	/**
	 * A typed version of {@link #getPropertyValue(Object, String)}.
	 * @param root the object.
	 * @param propertyPath the path.
	 * @param type the type to cast the object to.
	 * @param <T> the type.
	 * @return the field value.
	 * @see #getPropertyValue(Object, String)
	 */
	@SuppressWarnings("unchecked")
	public static <T> @Nullable T getPropertyValue(Object root, String propertyPath, Class<T> type) {
		Object value = getPropertyValue(root, propertyPath);
		if (value != null) {
			Assert.isAssignable(type, value.getClass());
		}
		return (T) value;
	}

	/**
	 * Return a {@link Properties} object equal to the default consumer property overrides.
	 * Useful when matching arguments in Mockito tests.
	 * @return the default properties.
	 * @since 2.2.5
	 */
	public static Properties defaultPropertyOverrides() {
		return defaults;
	}
}

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

package org.springframework.kafka.config;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.log.LogAccessor;
import org.springframework.expression.BeanResolver;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.adapter.BatchMessagingMessageListenerAdapter;
import org.springframework.kafka.listener.adapter.BatchToRecordAdapter;
import org.springframework.kafka.listener.adapter.HandlerAdapter;
import org.springframework.kafka.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.kafka.listener.adapter.RecordMessagingMessageListenerAdapter;
import org.springframework.kafka.support.JavaUtils;
import org.springframework.kafka.support.converter.BatchMessageConverter;
import org.springframework.kafka.support.converter.MessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.Assert;

/**
 * A {@link KafkaListenerEndpoint} providing the method to invoke to process
 * an incoming message for this endpoint.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 *
 * @author Stephane Nicoll
 * @author Artem Bilan
 * @author Gary Russell
 * @author Venil Noronha
 */
public class MethodKafkaListenerEndpoint<K, V> extends AbstractKafkaListenerEndpoint<K, V> {

	private final LogAccessor logger = new LogAccessor(LogFactory.getLog(getClass()));

	@SuppressWarnings("NullAway.Init")
	private Object bean;

	@SuppressWarnings("NullAway.Init")
	private Method method;

	private @Nullable MessageHandlerMethodFactory messageHandlerMethodFactory;

	private @Nullable KafkaListenerErrorHandler errorHandler;

	private @Nullable SmartMessageConverter messagingConverter;

	/**
	 * Set the object instance that should manage this endpoint.
	 * @param bean the target bean instance.
	 */
	public void setBean(Object bean) {
		this.bean = bean;
	}

	public Object getBean() {
		return this.bean;
	}

	/**
	 * Set the method to invoke to process a message managed by this endpoint.
	 * @param method the target method for the {@link #bean}.
	 */
	public void setMethod(Method method) {
		this.method = method;
	}

	public Method getMethod() {
		return this.method;
	}

	/**
	 * Set the {@link MessageHandlerMethodFactory} to use to build the
	 * {@link InvocableHandlerMethod} responsible to manage the invocation
	 * of this endpoint.
	 * @param messageHandlerMethodFactory the {@link MessageHandlerMethodFactory} instance.
	 */
	public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory messageHandlerMethodFactory) {
		this.messageHandlerMethodFactory = messageHandlerMethodFactory;
	}

	/**
	 * Set the {@link KafkaListenerErrorHandler} to invoke if the listener method
	 * throws an exception.
	 * @param errorHandler the error handler.
	 * @since 1.3
	 */
	public void setErrorHandler(KafkaListenerErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	/**
	 * Set a spring-messaging {@link SmartMessageConverter} to convert the record value to
	 * the desired type. This will also cause the
	 * {@link org.springframework.messaging.MessageHeaders#CONTENT_TYPE} to be converted
	 * to String when mapped inbound.
	 * @param messagingConverter the converter.
	 * @since 2.7.1
	 */
	public void setMessagingConverter(SmartMessageConverter messagingConverter) {
		this.messagingConverter = messagingConverter;
	}

	private @Nullable String getReplyTopic() {
		Method replyingMethod = getMethod();
		if (replyingMethod != null) {
			SendTo ann = AnnotatedElementUtils.findMergedAnnotation(replyingMethod, SendTo.class);
			if (ann != null) {
				if (replyingMethod.getReturnType().equals(void.class)) {
					this.logger.warn(() -> "Method "
							+ replyingMethod
							+ " has a void return type; @SendTo is ignored" +
							(this.errorHandler == null ? "" : " unless the error handler returns a result"));
				}
				String[] destinations = ann.value();
				if (destinations.length > 1) {
					throw new IllegalStateException("Invalid @" + SendTo.class.getSimpleName() + " annotation on '"
							+ replyingMethod + "' one destination must be set (got " + Arrays.toString(destinations) + ")");
				}
				String topic = destinations.length == 1 ? destinations[0] : "";
				BeanFactory beanFactory = getBeanFactory();
				if (beanFactory instanceof ConfigurableListableBeanFactory configurableListableBeanFactory) {
					topic = configurableListableBeanFactory.resolveEmbeddedValue(topic);
					if (topic != null) {
						topic = resolve(topic);
					}
				}
				return topic;
			}
		}
		return null;
	}

	/**
	 * Return the {@link MessageHandlerMethodFactory}.
	 * @return the messageHandlerMethodFactory
	 */
	protected @Nullable MessageHandlerMethodFactory getMessageHandlerMethodFactory() {
		return this.messageHandlerMethodFactory;
	}

	@Override
	@SuppressWarnings("NullAway") // Dataflow analysis limitation
	protected MessagingMessageListenerAdapter<K, V> createMessageListener(MessageListenerContainer container,
			@Nullable MessageConverter messageConverter) {

		Assert.state(this.messageHandlerMethodFactory != null,
				"Could not create message listener - MessageHandlerMethodFactory not set");
		MessagingMessageListenerAdapter<K, V> messageListener = createMessageListenerInstance(messageConverter);
		messageListener.setHandlerMethod(configureListenerAdapter(messageListener));
		JavaUtils.INSTANCE
			.acceptIfNotNull(getReplyTopic(), replyTopic -> {
				Assert.state(getMethod().getReturnType().equals(void.class)
						|| getReplyTemplate() != null, "a KafkaTemplate is required to support replies");
				messageListener.setReplyTopic(replyTopic);
			})
			.acceptIfNotNull(getReplyTemplate(), messageListener::setReplyTemplate);

		return messageListener;
	}

	/**
	 * Create a {@link HandlerAdapter} for this listener adapter.
	 * @param messageListener the listener adapter.
	 * @return the handler adapter.
	 */
	protected HandlerAdapter configureListenerAdapter(MessagingMessageListenerAdapter<K, V> messageListener) {
		Assert.state(this.messageHandlerMethodFactory != null,
				"MessageHandlerMethodFactory must not be null");
		InvocableHandlerMethod invocableHandlerMethod =
				this.messageHandlerMethodFactory.createInvocableHandlerMethod(getBean(), getMethod());
		return new HandlerAdapter(invocableHandlerMethod);
	}

	/**
	 * Create an empty {@link MessagingMessageListenerAdapter} instance.
	 * @param messageConverter the converter (may be null).
	 * @return the {@link MessagingMessageListenerAdapter} instance.
	 */
	protected MessagingMessageListenerAdapter<K, V> createMessageListenerInstance(
			@Nullable MessageConverter messageConverter) {

		MessagingMessageListenerAdapter<K, V> listener;
		if (isBatchListener()) {
			BatchMessagingMessageListenerAdapter<K, V> messageListener = new BatchMessagingMessageListenerAdapter<>(
					this.bean, this.method, this.errorHandler);
			BatchToRecordAdapter<K, V> batchToRecordAdapter = getBatchToRecordAdapter();
			if (batchToRecordAdapter != null) {
				messageListener.setBatchToRecordAdapter(batchToRecordAdapter);
			}
			if (messageConverter instanceof BatchMessageConverter batchMessageConverter) {
				messageListener.setBatchMessageConverter(batchMessageConverter);
			}
			listener = messageListener;
		}
		else {
			RecordMessagingMessageListenerAdapter<K, V> messageListener = new RecordMessagingMessageListenerAdapter<>(
					this.bean, this.method, this.errorHandler);
			if (messageConverter instanceof RecordMessageConverter recordMessageConverter) {
				messageListener.setMessageConverter(recordMessageConverter);
			}
			listener = messageListener;
		}
		if (this.messagingConverter != null) {
			listener.setMessagingConverter(this.messagingConverter);
		}
		BeanResolver resolver = getBeanResolver();
		if (resolver != null) {
			listener.setBeanResolver(resolver);
		}
		return listener;
	}

	private @Nullable String resolve(String value) {
		BeanExpressionContext beanExpressionContext = getBeanExpressionContext();
		BeanExpressionResolver resolver = getResolver();
		if (resolver != null && beanExpressionContext != null) {
			Object newValue = resolver.evaluate(value, beanExpressionContext);
			Assert.isInstanceOf(String.class, newValue, "Invalid @SendTo expression");
			return (String) newValue;
		}
		else {
			return value;
		}
	}

	@Override
	protected StringBuilder getEndpointDescription() {
		return super.getEndpointDescription()
				.append(" | bean='").append(this.bean).append("'")
				.append(" | method='").append(this.method).append("'");
	}

}

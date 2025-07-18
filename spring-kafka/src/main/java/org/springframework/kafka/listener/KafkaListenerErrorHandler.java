/*
 * Copyright 2017-present the original author or authors.
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

package org.springframework.kafka.listener;

import org.apache.kafka.clients.consumer.Consumer;
import org.jspecify.annotations.Nullable;

import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.Message;

/**
 * An error handler which is called when a {@code @KafkaListener} method
 * throws an exception. This is invoked higher up the stack than the
 * listener container's error handler. For methods annotated with
 * {@code @SendTo}, the error handler can return a result.
 *
 * @author Venil Noronha
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 1.3
 */
@FunctionalInterface
public interface KafkaListenerErrorHandler {

	/**
	 * Handle the error.
	 * @param message the spring-messaging message.
	 * @param exception the exception the listener threw, wrapped in a
	 * {@link ListenerExecutionFailedException}.
	 * @return the return value is ignored unless the annotated method has a
	 * {@code @SendTo} annotation.
	 */
	Object handleError(Message<?> message, ListenerExecutionFailedException exception);

	/**
	 * Handle the error.
	 * @param message the spring-messaging message.
	 * @param exception the exception the listener threw, wrapped in a
	 * {@link ListenerExecutionFailedException}.
	 * @param consumer the consumer.
	 * @return the return value is ignored unless the annotated method has a
	 * {@code @SendTo} annotation.
	 */
	default Object handleError(Message<?> message, ListenerExecutionFailedException exception,
			@Nullable Consumer<?, ?> consumer) {

		return handleError(message, exception);
	}

	/**
	 * Handle the error.
	 * @param message the spring-messaging message.
	 * @param exception the exception the listener threw, wrapped in a
	 * {@link ListenerExecutionFailedException}.
	 * @param consumer the consumer.
	 * @param ack the {@link Acknowledgment}.
	 * @return the return value is ignored unless the annotated method has a
	 * {@code @SendTo} annotation.
	 */
	@Nullable
	default Object handleError(Message<?> message, ListenerExecutionFailedException exception,
			@Nullable Consumer<?, ?> consumer, @Nullable Acknowledgment ack) {

		return handleError(message, exception, consumer);
	}

}

/*
 * Copyright 2018-present the original author or authors.
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

package org.springframework.kafka.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.util.Assert;

/**
 *
 * Attempts to provide an instance of
 * {@link org.springframework.kafka.retrytopic.RetryTopicConfiguration} by either creating
 * one from a {@link RetryableTopic} annotation, or from the bean container if no
 * annotation is available.
 *
 * <p>
 * If beans are found in the container there's a check to determine whether or not the
 * provided topics should be handled by any of such instances.
 *
 * <p>
 * If the annotation is provided, a
 * {@link org.springframework.kafka.annotation.DltHandler} annotated method is looked up.
 *
 * @author Tomaz Fernandes
 * @author Gary Russell
 * @author Wang Zhiyang
 *
 * @since 2.7
 * @see org.springframework.kafka.retrytopic.RetryTopicConfigurer
 * @see RetryableTopic
 * @see org.springframework.kafka.annotation.DltHandler
 *
 */
public class RetryTopicConfigurationProvider {

	@Nullable
	private final BeanFactory beanFactory;

	@Nullable
	private final BeanExpressionResolver resolver;

	@Nullable
	private final BeanExpressionContext expressionContext;

	private static final LogAccessor LOGGER = new LogAccessor(LogFactory.getLog(RetryTopicConfigurationProvider.class));

	/**
	 * Construct an instance using the provided bean factory and default resolver and bean
	 * expression context.
	 * @param beanFactory the bean factory.
	 */
	public RetryTopicConfigurationProvider(@Nullable BeanFactory beanFactory) {
		this(beanFactory, new StandardBeanExpressionResolver(), beanFactory instanceof ConfigurableBeanFactory
				? new BeanExpressionContext((ConfigurableBeanFactory) beanFactory, null)
				: null); // NOSONAR
	}

	/**
	 * Construct an instance using the provided parameters.
	 * @param beanFactory the bean factory.
	 * @param resolver the bean expression resolver.
	 * @param expressionContext the bean expression context.
	 */
	public RetryTopicConfigurationProvider(@Nullable BeanFactory beanFactory, @Nullable BeanExpressionResolver resolver,
			@Nullable BeanExpressionContext expressionContext) {

		this.beanFactory = beanFactory;
		this.resolver = resolver;
		this.expressionContext = expressionContext;
	}

	@Nullable
	public RetryTopicConfiguration findRetryConfigurationFor(String[] topics, Method method, Object bean) {
		return findRetryConfigurationFor(topics, method, null, bean);
	}

	/**
	 * Find retry topic configuration.
	 * @param topics the retryable topic list.
	 * @param method the method that gets @RetryableTopic annotation.
	 * @param clazz the class that gets @RetryableTopic annotation.
	 * @param bean the bean.
	 * @return the retry topic configuration.
	 */
	@Nullable
	public RetryTopicConfiguration findRetryConfigurationFor(String[] topics, @Nullable Method method,
			@Nullable Class<?> clazz, Object bean) {

		RetryableTopic annotation = getRetryableTopicAnnotationFromAnnotatedElement(
				Objects.requireNonNullElse(method, clazz));
		Class<?> declaringClass = method != null ? method.getDeclaringClass() : clazz;
		Assert.state(declaringClass != null, "No declaring class found for " + method);
		return annotation != null
				? new RetryableTopicAnnotationProcessor(this.beanFactory, this.resolver, this.expressionContext)
				.processAnnotation(topics, declaringClass, annotation, bean)
				: maybeGetFromContext(topics);
	}

	@Nullable
	private RetryableTopic getRetryableTopicAnnotationFromAnnotatedElement(AnnotatedElement element) {
		return MergedAnnotations.from(element, SearchStrategy.TYPE_HIERARCHY,
						RepeatableContainers.none())
				.get(RetryableTopic.class)
				.synthesize(MergedAnnotation::isPresent)
				.orElse(null);
	}

	@Nullable
	private RetryTopicConfiguration maybeGetFromContext(String[] topics) {
		if (this.beanFactory == null || !ListableBeanFactory.class.isAssignableFrom(this.beanFactory.getClass())) {
			LOGGER.warn("No ListableBeanFactory found, skipping RetryTopic configuration.");
			return null;
		}

		Map<String, RetryTopicConfiguration> retryTopicProcessors = ((ListableBeanFactory) this.beanFactory)
				.getBeansOfType(RetryTopicConfiguration.class);
		return retryTopicProcessors
				.values()
				.stream()
				.filter(topicConfiguration -> topicConfiguration.hasConfigurationForTopics(topics))
				.findFirst()
				.orElse(null);
	}
}

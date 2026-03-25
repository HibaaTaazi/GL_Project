/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.boot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.AotDetector;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.NativeDetector;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

class ApplicationContextPreparer {

	private final SpringApplication application;
	private final ApplicationProperties properties;
	private final @Nullable BeanNameGenerator beanNameGenerator;
	private final @Nullable ResourceLoader resourceLoader;

	ApplicationContextPreparer(SpringApplication application,
			ApplicationProperties properties,
			@Nullable BeanNameGenerator beanNameGenerator,
			@Nullable ResourceLoader resourceLoader) {
		this.application = application;
		this.properties = properties;
		this.beanNameGenerator = beanNameGenerator;
		this.resourceLoader = resourceLoader;
	}

	void prepare(DefaultBootstrapContext bootstrapContext,
			ConfigurableApplicationContext context,
			ConfigurableEnvironment environment,
			SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments,
			@Nullable Banner printedBanner) {
		context.setEnvironment(environment);
		postProcessApplicationContext(context);
		List<ApplicationContextInitializer<?>> initializers =
				new ArrayList<>(this.application.getInitializers());
		addAotGeneratedInitializerIfNecessary(initializers);
		applyInitializers(context, initializers);
		listeners.contextPrepared(context);
		bootstrapContext.close(context);
		if (this.properties.isLogStartupInfo()) {
			logStartupInfo(context);
			logStartupProfileInfo(context);
		}
		var beanFactory = context.getBeanFactory();
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		if (beanFactory instanceof AbstractAutowireCapableBeanFactory autowireCapableBeanFactory) {
			autowireCapableBeanFactory.setAllowCircularReferences(
					this.properties.isAllowCircularReferences());
			if (beanFactory instanceof DefaultListableBeanFactory listableBeanFactory) {
				listableBeanFactory.setAllowBeanDefinitionOverriding(
						this.properties.isAllowBeanDefinitionOverriding());
			}
		}
		if (this.properties.isLazyInitialization()) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		if (this.properties.isKeepAlive()) {
			context.addApplicationListener(new KeepAlive());
		}
		context.addBeanFactoryPostProcessor(
				new PropertySourceOrderingBeanFactoryPostProcessor(context));
		if (!AotDetector.useGeneratedArtifacts()) {
			Set<Object> sources = this.application.getAllSources();
			Assert.state(!ObjectUtils.isEmpty(sources), "No sources defined");
			this.application.load(context, sources.toArray(new Object[0]));
		}
		listeners.contextLoaded(context);
	}

	private void postProcessApplicationContext(ConfigurableApplicationContext context) {
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(
					org.springframework.context.annotation.AnnotationConfigUtils
							.CONFIGURATION_BEAN_NAME_GENERATOR,
					this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			if (context instanceof GenericApplicationContext genericApplicationContext) {
				genericApplicationContext.setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader defaultResourceLoader) {
				defaultResourceLoader.setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
		if (this.application.addConversionService) {
			context.getBeanFactory()
					.setConversionService(context.getEnvironment().getConversionService());
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void applyInitializers(ConfigurableApplicationContext context,
			List<ApplicationContextInitializer<?>> initializers) {
		for (ApplicationContextInitializer initializer : initializers) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(
					initializer.getClass(), ApplicationContextInitializer.class);
			Assert.state(requiredType != null,
					() -> "No generic type found for initializr of type " + initializer.getClass());
			Assert.state(requiredType.isInstance(context), "Unable to call initializer");
			initializer.initialize(context);
		}
	}

	private void addAotGeneratedInitializerIfNecessary(
			List<ApplicationContextInitializer<?>> initializers) {
		if (NativeDetector.inNativeImage()) {
			SpringApplication.NativeImageRequirementsException.throwIfNotMet();
		}
		if (AotDetector.useGeneratedArtifacts()) {
			List<ApplicationContextInitializer<?>> aotInitializers = new ArrayList<>(
					initializers.stream()
							.filter(AotApplicationContextInitializer.class::isInstance)
							.toList());
			if (aotInitializers.isEmpty()) {
				Class<?> mainClass = this.application.getMainApplicationClass();
				Assert.state(mainClass != null, "No application main class found");
				String initializerClassName =
						mainClass.getName() + "__ApplicationContextInitializer";
				if (!ClassUtils.isPresent(initializerClassName,
						this.application.getClassLoader())) {
					throw new AotInitializerNotFoundException(mainClass, initializerClassName);
				}
				aotInitializers.add(
						AotApplicationContextInitializer
								.forInitializerClasses(initializerClassName));
			}
			initializers.removeAll(aotInitializers);
			initializers.addAll(0, aotInitializers);
		}
	}

	private void logStartupInfo(ConfigurableApplicationContext context) {
		if (context.getParent() == null) {
			new StartupInfoLogger(this.application.getMainApplicationClass(),
					context.getEnvironment())
					.logStarting(this.application.getApplicationLog());
		}
	}

	private void logStartupProfileInfo(ConfigurableApplicationContext context) {
		var log = this.application.getApplicationLog();
		if (!log.isInfoEnabled()) {
			return;
		}
		List<String> activeProfiles = Arrays.stream(
						context.getEnvironment().getActiveProfiles())
				.map(p -> "\"" + p + "\"").toList();
		if (ObjectUtils.isEmpty(activeProfiles)) {
			List<String> defaultProfiles = Arrays.stream(
							context.getEnvironment().getDefaultProfiles())
					.map(p -> "\"" + p + "\"").toList();
			String message = String.format("%s default %s: ", defaultProfiles.size(),
					(defaultProfiles.size() <= 1) ? "profile" : "profiles");
			log.info("No active profile set, falling back to " + message
					+ StringUtils.collectionToDelimitedString(defaultProfiles, ", "));
		}
		else {
			String message = (activeProfiles.size() == 1) ? "1 profile is active: "
					: activeProfiles.size() + " profiles are active: ";
			log.info("The following " + message
					+ StringUtils.collectionToDelimitedString(activeProfiles, ", "));
		}
	}

}
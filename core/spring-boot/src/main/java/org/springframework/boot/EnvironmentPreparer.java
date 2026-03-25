
package org.springframework.boot;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

class EnvironmentPreparer {

	private final SpringApplication application;
	private final ApplicationProperties properties;
	private final ApplicationContextFactory applicationContextFactory;
	private final boolean addConversionService;
	private final boolean addCommandLineProperties;
	private final @Nullable Map<String, Object> defaultProperties;
	private final @Nullable Class<?> mainApplicationClass;
	private final boolean isCustomEnvironment;
	private final @Nullable ConfigurableEnvironment customEnvironment;

	EnvironmentPreparer(SpringApplication application,
			ApplicationProperties properties,
			ApplicationContextFactory applicationContextFactory,
			boolean addConversionService,
			boolean addCommandLineProperties,
			@Nullable Map<String, Object> defaultProperties,
			@Nullable Class<?> mainApplicationClass,
			boolean isCustomEnvironment,
			@Nullable ConfigurableEnvironment customEnvironment) {
		this.application = application;
		this.properties = properties;
		this.applicationContextFactory = applicationContextFactory;
		this.addConversionService = addConversionService;
		this.addCommandLineProperties = addCommandLineProperties;
		this.defaultProperties = defaultProperties;
		this.mainApplicationClass = mainApplicationClass;
		this.isCustomEnvironment = isCustomEnvironment;
		this.customEnvironment = customEnvironment;
	}

	ConfigurableEnvironment prepare(SpringApplicationRunListeners listeners,
			DefaultBootstrapContext bootstrapContext,
			ApplicationArguments applicationArguments) {
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		ConfigurationPropertySources.attach(environment);
		listeners.environmentPrepared(bootstrapContext, environment);
		ApplicationInfoPropertySource.moveToEnd(environment);
		DefaultPropertiesPropertySource.moveToEnd(environment);
		Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
				"Environment prefix cannot be set via properties.");
		bindToSpringApplication(environment);
		if (!this.isCustomEnvironment) {
			EnvironmentConverter environmentConverter =
					new EnvironmentConverter(this.application.getClassLoader());
			environment = environmentConverter.convertEnvironmentIfNecessary(
					environment, deduceEnvironmentClass());
		}
		ConfigurationPropertySources.attach(environment);
		return environment;
	}

	private ConfigurableEnvironment getOrCreateEnvironment() {
		if (this.customEnvironment != null) {
			return this.customEnvironment;
		}
		WebApplicationType webApplicationType = this.properties.getWebApplicationType();
		ConfigurableEnvironment environment =
				this.applicationContextFactory.createEnvironment(webApplicationType);
		if (environment == null
				&& this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
			environment = ApplicationContextFactory.DEFAULT.createEnvironment(webApplicationType);
		}
		return (environment != null) ? environment : new ApplicationEnvironment();
	}

	private void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
		if (this.addConversionService) {
			environment.setConversionService(new ApplicationConversionService());
		}
		configurePropertySources(environment, args);
	}

	private void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
		if (!CollectionUtils.isEmpty(this.defaultProperties)) {
			DefaultPropertiesPropertySource.addOrMerge(this.defaultProperties, sources);
		}
		if (this.addCommandLineProperties && args.length > 0) {
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			PropertySource<?> source = sources.get(name);
			if (source != null) {
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(
						new SimpleCommandLinePropertySource(
								"springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
		environment.getPropertySources()
				.addLast(new ApplicationInfoPropertySource(this.mainApplicationClass));
	}

	private void bindToSpringApplication(ConfigurableEnvironment environment) {
		try {
			Binder.get(environment).bind("spring.main", Bindable.ofInstance(this.properties));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot bind to SpringApplication", ex);
		}
	}

	private Class<? extends ConfigurableEnvironment> deduceEnvironmentClass() {
		WebApplicationType webApplicationType = this.properties.getWebApplicationType();
		Class<? extends ConfigurableEnvironment> environmentType =
				this.applicationContextFactory.getEnvironmentType(webApplicationType);
		if (environmentType == null
				&& this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
			environmentType =
					ApplicationContextFactory.DEFAULT.getEnvironmentType(webApplicationType);
		}
		return (environmentType != null) ? environmentType : ApplicationEnvironment.class;
	}

}
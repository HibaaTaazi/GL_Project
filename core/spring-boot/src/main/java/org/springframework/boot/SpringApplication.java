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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.AotDetector;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.NativeDetector;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.ThrowingConsumer;
import org.springframework.util.function.ThrowingSupplier;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @author Chris Bono
 * @author Moritz Halbritter
 * @author Tadaya Tsuyukubo
 * @author Lasse Wulff
 * @author Yanming Zhou
 * @since 1.0.0
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {

	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

	private static final Log logger = LogFactory.getLog(SpringApplication.class);

	static final SpringApplicationShutdownHook shutdownHook = new SpringApplicationShutdownHook();

	private static final ThreadLocal<SpringApplicationHook> applicationHook = new ThreadLocal<>();

	private final Set<Class<?>> primarySources;

	private @Nullable Class<?> mainApplicationClass;

	private boolean addCommandLineProperties = true;

	// package-private pour ApplicationContextPreparer
	boolean addConversionService = true;

	private @Nullable Banner banner;

	private @Nullable ResourceLoader resourceLoader;

	private @Nullable BeanNameGenerator beanNameGenerator;

	private @Nullable ConfigurableEnvironment environment;

	private boolean headless = true;

	private List<ApplicationContextInitializer<?>> initializers = new ArrayList<>();

	private List<ApplicationListener<?>> listeners = new ArrayList<>();

	private @Nullable Map<String, Object> defaultProperties;

	private final List<BootstrapRegistryInitializer> bootstrapRegistryInitializers;

	private Set<String> additionalProfiles = Collections.emptySet();

	private boolean isCustomEnvironment;

	private @Nullable String environmentPrefix;

	private ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.DEFAULT;

	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	final ApplicationProperties properties = new ApplicationProperties();

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details). The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #SpringApplication(ResourceLoader, Class...)
	 * @see #setSources(Set)
	 */
	public SpringApplication(Class<?>... primarySources) {
		this(null, primarySources);
	}

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details). The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param resourceLoader the resource loader to use
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #setSources(Set)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SpringApplication(@Nullable ResourceLoader resourceLoader, Class<?>... primarySources) {
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "'primarySources' must not be null");
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
		this.properties.setWebApplicationType(WebApplicationType.deduce());
		this.bootstrapRegistryInitializers = new ArrayList<>(
				getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		this.mainApplicationClass = new MainClassDeducer().deduce();
	}

	/**
	 * Run the Spring application, creating and refreshing a new
	 * {@link ApplicationContext}.
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		Startup startup = Startup.create();
		if (this.properties.isRegisterShutdownHook()) {
			SpringApplication.shutdownHook.enableShutdownHookAddition();
		}
		DefaultBootstrapContext bootstrapContext = createBootstrapContext();
		ConfigurableApplicationContext context = null;
		configureHeadlessProperty();
		SpringApplicationRunListeners listeners = getRunListeners(args);
		listeners.starting(bootstrapContext, this.mainApplicationClass);
		try {
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			ConfigurableEnvironment environment = prepareEnvironment(
					listeners, bootstrapContext, applicationArguments);
			Banner printedBanner = new BannerManager(this.resourceLoader, this.banner,
					this.mainApplicationClass, this.properties, getApplicationLog()).print(environment);
			context = createApplicationContext();
			context.setApplicationStartup(this.applicationStartup);
			new ApplicationContextPreparer(this, this.properties,
					this.beanNameGenerator, this.resourceLoader)
					.prepare(bootstrapContext, context, environment,
							listeners, applicationArguments, printedBanner);
			refreshContext(context);
			afterRefresh(context, applicationArguments);
			startup.started();
			if (this.properties.isLogStartupInfo()) {
				new StartupInfoLogger(this.mainApplicationClass, environment)
						.logStarted(getApplicationLog(), startup);
			}
			listeners.started(context, startup.timeTakenToStarted());
			new RunnersExecutor().callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			throw new RunFailureHandler(this, logger).handle(context, ex, listeners);
		}
		try {
			if (context.isRunning()) {
				listeners.ready(context, startup.ready());
			}
		}
		catch (Throwable ex) {
			throw new RunFailureHandler(this, logger).handle(context, ex, null);
		}
		return context;
	}

	private DefaultBootstrapContext createBootstrapContext() {
		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
		this.bootstrapRegistryInitializers.forEach(
				(initializer) -> initializer.initialize(bootstrapContext));
		return bootstrapContext;
	}

	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
			DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments) {
		return new EnvironmentPreparer(this, this.properties,
				this.applicationContextFactory, this.addConversionService,
				this.addCommandLineProperties, this.defaultProperties,
				this.mainApplicationClass, this.isCustomEnvironment, this.environment)
				.prepare(listeners, bootstrapContext, applicationArguments);
	}

	private void refreshContext(ConfigurableApplicationContext context) {
		if (this.properties.isRegisterShutdownHook()) {
			shutdownHook.registerApplicationContext(context);
		}
		refresh(context);
	}

	private void configureHeadlessProperty() {
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
				System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
						Boolean.toString(this.headless)));
	}

	private SpringApplicationRunListeners getRunListeners(String[] args) {
		ArgumentResolver argumentResolver = ArgumentResolver.of(SpringApplication.class, this);
		argumentResolver = argumentResolver.and(String[].class, args);
		List<SpringApplicationRunListener> listeners = getSpringFactoriesInstances(
				SpringApplicationRunListener.class, argumentResolver);
		SpringApplicationHook hook = applicationHook.get();
		SpringApplicationRunListener hookListener =
				(hook != null) ? hook.getRunListener(this) : null;
		if (hookListener != null) {
			listeners = new ArrayList<>(listeners);
			listeners.add(hookListener);
		}
		return new SpringApplicationRunListeners(logger, listeners, this.applicationStartup);
	}

	private <T> List<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, null);
	}

	// package-private pour RunFailureHandler
	<T> List<T> getSpringFactoriesInstances(Class<T> type,
			@Nullable ArgumentResolver argumentResolver) {
		return SpringFactoriesLoader.forDefaultResourceLocation(getClassLoader())
				.load(type, argumentResolver);
	}

	/**
	 * Strategy method used to create the {@link ApplicationContext}.
	 * @return the application context (not yet refreshed)
	 */
	protected ConfigurableApplicationContext createApplicationContext() {
		ConfigurableApplicationContext context = this.applicationContextFactory
				.create(this.properties.getWebApplicationType());
		Assert.state(context != null, "ApplicationContextFactory created null context");
		return context;
	}

	/**
	 * Apply any relevant post-processing to the {@link ApplicationContext}.
	 * Subclasses can apply additional processing as required.
	 * @param context the application context
	 */
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(
					AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR,
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
		if (this.addConversionService) {
			context.getBeanFactory()
					.setConversionService(context.getEnvironment().getConversionService());
		}
	}

	/**
	 * Apply any {@link ApplicationContextInitializer}s to the context before it is
	 * refreshed.
	 * @param context the configured ApplicationContext (not refreshed yet)
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(
					initializer.getClass(), ApplicationContextInitializer.class);
			Assert.state(requiredType != null,
					() -> "No generic type found for initializr of type "
							+ initializer.getClass());
			Assert.state(requiredType.isInstance(context), "Unable to call initializer");
			initializer.initialize(context);
		}
	}

	/**
	 * Called to log startup information.
	 * @param context the application context
	 * @since 3.4.0
	 */
	protected void logStartupInfo(ConfigurableApplicationContext context) {
		if (context.getParent() == null) {
			new StartupInfoLogger(this.mainApplicationClass, context.getEnvironment())
					.logStarting(getApplicationLog());
		}
	}

	/**
	 * Called to log active profile information.
	 * @param context the application context
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			List<String> activeProfiles =
					quoteProfiles(context.getEnvironment().getActiveProfiles());
			if (ObjectUtils.isEmpty(activeProfiles)) {
				List<String> defaultProfiles =
						quoteProfiles(context.getEnvironment().getDefaultProfiles());
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

	private List<String> quoteProfiles(String[] profiles) {
		return Arrays.stream(profiles)
				.map((profile) -> "\"" + profile + "\"").toList();
	}

	// package-private pour RunFailureHandler et BannerManager
	Log getApplicationLog() {
		if (this.mainApplicationClass == null) {
			return logger;
		}
		return LogFactory.getLog(this.mainApplicationClass);
	}

	/**
	 * Load beans into the application context.
	 * @param context the context to load beans into
	 * @param sources the sources to load
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading source "
					+ StringUtils.arrayToCommaDelimitedString(sources));
		}
		BeanDefinitionLoader loader =
				createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * @return the resource loader
	 */
	public @Nullable ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * Either the ClassLoader that will be used in the ApplicationContext.
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		if (this.resourceLoader != null) {
			ClassLoader classLoader = this.resourceLoader.getClassLoader();
			Assert.state(classLoader != null, "No classloader found");
			return classLoader;
		}
		ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
		Assert.state(classLoader != null, "No classloader found");
		return classLoader;
	}

	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry registry) {
			return registry;
		}
		if (context instanceof AbstractApplicationContext abstractApplicationContext) {
			return (BeanDefinitionRegistry) abstractApplicationContext.getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
	 * Factory method used to create the {@link BeanDefinitionLoader}.
	 * @param registry the bean definition registry
	 * @param sources the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry,
			Object[] sources) {
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ConfigurableApplicationContext applicationContext) {
		applicationContext.refresh();
	}

	/**
	 * Called after the context has been refreshed.
	 * @param context the application context
	 * @param args the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context,
			ApplicationArguments args) {
	}

	// package-private pour RunFailureHandler
	@Nullable SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}

	private boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName())
				|| "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

	/**
	 * Returns the main application class that has been deduced or explicitly configured.
	 * @return the main application class or {@code null}
	 */
	public @Nullable Class<?> getMainApplicationClass() {
		return this.mainApplicationClass;
	}

	/**
	 * Set a specific main application class.
	 * @param mainApplicationClass the mainApplicationClass to set or {@code null}
	 */
	public void setMainApplicationClass(@Nullable Class<?> mainApplicationClass) {
		this.mainApplicationClass = mainApplicationClass;
	}

	/**
	 * Returns the type of web application that is being run.
	 * @return the type of web application
	 * @since 2.0.0
	 */
	public @Nullable WebApplicationType getWebApplicationType() {
		return this.properties.getWebApplicationType();
	}

	/**
	 * Sets the type of web application to be run.
	 * @param webApplicationType the web application type
	 * @since 2.0.0
	 */
	public void setWebApplicationType(WebApplicationType webApplicationType) {
		Assert.notNull(webApplicationType, "'webApplicationType' must not be null");
		this.properties.setWebApplicationType(webApplicationType);
	}

	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.properties.setAllowBeanDefinitionOverriding(allowBeanDefinitionOverriding);
	}

	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.properties.setAllowCircularReferences(allowCircularReferences);
	}

	public void setLazyInitialization(boolean lazyInitialization) {
		this.properties.setLazyInitialization(lazyInitialization);
	}

	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.properties.setRegisterShutdownHook(registerShutdownHook);
	}

	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	public void setBannerMode(Banner.Mode bannerMode) {
		this.properties.setBannerMode(bannerMode);
	}

	public void setLogStartupInfo(boolean logStartupInfo) {
		this.properties.setLogStartupInfo(logStartupInfo);
	}

	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	public void setAddConversionService(boolean addConversionService) {
		this.addConversionService = addConversionService;
	}

	public void addBootstrapRegistryInitializer(
			BootstrapRegistryInitializer bootstrapRegistryInitializer) {
		Assert.notNull(bootstrapRegistryInitializer,
				"'bootstrapRegistryInitializer' must not be null");
		this.bootstrapRegistryInitializers.addAll(Arrays.asList(bootstrapRegistryInitializer));
	}

	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}

	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = Collections.unmodifiableSet(
				new LinkedHashSet<>(Arrays.asList(profiles)));
	}

	public Set<String> getAdditionalProfiles() {
		return this.additionalProfiles;
	}

	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	public void setEnvironment(@Nullable ConfigurableEnvironment environment) {
		this.isCustomEnvironment = true;
		this.environment = environment;
	}

	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		this.primarySources.addAll(additionalPrimarySources);
	}

	public Set<String> getSources() {
		return this.properties.getSources();
	}

	public void setSources(Set<String> sources) {
		Assert.notNull(sources, "'sources' must not be null");
		this.properties.setSources(sources);
	}

	public Set<Object> getAllSources() {
		Set<Object> allSources = new LinkedHashSet<>();
		if (!CollectionUtils.isEmpty(this.primarySources)) {
			allSources.addAll(this.primarySources);
		}
		if (!CollectionUtils.isEmpty(this.properties.getSources())) {
			allSources.addAll(this.properties.getSources());
		}
		return Collections.unmodifiableSet(allSources);
	}

	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "'resourceLoader' must not be null");
		this.resourceLoader = resourceLoader;
	}

	public @Nullable String getEnvironmentPrefix() {
		return this.environmentPrefix;
	}

	public void setEnvironmentPrefix(String environmentPrefix) {
		this.environmentPrefix = environmentPrefix;
	}

	public void setApplicationContextFactory(
			@Nullable ApplicationContextFactory applicationContextFactory) {
		this.applicationContextFactory = (applicationContextFactory != null)
				? applicationContextFactory : ApplicationContextFactory.DEFAULT;
	}

	public void setInitializers(
			Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<>(initializers);
	}

	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	public Set<ApplicationContextInitializer<?>> getInitializers() {
		return asUnmodifiableOrderedSet(this.initializers);
	}

	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<>(listeners);
	}

	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		this.applicationStartup = (applicationStartup != null)
				? applicationStartup : ApplicationStartup.DEFAULT;
	}

	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	public boolean isKeepAlive() {
		return this.properties.isKeepAlive();
	}

	public void setKeepAlive(boolean keepAlive) {
		this.properties.setKeepAlive(keepAlive);
	}

	public static SpringApplicationShutdownHandlers getShutdownHandlers() {
		return shutdownHook.getHandlers();
	}

	public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
		return run(new Class<?>[] { primarySource }, args);
	}

	public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
		return new SpringApplication(primarySources).run(args);
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Class<?>[0], args);
	}

	public static int exit(ApplicationContext context,
			ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "'context' must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans =
						context.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}

	public static SpringApplication.Augmented from(ThrowingConsumer<String[]> main) {
		Assert.notNull(main, "'main' must not be null");
		return new Augmented(main, Collections.emptySet(), Collections.emptySet());
	}

	public static void withHook(SpringApplicationHook hook, Runnable action) {
		withHook(hook, () -> {
			action.run();
			return Void.class;
		});
	}

	public static <T> T withHook(SpringApplicationHook hook, ThrowingSupplier<T> action) {
		applicationHook.set(hook);
		try {
			return action.get();
		}
		finally {
			applicationHook.remove();
		}
	}

	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext closable) {
			closable.close();
		}
	}

	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<>(elements);
		list.sort(AnnotationAwareOrderComparator.INSTANCE);
		return new LinkedHashSet<>(list);
	}

	// -------------------------------------------------------------------------
	// Classes internes conservées car publiques ou liées à l'API publique
	// -------------------------------------------------------------------------

	/**
	 * Used to configure and run an augmented {@link SpringApplication}.
	 * @since 3.1.0
	 */
	public static class Augmented {

		private final ThrowingConsumer<String[]> main;

		private final Set<Class<?>> sources;

		private final Set<String> additionalProfiles;

		Augmented(ThrowingConsumer<String[]> main, Set<Class<?>> sources,
				Set<String> additionalProfiles) {
			this.main = main;
			this.sources = Set.copyOf(sources);
			this.additionalProfiles = additionalProfiles;
		}

		public Augmented with(Class<?>... sources) {
			LinkedHashSet<Class<?>> merged = new LinkedHashSet<>(this.sources);
			merged.addAll(Arrays.asList(sources));
			return new Augmented(this.main, merged, this.additionalProfiles);
		}

		public Augmented withAdditionalProfiles(String... profiles) {
			Set<String> merged = new LinkedHashSet<>(this.additionalProfiles);
			merged.addAll(Arrays.asList(profiles));
			return new Augmented(this.main, this.sources, merged);
		}

		public SpringApplication.Running run(String... args) {
			RunListener runListener = new RunListener();
			SpringApplicationHook hook = new SingleUseSpringApplicationHook(
					(springApplication) -> {
						springApplication.addPrimarySources(this.sources);
						springApplication.setAdditionalProfiles(
								this.additionalProfiles.toArray(String[]::new));
						return runListener;
					});
			withHook(hook, () -> this.main.accept(args));
			return runListener;
		}

		private static final class RunListener
				implements SpringApplicationRunListener, Running {

			private final List<ConfigurableApplicationContext> contexts =
					Collections.synchronizedList(new ArrayList<>());

			@Override
			public void contextLoaded(ConfigurableApplicationContext context) {
				this.contexts.add(context);
			}

			@Override
			public ConfigurableApplicationContext getApplicationContext() {
				List<ConfigurableApplicationContext> rootContexts = this.contexts.stream()
						.filter((context) -> context.getParent() == null)
						.toList();
				Assert.state(!rootContexts.isEmpty(),
						"No root application context located");
				Assert.state(rootContexts.size() == 1,
						"No unique root application context located");
				return rootContexts.get(0);
			}

		}

	}

	/**
	 * Provides access to details of a {@link SpringApplication} run.
	 * @since 3.1.0
	 */
	public interface Running {

		ConfigurableApplicationContext getApplicationContext();

	}

	/**
	 * Exception that can be thrown to silently exit a running {@link SpringApplication}.
	 * @since 3.0.0
	 */
	public static class AbandonedRunException extends RuntimeException {

		private final @Nullable ConfigurableApplicationContext applicationContext;

		public AbandonedRunException() {
			this(null);
		}

		public AbandonedRunException(
				@Nullable ConfigurableApplicationContext applicationContext) {
			this.applicationContext = applicationContext;
		}

		public @Nullable ConfigurableApplicationContext getApplicationContext() {
			return this.applicationContext;
		}

	}

	/**
	 * Exception which is thrown if GraalVM's native-image requirements aren't met.
	 */
	static final class NativeImageRequirementsException extends RuntimeException {

		private static final JavaVersion MINIMUM_REQUIRED_JAVA_VERSION = JavaVersion.TWENTY_FIVE;

		private static final JavaVersion CURRENT_JAVA_VERSION = JavaVersion.getJavaVersion();

		NativeImageRequirementsException(String message) {
			super(message);
		}

		static void throwIfNotMet() {
			if (CURRENT_JAVA_VERSION.isOlderThan(MINIMUM_REQUIRED_JAVA_VERSION)) {
				throw new NativeImageRequirementsException(
						"Native Image requirements not met. "
								+ "Native Image must support at least Java %s but Java %s was detected"
								.formatted(MINIMUM_REQUIRED_JAVA_VERSION,
										CURRENT_JAVA_VERSION));
			}
		}

	}

	/**
	 * {@link SpringApplicationHook} decorator that ensures the hook is only used once.
	 */
	private static final class SingleUseSpringApplicationHook
			implements SpringApplicationHook {

		private final AtomicBoolean used = new AtomicBoolean();

		private final SpringApplicationHook delegate;

		private SingleUseSpringApplicationHook(SpringApplicationHook delegate) {
			this.delegate = delegate;
		}

		@Override
		public @Nullable SpringApplicationRunListener getRunListener(
				SpringApplication springApplication) {
			return this.used.compareAndSet(false, true)
					? this.delegate.getRunListener(springApplication) : null;
		}

	}

}
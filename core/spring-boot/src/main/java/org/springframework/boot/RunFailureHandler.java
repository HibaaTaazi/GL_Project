package org.springframework.boot;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NativeDetector;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;

/**
 * Handles failures that occur during a {@link SpringApplication} run.
 */
class RunFailureHandler {

	private final Log logger;

	private final SpringApplication application;

	RunFailureHandler(SpringApplication application, Log logger) {
		this.application = application;
		this.logger = logger;
	}

	RuntimeException handle(@Nullable ConfigurableApplicationContext context, Throwable exception,
			@Nullable SpringApplicationRunListeners listeners) {
		if (exception instanceof SpringApplication.AbandonedRunException abandonedRunException) {
			return abandonedRunException;
		}
		try {
			try {
				handleExitCode(context, exception);
				if (listeners != null) {
					listeners.failed(context, exception);
				}
			}
			finally {
				reportFailure(getExceptionReporters(context), exception);
				if (context != null) {
					context.close();
					SpringApplication.shutdownHook.deregisterFailedApplicationContext(context);
				}
			}
		}
		catch (Exception ex) {
			this.logger.warn("Unable to close ApplicationContext", ex);
		}
		return (exception instanceof RuntimeException runtimeException) ? runtimeException
				: new IllegalStateException(exception);
	}

	private void handleExitCode(@Nullable ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromException(context, exception);
		if (exitCode != 0) {
			if (context != null) {
				context.publishEvent(new ExitCodeEvent(context, exitCode));
			}
			SpringBootExceptionHandler handler = this.application.getSpringBootExceptionHandler();
			if (handler != null) {
				handler.registerExitCode(exitCode);
			}
		}
	}

	private int getExitCodeFromException(@Nullable ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromMappedException(context, exception);
		if (exitCode == 0) {
			exitCode = getExitCodeFromExitCodeGeneratorException(exception);
		}
		return exitCode;
	}

	private int getExitCodeFromMappedException(@Nullable ConfigurableApplicationContext context, Throwable exception) {
		if (context == null || !context.isActive()) {
			return 0;
		}
		ExitCodeGenerators generators = new ExitCodeGenerators();
		Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
		generators.addAll(exception, beans);
		return generators.getExitCode();
	}

	private int getExitCodeFromExitCodeGeneratorException(@Nullable Throwable exception) {
		if (exception == null) {
			return 0;
		}
		if (exception instanceof ExitCodeGenerator generator) {
			return generator.getExitCode();
		}
		return getExitCodeFromExitCodeGeneratorException(exception.getCause());
	}

	private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
		try {
			for (SpringBootExceptionReporter reporter : exceptionReporters) {
				if (reporter.reportException(failure)) {
					registerLoggedException(failure);
					return;
				}
			}
		}
		catch (Throwable ex) {
			// Continue with normal handling of the original failure
		}
		if (this.logger.isErrorEnabled()) {
			if (NativeDetector.inNativeImage()) {
				System.out.println("Application run failed");
				failure.printStackTrace(System.out);
			}
			else {
				this.logger.error("Application run failed", failure);
			}
			registerLoggedException(failure);
		}
	}

	private Collection<SpringBootExceptionReporter> getExceptionReporters(
			@Nullable ConfigurableApplicationContext context) {
		try {
			ArgumentResolver argumentResolver = (context != null)
					? ArgumentResolver.of(ConfigurableApplicationContext.class, context) : ArgumentResolver.none();
			return this.application.getSpringFactoriesInstances(SpringBootExceptionReporter.class, argumentResolver);
		}
		catch (Throwable ex) {
			return Collections.emptyList();
		}
	}

	private void registerLoggedException(Throwable exception) {
		SpringBootExceptionHandler handler = this.application.getSpringBootExceptionHandler();
		if (handler != null) {
			handler.registerLoggedException(exception);
		}
	}

}

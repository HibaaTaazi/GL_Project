
package org.springframework.boot;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.core.OrderComparator.OrderSourceProvider;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.ClassUtils;
import org.springframework.util.function.ThrowingConsumer;

class RunnersExecutor {

	void callRunners(ConfigurableApplicationContext context, ApplicationArguments args) {
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		String[] beanNames = beanFactory.getBeanNamesForType(Runner.class);
		Map<Runner, String> instancesToBeanNames = new IdentityHashMap<>();
		for (String beanName : beanNames) {
			instancesToBeanNames.put(beanFactory.getBean(beanName, Runner.class), beanName);
		}
		Comparator<Object> comparator = getOrderComparator(beanFactory)
				.withSourceProvider(
						new FactoryAwareOrderSourceProvider(beanFactory, instancesToBeanNames));
		instancesToBeanNames.keySet().stream()
				.sorted(comparator)
				.forEach((runner) -> callRunner(runner, args));
	}

	private OrderComparator getOrderComparator(ConfigurableListableBeanFactory beanFactory) {
		Comparator<?> dependencyComparator =
				(beanFactory instanceof DefaultListableBeanFactory defaultListableBeanFactory)
						? defaultListableBeanFactory.getDependencyComparator() : null;
		return (dependencyComparator instanceof OrderComparator orderComparator)
				? orderComparator : AnnotationAwareOrderComparator.INSTANCE;
	}

	private void callRunner(Runner runner, ApplicationArguments args) {
		if (runner instanceof ApplicationRunner) {
			callRunner(ApplicationRunner.class, runner,
					(applicationRunner) -> applicationRunner.run(args));
		}
		if (runner instanceof CommandLineRunner) {
			callRunner(CommandLineRunner.class, runner,
					(commandLineRunner) -> commandLineRunner.run(args.getSourceArgs()));
		}
	}

	@SuppressWarnings("unchecked")
	private <R extends Runner> void callRunner(Class<R> type, Runner runner,
			ThrowingConsumer<R> call) {
		call.throwing((message, ex) -> new IllegalStateException(
						"Failed to execute " + ClassUtils.getShortName(type), ex))
				.accept((R) runner);
	}

	private static class FactoryAwareOrderSourceProvider implements OrderSourceProvider {

		private final ConfigurableBeanFactory beanFactory;
		private final Map<?, String> instancesToBeanNames;

		FactoryAwareOrderSourceProvider(ConfigurableBeanFactory beanFactory,
				Map<?, String> instancesToBeanNames) {
			this.beanFactory = beanFactory;
			this.instancesToBeanNames = instancesToBeanNames;
		}

		@Override
		public @Nullable Object getOrderSource(Object obj) {
			String beanName = this.instancesToBeanNames.get(obj);
			return (beanName != null) ? getOrderSource(beanName, obj.getClass()) : null;
		}

		private @Nullable Object getOrderSource(String beanName, Class<?> instanceType) {
			try {
				RootBeanDefinition beanDefinition = (RootBeanDefinition) this.beanFactory
						.getMergedBeanDefinition(beanName);
				Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
				Class<?> targetType = beanDefinition.getTargetType();
				targetType = (targetType != instanceType) ? targetType : null;
				return Stream.of(factoryMethod, targetType)
						.filter(Objects::nonNull).toArray();
			}
			catch (NoSuchBeanDefinitionException ex) {
				return null;
			}
		}

	}

}
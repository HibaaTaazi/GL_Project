package org.springframework.boot;

import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.Banner.Mode;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;


class BannerManager {

	private final @Nullable ResourceLoader resourceLoader;

	private final @Nullable Banner banner;

	private final @Nullable Class<?> mainApplicationClass;

	private final ApplicationProperties properties;

	private final Log logger;

	BannerManager(@Nullable ResourceLoader resourceLoader, @Nullable Banner banner,
			@Nullable Class<?> mainApplicationClass, ApplicationProperties properties, Log logger) {
		this.resourceLoader = resourceLoader;
		this.banner = banner;
		this.mainApplicationClass = mainApplicationClass;
		this.properties = properties;
		this.logger = logger;
	}

	@Nullable Banner print(ConfigurableEnvironment environment) {
		if (this.properties.getBannerMode(environment) == Banner.Mode.OFF) {
			return null;
		}
		ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader
				: new DefaultResourceLoader(null);
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
		if (this.properties.getBannerMode(environment) == Mode.LOG) {
			return bannerPrinter.print(environment, this.mainApplicationClass, this.logger);
		}
		return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
	}

}
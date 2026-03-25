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

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

abstract class Startup {

	private @Nullable Duration timeTakenToStarted;

	protected abstract long startTime();

	protected abstract @Nullable Long processUptime();

	protected abstract String action();

	final Duration started() {
		long now = System.currentTimeMillis();
		this.timeTakenToStarted = Duration.ofMillis(now - startTime());
		return this.timeTakenToStarted;
	}

	Duration timeTakenToStarted() {
		Assert.state(this.timeTakenToStarted != null,
				"timeTakenToStarted is not set. Make sure to call started() before this method");
		return this.timeTakenToStarted;
	}

	Duration ready() {
		long now = System.currentTimeMillis();
		return Duration.ofMillis(now - startTime());
	}

	static Startup create() {
		ClassLoader classLoader = Startup.class.getClassLoader();
		return (ClassUtils.isPresent("jdk.crac.management.CRaCMXBean", classLoader)
				&& ClassUtils.isPresent("org.crac.management.CRaCMXBean", classLoader))
				? new CoordinatedRestoreAtCheckpointStartup() : new StandardStartup();
	}

}

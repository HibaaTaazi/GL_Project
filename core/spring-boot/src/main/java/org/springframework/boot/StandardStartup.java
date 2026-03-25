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

import java.lang.management.ManagementFactory;

import org.jspecify.annotations.Nullable;

final class StandardStartup extends Startup {

	private final Long startTime = System.currentTimeMillis();

	@Override
	protected long startTime() {
		return this.startTime;
	}

	@Override
	protected @Nullable Long processUptime() {
		try {
			return ManagementFactory.getRuntimeMXBean().getUptime();
		}
		catch (Throwable ex) {
			return null;
		}
	}

	@Override
	protected String action() {
		return "Started";
	}

}

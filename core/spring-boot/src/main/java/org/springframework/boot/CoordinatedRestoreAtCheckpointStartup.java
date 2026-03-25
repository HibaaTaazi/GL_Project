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

import org.crac.management.CRaCMXBean;
import org.jspecify.annotations.Nullable;

final class CoordinatedRestoreAtCheckpointStartup extends Startup {

	private final StandardStartup fallback = new StandardStartup();

	@Override
	protected @Nullable Long processUptime() {
		Long uptime = CRaCMXBean.getCRaCMXBean().getUptimeSinceRestore();
		return (uptime >= 0) ? uptime : this.fallback.processUptime();
	}

	@Override
	protected String action() {
		return (restoreTime() >= 0) ? "Restored" : this.fallback.action();
	}

	private long restoreTime() {
		return CRaCMXBean.getCRaCMXBean().getRestoreTime();
	}

	@Override
	protected long startTime() {
		long restoreTime = restoreTime();
		return (restoreTime >= 0) ? restoreTime : this.fallback.startTime();
	}

}

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

import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

class KeepAlive implements ApplicationListener<ApplicationContextEvent> {

	private final AtomicReference<@Nullable Thread> thread = new AtomicReference<>();

	@Override
	public void onApplicationEvent(ApplicationContextEvent event) {
		if (event instanceof ContextRefreshedEvent) {
			startKeepAliveThread();
		}
		else if (event instanceof ContextClosedEvent) {
			stopKeepAliveThread();
		}
	}

	private void startKeepAliveThread() {
		Thread thread = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(Long.MAX_VALUE);
				}
				catch (InterruptedException ex) {
					break;
				}
			}
		});
		if (this.thread.compareAndSet(null, thread)) {
			thread.setDaemon(false);
			thread.setName("keep-alive");
			thread.start();
		}
	}

	private void stopKeepAliveThread() {
		Thread thread = this.thread.getAndSet(null);
		if (thread == null) {
			return;
		}
		thread.interrupt();
	}

}

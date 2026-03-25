
package org.springframework.boot;

import java.lang.StackWalker.StackFrame;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

class MainClassDeducer {

	@org.jspecify.annotations.Nullable
	Class<?> deduce() {
		return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
				.walk(this::findMainClass)
				.orElse(null);
	}

	private Optional<Class<?>> findMainClass(Stream<StackFrame> stack) {
		return stack.filter((frame) -> Objects.equals(frame.getMethodName(), "main"))
				.findFirst()
				.map(StackWalker.StackFrame::getDeclaringClass);
	}

}

/**
 * Copyright to the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package moxy;

import java.util.Optional;

import static moxy.Log.Level.*;

public abstract class Log {
    private final Object LOCK = new Object();
    private Level level;

    public Log() {
        this(OFF);
    }

    public Log(Level level) {
        this.level = level;
    }

    public void debug(String message) {
        log(DEBUG, message, Optional.empty());
    }

    public boolean isDebug() {
        return level == DEBUG;
    }

    public void info(String message) {
        log(INFO, message, Optional.empty());
    }

    public void warn(String message) {
        log(WARN, message, Optional.empty());
    }

    public void error(String message, Exception e) {
        log(ERROR, message, Optional.ofNullable(e));
    }

    private void log(Level level, String message, Optional<Exception> exceptionOptional) {
        synchronized (LOCK) {
            if (this.level == OFF) {
                return;
            }

            if (level.ordinal() >= this.level.ordinal()) {
                reallyLogMessage(level, message, exceptionOptional);
            }
        }
    }

    protected abstract void reallyLogMessage(Level level, String message, Optional<Exception> exceptionOptional);

    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        OFF
    }
}

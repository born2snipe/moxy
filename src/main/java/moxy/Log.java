/**
 * Copyright to the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package moxy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static moxy.Log.Level.*;

public class Log {
    private static final Map<String, Log> LOGGERS = Collections.synchronizedMap(new HashMap<>());
    private static Level LEVEL = Level.OFF;
    private static Appender APPENDER = new SysOutAppender();
    private final String name;

    private Log(String name) {
        this.name = name;
    }

    public synchronized static void setAppender(Appender appender) {
        APPENDER = appender;
    }

    public synchronized static void setLevel(Level level) {
        LEVEL = level;
    }

    public static Log get(Class clazz) {
        return get(clazz.getName());
    }

    public static Log get(String name) {
        if (!LOGGERS.containsKey(name)) {
            LOGGERS.put(name, new Log(name));
        }
        return LOGGERS.get(name);
    }

    public void debug(String message) {
        log(DEBUG, message, Optional.empty());
    }

    public synchronized boolean isDebug() {
        return LEVEL == DEBUG;
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
        synchronized (LEVEL) {
            if (LEVEL == OFF) {
                return;
            }

            if (level.ordinal() >= LEVEL.ordinal()) {
                APPENDER.logMessage(name, level, message, exceptionOptional);
            }
        }
    }

    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        OFF
    }

    interface Appender {
        void logMessage(String loggerName, Level level, String message, Optional<Exception> exceptionOptional);
    }
}

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

public abstract class RetryableAssertion {
    private static final int MAX_ELAPSED_TIME = 2000;
    private AssertionError assertionError;

    public void performAssertion() {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < MAX_ELAPSED_TIME) {
            try {
                assertion();
                return;
            } catch (AssertionError e) {
                assertionError = e;
            }
        }

        throw assertionError;
    }

    protected abstract void assertion();
}

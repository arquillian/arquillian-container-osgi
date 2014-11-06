/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.osgi;

import java.util.Collections;
import java.util.List;

import org.jboss.arquillian.junit.container.JUnitTestRunner;
import org.jboss.arquillian.test.spi.TestResult;
import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

/**
 * A JUnitTestRunner for OSGi
 *
 * @author thomas.diesler@jboss.com
 */
public class JUnitBundleTestRunner extends JUnitTestRunner {

    @Override
    protected List<RunListener> getRunListeners() {
        RunListener listener = new RunListener() {
            public void testStarted(Description description) throws Exception {
                // [ARQ-1880] Workaround to reset the TCCL before the test is called
                Thread.currentThread().setContextClassLoader(null);
            }
        };
        return Collections.singletonList(listener);
    }

    @Override
    public TestResult execute(Class<?> testClass, String methodName) {
        ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
        try {
            // [ARQ-1880] Arquillian core relies on TCCL to load infra
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return super.execute(testClass, methodName);
        } finally {
            Thread.currentThread().setContextClassLoader(ctxLoader);
        }
    }
}

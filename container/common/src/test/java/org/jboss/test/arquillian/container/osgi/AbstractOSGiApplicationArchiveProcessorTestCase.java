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
package org.jboss.test.arquillian.container.osgi;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.arquillian.container.osgi.AbstractOSGiApplicationArchiveProcessor;
import org.junit.Test;

/**
 * Test {@link AbstractOSGiApplicationArchiveProcessor}
 *
 * @author mbasovni@redhat.com
 * @since 19-May-2014
 */
public class AbstractOSGiApplicationArchiveProcessorTestCase {

    @Test
    public void splitWithComma() throws Exception {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        map.put(
            "org.jboss.arquillian.junit", 
            Arrays.asList("org.jboss.arquillian.junit")
        );
        map.put(
            "org.jboss.arquillian.junit,org.junit.runner,org.osgi.framework", 
            Arrays.asList("org.jboss.arquillian.junit", "org.junit.runner", "org.osgi.framework")
        );
        map.put(
            "org.junit.runner;version=\"[0.0.0,5.0.0)\"", 
            Arrays.asList("org.junit.runner;version=\"[0.0.0,5.0.0)\"")
        );
        map.put(
            "org.junit.runner;version=\"[0.0.0,5.0.0)\",org.jboss.arquillian.junit", 
            Arrays.asList("org.junit.runner;version=\"[0.0.0,5.0.0)\"", "org.jboss.arquillian.junit")
        );
        map.put(
            "org.jboss.arquillian.junit;version=\"[0.0.0,3.0.0)\",org.junit.runner;version=\"[0.0.0,5.0.0)\"", 
            Arrays.asList("org.jboss.arquillian.junit;version=\"[0.0.0,3.0.0)\"", "org.junit.runner;version=\"[0.0.0,5.0.0)\"")
        );
        map.put(
            "org.jboss.arquillian.junit;version=\"[0.0.0,3.0.0)\";extra=\"A,B\"", 
            Arrays.asList("org.jboss.arquillian.junit;version=\"[0.0.0,3.0.0)\";extra=\"A,B\"")
        );
        map.put(
            "org.jboss.arquillian.junit;extra=\"A,B,C\",org.osgi.framework;version=\"[1.0.0,2.0.0)\"", 
            Arrays.asList("org.jboss.arquillian.junit;extra=\"A,B,C\"", "org.osgi.framework;version=\"[1.0.0,2.0.0)\"")
        );
        for (Map.Entry<String,List<String>> entry : map.entrySet()) {
            assertTrue("Packages are not split correctly", splitWithComma(entry.getKey()).equals(entry.getValue()));
        }
    }

    private static List<String> splitWithComma(String value) throws Exception{
        Method method = AbstractOSGiApplicationArchiveProcessor.class.getDeclaredMethod("splitWithComma", String.class);
        method.setAccessible(true);
        return Arrays.asList((String [])method.invoke(null, value));
    }
}

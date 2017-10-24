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
package org.jboss.test.arquillian.container;

import java.io.InputStream;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.arquillian.container.util.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * @author Cristina González
 */
public class TestInheritenceTestCase extends BaseTest {
	@Deployment
	public static JavaArchive createdeployment() {
		final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "test.jar");

		archive.setManifest(new Asset() {
			public InputStream openStream() {
				OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
				builder.addBundleSymbolicName(archive.getName());
				builder.addBundleManifestVersion(2);
				builder.addImportPackages(Bundle.class);
				return builder.openStream();
			}
		});

		archive.addClass(BaseTest.class);

		return archive;
	}

	@Test
	public void testInheritance() throws Exception {
		Assert.assertEquals("HELLO_WORLD",
			BaseTest.HELLO_I_SHOULD_BE_IN_THE_TEST_PACKAGE);
	}
}

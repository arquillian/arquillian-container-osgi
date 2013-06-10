Arquillian OSGi Container
========================

The Arquillian OSGi Container provides Arquillian testing in OSGi frameworks.

Maven Setup
-----------

For the [JBOSGi Framework](https://github.com/jbosgi/jbosgi-framework) setup the following dependencies

    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian.container</groupId>
            <artifactId>arquillian-osgi-embedded</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jboss.osgi.framework</groupId>
            <artifactId>jbosgi-framework-core</artifactId>
        </dependency>
    </dependencies>
    
for [Apache Felix](http://felix.apache.org/documentation/subprojects/apache-felix-framework.html) setup these

    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian.container</groupId>
            <artifactId>arquillian-osgi-felix</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.felix</groupId>
            <artifactId>org.apache.felix.framework</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.felix</groupId>
            <artifactId>org.apache.felix.main</artifactId>
        </dependency>
    </dependencies>
    
The arquillian.xml resource references the framework configuration file

	<arquillian xmlns="http://jboss.org/schema/arquillian" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		xsi:schemaLocation="http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd">
	
		<container qualifier="jboss" default="true">
			<configuration>
	            <property name="frameworkProperties">src/test/resources/jbosgi-framework.properties</property>
			</configuration>
		</container>
	</arquillian>

which is in standard properties format 

	# Properties to configure the Framework
	org.osgi.framework.storage=./target/osgi-store
	org.osgi.framework.storage.clean=onFirstInit
	
	# Extra System Packages
	org.osgi.framework.system.packages.extra=\
		org.jboss.logging;version=3.0,\
	    org.slf4j;version=1.6
	
	# Bundles that need to be installed with the Framework automatically 
	#org.jboss.osgi.auto.install=\
	
	# Bundles that need to be started automatically 
	org.jboss.osgi.auto.start=\
		file://${test.archive.directory}/bundles/org.apache.felix.log.jar,\
		file://${test.archive.directory}/bundles/jboss-osgi-logging.jar,\
		file://${test.archive.directory}/bundles/jbosgi-repository-bundle.jar,\
		file://${test.archive.directory}/bundles/jbosgi-provision-bundle.jar
	
Arquillian OSGi Tests
---------------------

A simple test case looks like this

	@RunWith(Arquillian.class)
	public class SimpleBundleTestCase {
	
	    @ArquillianResource
	    BundleContext context;
	
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
	        return archive;
	    }
	
	    @Test
	    public void testBundleContextInjection() throws Exception {
	        assertNotNull("BundleContext injected", context);
	        assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
	    }
	    
	    @Test
	    public void testBundleInjection(@ArquillianResource Bundle bundle) throws Exception {
	        // Assert that the bundle is injected
	        assertNotNull("Bundle injected", bundle);
	
	        // Assert that the bundle is in state RESOLVED
	        // Note when the test bundle contains the test case it
	        // must be resolved already when this test method is called
	        assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());
	
	        // Start the bundle
	        bundle.start();
	        assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());
	
	        // Assert the bundle context
	        BundleContext context = bundle.getBundleContext();
	        assertNotNull("BundleContext available", context);
	
	        // Stop the bundle
	        bundle.stop();
	        assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());
	    }
	}
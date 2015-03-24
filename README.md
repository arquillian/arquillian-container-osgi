Arquillian OSGi Container
========================

The Arquillian OSGi Container provides Arquillian testing in OSGi frameworks.

Embedded Container Setup
-----------

For the [JBOSGi Framework](https://github.com/jbosgi/jbosgi-framework) setup the following Maven dependencies:

    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian.container</groupId>
            <artifactId>arquillian-container-jbosgi-embedded</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jboss.osgi.framework</groupId>
            <artifactId>jbosgi-framework-core</artifactId>
        </dependency>
    </dependencies>
    
For [Apache Felix](http://felix.apache.org/documentation/subprojects/apache-felix-framework.html) setup these:

    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian.container</groupId>
            <artifactId>arquillian-container-felix-embedded</artifactId>
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
    
    
For [Eclipse Equinox](http://www.eclipse.org/equinox/) setup these:

    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian.container</groupId>
            <artifactId>arquillian-container-equinox-embedded</artifactId>
        </dependency>
        <dependency>
            <groupId>org.eclipse.osgi</groupId>
            <artifactId>org.eclipse.osgi</artifactId>
        </dependency>
    </dependencies>
    
For each of these embedded containers, create an arquillian.xml resource that references a framework configuration file:

	<arquillian xmlns="http://jboss.org/schema/arquillian" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		xsi:schemaLocation="http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd">
	
		<container qualifier="jboss" default="true">
			<configuration>
	            <property name="frameworkProperties">src/test/resources/jbosgi-framework.properties</property>
			</configuration>
		</container>
	</arquillian>

The framework configuration file is in standard properties format:

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

Check the test/resource folders for each of the container implementations for container specific examples.

Remote Container Setup
---------------------

The Arquillian OSGi Container also supports deployment to remote containers, running on the same host or remote hosts.

To use a remote [Apache Karaf](http://karaf.apache.org/), or any container running an
OSGi Enterprise JMX implementation such as [Apache Aries JMX](http://aries.apache.org/modules/jmx.html)
, setup the following dependencies:

    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian.container</groupId>
            <artifactId>arquillian-container-karaf-remote</artifactId>
        </dependency>
    </dependencies>


For remote containers the arquillian.xml resource describes the JMX connection details:

	<?xml version="1.0" encoding="UTF-8"?>
	<arquillian xmlns="http://jboss.org/schema/arquillian" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		xsi:schemaLocation="http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd">

		<container qualifier="karaf" default="true">
			<configuration>
	            <property name="jmxServiceURL">service:jmx:rmi://127.0.0.1:44444/jndi/rmi://127.0.0.1:1099/karaf-root</property>
	            <property name="jmxUsername">karaf</property>
	            <property name="jmxPassword">karaf</property>
			</configuration>
		</container>
	</arquillian>


For [Apache Aries JMX](http://aries.apache.org/modules/jmx.html), enable RMI access to the JVM and use the following configuration:

	<?xml version="1.0" encoding="UTF-8"?>
	<arquillian xmlns="http://jboss.org/schema/arquillian" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		xsi:schemaLocation="http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd">

		<container qualifier="aries" default="true">
			<configuration>
	            <property name="jmxServiceURL">service:jmx:rmi:///jndi/rmi://localhost:1090/jmxrmi</property>
	            <property name="jmxUsername">username</property>
	            <property name="jmxPassword">password</property>
			</configuration>
		</container>
	</arquillian>

Managed Container Setup
-----------------------

Managed containers are started in a separate JVM by the Arquillian OSGi Container.

For an [Apache Karaf](http://karaf.apache.org/) managed container, setup these dependencies:

    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian.container</groupId>
            <artifactId>arquillian-container-karaf-managed</artifactId>
        </dependency>
    </dependencies>

... and specify in the arquillian.xml resource the location of a Karaf installation, and JMX connection details:

	<?xml version="1.0" encoding="UTF-8"?>
	<arquillian xmlns="http://jboss.org/schema/arquillian"
	    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	    xsi:schemaLocation="http://jboss.org/schema/arquillian http://jboss.org/schema/arquillian/arquillian_1_0.xsd">

	    <container qualifier="jboss" default="true">
	        <configuration>
	            <property name="autostartBundle">false</property>
	            <property name="karafHome">target/apache-karaf-${version.apache.karaf}</property>
	            <property name="javaVmArguments">-agentlib:jdwp=transport=dt_socket,address=5005,server=y,suspend=n</property>
	            <property name="jmxServiceURL">service:jmx:rmi://127.0.0.1:44444/jndi/rmi://127.0.0.1:1099/karaf-root</property>
	            <property name="jmxUsername">karaf</property>
	            <property name="jmxPassword">karaf</property>
	        </configuration>
	    </container>
	</arquillian>


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

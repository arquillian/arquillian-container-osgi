package org.jboss.test.arquillian.container.complex;

import java.io.InputStream;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

/**
 * Complex deployment test case.
 * Verifies inheritance + usage Junit packages in parent class
 *
 * @author jbouska@redhat.com
 * @since 17-July-2018
 */
@RunWith(Arquillian.class)
public class ComplexDeploymentTestCase extends ComplexDeploymentParent{

    @Deployment
    public static JavaArchive createdeployment() {
       final JavaArchive archive= ShrinkWrap.create(JavaArchive.class, "test.jar")
               .addClass(CustomException.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackage(ExpectedException.class.getPackage().getName());
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void inheritanceTest(){
        inheritanceTest(true);
    }

    @Test
    public void ruleTest() throws CustomException {
        thrown.expect(CustomException.class);
        throw new CustomException("This exception should be catch by Junit rule");
    }
}

package org.jboss.test.arquillian.container.complex;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

public class ComplexDeploymentParent {

    @Rule
    public ExpectedException thrown= ExpectedException.none();


    protected void inheritanceTest(boolean x) {
        Assert.assertTrue(x);
    }
}

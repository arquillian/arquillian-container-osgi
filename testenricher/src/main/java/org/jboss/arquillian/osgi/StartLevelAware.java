package org.jboss.arquillian.osgi;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Defines the bundle start level for this deployment.
 *
 * @author thomas.diesler@jboss.com
 * @since 07-Jun-2011
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface StartLevelAware {

    /**
     * Defines whether the bundle should start automatically
     */
    boolean autostart() default false;

    /**
     * Defines the start level for this bundle deployment.
     */
    int startLevel() default 1;

}

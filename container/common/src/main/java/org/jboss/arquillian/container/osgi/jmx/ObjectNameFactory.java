package org.jboss.arquillian.container.osgi.jmx;

import java.util.Hashtable;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * A simple factory for creating safe object names.
 *
 * @author Thomas.Diesler@jboss.org
 * @since 08-May-2006
 */
public class ObjectNameFactory {

    public static ObjectName create(String name) {
        try {
            return new ObjectName(name);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Malformed ObjectName", e);
        }
    }

    public static ObjectName create(String domain, String key, String value) {
        try {
            return new ObjectName(domain, key, value);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Malformed ObjectName", e);
        }
    }

    public static ObjectName create(String domain, Hashtable<String, String> table) {
        try {
            return new ObjectName(domain, table);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Malformed ObjectName", e);
        }
    }
}

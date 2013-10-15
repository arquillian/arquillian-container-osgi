/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.arquillian.container.osgi.felix;

import org.jboss.logging.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

/**
 * An integration with the Felix Logger.
 *
 * This Logger gets registered with the Felix framework and
 * delegates framework log messages to jboss-logging.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Mar-2009
 */
public class FelixLogger extends org.apache.felix.framework.Logger {

    // Provide logging
    private static final Logger log = Logger.getLogger(FelixLogger.class);

    public FelixLogger() {
        setLogLevel(LOG_DEBUG);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void doLog(Bundle bundle, ServiceReference sref, int level, String msg, Throwable throwable) {
        if (bundle != null)
            msg = "[" + bundle.getSymbolicName() + "] " + msg;
        if (sref != null)
            msg = sref + ": " + msg;

        // An unresolved bundle causes a WARNING that comes with an exception
        // Currently we log WARNING exceptions at DEBUG level

        if (level == LOG_DEBUG) {
            log.debug(msg, throwable);
        } else if (level == LOG_INFO) {
            log.info(msg, throwable);
        } else if (level == LOG_WARNING) {
            log.warn(msg);
            if (throwable != null)
                log.debug(msg, throwable);
        } else if (level == LOG_ERROR) {
            log.error(msg, throwable);
        }
    }
}
/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core;

import org.togglz.core.logging.Jdk14LogProvider;
import org.togglz.core.logging.Log;
import org.togglz.core.spi.LogProvider;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MuleLogProvider implements LogProvider {

  public MuleLogProvider() {}

  public int priority() {
    return 1000;
  }

  public Log getLog(String name) {
    return new Jdk14LogProvider.Jdk14Log(name);
  }

  public static class Jdk14Log implements Log {

    private final Logger logger;

    public Jdk14Log(String name) {
      this.logger = Logger.getLogger(name);
    }

    public boolean isDebugEnabled() {
      return this.logger.isLoggable(Level.FINE);
    }

    public void debug(String msg) {
      this.logger.fine(msg);
    }

    public void info(String msg) {
      this.logger.info(msg);
    }

    public void warn(String msg) {
      this.logger.warning(msg);
    }

    public void error(String msg) {
      this.logger.severe(msg);
    }

    public void error(String msg, Throwable e) {
      this.logger.log(Level.SEVERE, msg, e);
    }
  }
}

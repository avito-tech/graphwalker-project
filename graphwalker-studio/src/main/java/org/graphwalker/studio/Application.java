package org.graphwalker.studio;

/*
 * #%L
 * GraphWalker Core
 * %%
 * Original work Copyright (c) 2005 - 2018 GraphWalker
 * Modified work Copyright (c) 2018 - 2019 Avito
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.apache.commons.lang3.StringUtils;
import org.graphwalker.studio.util.LoggerUtil;
import org.graphwalker.websocket.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import static org.graphwalker.io.common.Util.getVersionString;

/**
 * @author Nils Olsson
 */
@ComponentScan
@EnableAutoConfiguration
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws UnknownHostException {
    Application app = new Application();
    try {
      app.run(args);
    } catch (Exception e) {
      // We should have caught all exceptions up until here, but there
      // might have been problems with the command parser for instance...
      System.err.println(e + System.lineSeparator());
      logger.error("An error occurred when running command: " + StringUtils.join(args, " "), e);
    }
  }

  private void run(String[] args) {
    Options options = new Options();
    JCommander jc = new JCommander(options);
    jc.setProgramName("java -jar graphwalker.jar");
    try {
      jc.parseWithoutValidation(args);
    } catch (Exception e) {
      // ignore
    }

    try {
      setLogLevel(options);

      if (options.help) {
        options = new Options();
        jc = new JCommander(options);
        jc.parse(args);
        jc.usage();
        return;
      } else if (options.version) {
        System.out.println(printVersionInformation());
        return;
      }

      WebSocketServer gwSocketServer = new WebSocketServer(options.wsPort);
      gwSocketServer.start();

      Properties props = System.getProperties();
      props.setProperty("server.port", String.valueOf(options.browserPort));

      SpringApplication application = new SpringApplication(Application.class);
      Environment environment = application.run(args).getEnvironment();
      logger.info("Access URLs:\n----------------------------------------------------------\n" +
        "  Local web service:          http://127.0.0.1:" + options.browserPort + "\n" +
        "  External web service:       http://" + InetAddress.getLocalHost().getHostAddress() + ":" + options.browserPort + "\n" +
        "  Local websocket service:    http://127.0.0.1:" + options.wsPort + "\n" +
        "  External websocket service: http://" + InetAddress.getLocalHost().getHostAddress() + ":" + options.wsPort
        + "\n----------------------------------------------------------");

    } catch (ParameterException e) {
      System.err.println("An error occurred when running command: " + StringUtils.join(args, " "));
      System.err.println(e.getMessage() + System.lineSeparator());
      if (jc.getParsedCommand() != null) {
        jc.usage(jc.getParsedCommand());
      }
    } catch (Exception e) {
      System.err.println("An error occurred when running command: " + StringUtils.join(args, " "));
      System.err.println(e.getMessage() + System.lineSeparator());
      logger.error("An error occurred when running command: " + StringUtils.join(args, " "), e);
    }
  }

  private void setLogLevel(Options options) {
    // OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL
    if (options.debug.equalsIgnoreCase("OFF")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.OFF);
    } else if (options.debug.equalsIgnoreCase("ERROR")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.ERROR);
    } else if (options.debug.equalsIgnoreCase("WARN")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.WARN);
    } else if (options.debug.equalsIgnoreCase("INFO")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.INFO);
    } else if (options.debug.equalsIgnoreCase("DEBUG")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.DEBUG);
    } else if (options.debug.equalsIgnoreCase("TRACE")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.TRACE);
    } else if (options.debug.equalsIgnoreCase("ALL")) {
      LoggerUtil.setLogLevel(LoggerUtil.Level.ALL);
    } else {
      throw new ParameterException("Incorrect argument to --debug");
    }
  }

  private String printVersionInformation() {
    String version = "org.graphwalker version: " + getVersionString() + System.lineSeparator();
    version += System.lineSeparator();

    version += "org.graphwalker is open source software licensed under MIT license" + System.lineSeparator();
    version += "The software (and it's source) can be downloaded from http://graphwalker.org" + System.lineSeparator();
    version +=
      "For a complete list of this package software dependencies, see http://graphwalker.org/archive/site/graphwalker-cli/dependencies.html" + System.lineSeparator();

    return version;
  }
}

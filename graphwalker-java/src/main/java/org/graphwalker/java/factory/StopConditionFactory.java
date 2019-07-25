package org.graphwalker.java.factory;

/*
 * #%L
 * GraphWalker Java
 * %%
 * Original work Copyright (c) 2005 - 2014 GraphWalker
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

import org.graphwalker.core.condition.StopCondition;
import org.graphwalker.java.annotation.GraphWalker;
import org.graphwalker.java.test.TestExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nils Olsson
 */
public abstract class StopConditionFactory {

  private static final Logger logger = LoggerFactory.getLogger(StopConditionFactory.class);

  public static StopCondition createStopCondition(GraphWalker annotation) {
    String value = annotation.stopConditionValue();
    Class<? extends StopCondition> stopCondition = annotation.stopCondition();
    if (value.isEmpty()) {
      try {
        return stopCondition.newInstance();
      } catch (Throwable t) {
        logger.error(t.getMessage());
      }
    }
    try {
      return stopCondition.getConstructor(new Class[]{String.class}).newInstance(value);
    } catch (Throwable t) {
      logger.error(t.getMessage());
    }
    try {
      return stopCondition.getConstructor(new Class[]{Long.TYPE}).newInstance(Long.parseLong(value));
    } catch (Throwable t) {
      logger.error(t.getMessage());
    }
    try {
      return stopCondition.getConstructor(new Class[]{Integer.TYPE}).newInstance(Integer.parseInt(value));
    } catch (Throwable t) {
      logger.error(t.getMessage());
    }
    try {
      return stopCondition.getConstructor(new Class[]{Double.TYPE}).newInstance(Double.parseDouble(value));
    } catch (Throwable t) {
      logger.error(t.getMessage());
    }
    try {
      return stopCondition.getConstructor(new Class[]{Float.TYPE}).newInstance(Float.parseFloat(value));
    } catch (Throwable t) {
      logger.error(t.getMessage());
    }
    throw new TestExecutionException();
  }
}

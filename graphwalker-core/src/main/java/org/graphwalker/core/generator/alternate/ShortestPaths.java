package org.graphwalker.core.generator.alternate;

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

import org.graphwalker.core.model.Element;
import org.graphwalker.core.model.Path;

import java.util.List;

import static java.lang.Math.pow;

/**
 * Selects k paths with lowest distance.
 *
 * @implSpec {@code sum((n-i)/distance)}
 * @author Ivan Bonkin
 */
public class ShortestPaths extends FitnessFunction {

  public ShortestPaths(int size) {
    super(size);
  }

  @Override
  public double value(List<Path<Element>> sizedPaths) {

    double pathsOrderingBonus = 0;
    for (int i = 0; i < size; i++) {
      pathsOrderingBonus += 1. * (size - i) / distance(sizedPaths.get(i)) * pow(weight(sizedPaths.get(i)), .5);
    }

    return pathsOrderingBonus;
  }
}

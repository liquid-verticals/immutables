/*
   Copyright 2014 Immutables Authors and Contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.immutables.generator;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import java.util.Arrays;
import java.util.Iterator;
import javax.annotation.Nullable;
import static com.google.common.base.Preconditions.*;

/**
 * Basis for the functionality of generated templates
 */
public final class Templates {
  private Templates() {}

  public interface Binary<L, R, T> {
    T apply(L left, R right);
  }

  public interface Apply<T> {
    T apply(Object... parameters);
  }

  public interface Invokable {
    @Nullable
    Invokable invoke(Invokation parentInvokation, Object... parameters);

    int arity();
  }

  static class CharConsumer {
    private final StringBuilder builder = new StringBuilder();
    /** Index after indentation. */
    private int lineStartIndex;
    public CharSequence indentation = "";
    private boolean delimit;
    private boolean wasNewline = true;

    void append(CharSequence string) {
      beforeAppend();
      builder.append(string);
    }

    void append(String string) {
      beforeAppend();
      if (string != null) {
        builder.append(string);
      }
    }

    void append(char c) {
      if (c == '\n') {
        newline();
      } else {
        beforeAppend();
        builder.append(c);
      }
    }

    private void beforeAppend() {
      if (wasNewline) {
        indent();
        wasNewline = false;
      }
    }

    public CharSequence getCurrentIndentation() {
      CharSequence sequence = currentLine();
      return sequence.length() > 0 && CharMatcher.WHITESPACE.matchesAllOf(sequence)
          ? sequence
          : indentation;
    }

    private CharSequence currentLine() {
      // Optimize subsequence if necessary
      return builder.subSequence(lineStartIndex, builder.length());
    }

    private void indent() {
      if (indentation != null) {
        builder.append(indentation);
      }
    }

    /** makes next newline remove previous whitespace line */
    private void delimit() {
      delimit = true;
    }

    void newline() {
      if (delimit && wasBlankLine()) {
        builder.setLength(lineStartIndex);
        delimit = false;
      } else {
        builder.append('\n');
        delimit = false;
      }
      lineStartIndex = builder.length();
      wasNewline = true;
    }

    private boolean wasBlankLine() {
      return CharMatcher.WHITESPACE.matchesAllOf(currentLine());
    }

    @Override
    public String toString() {
      return builder.toString();
    }

    public CharSequence asCharSequence() {
      return builder;
    }
  }

  public static class Iteration {
    public int index = 0;
    public boolean first = true;
  }

  public final static class Invokation {
    @Nullable
    final CharConsumer consumer;
    private final Object[] params;

    static Invokation initial() {
      return new Invokation(null, "");
    }

    Invokation(@Nullable CharConsumer consumer, Object... params) {
      this.consumer = consumer;
      this.params = checkNotNull(params);
    }

    public int arity() {
      return params.length;
    }

    public Object param(int ordinal) {
      return params[ordinal];
    }

    public Invokation dl() {
      if (consumer != null) {
        consumer.delimit();
      }
      return this;
    }

    public Invokation ln() {
      if (consumer != null) {
        consumer.newline();
      }
      return this;
    }

    public Invokation out(String string) {
      if (consumer != null) {
        consumer.append(string);
      }
      return this;
    }

    public Invokation out(@Nullable Object content) {
      if (content instanceof Invokable) {
        content = ((Invokable) content).invoke(this);
      }
      if (content == null) {
        return this;
      }
      if (consumer != null) {
        consumer.append(content instanceof CharSequence
            ? ((CharSequence) content)
            : content.toString());
      }
      return this;
    }

    public Invokation out(Object... objects) {
      for (Object object : objects) {
        out(object);
      }
      return this;
    }

    /**
     * Specifies last processed position in template source file
     * @param pos the position in template source file
     * @return this invokation object for chained call
     */
    public Invokation pos(int pos) {
      throw new UnsupportedOperationException();
    }

    public CharSequence getCurrentIndentation() {
      return consumer != null ? consumer.getCurrentIndentation() : "";
    }
  }

  /**
   * Tuple-like combining of a values (product), could be iterated or converted to string by
   * concatenation of string forms (no separator).
   */
  static final class Product implements Iterable<Object> {
    private static final Joiner PLAIN_JOINER = Joiner.on("");

    private final Object[] elements;

    Product(Object[] elements) {
      this.elements = elements;
    }

    @Override
    public String toString() {
      return PLAIN_JOINER.join(elements);
    }

    @Override
    public Iterator<Object> iterator() {
      return Arrays.asList(elements).iterator();
    }
  }

  public static abstract class Fragment implements Invokable {
    private final int arity;

    /**
     * @param arity number of fragment parameters
     */
    protected Fragment(int arity) {
      assert arity >= 0;
      this.arity = arity;
    }

    @Override
    public int arity() {
      return arity;
    }

    public abstract void run(Invokation invokation);

    @Nullable
    @Override
    public Invokable invoke(Invokation invokation, Object... params) {
      CharSequence indentationToResore = "";
      if (invokation.consumer != null) {
        indentationToResore = invokation.consumer.indentation;
        // switch to the current indentation inside fragment
        invokation.consumer.indentation = invokation.consumer.getCurrentIndentation();
      }

      run(new Invokation(invokation.consumer, params));

      if (invokation.consumer != null) {
        invokation.consumer.indentation = indentationToResore;
      }

      return null;
    }

    private String cachedToString;

    CharSequence toCharSequence() {
      if (arity == 0) {
        CharConsumer consumer = new CharConsumer();
        invoke(new Invokation(consumer));
        CharSequence cs = consumer.asCharSequence();
        return cs;
      }
      return super.toString();
    }

    /**
     * Ability to pass caputured fragment and evaluate it as a string.
     * For non-captured fragments or fragments which expects parameters, plain {@code toString}
     * returned.
     */
    @Override
    public String toString() {
      if (cachedToString == null) {
        cachedToString = toCharSequence().toString();
      }
      return cachedToString;
    }
  }
}

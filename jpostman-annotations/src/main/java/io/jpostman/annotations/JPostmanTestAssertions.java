package io.jpostman.annotations;

import io.jpostman.secure.JPostmanAssertions;

/**
 * Framework-neutral facade for hard assertions used by {@link JPostman.Test}.
 */
public interface JPostmanTestAssertions extends JPostmanAssertions<JPostman.Test, JPostmanTestAssertions> {
}

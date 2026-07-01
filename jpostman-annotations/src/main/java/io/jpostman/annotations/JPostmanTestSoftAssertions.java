package io.jpostman.annotations;

import io.jpostman.secure.JPostmanSoftAssertions;

/**
 * Framework-neutral facade for soft assertions used by {@link JPostman.Test}.
 */
public interface JPostmanTestSoftAssertions extends JPostmanSoftAssertions<JPostman.Test, JPostmanTestSoftAssertions> {
}

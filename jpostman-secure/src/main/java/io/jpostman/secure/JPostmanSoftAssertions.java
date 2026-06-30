package io.jpostman.secure;

/**
 * Shared soft assertion contract used by the JPostman TestNG and JUnit modules.
 * <p>
 * TestNG and JUnit soft assertion implementations should extend this interface
 * so both modules expose the same fluent soft assertion API while still using
 * their framework-specific assertion behavior internally. The annotations
 * module can reuse this same contract from {@code JPostman.SoftAssertions}.
 */
public interface JPostmanSoftAssertions<C, A> extends JPostmanAssertions<C, A> {

	C assertAll();
}

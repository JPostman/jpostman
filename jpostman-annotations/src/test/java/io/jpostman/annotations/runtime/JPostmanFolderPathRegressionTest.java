package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jpostman.Collection;
import io.jpostman.Request;

/** Regression coverage for nested collection folder paths. */
public class JPostmanFolderPathRegressionTest {

	@Test
	public void resolvesNestedFolderLevelsInOrder() throws Exception {
		Collection collection = collection();

		List<Request> requests = JPostmanFolderPath.requests(collection, new String[] { "level1", "level2", "level3" });

		assertEquals(1, requests.size());
		assertEquals("Nested request", requests.get(0).getName());
		assertEquals("level1/level2/level3", requests.get(0).getFolderName());
		assertEquals("Nested request",
				JPostmanFolderPath.request(collection, "level1/level2/level3", "Nested request").getName());
	}

	@Test
	public void distinguishesFoldersWithTheSameLeafName() throws Exception {
		Collection collection = collection();

		assertEquals("First duplicate request", JPostmanFolderPath
				.requests(collection, new String[] { "level1", "level2", "Duplicate" }).get(0).getName());
		assertEquals("Second duplicate request",
				JPostmanFolderPath.requests(collection, new String[] { "other", "Duplicate" }).get(0).getName());
	}

	@Test
	public void rejectsMissingAndEmptyNestedLevels() throws Exception {
		Collection collection = collection();

		assertThrows(IllegalArgumentException.class,
				() -> JPostmanFolderPath.requests(collection, new String[] { "level1", "missing" }));
		assertThrows(IllegalArgumentException.class,
				() -> JPostmanFolderPath.requests(collection, new String[] { "level1", "", "level3" }));
	}

	private Collection collection() throws Exception {
		try (InputStream input = getClass().getResourceAsStream("/annotation-test-nested-collection.json")) {
			if (input == null) {
				throw new IllegalStateException("Nested collection test resource was not found.");
			}
			return Collection.load(input);
		}
	}
}

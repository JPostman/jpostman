package io.jpostman.schema.env;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request model used to update environment keys and values in an existing
 * ApiSpec model.
 */
public class ApiSpecEnvironmentUpdateRequest {
	private Map<String, String> renames = new LinkedHashMap<>();
	private Map<String, Object> adds = new LinkedHashMap<>();
	private Map<String, Object> values = new LinkedHashMap<>();
	private List<String> deletes = new ArrayList<>();

	/**
	 * Returns environment key renames, where map key is the old name and map value
	 * is the new name.
	 */
	public Map<String, String> getRenames() {
		return renames;
	}

	/**
	 * Sets environment key renames.
	 */
	public void setRenames(Map<String, String> renames) {
		this.renames = renames == null ? new LinkedHashMap<>() : renames;
	}

	/**
	 * Returns new environment key/value pairs to add. Adds fail fast when the key
	 * already exists after renames are applied.
	 */
	public Map<String, Object> getAdds() {
		return adds;
	}

	/**
	 * Sets new environment key/value pairs to add.
	 */
	public void setAdds(Map<String, Object> adds) {
		this.adds = adds == null ? new LinkedHashMap<>() : adds;
	}

	/**
	 * Returns environment value updates, where map key is the environment key and
	 * map value is the new environment value.
	 */
	public Map<String, Object> getValues() {
		return values;
	}

	/**
	 * Sets environment value updates.
	 */
	public void setValues(Map<String, Object> values) {
		this.values = values == null ? new LinkedHashMap<>() : values;
	}

	/**
	 * Returns environment keys to delete from the environment map. Token usages are
	 * intentionally left unchanged so request paths, params, headers, bodies, and
	 * examples still show the unresolved variable.
	 */
	public List<String> getDeletes() {
		return deletes;
	}

	/**
	 * Sets environment keys to delete.
	 */
	public void setDeletes(List<String> deletes) {
		this.deletes = deletes == null ? new ArrayList<>() : deletes;
	}
}

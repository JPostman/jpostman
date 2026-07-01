package io.jpostman.schema.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Common API document model used by JPostman importers. */
public class ApiSpec {
	private String name;
	private String baseUrl;
	private boolean overrideUrl;
	private List<ApiFolder> folders = new ArrayList<>();
	private List<ApiOperation> operations = new ArrayList<>();
	private Map<String, Object> envs = new LinkedHashMap<>();

	/**
	 * Returns the name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the base url.
	 */
	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * Sets the base url.
	 */
	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	/**
	 * Returns whether override url is enabled or true.
	 */
	public boolean isOverrideUrl() {
		return overrideUrl;
	}

	/**
	 * Sets the override url.
	 */
	public void setOverrideUrl(boolean overrideUrl) {
		this.overrideUrl = overrideUrl;
	}

	/**
	 * Returns the folders.
	 */
	public List<ApiFolder> getFolders() {
		return folders;
	}

	/**
	 * Sets the folders.
	 */
	public void setFolders(List<ApiFolder> folders) {
		this.folders = folders == null ? new ArrayList<>() : folders;
	}

	/**
	 * Returns the operations.
	 */
	public List<ApiOperation> getOperations() {
		return operations;
	}

	/**
	 * Sets the operations.
	 */
	public void setOperations(List<ApiOperation> operations) {
		this.operations = operations == null ? new ArrayList<>() : operations;
	}

	/**
	 * Returns the environment variable map collected from placeholders and
	 * examples.
	 */
	public Map<String, Object> getEnvs() {
		return envs;
	}

	/**
	 * Sets the envs.
	 */
	public void setEnvs(Map<String, Object> envs) {
		this.envs = envs == null ? new LinkedHashMap<>() : envs;
	}
}

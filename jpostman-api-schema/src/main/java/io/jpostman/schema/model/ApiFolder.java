package io.jpostman.schema.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a logical folder or group of API operations.
 */
public class ApiFolder {
	private String name;
	private String description;
	private List<ApiFolder> folders = new ArrayList<>();
	private List<ApiOperation> operations = new ArrayList<>();

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
	 * Returns the description.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description.
	 */
	public void setDescription(String description) {
		this.description = description;
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
}

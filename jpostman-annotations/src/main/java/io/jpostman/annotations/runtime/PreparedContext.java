package io.jpostman.annotations.runtime;

import io.jpostman.Collection;

final class PreparedContext<C> {
	final C context;
	final Collection collection;

	public PreparedContext(C context, Collection collection) {
		this.context = context;
		this.collection = collection;
	}
}

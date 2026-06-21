package io.jpostman.annotations.runtime;

import io.jpostman.Collection;
import io.jpostman.JPostman.Context;

final class PreparedContext<C> {

	final C context;
	final Context loaded;
	final Collection collection;

	PreparedContext(C context, Context loaded) {
		this.context = context;
		this.loaded = loaded;
		this.collection = loaded == null ? null : loaded.getCollection();
	}
}